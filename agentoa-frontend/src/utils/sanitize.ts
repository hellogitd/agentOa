/**
 * 富文本清洗（docs/22 F5-03 最小安全加固）。
 * 标签/属性白名单 + URL 协议白名单（复用 markdown.ts safeUrl）：
 * 供 vue-quill 编辑器粘贴拦截与受控 HTML 注入前净化，
 * 杜绝 javascript: 链接、on* 事件属性、script/iframe 注入面与 data: 内联图片（docs/21 §2.3 安全红线）。
 */
import { safeUrl } from '@/utils/markdown';

/** 允许的标签 -> 允许的属性（不在表内：标签解包、仅保留文本；DROP_WITH_CONTENT 内：连内容一起丢弃） */
const ALLOWED_ATTRS: Record<string, Set<string>> = {
  a: new Set(['href', 'title']),
  img: new Set(['src', 'alt']),
  p: new Set(['style']),
  div: new Set(['style']),
  span: new Set(['style']),
  blockquote: new Set(['style']),
  pre: new Set(['style']),
  code: new Set(['style']),
  ul: new Set(['style']),
  ol: new Set(['style']),
  li: new Set(['style']),
  h1: new Set(['style']),
  h2: new Set(['style']),
  h3: new Set(['style']),
  h4: new Set(['style']),
  h5: new Set(['style']),
  h6: new Set(['style']),
  strong: new Set(),
  b: new Set(),
  em: new Set(),
  i: new Set(),
  u: new Set(),
  s: new Set(),
  strike: new Set(),
  sup: new Set(),
  sub: new Set(),
  br: new Set(),
  hr: new Set()
};

/** 连同内容一起丢弃的标签（脚本/嵌入/文档包装） */
const DROP_WITH_CONTENT = new Set([
  'script',
  'style',
  'iframe',
  'object',
  'embed',
  'svg',
  'math',
  'template',
  'noscript',
  'form',
  'link',
  'meta',
  'base',
  'title',
  'head'
]);

/** 空元素 */
const VOID_TAGS = new Set(['br', 'hr', 'img']);

/** style 属性放行的声明（其余一律丢弃；url()/expression() 等值直接拒绝） */
const STYLE_PROPS = /^(color|background-color|text-align|font-size|font-weight|font-style|text-decoration)$/i;

const TAG_TOKEN = /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<![^>]*>|<(\/?)([a-zA-Z][a-zA-Z0-9-]*)((?:"[^"]*"|'[^']*'|[^'">])*)(\/?)>/g;
const ATTR_TOKEN = /([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*("[^"]*"|'[^']*'|[^\s"'`=<>]+))?/g;

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function escapeAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function unquote(raw: string): string {
  if (raw.length >= 2 && ((raw.startsWith('"') && raw.endsWith('"')) || (raw.startsWith("'") && raw.endsWith("'")))) {
    return raw.slice(1, -1);
  }
  return raw;
}

/** style 声明白名单过滤：仅保留安全属性，剔除 url()/expression()/转义伪装 */
export function filterStyle(value: string): string {
  return (value ?? '')
    .split(';')
    .map((declaration) => declaration.trim())
    .filter(Boolean)
    .map((declaration) => {
      const colon = declaration.indexOf(':');
      if (colon <= 0) {
        return '';
      }
      const prop = declaration.slice(0, colon).trim();
      const val = declaration.slice(colon + 1).trim();
      if (!STYLE_PROPS.test(prop) || !val) {
        return '';
      }
      if (/url\s*\(|expression\s*\(|javascript:|@import|\\3[\da-f]/i.test(val)) {
        return '';
      }
      return `${prop}: ${val.replace(/["'<>&]/g, '')}`;
    })
    .filter(Boolean)
    .join('; ');
}

function renderAttrs(tag: string, attrText: string): string | null {
  const allowed = ALLOWED_ATTRS[tag];
  const rendered: string[] = [];
  let hasHref = false;
  let hasSrc = false;
  ATTR_TOKEN.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = ATTR_TOKEN.exec(attrText)) !== null) {
    const name = match[1].toLowerCase();
    // on* 事件属性一律丢弃；非白名单属性丢弃
    if (name.startsWith('on') || !allowed.has(name)) {
      continue;
    }
    let value = match[2] == null ? '' : unquote(match[2]);
    if (name === 'href' || name === 'src') {
      // 图片禁止 data: 内联（docs/22 F5-03：图片一律走 sys_file 上传通道）
      const safe = safeUrl(value, { allowDataImage: false });
      if (!safe) {
        continue;
      }
      value = safe;
      if (name === 'href') {
        hasHref = true;
      } else {
        hasSrc = true;
      }
    } else if (name === 'style') {
      value = filterStyle(value);
      if (!value) {
        continue;
      }
    }
    rendered.push(` ${name}="${escapeAttr(value)}"`);
  }
  if (tag === 'img' && !hasSrc) {
    // 无合法 src 的图片整标签丢弃（残缺 img 无意义）
    return null;
  }
  if (tag === 'a' && hasHref) {
    rendered.push(' target="_blank"', ' rel="noopener noreferrer"');
  }
  return rendered.join('');
}

/**
 * 富文本 HTML 清洗：
 * - 白名单外标签解包（保留文本），script/iframe 等连内容丢弃；
 * - 属性白名单 + on* 拒绝 + URL 协议白名单（safeUrl）+ style 声明过滤；
 * - 文本节点转义，注释/CDATA/doctype 丢弃。
 */
export function sanitizeRichHtml(html: string): string {
  if (!html) {
    return '';
  }
  const out: string[] = [];
  let skipTag = '';
  let skipDepth = 0;
  let last = 0;
  TAG_TOKEN.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = TAG_TOKEN.exec(html)) !== null) {
    if (match.index > last && !skipTag) {
      out.push(escapeHtml(html.slice(last, match.index)));
    }
    last = match.index + match[0].length;
    const token = match[0];
    if (token.startsWith('<!--') || token.startsWith('<!')) {
      continue;
    }
    const closing = match[1] === '/';
    const tag = match[2].toLowerCase();
    const attrText = match[3] ?? '';
    const selfClosed = match[4] === '/' || VOID_TAGS.has(tag);
    if (skipTag) {
      if (tag === skipTag) {
        if (!closing && !selfClosed) {
          skipDepth++;
        } else if (closing) {
          skipDepth--;
          if (skipDepth <= 0) {
            skipTag = '';
          }
        }
      }
      continue;
    }
    if (DROP_WITH_CONTENT.has(tag)) {
      if (!closing && !selfClosed) {
        skipTag = tag;
        skipDepth = 1;
      }
      continue;
    }
    if (!(tag in ALLOWED_ATTRS)) {
      // 白名单外：解包保留文本
      continue;
    }
    if (closing) {
      if (!VOID_TAGS.has(tag)) {
        out.push(`</${tag}>`);
      }
      continue;
    }
    const attrs = renderAttrs(tag, attrText);
    if (attrs == null) {
      continue;
    }
    out.push(VOID_TAGS.has(tag) ? `<${tag}${attrs} />` : `<${tag}${attrs}>`);
  }
  if (!skipTag && last < html.length) {
    out.push(escapeHtml(html.slice(last)));
  }
  return out.join('');
}
