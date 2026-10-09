package org.dromara.agentoa.finance.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.bo.InvoiceBo;
import org.dromara.agentoa.finance.domain.vo.InvoiceVo;
import org.dromara.agentoa.finance.service.IInvoiceService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 发票接口（API 规范 6.5）：本人录入与查询；明文详情/图片下载按 FinanceAccessPolicy 授权。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/finance/invoices")
public class FinanceInvoiceController {

    private final IInvoiceService invoiceService;
    private final S3Client s3;
    private final JdbcTemplate jdbc;

    @Log(title = "发票录入", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<InvoiceVo> add(@Validated @RequestBody InvoiceBo bo) {
        return R.ok(invoiceService.create(bo, LoginHelper.getUserId()));
    }

    @GetMapping("/{id}")
    public R<InvoiceVo> get(@PathVariable Long id) {
        return R.ok(invoiceService.get(id, LoginHelper.getUserId()));
    }

    @GetMapping
    public R<PageVo<InvoiceVo>> list(FinancePageQuery page,
                                     @RequestParam(required = false) Long ownerUserId) {
        Long scopeOwner = ownerUserId;
        if (scopeOwner == null || !scopeOwner.equals(LoginHelper.getUserId())) {
            boolean canQueryOthers = LoginHelper.isSuperAdmin()
                || LoginHelper.getLoginUser().getMenuPermission().contains("fn:invoice:query");
            if (!canQueryOthers) {
                scopeOwner = LoginHelper.getUserId();
            }
        }
        return R.ok(invoiceService.list(scopeOwner, page));
    }

    @Log(title = "发票删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        invoiceService.delete(id, LoginHelper.getUserId());
        return R.ok();
    }

    /** 发票图片下载：本人或财务/出纳角色（docs/14 发票文件绑定授权） */
    @Log(title = "发票图片下载", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        InvoiceVo invoice = invoiceService.get(id, LoginHelper.getUserId());
        if (invoice.getFileId() == null) {
            throw new ServiceException("FINANCE_INVOICE_FILE_MISSING 发票未上传图片", 404);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sys_file WHERE id = ?", invoice.getFileId());
        if (rows.isEmpty()) {
            throw new ServiceException("FINANCE_INVOICE_FILE_MISSING 发票文件不存在", 404);
        }
        Map<String, Object> file = rows.get(0);
        byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(file.get("bucket").toString())
            .key(file.get("object_key").toString())
            .build()).asByteArray();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.get("original_name").toString(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(bytes);
    }
}
