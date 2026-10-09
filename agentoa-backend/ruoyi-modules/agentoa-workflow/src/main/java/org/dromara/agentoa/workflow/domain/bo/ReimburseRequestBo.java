package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class ReimburseRequestBo {

    @Pattern(regexp = "expense|travel|meal|office|other", message = "报销类型取值非法")
    private String reimburseType;

    /** 两位小数金额字符串（API 规范 1.6），服务端按明细汇总校验 */
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$", message = "金额格式非法")
    private String totalAmount;

    @Pattern(regexp = "CNY", message = "币种暂只支持 CNY")
    private String currency;

    @Pattern(regexp = "BANK_TRANSFER|CASH|OTHER", message = "支付方式取值非法")
    private String payMethod;

    /** 关联预算 ID（P1，FN-03 预算控制，可选） */
    private Long budgetId;

    @NotEmpty(message = "报销明细不能为空")
    private List<ReimburseDetailBo> details;

    @Size(max = 500, message = "事由长度不能超过{max}个字符")
    private String reason;

    private Integer lockVersion;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;

    @Data
    public static class ReimburseDetailBo {

        @Size(max = 32, message = "费用类型长度不能超过{max}个字符")
        private String expenseType;

        /** 费用类型 ID（模块 4 财务口径，可选） */
        private Long expenseTypeId;

        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate occurDate;

        /** 两位小数金额字符串 */
        @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,2})?$", message = "金额格式非法")
        private String amount;

        @Size(max = 64, message = "发票号长度不能超过{max}个字符")
        private String invoiceNo;

        /** 发票 ID（模块 4 发票占用引用，可选；优先于 invoiceNo） */
        private Long invoiceId;

        @Size(max = 255, message = "说明长度不能超过{max}个字符")
        private String description;
    }
}
