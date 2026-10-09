import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  formatDate,
  formatTime,
  getQueryObject,
  byteLength,
  cleanArray,
  param,
  param2Obj,
  objectMerge,
  toggleClass,
  debounce,
  deepClone,
  uniqueArr,
  createUniqueString,
  hasClass,
  addClass,
  removeClass,
  isExternal
} from '@/utils/index';

describe('utils/index formatDate', () => {
  it('空值返回空串', () => {
    expect(formatDate('')).toBe('');
    expect(formatDate(null as any)).toBe('');
    expect(formatDate(undefined as any)).toBe('');
  });

  it('格式化为 yyyy-MM-dd HH:mm:ss', () => {
    expect(formatDate('2026-01-02 03:04:05')).toBe('2026-01-02 03:04:05');
  });
});

describe('utils/index formatTime', () => {
  it('30 秒内显示刚刚', () => {
    expect(formatTime(String(Date.now() - 5 * 1000), '')).toBe('刚刚');
  });

  it('一小时内显示分钟前', () => {
    expect(formatTime(String(Date.now() - 20 * 60 * 1000), '')).toMatch(/^\d+分钟前$/);
  });

  it('一天内显示小时前', () => {
    expect(formatTime(String(Date.now() - 2 * 60 * 60 * 1000), '')).toMatch(/^\d+小时前$/);
  });

  it('两天内显示 1 天前', () => {
    expect(formatTime(String(Date.now() - 30 * 60 * 60 * 1000), '')).toBe('1天前');
  });

  it('传入 option 时走 parseTime', () => {
    expect(formatTime(String(Date.now() - 72 * 60 * 60 * 1000), '{y}')).toMatch(/^\d{4}$/);
  });

  it('10 位字符串按秒级时间戳处理', () => {
    expect(formatTime('1600000000', '')).toMatch(/^\d+月\d+日\d+时\d+分$/);
  });
});

describe('utils/index getQueryObject', () => {
  it('解析查询串并解码', () => {
    expect(getQueryObject('http://localhost/?a=1&b=%E4%B8%AD')).toEqual({ a: '1', b: '中' });
  });

  it('无查询串返回空对象', () => {
    expect(getQueryObject('http://localhost')).toEqual({});
    expect(getQueryObject('http://localhost?')).toEqual({});
  });
});

describe('utils/index byteLength', () => {
  it('按 UTF-8 字节长度计算', () => {
    expect(byteLength('abc')).toBe(3);
    expect(byteLength('中')).toBe(3);
    expect(byteLength('中文')).toBe(6);
  });
});

describe('utils/index cleanArray / param / param2Obj', () => {
  it('cleanArray 移除假值项', () => {
    expect(cleanArray([0, 1, '', null, undefined, 2, 'a'])).toEqual([1, 2, 'a']);
  });

  it('param 拼接查询串', () => {
    expect(param({ a: 1, b: undefined, c: 'x y' })).toBe('a=1&c=x%20y');
    expect(param(null)).toBe('');
  });

  it('param2Obj 反解查询串', () => {
    expect(param2Obj('http://localhost/?a=1&b=hello%20world')).toEqual({ a: '1', b: 'hello world' });
    expect(param2Obj('http://localhost')).toEqual({});
  });
});

describe('utils/index objectMerge', () => {
  it('浅层合并', () => {
    expect(objectMerge({ a: 1 }, { b: 2 })).toEqual({ a: 1, b: 2 });
  });

  it('嵌套对象递归合并', () => {
    expect(objectMerge({ a: { x: 1 } }, { a: { y: 2 } })).toEqual({ a: { x: 1, y: 2 } });
  });

  it('source 为数组时直接复制', () => {
    expect(objectMerge({}, [1, 2])).toEqual([1, 2]);
  });

  it('非对象 target 以空对象起步', () => {
    expect(objectMerge('x' as any, { a: 1 })).toEqual({ a: 1 });
  });
});

describe('utils/index class helpers', () => {
  it('hasClass / addClass / removeClass', () => {
    const el = { className: 'foo bar' } as any;
    expect(hasClass(el, 'foo')).toBe(true);
    expect(hasClass(el, 'baz')).toBe(false);
    addClass(el, 'baz');
    expect(hasClass(el, 'baz')).toBe(true);
    addClass(el, 'baz');
    expect(el.className.match(/baz/g)).toHaveLength(1);
    removeClass(el, 'foo');
    expect(hasClass(el, 'foo')).toBe(false);
    expect(hasClass(el, 'bar')).toBe(true);
  });

  it('toggleClass 增删互逆', () => {
    const el = { className: '' } as any;
    toggleClass(el, 'x');
    expect(el.className).toBe('x');
    toggleClass(el, 'x');
    expect(el.className).toBe('');
    expect(toggleClass(null as any, 'x')).toBeUndefined();
    expect(toggleClass(el, '')).toBeUndefined();
  });
});

describe('utils/index debounce', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('immediate=true 首次立即执行', () => {
    const fn = vi.fn();
    const wrapped = debounce(fn, 100, true);
    wrapped('x');
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith('x');
  });

  it('immediate=false 等待期内合并为一次调用', () => {
    const fn = vi.fn();
    const wrapped = debounce(fn, 100, false);
    wrapped(1);
    wrapped(2);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(200);
    expect(fn).toHaveBeenCalledTimes(1);
  });
});

describe('utils/index deepClone', () => {
  it('深拷贝嵌套结构', () => {
    const source = { a: { b: 1 }, c: [1, { d: 2 }] };
    const target = deepClone(source);
    expect(target).toEqual(source);
    expect(target.a).not.toBe(source.a);
    expect(target.c).not.toBe(source.c);
    expect(target.c[1]).not.toBe(source.c[1]);
  });

  it('空入参抛错', () => {
    expect(() => deepClone(undefined)).toThrow();
    expect(() => deepClone(null)).toThrow();
  });
});

describe('utils/index uniqueArr / createUniqueString', () => {
  it('数组去重', () => {
    expect(uniqueArr([1, 1, 2, 'a', 'a', 3])).toEqual([1, 2, 'a', 3]);
  });

  it('生成非空 32 进制串', () => {
    const id = createUniqueString();
    expect(id).toMatch(/^[0-9a-z]+$/);
  });
});

describe('utils/index isExternal', () => {
  it('识别外链协议', () => {
    expect(isExternal('https://example.com')).toBe(true);
    expect(isExternal('http://example.com')).toBe(true);
    expect(isExternal('mailto:a@b.com')).toBe(true);
    expect(isExternal('tel:123')).toBe(true);
    expect(isExternal('/inner/path')).toBe(false);
    expect(isExternal('inner/path')).toBe(false);
  });
});
