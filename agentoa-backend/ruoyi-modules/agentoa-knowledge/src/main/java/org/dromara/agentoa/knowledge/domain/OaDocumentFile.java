package org.dromara.agentoa.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文件柜记录（docs/16）：复用 sys_file 存储，本表负责业务绑定、下载授权与回收站。
 */
@Data
@TableName("oa_document_file")
public class OaDocumentFile {

    @TableId(value = "id")
    private Long id;

    private Long spaceId;

    /** 关联文档ID（NULL 为文件柜独立文件） */
    private Long documentId;

    /** sys_file 元数据ID（私有桶） */
    private Long fileId;

    private String fileName;

    private String fileExt;

    private Long fileSize;

    private String contentType;

    private Integer downloadCount;

    /** 回收站起算时间（NULL 未删除） */
    private Date deletedAt;

    /** 删除操作账号ID（审计） */
    private Long deletedBy;

    /** 最后下载账号ID（审计） */
    private Long lastDownloadBy;

    /** 最后下载时间（审计） */
    private Date lastDownloadTime;

    private Long createDept;

    /** 上传者账号ID */
    private Long createBy;

    private Date createTime;
}
