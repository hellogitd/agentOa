package org.dromara.agentoa.ai.service.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.dromara.common.core.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 直传文件解析（docs/21 AI-M3-02）：PDF/DOCX/TXT/MD → 纯文本。
 * <p>
 * 三重校验口径同私有文件：扩展名 + 魔数 + 大小；解析失败按 400 归一化。
 */
public final class AiDocumentParser {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private AiDocumentParser() {
    }

    /** 校验并返回小写扩展名（pdf/docx/txt/md），非法 400 */
    public static String verifiedType(String name, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            throw new ServiceException("AI_KB_FILE_SIZE 文件大小非法（1B-20MiB）", 400);
        }
        String safe = name == null ? "" : name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        int dot = safe.lastIndexOf('.');
        String ext = dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
        boolean ok = switch (ext) {
            case "pdf" -> bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
            case "docx" -> bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
            case "txt", "md" -> !containsNul(bytes);
            default -> false;
        };
        if (!ok) {
            throw new ServiceException("AI_KB_FILE_TYPE 仅支持 pdf/docx/txt/md，且内容需与扩展名一致", 400);
        }
        return ext;
    }

    /** 提取纯文本 */
    public static String parse(String name, byte[] bytes) {
        String ext = verifiedType(name, bytes);
        try {
            return switch (ext) {
                case "pdf" -> parsePdf(bytes);
                case "docx" -> parseDocx(bytes);
                default -> new String(bytes, StandardCharsets.UTF_8);
            };
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AI_KB_FILE_PARSE 文件解析失败：" + safeMessage(e), 400);
        }
    }

    private static String parsePdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String parseDocx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String line = paragraph.getText();
                if (line != null && !line.isBlank()) {
                    text.append(line).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String value = cell.getText();
                        if (value != null && !value.isBlank()) {
                            text.append(value).append(' ');
                        }
                    }
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
