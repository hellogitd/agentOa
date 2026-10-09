package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 快捷入口（站内相对路径，docs/02 9.2）。 */
@Data
public class QuickLinkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String label;

    private String url;

    public static QuickLinkVo of(String label, String url) {
        QuickLinkVo vo = new QuickLinkVo();
        vo.setLabel(label);
        vo.setUrl(url);
        return vo;
    }
}
