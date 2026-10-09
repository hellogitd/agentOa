package org.dromara.agentoa.attendance.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 打卡命令（API 规范 5.1）。打卡时间取服务器时钟，客户端只提供定位等风险信号；
 * 外勤（punchType=3，P1）必须携带定位与现场照片。
 */
@Data
public class PunchBo {

    @NotNull(message = "打卡类型不能为空")
    @Min(value = 1, message = "打卡类型取值非法")
    @Max(value = 3, message = "打卡类型取值非法")
    private Integer punchType;

    private BigDecimal lng;

    private BigDecimal lat;

    private BigDecimal accuracyMeters;

    @Size(max = 255, message = "地址长度不能超过{max}个字符")
    private String address;

    @Size(max = 64, message = "设备长度不能超过{max}个字符")
    private String device;

    /** WiFi 名称（风险信号，P1） */
    @Size(max = 64, message = "WiFi 名称长度不能超过{max}个字符")
    private String wifiName;

    /** 现场照片 sys_file ID（外勤必填，P1） */
    private Long photoFileId;

    /** 1PC 2App 3小程序 */
    private Integer source = 1;
}
