package org.dromara.agentoa.workflow.font;

import lombok.extern.slf4j.Slf4j;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 流程图渲染字体提供器。
 * <p>
 * Flowable 的 {@code DefaultProcessDiagramGenerator} 只接受字体名字符串，而在精简 JRE/Linux 容器中
 * "Arial" 不存在且不含中文字形，导致节点/连线中文渲染成方块（乱码）。
 * <p>
 * 解析顺序：
 * <ol>
 *   <li>内置 Noto Sans SC（classpath {@code fonts/NotoSansSC-Regular.otf}），注册进 AWT 后直接使用；</li>
 *   <li>系统已安装的常见中文字体族（Noto Sans CJK SC / 微软雅黑 / 黑体 / 文泉驿 等）；</li>
 *   <li>逻辑字体 {@code SansSerif}（JRE composite font，自带 CJK fallback）。</li>
 * </ol>
 * 每一步都做字形探针校验，确保选中的字体真的能画出中文。任何失败都不抛异常，只回退。
 */
@Slf4j
public final class DiagramFontProvider {

    /** 内置字体资源路径。 */
    public static final String BUNDLED_FONT_RESOURCE = "fonts/NotoSansSC-Regular.otf";

    /** 逻辑字体兜底名：JRE composite font，带 CJK fallback，永远存在。 */
    public static final String LOGICAL_FALLBACK = "SansSerif";

    /**
     * 系统字体候选，按优先级排序。均在 Linux 容器（fonts-noto-cjk）/ Windows / macOS 上有中文覆盖。
     */
    private static final String[] CJK_CANDIDATES = {
        "Noto Sans SC",
        "Noto Sans CJK SC",
        "Noto Sans CJK",
        "Source Han Sans SC",
        "Source Han Sans CN",
        "WenQuanYi Micro Hei",
        "WenQuanYi Zen Hei",
        "Microsoft YaHei",
        "SimHei",
        "Heiti SC",
        "PingFang SC",
        "Droid Sans Fallback",
        "AR PL UMing CN",
        "AR PL UKai CN"
    };

    /** 字形探针：覆盖流程场景常用汉字，避免选到只含拉丁字形的字体。 */
    private static final String PROBE_TEXT = "流程审批请假加班报销转正离职会签或签抄送金额条件分支总经理部门财务人事行政";

    private static final Object LOCK = new Object();

    private static volatile String resolvedFamily;
    private static volatile boolean resolvedFromBundle;

    private DiagramFontProvider() {
    }

    /**
     * 返回可安全渲染中文的字体族名。线程安全、幂等、永不抛异常。
     */
    public static String family() {
        String local = resolvedFamily;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (resolvedFamily == null) {
                Resolution resolution = resolve();
                resolvedFromBundle = resolution.fromBundle();
                resolvedFamily = resolution.family();
                log.info("流程图渲染字体解析完成: {} (内置字体={})", resolvedFamily, resolvedFromBundle);
            }
            return resolvedFamily;
        }
    }

    /** 活动节点（userTask / 网关等）字体名。 */
    public static String activityFont() {
        return family();
    }

    /** 连线/节点标签字体名。 */
    public static String labelFont() {
        return family();
    }

    /** 注释（annotation）字体名。 */
    public static String annotationFont() {
        return family();
    }

    /** 是否已通过内置字体解析（用于自检/日志）。 */
    public static boolean usingBundledFont() {
        family();
        return resolvedFromBundle;
    }

    /** 解析结果。 */
    private record Resolution(String family, boolean fromBundle) {
    }

    private static Resolution resolve() {
        String bundled = loadBundledFont();
        if (bundled != null && canRender(bundled)) {
            return new Resolution(bundled, true);
        }
        List<String> available = availableFamilies();
        for (String candidate : CJK_CANDIDATES) {
            if (available.contains(candidate) && canRender(candidate)) {
                return new Resolution(candidate, false);
            }
        }
        for (String name : available) {
            if (looksCjk(name) && canRender(name)) {
                return new Resolution(name, false);
            }
        }
        return new Resolution(LOGICAL_FALLBACK, false);
    }

    private static String loadBundledFont() {
        try (InputStream in = DiagramFontProvider.class.getClassLoader().getResourceAsStream(BUNDLED_FONT_RESOURCE)) {
            if (in == null) {
                log.warn("内置字体资源缺失: {}，回退到系统字体", BUNDLED_FONT_RESOURCE);
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            boolean registered = ge.registerFont(font);
            String familyName = font.getFamily();
            if (!registered) {
                log.debug("内置字体注册未生效（可能系统已有同名字体），仍使用字体族名: {}", familyName);
            }
            return familyName;
        } catch (Exception e) {
            log.warn("内置字体加载失败: {}，回退到系统字体", e.toString());
            return null;
        }
    }

    private static List<String> availableFamilies() {
        try {
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            List<String> list = new ArrayList<>(names.length);
            for (String name : names) {
                list.add(name);
            }
            return list;
        } catch (Exception e) {
            log.warn("枚举系统字体失败: {}", e.toString());
            return List.of();
        }
    }

    private static boolean looksCjk(String familyName) {
        String lower = familyName.toLowerCase(Locale.ROOT);
        return lower.contains("noto")
            || lower.contains("cjk")
            || lower.contains("han")
            || lower.contains("hei")
            || lower.contains("ming")
            || lower.contains("yahei")
            || lower.contains("wenquanyi")
            || lower.contains("songti")
            || lower.contains("simsun")
            || lower.contains("droid sans fallback");
    }

    private static boolean canRender(String familyName) {
        try {
            if (familyName.equalsIgnoreCase(LOGICAL_FALLBACK)) {
                return true;
            }
            Font font = new Font(familyName, Font.PLAIN, 12);
            for (int i = 0; i < PROBE_TEXT.length(); i++) {
                if (!font.canDisplay(PROBE_TEXT.charAt(i))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 仅供自检/测试重置缓存。 */
    static void resetForTesting() {
        synchronized (LOCK) {
            resolvedFamily = null;
            resolvedFromBundle = false;
        }
    }
}
