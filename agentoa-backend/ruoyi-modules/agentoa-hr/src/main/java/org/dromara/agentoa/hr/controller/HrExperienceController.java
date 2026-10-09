package org.dromara.agentoa.hr.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.OaEducationBo;
import org.dromara.agentoa.hr.domain.bo.OaWorkBo;
import org.dromara.agentoa.hr.domain.vo.OaEducationVo;
import org.dromara.agentoa.hr.domain.vo.OaWorkVo;
import org.dromara.agentoa.hr.service.IHrExperienceService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工教育/工作经历接口（需求 HR-03 P1、HR-10）。
 * <p>
 * 对象级授权在服务层判定：HR/管理员可维护任意员工，员工本人可自助维护。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/employees/{employeeId}")
public class HrExperienceController {

    private final IHrExperienceService experienceService;

    // ---------------------------------------------------------------- 教育经历

    @GetMapping("/educations")
    public R<List<OaEducationVo>> listEducations(@PathVariable Long employeeId) {
        return R.ok(experienceService.selectEducations(employeeId));
    }

    @Log(title = "教育经历", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/educations")
    public R<OaEducationVo> addEducation(@PathVariable Long employeeId,
                                         @Validated @RequestBody OaEducationBo bo) {
        return R.ok(experienceService.createEducation(employeeId, bo));
    }

    @Log(title = "教育经历", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/educations/{educationId}")
    public R<OaEducationVo> editEducation(@PathVariable Long employeeId, @PathVariable Long educationId,
                                          @Validated @RequestBody OaEducationBo bo) {
        return R.ok(experienceService.updateEducation(employeeId, educationId, bo));
    }

    @Log(title = "教育经历", businessType = BusinessType.DELETE)
    @DeleteMapping("/educations/{educationId}")
    public R<Void> removeEducation(@PathVariable Long employeeId, @PathVariable Long educationId) {
        experienceService.deleteEducation(employeeId, educationId);
        return R.ok();
    }

    // ---------------------------------------------------------------- 工作经历

    @GetMapping("/works")
    public R<List<OaWorkVo>> listWorks(@PathVariable Long employeeId) {
        return R.ok(experienceService.selectWorks(employeeId));
    }

    @Log(title = "工作经历", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/works")
    public R<OaWorkVo> addWork(@PathVariable Long employeeId, @Validated @RequestBody OaWorkBo bo) {
        return R.ok(experienceService.createWork(employeeId, bo));
    }

    @Log(title = "工作经历", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/works/{workId}")
    public R<OaWorkVo> editWork(@PathVariable Long employeeId, @PathVariable Long workId,
                                @Validated @RequestBody OaWorkBo bo) {
        return R.ok(experienceService.updateWork(employeeId, workId, bo));
    }

    @Log(title = "工作经历", businessType = BusinessType.DELETE)
    @DeleteMapping("/works/{workId}")
    public R<Void> removeWork(@PathVariable Long employeeId, @PathVariable Long workId) {
        experienceService.deleteWork(employeeId, workId);
        return R.ok();
    }
}
