import { describe, it, expect } from 'vitest';
import { sanitizeRichHtml, filterStyle } from '@/utils/sanitize';

describe('utils/sanitize 标签白名单', () => {
  it('保留常规格式标签', () => {
    expect(sanitizeRichHtml('<p><strong>加粗</strong>和<em>斜体</em></p>')).toBe('<p><strong>加粗</strong>和<em>斜体</em></p>');
    expect(sanitizeRichHtml('<h2>标题</h2><ul><li>项</li></ul>')).toBe('<h2>标题</h2><ul><li>项</li></ul>');
    expect(sanitizeRichHtml('<blockquote>引用</blockquote>')).toBe('<blockquote>引用</blockquote>');
    expect(sanitizeRichHtml('换行<br>继续')).toBe('换行<br />继续');
  });

  it('script/style/iframe 连内容一起丢弃', () => {
    expect(sanitizeRichHtml('<p>a</p><script>alert(1)</script><p>b</p>')).toBe('<p>a</p><p>b</p>');
    expect(sanitizeRichHtml('<style>body{color:red}</style><p>x</p>')).toBe('<p>x</p>');
    expect(sanitizeRichHtml('<iframe src="https://evil"><p>内文</p></iframe>')).toBe('');
    expect(sanitizeRichHtml('<object data="x"><embed src="y"></object>')).toBe('');
    expect(sanitizeRichHtml('<svg><script>alert(1)</script></svg>')).toBe('');
  });

  it('白名单外标签解包保留文本', () => {
    expect(sanitizeRichHtml('<table><tr><td>单元格</td></tr></table>')).toBe('单元格');
    expect(sanitizeRichHtml('<font color="red">红字</font>')).toBe('红字');
    expect(sanitizeRichHtml('<html><head><meta charset="utf-8"><title>t</title></head><body><p>正文</p></body></html>')).toBe('<p>正文</p>');
  });

  it('注释/doctype/CDATA 丢弃', () => {
    expect(sanitizeRichHtml('<!-- 注释 --><p>x</p>')).toBe('<p>x</p>');
    expect(sanitizeRichHtml('<!DOCTYPE html><p>x</p>')).toBe('<p>x</p>');
    expect(sanitizeRichHtml('<![CDATA[abc]]><p>x</p>')).toBe('<p>x</p>');
  });

  it('文本节点转义，不产生新标签', () => {
    expect(sanitizeRichHtml('a < b 和 c > d')).toBe('a &lt; b 和 c &gt; d');
    expect(sanitizeRichHtml('<p>1 < 2</p>')).toBe('<p>1 &lt; 2</p>');
  });
});

describe('utils/sanitize 属性与 URL 白名单', () => {
  it('剥离 on* 事件属性', () => {
    const out = sanitizeRichHtml('<img src="https://a/x.png" alt="图" onerror="alert(1)">');
    expect(out).toBe('<img src="https://a/x.png" alt="图" />');
    const p = sanitizeRichHtml('<p onclick="alert(1)" onmouseover="x">文</p>');
    expect(p).toBe('<p>文</p>');
  });

  it('剥离 javascript:/vbscript:/data: 链接协议', () => {
    expect(sanitizeRichHtml('<a href="javascript:alert(1)">x</a>')).toBe('<a>x</a>');
    expect(sanitizeRichHtml('<a href="JAVASCRIPT:alert(1)">x</a>')).toBe('<a>x</a>');
    expect(sanitizeRichHtml('<a href="vbscript:msgbox(1)">x</a>')).toBe('<a>x</a>');
    expect(sanitizeRichHtml('<a href="data:text/html,<script>alert(1)</script>">x</a>')).toBe('<a>x</a>');
    expect(sanitizeRichHtml('<a href="java\nscript:alert(1)">x</a>')).toBe('<a>x</a>');
    expect(sanitizeRichHtml('<a href="%6aavascript:alert(1)">x</a>')).toBe('<a>x</a>');
  });

  it('放行 http/https/mailto/tel/相对链接并补安全属性', () => {
    const out = sanitizeRichHtml('<a href="https://a/b">链</a>');
    expect(out).toBe('<a href="https://a/b" target="_blank" rel="noopener noreferrer">链</a>');
    expect(sanitizeRichHtml('<a href="/notice?id=1">内链</a>')).toContain('href="/notice?id=1"');
    expect(sanitizeRichHtml('<a href="mailto:a@b.com">邮</a>')).toContain('mailto:a@b.com');
  });

  it('图片禁止 data: 内联与非法协议，无合法 src 整标签丢弃', () => {
    expect(sanitizeRichHtml('<img src="data:image/png;base64,AAAA">')).toBe('');
    expect(sanitizeRichHtml('<img src="data:text/html,<script>alert(1)</script>">')).toBe('');
    expect(sanitizeRichHtml('<img src="javascript:alert(1)" alt="x">')).toBe('');
    expect(sanitizeRichHtml('<img src="https://a/x.png">')).toBe('<img src="https://a/x.png" />');
    expect(sanitizeRichHtml('<img src="//cdn.a/x.png" alt="c">')).toBe('<img src="//cdn.a/x.png" alt="c" />');
  });

  it('style 属性按声明过滤', () => {
    const out = sanitizeRichHtml('<span style="color:red;background:url(javascript:1)">文</span>');
    expect(out).toBe('<span style="color: red">文</span>');
    expect(sanitizeRichHtml('<p style="expression(alert(1));text-align:center">文</p>')).toBe('<p style="text-align: center">文</p>');
    expect(filterStyle('font-size:12pt;behavior:url(x)')).toBe('font-size: 12pt');
    expect(filterStyle('position:fixed')).toBe('');
    expect(filterStyle('')).toBe('');
    expect(filterStyle(null as any)).toBe('');
  });
});
