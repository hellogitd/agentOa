package org.dromara.agentoa.reporting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.reporting.domain.OaReportExport;
import org.dromara.agentoa.reporting.domain.bo.ExportCreateBo;
import org.dromara.agentoa.reporting.domain.bo.ExportFilters;
import org.dromara.agentoa.reporting.domain.bo.ExportPageQuery;
import org.dromara.agentoa.reporting.domain.enums.ExportFormat;
import org.dromara.agentoa.reporting.domain.enums.ExportStatus;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.dromara.agentoa.reporting.domain.vo.ExportVo;
import org.dromara.agentoa.reporting.mapper.OaReportExportMapper;
import org.dromara.agentoa.reporting.service.IReportExportService;
import org.dromara.agentoa.reporting.service.IReportMetricService;
import org.dromara.agentoa.reporting.service.IReportQueryService;
import org.dromara.agentoa.reporting.service.support.ExportFileStore;
import org.dromara.agentoa.reporting.service.support.ReportExportWriter;
import org.dromara.agentoa.reporting.service.support.ReportRange;
import org.dromara.agentoa.reporting.service.support.ReportTable;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 报表导出实现：同步与异步共用同一 SQL 口径；导出任务记录操作者、过滤条件、口径版本与私有文件。
 * 异步处理在调度线程执行，按任务记录的范围读取，不依赖登录上下文。
 */
@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements IReportExportService {

    /** 同步导出行上限。 */
    public static final int MAX_SYNC_ROWS = 10000;
    /** 异步导出行上限。 */
    public static final int MAX_ASYNC_ROWS = 50000;

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OaReportExportMapper exportMapper;
    private final IReportQueryService queryService;
    private final IReportMetricService metricService;
    private final ExportFileStore fileStore;

    @Override
    public ExportVo create(ExportCreateBo bo, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("RP_IDEMPOTENCY_KEY 缺少 Idempotency-Key 请求头", 400);
        }
        if (idempotencyKey.length() > 64) {
            throw new ServiceException("RP_IDEMPOTENCY_KEY Idempotency-Key 长度不能超过 64", 400);
        }
        ReportType type = requireType(bo.getReportType());
        ExportFormat format = bo.getFormat() == null || bo.getFormat().isBlank()
            ? ExportFormat.CSV : requireFormat(bo.getFormat());
        Long userId = LoginHelper.getUserId();
        ExportFilters filters = buildFilters(bo);
        String filtersJson = JsonUtils.toJsonString(filters);

        OaReportExport existing = exportMapper.selectOne(new LambdaQueryWrapper<OaReportExport>()
            .eq(OaReportExport::getRequestedBy, userId)
            .eq(OaReportExport::getIdempotencyKey, idempotencyKey)
            .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getReportType().equals(type.code())
                && existing.getFormat().equals(format.name())
                && filtersJson.equals(existing.getFilters())) {
                return toVo(existing);
            }
            throw new ServiceException("IDEMPOTENCY_CONFLICT 相同幂等键对应不同导出请求", 409);
        }

        OaReportExport task = new OaReportExport();
        task.setExportNo(newExportNo());
        task.setReportType(type.code());
        task.setMetricVersion(metricService.currentVersion(type));
        task.setFormat(format.name());
        task.setFilters(filtersJson);
        task.setStatus(ExportStatus.PENDING.name());
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestedBy(userId);
        task.setCreateTime(LocalDateTime.now());
        try {
            exportMapper.insert(task);
        } catch (DuplicateKeyException e) {
            OaReportExport raced = exportMapper.selectOne(new LambdaQueryWrapper<OaReportExport>()
                .eq(OaReportExport::getRequestedBy, userId)
                .eq(OaReportExport::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
            if (raced != null && raced.getFilters().equals(filtersJson)) {
                return toVo(raced);
            }
            throw new ServiceException("IDEMPOTENCY_CONFLICT 相同幂等键对应不同导出请求", 409);
        }
        return toVo(task);
    }

    @Override
    public ExportVo get(Long id) {
        OaReportExport task = requireTask(id);
        return toVo(task);
    }

    @Override
    public PageVo<ExportVo> list(ExportPageQuery query) {
        Long userId = LoginHelper.getUserId();
        boolean all = ReportingAccessPolicy.canExport(LoginHelper.isSuperAdmin(), permissions());
        LambdaQueryWrapper<OaReportExport> filter = new LambdaQueryWrapper<OaReportExport>()
            .eq(!all, OaReportExport::getRequestedBy, userId)
            .eq(query.getReportType() != null && !query.getReportType().isBlank(),
                OaReportExport::getReportType, query.getReportType())
            .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                OaReportExport::getStatus, query.getStatus());
        Long total = exportMapper.selectCount(filter);
        List<ExportVo> records = new ArrayList<>();
        for (OaReportExport task : exportMapper.selectList(filter
            .orderByDesc(OaReportExport::getCreateTime)
            .last("LIMIT " + query.safePageSize() + " OFFSET " + ((query.safePageNum() - 1) * query.safePageSize())))) {
            records.add(toVo(task));
        }
        return PageVo.of(records, total == null ? 0 : total, query.safePageNum(), query.safePageSize());
    }

    @Override
    public void processPending() {
        List<OaReportExport> pending = exportMapper.selectList(new LambdaQueryWrapper<OaReportExport>()
            .eq(OaReportExport::getStatus, ExportStatus.PENDING.name())
            .orderByAsc(OaReportExport::getCreateTime)
            .last("LIMIT 10"));
        for (OaReportExport task : pending) {
            int claimed = exportMapper.update(null, new LambdaUpdateWrapper<OaReportExport>()
                .eq(OaReportExport::getId, task.getId())
                .eq(OaReportExport::getStatus, ExportStatus.PENDING.name())
                .set(OaReportExport::getStatus, ExportStatus.RUNNING.name()));
            if (claimed == 0) {
                continue;
            }
            run(task);
        }
    }

    @Override
    public ReportTable syncExport(ReportType type, ExportFormat format, ExportFilters filters) {
        Long deptId = queryService.resolveDeptId(filters.getDeptId());
        filters.setDeptId(deptId);
        filters.setScope(deptId == null ? "ALL" : "DEPT");
        ReportRange range = rangeOf(filters);
        ReportTable table = queryService.table(type, range, deptId, MAX_SYNC_ROWS);
        record(type, format, filters, table.rows().size(), null, null, ExportStatus.SUCCESS);
        return table;
    }

    @Override
    public ReportRange rangeOf(ExportFilters filters) {
        if (filters == null) {
            return ReportRange.currentMonth();
        }
        LocalDate start = filters.getStartDate() == null || filters.getStartDate().isBlank()
            ? null : LocalDate.parse(filters.getStartDate(), DAY_FORMAT);
        LocalDate end = filters.getEndDate() == null || filters.getEndDate().isBlank()
            ? null : LocalDate.parse(filters.getEndDate(), DAY_FORMAT);
        return start == null && end == null ? ReportRange.currentMonth() : ReportRange.of(start, end);
    }

    // ---------------------------------------------------------------- helpers

    private void run(OaReportExport task) {
        try {
            ReportType type = requireType(task.getReportType());
            ExportFormat format = requireFormat(task.getFormat());
            ExportFilters filters = JsonUtils.parseObject(task.getFilters(), ExportFilters.class);
            ReportRange range = rangeOf(filters);
            ReportTable table = queryService.table(type, range,
                filters == null ? null : filters.getDeptId(), MAX_ASYNC_ROWS);
            byte[] bytes = ReportExportWriter.write(table, format, type.label());
            String fileName = type.label() + "-" + LocalDate.now().format(DAY_FORMAT)
                + "." + format.extension();
            long fileId = fileStore.store(fileName, bytes, task.getRequestedBy());
            task.setFileId(fileId);
            task.setRowCount(table.rows().size());
            task.setStatus(ExportStatus.SUCCESS.name());
            task.setFinishTime(LocalDateTime.now());
            exportMapper.updateById(task);
        } catch (Exception e) {
            task.setStatus(ExportStatus.FAILED.name());
            task.setErrorMessage(truncate(e.getMessage(), 480));
            task.setFinishTime(LocalDateTime.now());
            exportMapper.updateById(task);
        }
    }

    private OaReportExport record(ReportType type, ExportFormat format, ExportFilters filters,
                                  Integer rowCount, Long fileId, String error, ExportStatus status) {
        OaReportExport task = new OaReportExport();
        task.setExportNo(newExportNo());
        task.setReportType(type.code());
        task.setMetricVersion(metricService.currentVersion(type));
        task.setFormat(format.name());
        task.setFilters(JsonUtils.toJsonString(filters));
        task.setStatus(status.name());
        task.setRowCount(rowCount);
        task.setFileId(fileId);
        task.setErrorMessage(error);
        task.setRequestedBy(LoginHelper.getUserId());
        task.setCreateTime(LocalDateTime.now());
        task.setFinishTime(status == ExportStatus.PENDING ? null : LocalDateTime.now());
        exportMapper.insert(task);
        return task;
    }

    private ExportFilters buildFilters(ExportCreateBo bo) {
        ExportFilters filters = new ExportFilters();
        filters.setStartDate(bo.getStartDate());
        filters.setEndDate(bo.getEndDate());
        Long deptId = queryService.resolveDeptId(bo.getDeptId());
        filters.setDeptId(deptId);
        filters.setScope(deptId == null ? "ALL" : "DEPT");
        return filters;
    }

    private OaReportExport requireTask(Long id) {
        OaReportExport task = id == null ? null : exportMapper.selectById(id);
        if (task == null) {
            throw new ServiceException("RP_EXPORT_NOT_FOUND 导出任务不存在", 404);
        }
        Long userId = LoginHelper.getUserId();
        boolean all = ReportingAccessPolicy.canExport(LoginHelper.isSuperAdmin(), permissions());
        if (!all && !userId.equals(task.getRequestedBy())) {
            throw new ServiceException("RP_EXPORT_NOT_FOUND 导出任务不存在", 404);
        }
        return task;
    }

    private ReportType requireType(String code) {
        ReportType type = ReportType.of(code);
        if (type == null) {
            throw new ServiceException("RP_TYPE_INVALID 不支持的报表类型：" + code, 400);
        }
        return type;
    }

    private ExportFormat requireFormat(String value) {
        ExportFormat format = ExportFormat.of(value);
        if (format == null) {
            throw new ServiceException("RP_FORMAT_INVALID 不支持的导出格式：" + value, 400);
        }
        return format;
    }

    private static String newExportNo() {
        return "RP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }

    private static ExportVo toVo(OaReportExport task) {
        ExportVo vo = new ExportVo();
        vo.setId(task.getId());
        vo.setExportNo(task.getExportNo());
        vo.setReportType(task.getReportType());
        vo.setMetricVersion(task.getMetricVersion());
        vo.setFormat(task.getFormat());
        vo.setFilters(task.getFilters());
        vo.setStatus(task.getStatus());
        vo.setRowCount(task.getRowCount());
        vo.setFileId(task.getFileId());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setRequestedBy(task.getRequestedBy());
        vo.setCreateTime(task.getCreateTime() == null ? null : task.getCreateTime().format(TIME_FORMAT));
        vo.setFinishTime(task.getFinishTime() == null ? null : task.getFinishTime().format(TIME_FORMAT));
        return vo;
    }

    private Set<String> permissions() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getMenuPermission() == null ? Set.of() : loginUser.getMenuPermission();
    }
}
