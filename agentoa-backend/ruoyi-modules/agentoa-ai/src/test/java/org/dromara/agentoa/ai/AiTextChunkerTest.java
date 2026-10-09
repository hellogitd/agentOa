package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.service.support.AiTextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分块契约（docs/21 AI-M3-03）：块边界优先标题/段落、超限再切、相邻块重叠、Markdown 去标记。
 */
class AiTextChunkerTest {

    @Test
    void chunksRespectTargetAndCarryOverlap() {
        String text = "甲甲甲甲甲甲甲甲\n\n乙乙乙乙乙乙乙乙\n\n丙丙丙丙丙丙丙丙\n\n丁丁丁丁丁丁丁丁";
        List<AiTextChunker.Chunk> chunks = AiTextChunker.chunk(text, 10, 3);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (AiTextChunker.Chunk chunk : chunks) {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(16);
        }
        // 相邻块重叠：下一块以（约 overlap token 的）上一块尾部开�?
        String previousTail = chunks.get(0).content().replaceAll("\\s+", "");
        String overlap = previousTail.substring(previousTail.length() - 3);
        assertThat(chunks.get(1).content().replaceAll("\\s+", "")).startsWith(overlap);
        // 序号连续
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).seq()).isEqualTo(i);
        }
    }

    @Test
    void headingBecomesChunkHeading() {
        String text = "# 制度总则\n第一条 说明\n\n## 附则\n附则内容";
        List<AiTextChunker.Chunk> chunks = AiTextChunker.chunk(text, 512, 0);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).heading()).isEqualTo("制度总则");
        assertThat(chunks.get(0).content()).contains("第一条");
        assertThat(chunks.get(1).heading()).isEqualTo("附则");
        assertThat(chunks.get(1).content()).contains("附则内容");
    }

    @Test
    void oversizedBlockIsSplitByLines() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append("这是一段用于触发切分的较长文本内容").append(i).append('\n');
        }
        List<AiTextChunker.Chunk> chunks = AiTextChunker.chunk(text.toString(), 64, 8);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(80));
    }

    @Test
    void stripMarkdownRemovesMarkers() {
        String stripped = AiTextChunker.stripMarkdown("# 标题\n**加粗** 与 `代码` 与 [链接](http://x)\n\n- 列表项");

        assertThat(stripped).doesNotContain("#", "**", "`", "](", "- ");
        assertThat(stripped).contains("标题", "加粗", "代码", "链接", "列表项");
    }

    @Test
    void emptyTextYieldsNoChunks() {
        assertThat(AiTextChunker.chunk("   \n  ")).isEmpty();
        assertThat(AiTextChunker.chunk(null)).isEmpty();
    }
}
