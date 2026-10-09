package org.dromara.agentoa.notice.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通知模板创建/修改入参（NC-04）。
 */
@Data
public class NoticeTemplateBo {

    @NotBlank(message = "模板编码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}", message = "模板编码仅允许小写字母、数字、下划线，且以字母开头")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过{max}个字符")
    private String name;

    @NotBlank(message = "标题模板不能为空")
    @Size(max = 255, message = "标题模板长度不能超过{max}个字符")
    private String titleTpl;

    @NotBlank(message = "内容模板不能为空")
    @Size(max = 4000, message = "内容模板长度不能超过{max}个字符")
    private String contentTpl;

    @NotBlank(message = "消息类型不能为空")
    @Pattern(regexp = "TODO|NOTICE|SYSTEM", message = "消息类型取值非法")
    private String msgType;

    /** 变量声明（白名单，{key} 占位符必须全部声明） */
    private List<@Size(max = 32, message = "变量名长度不能超过{max}个字符") String> vars;

    @NotNull(message = "状态不能为空")
    @Pattern(regexp = "1|2", message = "状态取值非法")
    private String status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}
