package org.dromara.agentoa.notice.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知模板 oa_notice_template（NC-04）：受控 {var} 占位符替换，不引入表达式引擎。
 */
@Data
@TableName("oa_notice_template")
public class OaNoticeTemplate {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(value = "id")
    private Long id;

    private String templateCode;

    private String name;

    private String titleTpl;

    private String contentTpl;

    /** 消息类型（TODO/NOTICE/SYSTEM） */
    private String msgType;

    /** 变量声明（JSON 数组字符串） */
    private String varsJson;

    /** 状态（1启用 2停用） */
    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private String remark;
}
