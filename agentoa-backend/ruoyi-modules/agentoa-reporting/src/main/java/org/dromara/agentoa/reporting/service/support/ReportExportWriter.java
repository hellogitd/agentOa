package org.dromara.agentoa.reporting.service.support;

import cn.idev.excel.FastExcel;
import org.dromara.agentoa.reporting.domain.enums.ExportFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 导出内容生成：CSV（UTF-8 BOM）与 XLSX（FastExcel，无模板）。 */
public final class ReportExportWriter {

    private ReportExportWriter() {
    }

    public static byte[] write(ReportTable table, ExportFormat format, String sheetName) {
        return format == ExportFormat.XLSX ? toXlsx(table, sheetName) : toCsv(table);
    }

    public static byte[] toCsv(ReportTable table) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendLine(sb, table.headers());
        for (List<String> row : table.rows()) {
            appendLine(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toXlsx(ReportTable table, String sheetName) {
        List<List<String>> head = new ArrayList<>();
        for (String header : table.headers()) {
            head.add(List.of(header));
        }
        List<List<Object>> data = new ArrayList<>();
        for (List<String> row : table.rows()) {
            data.add(new ArrayList<>(row));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcel.write(out).head(head).sheet(sheetName == null ? "导出" : sheetName).doWrite(data);
        return out.toByteArray();
    }

    private static void appendLine(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    /** 便捷方法：写入流（同步下载用）。 */
    public static void writeTo(java.io.OutputStream out, ReportTable table, ExportFormat format, String sheetName) {
        try {
            out.write(write(table, format, sheetName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static OutputStreamWriter utf8Writer(java.io.OutputStream out) {
        return new OutputStreamWriter(out, StandardCharsets.UTF_8);
    }
}
