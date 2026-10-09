package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.domain.OaAiMessage;
import org.dromara.agentoa.ai.service.support.ChatTransport.Turn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 上下文装配（docs/21 AI-M2-02）：按模型 context_window 截断，保留系统提示 + 最近 N 轮。
 * <p>
 * 历史图片不回灌（仅当前消息携带图片），控制 token 与传输成本。
 */
public final class ChatContextBuilder {

    private ChatContextBuilder() {
    }

    public static List<Turn> build(String systemPrompt, List<OaAiMessage> history, Turn currentTurn,
                                   int contextWindow, int reservedForResponse) {
        int window = contextWindow <= 0 ? 8192 : contextWindow;
        int reserve = Math.max(256, Math.min(reservedForResponse <= 0 ? 2048 : reservedForResponse, window / 2));
        int budget = Math.max(512, window - reserve);

        List<Turn> result = new ArrayList<>();
        int used = 0;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(Turn.system(systemPrompt));
            used += TokenEstimator.estimate(systemPrompt);
        }
        int currentCost = TokenEstimator.estimate(List.of(currentTurn));
        List<Turn> historyTurns = new ArrayList<>();
        List<OaAiMessage> reversed = new ArrayList<>(history == null ? List.<OaAiMessage>of() : history);
        Collections.reverse(reversed);
        for (OaAiMessage message : reversed) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = OaAiMessage.ROLE_ASSISTANT.equals(message.getRole()) ? "assistant"
                : OaAiMessage.ROLE_SYSTEM.equals(message.getRole()) ? "system" : "user";
            Turn turn = new Turn(role, message.getContent(), List.of());
            int cost = TokenEstimator.estimate(List.of(turn));
            if (used + cost + currentCost > budget) {
                break;
            }
            used += cost;
            historyTurns.add(turn);
        }
        Collections.reverse(historyTurns);
        result.addAll(historyTurns);
        result.add(currentTurn);
        return result;
    }
}
