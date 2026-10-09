package org.dromara.agentoa.notice.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告创建/修改入参（docs/05 7.1）。
 */
@Data
public class AnnouncementBo {

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @Size(max = 32, message = "公告类型长度不能超过{max}个字符")
    private String noticeType;

    @NotNull(message = "受众范围不能为空")
    @Pattern(regexp = "1|2|3|4", message = "受众范围取值非法")
    private String scopeType;

    @Size(max = 2000, message = "范围值长度不能超过{max}个字符")
    private String scopeValues;

    private Integer isTop;

    private Integer isPopup;

    /** 附件列表 JSON（发布时冻结） */
    private String attachments;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
