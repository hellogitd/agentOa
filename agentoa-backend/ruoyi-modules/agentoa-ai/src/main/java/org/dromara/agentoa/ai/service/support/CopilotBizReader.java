package org.dromara.agentoa.ai.service.support;

import org.dromara.common.core.exception.ServiceException;

/**
 * 业务只读摘要读取器（docs/21 §6：业务数据只取只读摘要，AI 不直接修改业务数据）。
 * <p>
 * 每个业务模块实现一个阅读器并注册为 Spring Bean；越权一律按 404/403 处理，
 * 不把原始明细泄给提示词之外的调用方。找不到实现时按"无上下文"降级，不阻断生成。
 */
public interface CopilotBizReader {

    /** 本阅读器负责的场景码（见 {@code AiCopilotScene}） */
    String scene();

    /**
     * 读取业务只读摘要。
     *
     * @param bizId  业务 ID
     * @param userId 当前账号
     * @return 只读摘要文本；无权访问时抛 {@link ServiceException}
     */
    String readContext(Long bizId, Long userId);
}
