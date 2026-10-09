package org.dromara.agentoa.knowledge.service.support;

/**
 * Markdown 纯文本化与命中高亮（docs/16：搜索先用 MySQL 可验证查询）。
 */
public final class MarkdownText {

    private MarkdownText() {
    }

    /** 去掉 Markdown 标记，得到可搜索纯文本（docs/04 content_text） */
    public static String strip(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = markdown;
        text = text.replaceAll("(?s)```.*?```", " ");
        text = text.replaceAll("(?s)~~~.*?~~~", " ");
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        text = text.replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("(?m)^\\s*>+\\s?", "");
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        text = text.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");
        text = text.replaceAll("[`*_~]{1,3}", "");
        text = text.replaceAll("(?m)^\\s*\\|?[\\s:|-]*[-]{3,}[\\s:|-]*$", " ");
        text = text.replace('|', ' ');
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    /** 生成带 <em> 高亮的命中上下文 */
    public static String highlight(String plainText, String keyword) {
        if (plainText == null) {
            plainText = "";
        }
        if (keyword == null || keyword.isBlank()) {
            return clip(plainText, 160);
        }
        String lower = plainText.toLowerCase();
        int idx = lower.indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return clip(plainText, 160);
        }
        int start = Math.max(0, idx - 60);
        int end = Math.min(plainText.length(), idx + keyword.length() + 60);
        String head = (start > 0 ? "..." : "") + plainText.substring(start, idx);
        String hit = plainText.substring(idx, idx + keyword.length());
        String tail = plainText.substring(idx + keyword.length(), end) + (end < plainText.length() ? "..." : "");
        return escape(head) + "<em>" + escape(hit) + "</em>" + escape(tail);
    }

    private static String clip(String text, int max) {
        if (text.length() <= max) {
            return escape(text);
        }
        return escape(text.substring(0, max)) + "...";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
