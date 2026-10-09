package org.dromara.agentoa.collaboration.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 任务创建/修改入参（docs/05 9.3，业务日期为 yyyy-MM-dd） */
@Data
public class TaskBo {

    /** 更新必填：乐观锁版本 */
    private Integer lockVersion;

    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题长度不能超过{max}个字符")
    private String title;

    private String description;

    private String parentId;

    /** 负责人账号ID */
    private String assigneeId;

    /** 优先级（1P0 2P1 3P2 4P3） */
    @Pattern(regexp = "1|2|3|4", message = "优先级取值非法")
    private String priority;

    private String startDate;

    private String dueDate;

    @Size(max = 255, message = "标签长度不能超过{max}个字符")
    private String tags;

    /** 协作者账号ID */
    private List<String> memberIds;
}
