package org.dromara.agentoa.workflow.font;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流程图乱码修复（M1）回归测试：
 * 内置 Noto Sans SC 必须可加载，解析出的字体必须能真正画出中文字形。
 */
class DiagramFontProviderTest {

    private static final String SAMPLE = "流程审批请假加班报销转正离职会签或签抄送金额条件分支总经理";

    @Test
    void bundledNotoSansScIsOnClasspath() throws Exception {
        try (InputStream in = DiagramFontProvider.class.getClassLoader()
            .getResourceAsStream(DiagramFontProvider.BUNDLED_FONT_RESOURCE)) {
            assertThat(in).as("内置字体资源 %s 必须存在", DiagramFontProvider.BUNDLED_FONT_RESOURCE).isNotNull();
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            assertThat(font.getFamily()).isNotBlank();
        }
    }

    @Test
    void resolvesFamilyThatCanRenderChinese() {
        String family = DiagramFontProvider.family();
        assertThat(family).isNotBlank();
        Font font = new Font(family, Font.PLAIN, 14);
        for (int i = 0; i < SAMPLE.length(); i++) {
            assertThat(font.canDisplay(SAMPLE.charAt(i)))
                .as("字体 %s 必须能渲染字符 %s", family, SAMPLE.charAt(i))
                .isTrue();
        }
    }

    @Test
    void usesBundledFontWhenAvailable() {
        assertThat(DiagramFontProvider.usingBundledFont())
            .as("内置 Noto Sans SC 应被优先采用（否则说明字体资源加载失败）")
            .isTrue();
    }

    @Test
    void drawsChineseGlyphsWithoutTofu() {
        String family = DiagramFontProvider.family();
        BufferedImage image = new BufferedImage(480, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 480, 80);
            g.setColor(Color.BLACK);
            g.setFont(new Font(family, Font.PLAIN, 28));
            g.drawString(SAMPLE, 8, 50);
        } finally {
            g.dispose();
        }
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) < 0x00808080) {
                    ink++;
                }
            }
        }
        // 缺字形时画出来是空白或细边框方块，墨迹像素显著偏低
        assertThat(ink).as("字体 %s 绘制中文的墨迹像素数应足够多，实际 %d", family, ink).isGreaterThan(600);
    }

    @Test
    void fontsAreAvailableInThisJvm() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        assertThat(families).isNotEmpty();
        assertThat(DiagramFontProvider.activityFont()).isEqualTo(DiagramFontProvider.labelFont());
        assertThat(DiagramFontProvider.annotationFont()).isEqualTo(DiagramFontProvider.family());
    }
}
