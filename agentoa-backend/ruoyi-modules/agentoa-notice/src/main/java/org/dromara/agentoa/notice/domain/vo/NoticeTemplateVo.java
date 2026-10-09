package org.dromara.agentoa.notice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知模板视图（NC-04）。
 */
@Data
public class NoticeTemplateVo {

    private Long id;

    private String templateCode;

    private String name;

    private String titleTpl;

    private String contentTpl;

    private String msgType;

    /** 变量声明列表 */
    private List<String> vars;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
