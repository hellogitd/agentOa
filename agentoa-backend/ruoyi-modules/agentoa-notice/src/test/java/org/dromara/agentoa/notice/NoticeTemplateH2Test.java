package org.dromara.agentoa.notice;

import org.dromara.agentoa.notice.domain.bo.NoticeTemplateBo;
import org.dromara.agentoa.notice.domain.bo.TemplateSendBo;
import org.dromara.agentoa.notice.support.NoticeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通知模板（NC-04）：占位符声明校验、按受众渲染发送、偏好抑制与空受众拒绝。
 */
class NoticeTemplateH2Test {

    @BeforeAll
    static void boot() {
        NoticeTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        NoticeTestEnvironment.clearData();
        NoticeTestEnvironment.logout();
        NoticeTestEnvironment.seedDept(NoticeTestEnvironment.DEPT_A, "部门A");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_HR, NoticeTestEnvironment.DEPT_A, "人力");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_EMPLOYEE, NoticeTestEnvironment.DEPT_A, "员工");
        NoticeTestEnvironment.seedUser(NoticeTestEnvironment.USER_OTHER, NoticeTestEnvironment.DEPT_A, "他人");
        NoticeTestEnvironment.loginAs(NoticeTestEnvironment.USER_HR, NoticeTestEnvironment.DEPT_A,
            java.util.Set.of("nt:template:add", "nt:template:edit", "nt:template:remove", "nt:template:send"));
    }

    @Test
    void createValidatesPlaceholderDeclarations() {
        NoticeTemplateBo bo = new NoticeTemplateBo();
        bo.setTemplateCode("welcome_x");
        bo.setName("欢迎");
        bo.setTitleTpl("欢迎 {name}");
        bo.setContentTpl("你好 {name}，部门 {dept}");
        bo.setMsgType("NOTICE");
        bo.setVars(List.of("name"));
        bo.setStatus("1");
        assertThatThrownBy(() -> NoticeTestEnvironment.templateService.create(bo, NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_UNDECLARED_VAR");

        bo.setVars(List.of("name", "dept"));
        var created = NoticeTestEnvironment.templateService.create(bo, NoticeTestEnvironment.USER_HR);
        assertThat(created.getVars()).containsExactly("name", "dept");

        NoticeTemplateBo duplicate = new NoticeTemplateBo();
        duplicate.setTemplateCode("welcome_x");
        duplicate.setName("重复");
        duplicate.setTitleTpl("t");
        duplicate.setContentTpl("c");
        duplicate.setMsgType("NOTICE");
        duplicate.setVars(List.of());
        duplicate.setStatus("1");
        assertThatThrownBy(() -> NoticeTestEnvironment.templateService.create(duplicate, NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_CODE_DUPLICATE");
    }

    @Test
    void sendRendersAndFansOutToAudience() {
        NoticeTestEnvironment.seedTemplate("onboard_x", "入职", "欢迎 {name}", "{name} 欢迎加入 {dept}",
            "[\"name\",\"dept\"]", "NOTICE", 1);
        TemplateSendBo bo = new TemplateSendBo();
        bo.setScopeType("1");
        bo.setVars(Map.of("name", "张三", "dept", "研发部"));

        int delivered = NoticeTestEnvironment.templateService.send("onboard_x", bo, NoticeTestEnvironment.USER_HR);

        assertThat(delivered).isEqualTo(3);
        List<Map<String, Object>> events = NoticeTestEnvironment.jdbcTemplate()
            .queryForList("SELECT event_type, payload FROM sys_outbox ORDER BY id");
        assertThat(events).hasSize(3);
        String payload = String.valueOf(events.get(0).get("payload"));
        assertThat(payload).contains("欢迎 张三").contains("张三 欢迎加入 研发部");
        assertThat(events.get(0).get("event_type")).isEqualTo("NOTICE");
    }

    @Test
    void sendRejectsMissingAndUnknownVariables() {
        NoticeTestEnvironment.seedTemplate("tpl_var", "变量", "Hi {name}", "Bye {name}",
            "[\"name\"]", "NOTICE", 1);
        TemplateSendBo missing = new TemplateSendBo();
        missing.setScopeType("1");
        missing.setVars(Map.of());
        assertThatThrownBy(() -> NoticeTestEnvironment.templateService.send("tpl_var", missing,
            NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_VAR_MISSING");

        TemplateSendBo unknown = new TemplateSendBo();
        unknown.setScopeType("1");
        unknown.setVars(Map.of("name", "x", "hacker", "y"));
        assertThatThrownBy(() -> NoticeTestEnvironment.templateService.send("tpl_var", unknown,
            NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_UNDECLARED_VAR");
    }

    @Test
    void sendHonorsPreferenceAndRejectsEmptyAudience() {
        NoticeTestEnvironment.seedTemplate("tpl_pref", "偏好", "标题", "内容", "[]", "NOTICE", 1);
        NoticeTestEnvironment.preferences.insert(NoticeTestEnvironment.USER_EMPLOYEE, "NOTICE", 0,
            new java.util.Date());
        TemplateSendBo bo = new TemplateSendBo();
        bo.setScopeType("1");
        assertThat(NoticeTestEnvironment.templateService.send("tpl_pref", bo, NoticeTestEnvironment.USER_HR))
            .isEqualTo(2);
        assertThat(NoticeTestEnvironment.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM sys_outbox", Long.class))
            .isEqualTo(2);

        TemplateSendBo empty = new TemplateSendBo();
        empty.setScopeType("4");
        empty.setScopeValues("999");
        assertThatThrownBy(() -> NoticeTestEnvironment.templateService.send("tpl_pref", empty,
            NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_AUDIENCE_EMPTY");
    }

    @Test
    void disabledTemplateCannotSend() {
        NoticeTestEnvironment.seedTemplate("tpl_off", "停用", "标题", "内容", "[]", "NOTICE", 2);
        TemplateSendBo bo = new TemplateSendBo();
        bo.setScopeType("1");
        assertThatThrownBy(() -> NoticeTestEnvironment.templateService.send("tpl_off", bo,
            NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_DISABLED");
    }
}
