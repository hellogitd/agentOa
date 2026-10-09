package org.dromara.agentoa.finance.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 人工付款登记入参（docs/05 6.4）：金额必须等于已审批金额。
 */
@Data
public class PaymentBo {

    private Integer lockVersion;

    @NotBlank(message = "付款金额不能为空")
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$", message = "金额格式非法")
    private String amount;

    @NotNull(message = "付款日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    @NotBlank(message = "付款方式不能为空")
    @Pattern(regexp = "BANK_TRANSFER|CASH|OTHER", message = "付款方式取值非法")
    private String paymentMethod;

    @Size(max = 64, message = "凭证号长度不能超过{max}个字符")
    private String voucherNo;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
