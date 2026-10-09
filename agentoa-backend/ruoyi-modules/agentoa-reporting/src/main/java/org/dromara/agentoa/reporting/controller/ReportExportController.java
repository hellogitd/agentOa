package org.dromara.agentoa.reporting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.reporting.domain.bo.ExportCreateBo;
import org.dromara.agentoa.reporting.domain.bo.ExportFilters;
import org.dromara.agentoa.reporting.domain.bo.ExportPageQuery;
import org.dromara.agentoa.reporting.domain.enums.ExportFormat;
import org.dromara.agentoa.reporting.domain.enums.ReportType;
import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.dromara.agentoa.reporting.domain.vo.ExportVo;
import org.dromara.agentoa.reporting.service.IReportExportService;
import org.dromara.agentoa.reporting.service.support.ReportExportWriter;
import org.dromara.agentoa.reporting.service.support.ReportTable;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 报表导出接口（docs/05 第 10 节 export/{type} + docs/18 步骤 4 导出任务）：
 * 同步导出与异步导出任务都要求 rp:report:export，并记录操作者、过滤条件与口径版本。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/report")
public class ReportExportController {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IReportExportService exportService;

    /** 同步导出（CSV/XLSX），行数上限 10000，导出与列表同一权限与筛选口径。 */
    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_EXPORT)
    @Log(title = "报表导出", businessType = BusinessType.EXPORT)
    @GetMapping("/export/{type}")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(required = false) String format,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) Long deptId) {
        ReportType reportType = requireType(type);
        ExportFormat exportFormat = format == null || format.isBlank()
            ? ExportFormat.XLSX : requireFormat(format);
        ExportFilters filters = new ExportFilters();
        filters.setStartDate(startDate);
        filters.setEndDate(endDate);
        filters.setDeptId(deptId);
        ReportTable table = exportService.syncExport(reportType, exportFormat, filters);
        byte[] bytes = ReportExportWriter.write(table, exportFormat, reportType.label());
        String fileName = reportType.label() + "-" + LocalDate.now().format(DAY_FORMAT)
            + "." + exportFormat.extension();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(exportFormat.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8))
            .header("X-Content-Type-Options", "nosniff")
            .body(bytes);
    }

    /** 创建异步导出任务；必须携带 Idempotency-Key（同键同请求重放、同键异请求 409）。 */
    @SaCheckPermission(ReportingAccessPolicy.PERM_REPORT_EXPORT)
    @Log(title = "报表导出任务", businessType = BusinessType.EXPORT)
    @PostMapping("/exports")
    public R<ExportVo> create(@Validated @RequestBody ExportCreateBo bo,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(exportService.create(bo, idempotencyKey));
    }

    /** 导出记录分页（本人记录；rp:report:export 可看全量）。 */
    @GetMapping("/exports")
    public R<PageVo<ExportVo>> list(ExportPageQuery query) {
        return R.ok(exportService.list(query));
    }

    /** 导出任务详情；完成后的文件经 /api/v1/files/{fileId}/download 按属主下载。 */
    @GetMapping("/exports/{id}")
    public R<ExportVo> get(@PathVariable Long id) {
        return R.ok(exportService.get(id));
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
}
