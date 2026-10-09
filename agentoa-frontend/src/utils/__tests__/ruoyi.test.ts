import { describe, it, expect } from 'vitest';
import {
  parseTime,
  addDateRange,
  selectDictLabel,
  selectDictLabels,
  parseStrEmpty,
  mergeRecursive,
  handleTree,
  tansParams,
  getNormalPath,
  blobValidate
} from '@/utils/ruoyi';

describe('utils/ruoyi parseTime', () => {
  it('空值返回 null', () => {
    expect(parseTime(undefined)).toBeNull();
    expect(parseTime('')).toBeNull();
    expect(parseTime(0)).toBeNull();
  });

  it('Date 对象按默认模板格式化', () => {
    expect(parseTime(new Date(2026, 0, 2, 3, 4, 5))).toBe('2026-01-02 03:04:05');
  });

  it('支持自定义模板', () => {
    expect(parseTime(new Date(2026, 0, 2, 3, 4, 5), '{y}-{m}-{d}')).toBe('2026-01-02');
    expect(parseTime(new Date(2026, 0, 2, 3, 4, 5), '{h}:{i}:{s}')).toBe('03:04:05');
  });

  it('10 位数字字符串按秒级时间戳解析', () => {
    expect(parseTime('1600000000')).toBe(parseTime(1600000000000));
  });

  it('日期字符串解析后回显一致', () => {
    expect(parseTime('2026-01-02 03:04:05')).toBe('2026-01-02 03:04:05');
    expect(parseTime('2026-01-02T03:04:05.000')).toBe('2026-01-02 03:04:05');
  });

  it('星期模板 {a} 输出中文星期', () => {
    expect(parseTime(new Date(2026, 0, 4), '{a}')).toBe('日');
    expect(parseTime(new Date(2026, 0, 5), '{a}')).toBe('一');
  });
});

describe('utils/ruoyi addDateRange', () => {
  it('默认写入 beginTime/endTime', () => {
    const result = addDateRange({ a: 1 }, ['2026-01-01', '2026-01-02']);
    expect(result.a).toBe(1);
    expect(result.params.beginTime).toBe('2026-01-01');
    expect(result.params.endTime).toBe('2026-01-02');
  });

  it('指定 propName 写入 begin/end + propName', () => {
    const result = addDateRange({ a: 1 }, ['2026-01-01', '2026-01-02'], 'CreateTime');
    expect(result.params.beginCreateTime).toBe('2026-01-01');
    expect(result.params.endCreateTime).toBe('2026-01-02');
    expect(result.params.beginTime).toBeUndefined();
  });

  it('非数组日期范围不报错', () => {
    const result = addDateRange({ a: 1 }, undefined as any);
    expect(result.params).toEqual({ beginTime: undefined, endTime: undefined });
  });

  it('已有 params 对象时保留既有键', () => {
    const result = addDateRange({ params: { keep: 1 } }, ['x', 'y']);
    expect(result.params.keep).toBe(1);
    expect(result.params.beginTime).toBe('x');
  });
});

describe('utils/ruoyi selectDictLabel / selectDictLabels', () => {
  const dict = [
    { value: '1', label: '男' },
    { value: '2', label: '女' }
  ];

  it('命中字典值回显标签', () => {
    expect(selectDictLabel(dict, '1')).toBe('男');
    expect(selectDictLabel(dict, 2)).toBe('女');
  });

  it('未命中回显原始值', () => {
    expect(selectDictLabel(dict, '9')).toBe('9');
  });

  it('undefined 返回空串', () => {
    expect(selectDictLabel(dict, undefined as any)).toBe('');
    expect(selectDictLabels(dict, undefined as any, ',')).toBe('');
    expect(selectDictLabels(dict, '', ',')).toBe('');
  });

  it('多值按分隔符回显', () => {
    expect(selectDictLabels(dict, '1,2', ',')).toBe('男,女');
    expect(selectDictLabels(dict, ['1', '2'], ',')).toBe('男,女');
    expect(selectDictLabels(dict, '1,9', ',')).toBe('男,9');
    expect(selectDictLabels(dict, '1;2', ';')).toBe('男;女');
  });
});

describe('utils/ruoyi parseStrEmpty', () => {
  it('空值归一为空串', () => {
    expect(parseStrEmpty(null)).toBe('');
    expect(parseStrEmpty(undefined)).toBe('');
    expect(parseStrEmpty('')).toBe('');
    expect(parseStrEmpty('undefined')).toBe('');
    expect(parseStrEmpty('null')).toBe('');
    expect(parseStrEmpty(0)).toBe('');
  });

  it('真实值原样返回', () => {
    expect(parseStrEmpty('abc')).toBe('abc');
  });
});

describe('utils/ruoyi mergeRecursive', () => {
  it('对象深度合并', () => {
    const source = { a: 1, b: { c: 1 } };
    const result = mergeRecursive(source, { b: { d: 2 }, e: 3 });
    expect(result).toEqual({ a: 1, b: { c: 1, d: 2 }, e: 3 });
  });

  it('非对象值直接覆盖', () => {
    expect(mergeRecursive({ a: 1 }, { a: 2 })).toEqual({ a: 2 });
  });
});

describe('utils/ruoyi handleTree', () => {
  it('默认 id/parentId 构造树', () => {
    const tree = handleTree<any>([
      { id: 1, parentId: 0 },
      { id: 2, parentId: 1 },
      { id: 3, parentId: 0 }
    ]);
    expect(tree).toHaveLength(2);
    expect(tree[0].id).toBe(1);
    expect(tree[0].children).toHaveLength(1);
    expect(tree[0].children[0].id).toBe(2);
    expect(tree[1].id).toBe(3);
    expect(tree[1].children).toHaveLength(0);
  });

  it('自定义字段名', () => {
    const tree = handleTree<any>(
      [
        { code: 'a', up: '' },
        { code: 'b', up: 'a' }
      ],
      'code',
      'up',
      'kids'
    );
    expect(tree).toHaveLength(1);
    expect(tree[0].code).toBe('a');
    expect(tree[0].kids[0].code).toBe('b');
  });
});

describe('utils/ruoyi tansParams', () => {
  it('跳过 null/空串/undefined 并以 & 结尾', () => {
    expect(tansParams({ a: 1, b: '', c: null, d: undefined, e: 'x' })).toBe('a=1&e=x&');
  });

  it('对象参数展开为 bracket 形式', () => {
    expect(tansParams({ q: { k: 'v', empty: '' } })).toBe('q%5Bk%5D=v&');
  });

  it('对特殊字符编码', () => {
    expect(tansParams({ 'a b': 'c d' })).toBe('a%20b=c%20d&');
  });
});

describe('utils/ruoyi getNormalPath', () => {
  it('归一化重复斜杠与结尾斜杠', () => {
    expect(getNormalPath('a//b')).toBe('a/b');
    expect(getNormalPath('/api/')).toBe('/api');
  });

  it('空串与 undefined 字符串原样返回', () => {
    expect(getNormalPath('')).toBe('');
    expect(getNormalPath('undefined')).toBe('undefined');
  });
});

describe('utils/ruoyi blobValidate', () => {
  it('JSON 响应不是真实 blob', () => {
    expect(blobValidate({ type: 'application/json' })).toBe(false);
  });

  it('二进制响应判定为 blob', () => {
    expect(blobValidate({ type: 'application/octet-stream' })).toBe(true);
  });
});
