package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用量统计（docs/21 AI-M1-09）：总量 + 按日聚合 + 按模型聚合。
 */
@Data
public class AiUsageStatVo {

    private long requests;

    private long promptTokens;

    private long completionTokens;

    private long totalTokens;

    private List<DayRow> byDay = new ArrayList<>();

    private List<ModelRow> byModel = new ArrayList<>();

    @Data
    public static class DayRow {
        private String day;
        private long requests;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
    }

    @Data
    public static class ModelRow {
        private String modelKey;
        private long requests;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
    }
}
