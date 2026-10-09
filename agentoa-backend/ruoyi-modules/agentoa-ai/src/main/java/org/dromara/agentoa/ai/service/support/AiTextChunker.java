package org.dromara.agentoa.ai.service.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本清洗与分块（docs/21 AI-M3-03）：Markdown 去标记 → 按标题/段落边界分块，
 * 默认 512 token/块、64 token 重叠；token 口径同 {@link TokenEstimator}。
 */
public final class AiTextChunker {

    /** 默认块大小（token） */
    public static final int DEFAULT_TARGET_TOKENS = 512;
    /** 默认重叠（token） */
    public static final int DEFAULT_OVERLAP_TOKENS = 64;

    private AiTextChunker() {
    }

    /** 一个分块 */
    public record Chunk(int seq, String heading, String content, int tokenCount) {
    }

    public static List<Chunk> chunk(String text) {
        return chunk(text, DEFAULT_TARGET_TOKENS, DEFAULT_OVERLAP_TOKENS);
    }

    /**
     * 分块：优先标题/段落边界；单块超限按行切；相邻块重叠 {@code overlapTokens}。
     */
    public static List<Chunk> chunk(String text, int targetTokens, int overlapTokens) {
        List<Chunk> chunks = new ArrayList<>();
        String cleaned = text == null ? "" : text.strip();
        if (cleaned.isEmpty()) {
            return chunks;
        }
        int target = Math.max(1, targetTokens);
        int overlap = Math.max(0, Math.min(overlapTokens, target / 2));

        List<Block> blocks = splitBlocks(cleaned);
        StringBuilder buffer = new StringBuilder();
        String heading = null;
        String bufferHeading = null;
        String carry = "";
        int seq = 0;
        for (Block block : blocks) {
            if (block.heading() != null) {
                // 标题是优先切分边界：先收尾当前块
                if (buffer.length() > 0) {
                    String content = compose(carry, buffer.toString());
                    chunks.add(new Chunk(seq++, bufferHeading == null ? heading : bufferHeading, content,
                        TokenEstimator.estimate(content)));
                    carry = tailTokens(buffer.toString(), overlap);
                    buffer.setLength(0);
                }
                heading = block.heading();
            }
            String piece = block.text();
            if (piece.isEmpty()) {
                continue;
            }
            // 超长块按行再切
            for (String part : splitOversized(piece, target)) {
                int merged = TokenEstimator.estimate(buffer.toString()) + TokenEstimator.estimate(part)
                    + TokenEstimator.estimate(carry);
                if (buffer.length() > 0 && merged > target) {
                    String content = compose(carry, buffer.toString());
                    chunks.add(new Chunk(seq++, bufferHeading == null ? heading : bufferHeading, content,
                        TokenEstimator.estimate(content)));
                    carry = tailTokens(buffer.toString(), overlap);
                    buffer.setLength(0);
                }
                if (buffer.length() == 0) {
                    bufferHeading = heading;
                }
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(part);
            }
        }
        if (buffer.length() > 0 || !chunks.isEmpty()) {
            String content = compose(carry, buffer.toString());
            if (!content.isEmpty()) {
                chunks.add(new Chunk(seq, bufferHeading == null ? heading : bufferHeading, content,
                    TokenEstimator.estimate(content)));
            }
        }
        return chunks;
    }

    /** Markdown 去标记（正文用纯文本参与分块与检索） */
    public static String stripMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = markdown.replace("\r\n", "\n");
        text = text.replaceAll("(?m)^\\s*```.*$", " ");
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*>+\\s?", "");
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        text = text.replaceAll("(?m)^\\s*\\d+[.)]\\s+", "");
        text = text.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("(```|~~~|\\*\\*|__|`|\\*|_|~~)", "");
        text = text.replaceAll("(?m)^\\s*\\|[-:| ]+\\|\\s*$", " ");
        text = text.replace('|', ' ');
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    // ---------------------------------------------------------------- internals

    private record Block(String heading, String text) {
    }

    /** 按标题行与空行段落切块 */
    private static List<Block> splitBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            boolean headingLine = line.matches("#{1,6}\\s+.*");
            if (headingLine) {
                flush(blocks, paragraph);
                blocks.add(new Block(line.replaceFirst("^#{1,6}\\s+", "").strip(), ""));
                continue;
            }
            if (line.isBlank()) {
                flush(blocks, paragraph);
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }
        flush(blocks, paragraph);
        return blocks;
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            blocks.add(new Block(null, paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    /** 单块超限时按行均分到 target 内 */
    private static List<String> splitOversized(String piece, int targetTokens) {
        List<String> parts = new ArrayList<>();
        if (TokenEstimator.estimate(piece) <= targetTokens) {
            parts.add(piece);
            return parts;
        }
        StringBuilder current = new StringBuilder();
        for (String line : piece.split("\n", -1)) {
            if (current.length() > 0
                && TokenEstimator.estimate(current.toString()) + TokenEstimator.estimate(line) > targetTokens) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
            while (TokenEstimator.estimate(current.toString()) > targetTokens * 2) {
                String text = current.toString();
                int cut = Math.max(1, text.length() / 2);
                parts.add(text.substring(0, cut));
                current = new StringBuilder(text.substring(cut));
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    /** 取尾部约 overlap token 的文本作为下一块重叠前缀 */
    private static String tailTokens(String text, int overlapTokens) {
        if (overlapTokens <= 0 || text.isEmpty()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").strip();
        if (TokenEstimator.estimate(normalized) <= overlapTokens) {
            return normalized;
        }
        double budget = 0;
        int index = normalized.length();
        while (index > 0 && budget < overlapTokens) {
            char c = normalized.charAt(index - 1);
            budget += c >= 0x2E80 ? 1.0d : 0.25d;
            index--;
        }
        return normalized.substring(Math.max(0, index)).strip();
    }

    private static String compose(String carry, String body) {
        if (carry == null || carry.isEmpty()) {
            return body.strip();
        }
        return (carry + "\n" + body).strip();
    }
}
