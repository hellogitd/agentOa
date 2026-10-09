package org.dromara.agentoa.reporting.service.support;

/** 导出文件存储抽象：生产实现写入私有 S3 与 sys_file，测试可替换。 */
public interface ExportFileStore {

    /** 保存导出文件并返回 sys_file 文件ID（私有对象，仅属主可下载）。 */
    long store(String fileName, byte[] content, Long ownerUserId);
}
