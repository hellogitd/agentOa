package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 打卡记录 oa_punch_record：保留全部原始记录，迟到早退是计算结果而不是过滤条件。
 */
@Data
@TableName("oa_punch_record")
public class OaPunchRecord {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    /** 归属考勤日期（跨夜班归属班次开始日期） */
    private LocalDate punchDate;

    /** 服务器接收时间 */
    private LocalDateTime punchTime;

    /** 1上班 2下班 3外勤 4补卡 */
    private Integer punchType;

    private Long correctionRequestId;

    private Integer isLate;

    private Integer lateMinutes;

    private Integer isEarly;

    private Integer earlyMinutes;

    private BigDecimal lng;

    private BigDecimal lat;

    private BigDecimal accuracyMeters;

    private String address;

    private String device;

    private String ip;

    /** WiFi 名称（风险信号，P1） */
    private String wifiName;

    /** 现场照片 sys_file ID（外勤必填，P1） */
    private Long photoFileId;

    /** 1PC 2App 3小程序 */
    private Integer source;

    private Date createTime;
}
