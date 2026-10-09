/**
 * 轻量 Markdown 渲染与文本对比（知识库 P0：左右分栏预览、版本对比）。
 * 输出前统一转义 HTML，链接/图片走协议白名单，满足 XSS 输出转义要求（docs/22 §5）。
 */
import hljs from 'highlight.js/lib/common';

export type DiffLineType = 'same' | 'add' | 'del';

export interface DiffLine {
  type: DiffLineType;
  text: string;
}

/** 允许的链接协议：拒绝 javascript:/vbscript:/data: 等可执行协议 */
const SAFE_URL_SCHEMES = new Set(['http:', 'https:', 'mailto:', 'tel:']);
/** 图片额外放行的 data 协议（仅栅格格式，不含 svg 以防内嵌脚本） */
const SAFE_DATA_IMAGE = /^data:image\/(?:png|jpe?g|gif|webp|bmp|avif|x-icon|vnd\.microsoft\.icon);/i;

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/**
 * URL 协议白名单校验。
 * 先抹掉 C0 控制符与空白再判协议，避免 `java\nscript:`、`%6aavascript:` 等绕过；
 * 不放行时返回空串，由调用方降级为纯文本（保留可读内容，不产出可点击面）。
 */
export function safeUrl(raw: string, options: { allowDataImage?: boolean } = {}): string {
  const trimmed = (raw ?? '').trim();
  if (!trimmed) {
    return '';
  }
  // URL 解析器会剔除制表/换行/回车与首尾 C0 控制符，先按同样口径归一再判定
  const normalized = trimmed.replace(/[\u0000-\u0020\u007f]/g, '');
  const schemeOf = (value: string) => {
    const matched = /^([a-zA-Z][a-zA-Z0-9+.-]*):/.exec(value);
    return matched ? matched[1].toLowerCase() + ':' : '';
  };
  let scheme = schemeOf(normalized);
  if (!scheme) {
    // 百分号编码可能伪装协议头（%6aavascript:），解码后二次判定
    try {
      scheme = schemeOf(decodeURIComponent(normalized).replace(/[\u0000-\u0020\u007f]/g, ''));
    } catch {
      scheme = '';
    }
  }
  if (scheme && SAFE_URL_SCHEMES.has(scheme)) {
    return trimmed;
  }
  if (options.allowDataImage && SAFE_DATA_IMAGE.test(normalized)) {
    return trimmed;
  }
  // 无协议头即相对路径/锚点，放行
  return scheme ? '' : trimmed;
}

function renderInline(text: string): string {
  let out = escapeHtml(text);
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>');
  out = out.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (_match, alt: string, src: string) => {
    const url = safeUrl(src, { allowDataImage: true });
    return url ? `<img alt="${alt}" src="${url}" style="max-width:100%" />` : alt;
  });
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_match, label: string, href: string) => {
    const url = safeUrl(href);
    return url ? `<a href="${url}" target="_blank" rel="noopener noreferrer">${label}</a>` : label;
  });
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/~~([^~]+)~~/g, '<del>$1</del>');
  out = out.replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>');
  return out;
}

/** Markdown -> HTML（支持标题、列表、引用、代码块、表格、行内样式） */
export function markdownToHtml(markdown: string): string {
  if (!markdown) {
    return '';
  }
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const html: string[] = [];
  let inCode = false;
  let codeLang = '';
  let codeLines: string[] = [];
  let inList: 'ul' | 'ol' | null = null;
  let tableRows: string[] = [];

  const flushList = () => {
    if (inList) {
      html.push(inList === 'ul' ? '</ul>' : '</ol>');
      inList = null;
    }
  };
  const flushTable = () => {
    if (tableRows.length > 0) {
      const rows = tableRows.filter((r) => !/^\s*\|?[\s:|-]*-{3,}[\s:|-]*$/.test(r));
      const cells = (row: string) =>
        row
          .replace(/^\s*\|/, '')
          .replace(/\|\s*$/, '')
          .split('|')
          .map((c) => renderInline(c.trim()));
      html.push('<table style="border-collapse:collapse;width:100%">');
      rows.forEach((row, index) => {
        html.push('<tr>');
        cells(row).forEach((cell) => {
          const tag = index === 0 ? 'th' : 'td';
          html.push(`<${tag} style="border:1px solid #dcdfe6;padding:4px 8px">${cell}</${tag}>`);
        });
        html.push('</tr>');
      });
      html.push('</table>');
      tableRows = [];
    }
  };

  for (const line of lines) {
    if (inCode) {
      if (/^```/.test(line)) {
        const code = escapeHtml(codeLines.join('\n'));
        try {
          const highlighted = codeLang && hljs.getLanguage(codeLang) ? hljs.highlight(codeLines.join('\n'), { language: codeLang }).value : code;
          html.push(`<pre style="background:#f5f7fa;padding:12px;border-radius:4px;overflow:auto"><code>${highlighted}</code></pre>`);
        } catch {
          html.push(`<pre style="background:#f5f7fa;padding:12px;border-radius:4px;overflow:auto"><code>${code}</code></pre>`);
        }
        inCode = false;
        codeLines = [];
        codeLang = '';
      } else {
        codeLines.push(line);
      }
      continue;
    }
    const fence = /^```(\w*)/.exec(line);
    if (fence) {
      flushList();
      flushTable();
      inCode = true;
      codeLang = fence[1] || '';
      continue;
    }
    if (/^\s*\|/.test(line)) {
      flushList();
      tableRows.push(line);
      continue;
    }
    flushTable();
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      flushList();
      const level = heading[1].length;
      html.push(`<h${level} style="margin:12px 0 8px">${renderInline(heading[2])}</h${level}>`);
      continue;
    }
    if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) {
      flushList();
      html.push('<hr style="border:none;border-top:1px solid #dcdfe6;margin:12px 0" />');
      continue;
    }
    const quote = /^&gt;\s?(.*)$/.exec(renderInline(line));
    if (/^\s*>/.test(line)) {
      flushList();
      html.push(
        `<blockquote style="border-left:4px solid #dcdfe6;margin:8px 0;padding:4px 12px;color:#606266">${renderInline(line.replace(/^\s*>\s?/, ''))}</blockquote>`
      );
      continue;
    }
    void quote;
    const ul = /^\s*[-*+]\s+(.*)$/.exec(line);
    if (ul) {
      if (inList !== 'ul') {
        flushList();
        html.push('<ul style="padding-left:24px;margin:8px 0">');
        inList = 'ul';
      }
      html.push(`<li>${renderInline(ul[1])}</li>`);
      continue;
    }
    const ol = /^\s*\d+\.\s+(.*)$/.exec(line);
    if (ol) {
      if (inList !== 'ol') {
        flushList();
        html.push('<ol style="padding-left:24px;margin:8px 0">');
        inList = 'ol';
      }
      html.push(`<li>${renderInline(ol[1])}</li>`);
      continue;
    }
    flushList();
    if (line.trim() === '') {
      continue;
    }
    html.push(`<p style="margin:8px 0;line-height:1.7">${renderInline(line)}</p>`);
  }
  if (inCode) {
    html.push(`<pre style="background:#f5f7fa;padding:12px;border-radius:4px;overflow:auto"><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
  }
  flushList();
  flushTable();
  return html.join('\n');
}

/** 行级 LCS 对比（版本比较） */
export function diffLines(oldText: string, newText: string): DiffLine[] {
  const oldLines = (oldText || '').replace(/\r\n/g, '\n').split('\n');
  const newLines = (newText || '').replace(/\r\n/g, '\n').split('\n');
  const m = oldLines.length;
  const n = newLines.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] = oldLines[i] === newLines[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  const result: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (oldLines[i] === newLines[j]) {
      result.push({ type: 'same', text: oldLines[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      result.push({ type: 'del', text: oldLines[i] });
      i++;
    } else {
      result.push({ type: 'add', text: newLines[j] });
      j++;
    }
  }
  while (i < m) {
    result.push({ type: 'del', text: oldLines[i++] });
  }
  while (j < n) {
    result.push({ type: 'add', text: newLines[j++] });
  }
  return result;
}
