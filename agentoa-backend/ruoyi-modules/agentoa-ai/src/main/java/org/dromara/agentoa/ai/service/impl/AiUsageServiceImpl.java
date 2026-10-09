package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiUsageLog;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiUsageQueryBo;
import org.dromara.agentoa.ai.domain.vo.AiUsageExportVo;
import org.dromara.agentoa.ai.domain.vo.AiUsageLogVo;
import org.dromara.agentoa.ai.domain.vo.AiUsageStatVo;
import org.dromara.agentoa.ai.mapper.OaAiUsageLogMapper;
import org.dromara.agentoa.ai.service.IAiUsageService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.excel.utils.ExcelUtil;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量统计实现：按日期范围取明细，服务端聚合为日/模型维度（docs/21 AI-M1-09）。
 */
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements IAiUsageService {

    /** 聚合查询上限（超过则按时间倒序截断，保护内存） */
    private static final int AGGREGATION_LIMIT = 50000;

    private final OaAiUsageLogMapper usageLogMapper;

    @Override
    public AiUsageStatVo stats(AiUsageQueryBo query) {
        List<OaAiUsageLog> rows = usageLogMapper.selectList(baseWrapper(query)
            .orderByAsc(OaAiUsageLog::getCreateTime)
            .last("LIMIT " + AGGREGATION_LIMIT));
        AiUsageStatVo vo = new AiUsageStatVo();
        Map<String, AiUsageStatVo.DayRow> byDay = new LinkedHashMap<>();
        Map<String, AiUsageStatVo.ModelRow> byModel = new LinkedHashMap<>();
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (OaAiUsageLog row : rows) {
            long tokens = row.getTotalTokens() == null ? 0 : row.getTotalTokens();
            long prompt = row.getPromptTokens() == null ? 0 : row.getPromptTokens();
            long completion = row.getCompletionTokens() == null ? 0 : row.getCompletionTokens();
            vo.setRequests(vo.getRequests() + 1);
            vo.setPromptTokens(vo.getPromptTokens() + prompt);
            vo.setCompletionTokens(vo.getCompletionTokens() + completion);
            vo.setTotalTokens(vo.getTotalTokens() + tokens);

            String day = row.getCreateTime() == null ? "unknown" :
                row.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormat);
            AiUsageStatVo.DayRow dayRow = byDay.computeIfAbsent(day, key -> {
                AiUsageStatVo.DayRow created = new AiUsageStatVo.DayRow();
                created.setDay(key);
                return created;
            });
            dayRow.setRequests(dayRow.getRequests() + 1);
            dayRow.setPromptTokens(dayRow.getPromptTokens() + prompt);
            dayRow.setCompletionTokens(dayRow.getCompletionTokens() + completion);
            dayRow.setTotalTokens(dayRow.getTotalTokens() + tokens);

            String modelKey = row.getModelKey() == null ? "unknown" : row.getModelKey();
            AiUsageStatVo.ModelRow modelRow = byModel.computeIfAbsent(modelKey, key -> {
                AiUsageStatVo.ModelRow created = new AiUsageStatVo.ModelRow();
                created.setModelKey(key);
                return created;
            });
            modelRow.setRequests(modelRow.getRequests() + 1);
            modelRow.setPromptTokens(modelRow.getPromptTokens() + prompt);
            modelRow.setCompletionTokens(modelRow.getCompletionTokens() + completion);
            modelRow.setTotalTokens(modelRow.getTotalTokens() + tokens);
        }
        vo.getByDay().addAll(byDay.values());
        vo.getByModel().addAll(byModel.values());
        return vo;
    }

    @Override
    public PageVo<AiUsageLogVo> logs(AiUsageQueryBo query, AiPageQuery page) {
        IPage<OaAiUsageLog> result = usageLogMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            baseWrapper(query).orderByDesc(OaAiUsageLog::getCreateTime));
        List<AiUsageLogVo> records = new ArrayList<>();
        for (OaAiUsageLog row : result.getRecords()) {
            records.add(toVo(row));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public void export(AiUsageQueryBo query, HttpServletResponse response) {
        List<OaAiUsageLog> rows = usageLogMapper.selectList(baseWrapper(query)
            .orderByDesc(OaAiUsageLog::getCreateTime)
            .last("LIMIT " + AGGREGATION_LIMIT));
        List<AiUsageExportVo> data = new ArrayList<>();
        for (OaAiUsageLog row : rows) {
            AiUsageExportVo vo = new AiUsageExportVo();
            vo.setCreateTime(row.getCreateTime());
            vo.setUsername(row.getUsername());
            vo.setModelKey(row.getModelKey());
            vo.setBizType(row.getBizType());
            vo.setPromptTokens(row.getPromptTokens());
            vo.setCompletionTokens(row.getCompletionTokens());
            vo.setTotalTokens(row.getTotalTokens());
            vo.setLatencyMs(row.getLatencyMs());
            vo.setStatus(row.getStatus());
            vo.setErrorCode(row.getErrorCode());
            data.add(vo);
        }
        ExcelUtil.exportExcel(data, "AI 用量明细", AiUsageExportVo.class, response);
    }

    // ---------------------------------------------------------------- internals

    private LambdaQueryWrapper<OaAiUsageLog> baseWrapper(AiUsageQueryBo query) {
        LambdaQueryWrapper<OaAiUsageLog> wrapper = new LambdaQueryWrapper<OaAiUsageLog>();
        if (query != null) {
            wrapper.eq(query.getUserId() != null, OaAiUsageLog::getUserId, query.getUserId())
                .eq(query.getModelKey() != null && !query.getModelKey().isBlank(),
                    OaAiUsageLog::getModelKey, query.getModelKey() == null ? null : query.getModelKey().trim())
                .eq(query.getBizType() != null && !query.getBizType().isBlank(),
                    OaAiUsageLog::getBizType, query.getBizType())
                .ge(query.getBeginTime() != null, OaAiUsageLog::getCreateTime,
                    query.getBeginTime() == null ? defaultBegin() : query.getBeginTime())
                .le(query.getEndTime() != null, OaAiUsageLog::getCreateTime, query.getEndTime());
        }
        return wrapper;
    }

    private Date defaultBegin() {
        return Date.from(LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private AiUsageLogVo toVo(OaAiUsageLog row) {
        AiUsageLogVo vo = new AiUsageLogVo();
        vo.setId(row.getId());
        vo.setUserId(row.getUserId());
        vo.setUsername(row.getUsername());
        vo.setProviderId(row.getProviderId());
        vo.setModelKey(row.getModelKey());
        vo.setBizType(row.getBizType());
        vo.setConversationId(row.getConversationId());
        vo.setTaskId(row.getTaskId());
        vo.setPromptTokens(row.getPromptTokens());
        vo.setCompletionTokens(row.getCompletionTokens());
        vo.setTotalTokens(row.getTotalTokens());
        vo.setLatencyMs(row.getLatencyMs());
        vo.setStatus(row.getStatus());
        vo.setErrorCode(row.getErrorCode());
        vo.setErrorMsg(row.getErrorMsg());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
