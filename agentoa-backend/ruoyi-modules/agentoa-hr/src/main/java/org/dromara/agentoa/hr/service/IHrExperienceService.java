package org.dromara.agentoa.hr.service;

import org.dromara.agentoa.hr.domain.bo.OaEducationBo;
import org.dromara.agentoa.hr.domain.bo.OaWorkBo;
import org.dromara.agentoa.hr.domain.vo.OaEducationVo;
import org.dromara.agentoa.hr.domain.vo.OaWorkVo;

import java.util.List;

/**
 * 员工教育/工作经历服务（需求 HR-03 P1 扩展）。
 * <p>
 * HR 与管理员可维护任意员工；员工本人可自助维护自己的经历（HR-10）。
 */
public interface IHrExperienceService {

    List<OaEducationVo> selectEducations(Long employeeId);

    OaEducationVo createEducation(Long employeeId, OaEducationBo bo);

    OaEducationVo updateEducation(Long employeeId, Long educationId, OaEducationBo bo);

    void deleteEducation(Long employeeId, Long educationId);

    List<OaWorkVo> selectWorks(Long employeeId);

    OaWorkVo createWork(Long employeeId, OaWorkBo bo);

    OaWorkVo updateWork(Long employeeId, Long workId, OaWorkBo bo);

    void deleteWork(Long employeeId, Long workId);
}
