package org.dromara.agentoa.hr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeBo;
import org.dromara.agentoa.hr.domain.bo.OaEmployeeProfileBo;
import org.dromara.agentoa.hr.domain.bo.OaOffboardBo;
import org.dromara.agentoa.hr.domain.bo.OaOnboardBo;
import org.dromara.agentoa.hr.domain.bo.OaRegularizeBo;
import org.dromara.agentoa.hr.domain.bo.OaStatusBo;
import org.dromara.agentoa.hr.domain.policy.HrAccessPolicy;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeExportFullVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeExportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeHistoryVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeImportVo;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeVo;
import org.dromara.agentoa.hr.domain.vo.OaImportReportVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.IHrEmployeeService;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工档案与生命周期接口（API 规范 3.3/3.4/3.5/3.7）。
 * <p>
 * 命令类写接口（创建、入职、转正、离职）必须携带 Idempotency-Key（规范 1.7）：
 * 同键同请求返回首次结果，同键不同请求返回 409。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/employees")
public class HrEmployeeController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IHrEmployeeService employeeService;
    private final IdempotencyGuard idempotencyGuard;

    // ---------------------------------------------------------------- 查询

    @SaCheckPermission("hr:employee:list")
    @GetMapping
    public R<PageVo<OaEmployeeVo>> list(OaEmployeeBo query, HrPageQuery page) {
        return R.ok(employeeService.selectPageEmployees(query, page));
    }

    @SaCheckPermission("hr:employee:query")
    @GetMapping("/{employeeId}")
    public R<OaEmployeeVo> get(@PathVariable Long employeeId) {
        return R.ok(employeeService.selectEmployee(employeeId));
    }

    /** 变动历史 */
    @SaCheckPermission("hr:employee:query")
    @GetMapping("/{employeeId}/changes")
    public R<List<OaEmployeeHistoryVo>> changes(@PathVariable Long employeeId) {
        return R.ok(employeeService.selectChanges(employeeId));
    }

    // ---------------------------------------------------------------- 档案维护

    @SaCheckPermission("hr:employee:add")
    @Log(title = "员工管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<OaEmployeeVo> add(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                               @Validated @RequestBody OaEmployeeBo bo) {
        return runIdempotent(idempotencyKey, bo, () -> employeeService.createEmployee(bo));
    }

    @SaCheckPermission("hr:employee:edit")
    @Log(title = "员工管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{employeeId}")
    public R<OaEmployeeVo> edit(@PathVariable Long employeeId, @Validated @RequestBody OaEmployeeBo bo) {
        return R.ok(employeeService.updateEmployee(employeeId, bo));
    }

    /** 自助字段（手机、邮箱） */
    @RepeatSubmit()
    @PutMapping("/{employeeId}/profile")
    public R<OaEmployeeVo> profile(@PathVariable Long employeeId, @Validated @RequestBody OaEmployeeProfileBo bo) {
        return R.ok(employeeService.updateProfile(employeeId, bo));
    }

    /** 状态端点：入职/转正/离职/待离职/停用 */
    @SaCheckPermission("hr:employee:edit")
    @Log(title = "员工管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{employeeId}/status")
    public R<OaEmployeeVo> status(@PathVariable Long employeeId, @Validated @RequestBody OaStatusBo bo) {
        return R.ok(employeeService.updateStatus(employeeId, bo));
    }

    @SaCheckPermission("hr:employee:remove")
    @Log(title = "员工管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{employeeId}")
    public R<Void> remove(@PathVariable Long employeeId) {
        employeeService.deleteEmployee(employeeId);
        return R.ok();
    }

    // ---------------------------------------------------------------- 生命周期命令

    /** 入职：建档案并转试用期 */
    @SaCheckPermission("hr:employee:add")
    @Log(title = "员工入职", businessType = BusinessType.INSERT)
    @PostMapping("/onboard")
    public R<OaEmployeeVo> onboard(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                   @Validated @RequestBody OaOnboardBo bo) {
        return runIdempotent(idempotencyKey, bo, () -> employeeService.onboard(bo));
    }

    /** 转正 */
    @SaCheckPermission("hr:employee:edit")
    @Log(title = "员工转正", businessType = BusinessType.UPDATE)
    @PostMapping("/regularize")
    public R<OaEmployeeVo> regularize(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                      @Validated @RequestBody OaRegularizeBo bo) {
        return runIdempotent(idempotencyKey, bo, () -> employeeService.regularize(bo));
    }

    /** 离职：完成即冻结账号并注销会话 */
    @SaCheckPermission("hr:employee:edit")
    @Log(title = "员工离职", businessType = BusinessType.UPDATE)
    @PostMapping("/offboard")
    public R<OaEmployeeVo> offboard(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                                    @Validated @RequestBody OaOffboardBo bo) {
        return runIdempotent(idempotencyKey, bo, () -> employeeService.offboard(bo));
    }

    // ---------------------------------------------------------------- 导入导出

    @SaCheckPermission("hr:employee:import")
    @Log(title = "员工管理", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<OaImportReportVo> importData(@RequestPart("file") MultipartFile file) throws Exception {
        List<OaEmployeeImportVo> rows = ExcelUtil.importExcel(file.getInputStream(), OaEmployeeImportVo.class);
        return R.ok(employeeService.importEmployees(rows));
    }

    /** 导入模板 */
    @SaCheckPermission("hr:employee:import")
    @GetMapping("/import/template")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.exportExcel(new ArrayList<OaEmployeeImportVo>(), "员工导入模板", OaEmployeeImportVo.class, response);
    }

    @SaCheckPermission("hr:employee:export")
    @Log(title = "员工管理", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(OaEmployeeBo query, HttpServletResponse response) {
        if (canViewSensitive()) {
            List<OaEmployeeExportFullVo> rows = employeeService.exportEmployeesFull(query);
            ExcelUtil.exportExcel(rows, "员工数据", OaEmployeeExportFullVo.class, response);
        } else {
            List<OaEmployeeExportVo> rows = employeeService.exportEmployees(query);
            ExcelUtil.exportExcel(rows, "员工数据", OaEmployeeExportVo.class, response);
        }
    }

    // ---------------------------------------------------------------- 内部方法

    private R<OaEmployeeVo> runIdempotent(String idempotencyKey, Object body,
                                          java.util.function.Supplier<OaEmployeeVo> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        Long userId = LoginHelper.getUserId();
        String path = "/api/v1/hr/employees";
        String replayRef = idempotencyGuard.begin(userId, idempotencyKey.trim(), path,
            IdempotencyGuard.digest(JsonUtils.toJsonString(body)));
        if (replayRef != null) {
            return R.ok(employeeService.selectEmployee(Long.valueOf(replayRef)));
        }
        OaEmployeeVo vo = action.get();
        idempotencyGuard.complete(userId, idempotencyKey.trim(), String.valueOf(vo.getId()));
        return R.ok(vo);
    }

    private boolean canViewSensitive() {
        LoginUser user = LoginHelper.getLoginUser();
        return HrAccessPolicy.canViewSensitive(LoginHelper.isSuperAdmin(),
            user == null ? null : user.getMenuPermission());
    }
}
