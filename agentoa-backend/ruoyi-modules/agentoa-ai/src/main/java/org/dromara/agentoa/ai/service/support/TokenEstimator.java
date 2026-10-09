package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.service.support.ChatTransport.Turn;

import java.util.List;

/**
 * token 估算（docs/21 AI-M1-08）：ASCII 约 4 字符/token，CJK 约 1 字符/token。
 * 仅用于限额预算与上下文截断，不作为计费口径。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int ascii = 0;
        int wide = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x2E80) {
                wide++;
            } else {
                ascii++;
            }
        }
        return (int) Math.ceil(ascii / 4.0) + wide;
    }

    public static int estimate(List<Turn> turns) {
        if (turns == null) {
            return 0;
        }
        int total = 0;
        for (Turn turn : turns) {
            total += estimate(turn.text());
            if (turn.images() != null) {
                // 图片按固定预算计（vision 输入开销）
                total += 256 * turn.images().size();
            }
        }
        return total;
    }
}
