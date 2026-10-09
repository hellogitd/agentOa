package org.dromara.agentoa.finance.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报销承接单明细解析（oa_reimburse_request.details_json，模块 2 承接单格式）。
 */
public final class ClaimDetailParser {

    private ClaimDetailParser() {
    }

    /** 明细行：invoiceId 优先，invoiceNo 为模块 2 草案兼容字段 */
    public record ClaimDetail(Long invoiceId, String invoiceNo, Long expenseTypeId, String expenseType,
                              LocalDate occurDate, long amountFen, String description, int sort) {
    }

    public static List<ClaimDetail> parse(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return List.of();
        }
        List<? extends Map<String, Object>> rows = JsonUtils.parseArrayMap(detailsJson);
        List<ClaimDetail> details = new ArrayList<>();
        int sort = 0;
        for (Map<String, Object> row : rows) {
            Long invoiceId = asLong(row.get("invoiceId"));
            String invoiceNo = asString(row.get("invoiceNo"));
            Long expenseTypeId = asLong(row.get("expenseTypeId"));
            String expenseType = asString(row.get("expenseType"));
            Object occur = row.get("occurDate");
            LocalDate occurDate = occur == null || asString(occur) == null ? null : LocalDate.parse(asString(occur));
            Object amount = row.get("amount");
            if (amount == null) {
                throw new ServiceException("FINANCE_AMOUNT_INVALID 明细金额不能为空", 400);
            }
            long amountFen = MoneyUtil.toFen(String.valueOf(amount));
            details.add(new ClaimDetail(invoiceId, invoiceNo, expenseTypeId, expenseType, occurDate,
                amountFen, asString(row.get("description")), sort++));
        }
        return details;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equals(text)) {
            return null;
        }
        return Long.valueOf(text);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
