package org.dromara.agentoa.finance.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 发票录入入参（docs/05 6.5）：类型+代码（无代码为空串）+号码+金额+日期组成可审计指纹。
 */
@Data
public class InvoiceBo {

    @NotBlank(message = "发票类型不能为空")
    @Size(max = 32, message = "发票类型长度不能超过{max}个字符")
    private String invoiceType;

    @Size(max = 32, message = "发票代码长度不能超过{max}个字符")
    private String invoiceCode;

    @NotBlank(message = "发票号码不能为空")
    @Size(max = 64, message = "发票号码长度不能超过{max}个字符")
    private String invoiceNo;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate invoiceDate;

    /** 两位小数金额字符串（API 规范 1.6） */
    @NotBlank(message = "发票金额不能为空")
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$", message = "金额格式非法")
    private String amount;

    private Long fileId;
}
