package org.dromara.agentoa.hr.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 员工自助视图（需求 HR-10：个人信息维护、经历维护）。
 */
@Data
public class OaSelfProfileVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private OaEmployeeVo employee;

    private List<OaEducationVo> educations;

    private List<OaWorkVo> works;

    private List<OaContractVo> contracts;
}
