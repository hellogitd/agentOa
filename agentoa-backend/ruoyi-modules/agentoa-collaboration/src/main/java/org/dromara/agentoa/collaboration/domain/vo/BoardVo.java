package org.dromara.agentoa.collaboration.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 任务看板视图（docs/05 9.3 /tasks/board）：按状态分列 */
@Data
public class BoardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Column> columns = new ArrayList<>();

    @Data
    public static class Column implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Integer status;

        private String statusName;

        private List<CollabTaskVo> tasks = new ArrayList<>();
    }
}
