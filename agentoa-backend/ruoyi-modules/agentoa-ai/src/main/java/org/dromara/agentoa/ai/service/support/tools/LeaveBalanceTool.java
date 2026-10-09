package org.dromara.agentoa.ai.service.support.tools;

import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.reporting.domain.vo.ReportBalanceVo;
import org.dromara.agentoa.reporting.mapper.ReportQueryMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 请假余额查询工具（docs/21 AI-M5-01）：只返回当前用户本人余额，不越权。
 */
@Component
public class LeaveBalanceTool implements AgentTool {

    private final ReportQueryMapper reportQueryMapper;

    public LeaveBalanceTool(ReportQueryMapper reportQueryMapper) {
        this.reportQueryMapper = reportQueryMapper;
    }

    @Override
    public String code() {
        return "leave_balance";
    }

    @Override
    public String name() {
        return "请假余额查询";
    }

    @Override
    public String description() {
        return "查询当前用户指定年份的各类假期剩余额度";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"year\":{\"type\":\"integer\",\"description\":\"年份，如 2026\"}}}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        int year = parseYear(argumentsJson);
        List<ReportBalanceVo> balances = reportQueryMapper.leaveBalances(userId, year);
        if (balances == null || balances.isEmpty()) {
            return year + " 年没有假期余额记录";
        }
        StringBuilder sb = new StringBuilder(year + " 年假期余额：\n");
        for (ReportBalanceVo balance : balances) {
            sb.append("- ").append(balance.getLeaveType() == null ? "假期" : balance.getLeaveType())
                .append("：可用 ").append(minutes(balance.getAvailableMinutes()))
                .append("（已用 ").append(minutes(balance.getUsedMinutes()))
                .append("，总额 ").append(minutes(balance.getTotalMinutes())).append("）\n");
        }
        return sb.toString();
    }

    private String minutes(Integer value) {
        if (value == null) {
            return "0 小时";
        }
        return (value / 60.0) + " 小时";
    }

    private int parseYear(String argumentsJson) {
        if (argumentsJson != null && !argumentsJson.isBlank()) {
            try {
                Integer year = cn.hutool.json.JSONUtil.parseObj(argumentsJson).getInt("year");
                if (year != null && year > 1970 && year < 2100) {
                    return year;
                }
            } catch (RuntimeException ignored) {
                // 落回当前年份
            }
        }
        return java.time.LocalDate.now().getYear();
    }
}
