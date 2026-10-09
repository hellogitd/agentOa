package org.dromara.agentoa.hr.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 员工导入校验报告。存在任一错误行时不导入任何数据，避免部分静默成功。
 */
@Data
public class OaImportReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否有数据落库 */
    private boolean imported;

    private int totalRows;

    private int successRows;

    private int failedRows;

    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Excel 行号（从 1 开始，含表头时为数据首行=2，由调用方约定） */
        private int rowNumber;

        private String employeeNo;

        private String name;

        private boolean valid;

        private List<String> errors = new ArrayList<>();

        public void addError(String message) {
            this.valid = false;
            this.errors.add(message);
        }
    }
}
