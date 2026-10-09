package org.dromara.agentoa.workflow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 发起申请目录（H5/移动端）：启用分类 + 已发布流程定义（含表单快照）。
 */
@Data
public class LaunchCatalogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 启用中的流程分类（status=0，按 sort 排序） */
    private List<CategoryVo> categories = new ArrayList<>();

    /** 已发布（PUBLISHED）且分类启用的流程定义（按 sort 排序） */
    private List<LaunchDefinitionVo> definitions = new ArrayList<>();
}
