package org.dromara.agentoa.workflow.assigner;

/**
 * 办理人解析失败：确定性报错，禁止回退到任意表达式或用户脚本（docs/12）。
 */
public class AssigneeResolutionException extends RuntimeException {

    public AssigneeResolutionException(String message) {
        super(message);
    }
}
