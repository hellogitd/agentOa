package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.common.sensitive.annotation.Sensitive;
import org.dromara.common.sensitive.core.SensitiveStrategy;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 员工档案视图对象（API 规范 3.3）。ID 序列化为字符串；身份证与手机号脱敏。
 */
@Data
@AutoMapper(target = OaEmployee.class)
public class OaEmployeeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String employeeNo;

    private String name;

    /** 性别（0女 1男 2未知） */
    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @Sensitive(strategy = SensitiveStrategy.ID_CARD, perms = "hr:employee:sensitive")
    private String idCardNo;

    @Sensitive(strategy = SensitiveStrategy.PHONE, perms = "hr:employee:sensitive")
    private String phone;

    private String email;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;

    private String positionLevel;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long directLeaderId;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate regularDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate leaveDate;

    private String workflowInstanceId;

    private String remark;

    @Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "deptId")
    private String deptName;

    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "directLeaderId")
    private String directLeaderName;

    /** 岗位名称，服务层填充 */
    private String postName;
}
