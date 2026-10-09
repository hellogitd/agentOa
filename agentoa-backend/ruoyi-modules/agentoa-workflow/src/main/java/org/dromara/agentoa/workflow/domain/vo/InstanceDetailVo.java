package org.dromara.agentoa.workflow.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class InstanceDetailVo extends InstanceVo {

    @Serial
    private static final long serialVersionUID = 1L;

    private String formKey;

    private String formName;

    private Integer formVersionNo;

    /** 表单结构快照（提交时固定） */
    private String formSchema;

    /** 表单数据 */
    private String formData;

    private List<TaskActionVo> actions = new ArrayList<>();

    private List<TaskVo> currentTasks = new ArrayList<>();
}
