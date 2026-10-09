package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiMessage;
import org.dromara.agentoa.ai.service.support.ChatContextBuilder;
import org.dromara.agentoa.ai.service.support.ChatTransport.Turn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文截断（docs/21 AI-M2-02）：保留系统提示 + 最近 N 轮。
 */
class ChatContextBuilderTest {

    @Test
    void keepsSystemPromptAndRecentTurnsWithinWindow() {
        List<OaAiMessage> history = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            history.add(message(OaAiMessage.ROLE_USER, "历史问题" + i + "，".repeat(20)));
            history.add(message(OaAiMessage.ROLE_ASSISTANT, "历史回答" + i + "，".repeat(20)));
        }
        Turn current = Turn.user("当前问题");
        List<Turn> turns = ChatContextBuilder.build("你是助手", history, current, 1024, 256);

        assertThat(turns.get(0).role()).isEqualTo("system");
        assertThat(turns.get(turns.size() - 1).text()).isEqualTo("当前问题");
        assertThat(turns.size()).isLessThan(history.size() + 2);
        // 最近一轮历史必须保留
        assertThat(turns.get(turns.size() - 2).text()).contains("历史回答49");
    }

    @Test
    void smallWindowFallsBackToCurrentTurnOnly() {
        List<OaAiMessage> history = List.of(message(OaAiMessage.ROLE_USER, "旧问题"));
        List<Turn> turns = ChatContextBuilder.build(null, history, Turn.user("当前"), 600, 300);
        assertThat(turns).hasSize(2);
        assertThat(turns.get(0).text()).isEqualTo("旧问题");
        assertThat(turns.get(1).text()).isEqualTo("当前");
    }

    private static OaAiMessage message(String role, String content) {
        OaAiMessage message = new OaAiMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
