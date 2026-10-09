package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 补卡生效记录 oa_correction：审批通过后写入，并按 correction_request_id 生成补卡打卡记录。
 */
@Data
@TableName("oa_correction")
public class OaCorrection {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private Long correctionRequestId;

    private LocalDate attendanceDate;

    /** 1上班 2下班 */
    private Integer punchType;

    private LocalDateTime correctedTime;

    /** 3已通过 7已撤销 */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}
