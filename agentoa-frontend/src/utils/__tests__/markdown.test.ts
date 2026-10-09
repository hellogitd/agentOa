import { describe, it, expect } from 'vitest';
import { markdownToHtml, safeUrl, diffLines } from '@/utils/markdown';

describe('utils/markdown safeUrl 协议白名单', () => {
  it('放行 http/https/mailto/tel', () => {
    expect(safeUrl('https://example.com/a')).toBe('https://example.com/a');
    expect(safeUrl('http://example.com')).toBe('http://example.com');
    expect(safeUrl('mailto:a@b.com')).toBe('mailto:a@b.com');
    expect(safeUrl('tel:+8610000')).toBe('tel:+8610000');
  });

  it('放行相对路径与锚点', () => {
    expect(safeUrl('/knowledge/document?docId=1')).toBe('/knowledge/document?docId=1');
    expect(safeUrl('./a.png')).toBe('./a.png');
    expect(safeUrl('../a')).toBe('../a');
    expect(safeUrl('#anchor')).toBe('#anchor');
    expect(safeUrl('foo/bar')).toBe('foo/bar');
    expect(safeUrl('//cdn.example.com/x.png')).toBe('//cdn.example.com/x.png');
  });

  it('拒绝 javascript/vbscript/data 等可执行协议', () => {
    expect(safeUrl('javascript:alert(1)')).toBe('');
    expect(safeUrl('JAVASCRIPT:alert(1)')).toBe('');
    expect(safeUrl('vbscript:msgbox(1)')).toBe('');
    expect(safeUrl('data:text/html,<script>alert(1)</script>')).toBe('');
    expect(safeUrl('file:///etc/passwd')).toBe('');
  });

  it('拒绝控制符/百分号编码伪装的协议头', () => {
    expect(safeUrl('java\nscript:alert(1)')).toBe('');
    expect(safeUrl('java\tscript:alert(1)')).toBe('');
    expect(safeUrl('java\rscript:alert(1)')).toBe('');
    expect(safeUrl('  javascript:alert(1)')).toBe('');
    expect(safeUrl('%6aavascript:alert(1)')).toBe('');
  });

  it('图片单独放行栅格 data 协议，拒绝 svg data 协议', () => {
    expect(safeUrl('data:image/png;base64,iVBORw0KGgo=', { allowDataImage: true })).toBe('data:image/png;base64,iVBORw0KGgo=');
    expect(safeUrl('data:image/jpeg;base64,AAAA', { allowDataImage: true })).toBe('data:image/jpeg;base64,AAAA');
    expect(safeUrl('data:image/svg+xml;base64,PHN2Zz4=', { allowDataImage: true })).toBe('');
    // 未显式放行时不接受 data 协议
    expect(safeUrl('data:image/png;base64,iVBORw0KGgo=')).toBe('');
  });

  it('空值返回空串', () => {
    expect(safeUrl('')).toBe('');
    expect(safeUrl('   ')).toBe('');
    expect(safeUrl(null as any)).toBe('');
    expect(safeUrl(undefined as any)).toBe('');
  });
});

describe('utils/markdown markdownToHtml XSS 防护', () => {
  it('转义原始 HTML 标签', () => {
    const html = markdownToHtml('<script>alert(1)</script>');
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('图片 src 走白名单，非法协议降级为 alt 文本', () => {
    const blocked = markdownToHtml('![x](javascript:alert(1))');
    expect(blocked).not.toContain('<img');
    expect(blocked).not.toContain('javascript:');
    expect(blocked).toContain('x');

    const allowed = markdownToHtml('![封面](https://example.com/a.png)');
    expect(allowed).toContain('<img alt="封面" src="https://example.com/a.png"');
  });

  it('链接 href 走白名单，非法协议降级为纯文本', () => {
    const blocked = markdownToHtml('[点我](javascript:alert(1))');
    expect(blocked).not.toContain('<a ');
    expect(blocked).not.toContain('javascript:');
    expect(blocked).toContain('点我');

    const allowed = markdownToHtml('[文档](https://example.com/doc)');
    expect(allowed).toContain('<a href="https://example.com/doc" target="_blank" rel="noopener noreferrer">文档</a>');
  });

  it('属性值内的引号被转义，无法闭合属性', () => {
    const html = markdownToHtml('![" onerror="alert(1)](https://example.com/a.png)');
    expect(html).not.toMatch(/onerror="alert\(1\)"/);
    expect(html).toContain('alt="&quot; onerror=&quot;alert(1)"');
    expect(html).toContain('src="https://example.com/a.png"');
  });

  it('链接协议对比不区分大小写且不吃控制符', () => {
    expect(markdownToHtml('[x](JaVaScRiPt:alert(1))')).not.toContain('<a ');
    // NUL 不在链接正则的空白类内，能走到协议判定并被拦截
    expect(markdownToHtml('[x](java\u0000script:alert(1))')).not.toContain('<a ');
    expect(markdownToHtml('[x](java\u0000script:alert(1))')).toContain('x');
    // 含空白的 URL 根本不会被识别为链接
    expect(markdownToHtml('[x](java\nscript:alert(1))')).not.toContain('<a ');
  });

  it('正常 markdown 结构仍可渲染', () => {
    const html = markdownToHtml('# 标题\n\n**加粗** 与 *斜体*\n\n- 项一\n- 项二');
    expect(html).toContain('<h1');
    expect(html).toContain('<strong>加粗</strong>');
    expect(html).toContain('<em>斜体</em>');
    expect(html).toContain('<ul');
  });
});

describe('utils/markdown diffLines', () => {
  it('识别增删行', () => {
    const result = diffLines('a\nb\nc', 'a\nx\nc');
    expect(result.map((r) => r.type)).toEqual(['same', 'del', 'add', 'same']);
    expect(result[1].text).toBe('b');
    expect(result[2].text).toBe('x');
  });

  it('空文本不抛错', () => {
    expect(diffLines('', 'x').map((r) => r.type)).toEqual(['del', 'add']);
    expect(diffLines('x', '').map((r) => r.type)).toEqual(['del', 'add']);
    expect(diffLines('', '').map((r) => r.type)).toEqual(['same']);
  });
});
