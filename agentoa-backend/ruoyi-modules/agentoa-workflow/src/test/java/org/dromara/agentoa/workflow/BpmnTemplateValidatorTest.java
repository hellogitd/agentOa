package org.dromara.agentoa.workflow;

import org.dromara.agentoa.workflow.domain.template.BuiltInTemplateSpec;
import org.dromara.agentoa.workflow.service.support.BpmnTemplateValidator;
import org.dromara.agentoa.workflow.service.support.BuiltInTemplateCatalog;
import org.dromara.agentoa.workflow.service.support.FlowChainCompiler;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnTemplateValidatorTest {

    /** 26 个内置模板的编译产物必须全部通过受控门禁（M4） */
    @Test
    void allBuiltInTemplatesCompileIntoControlledBpmn() {
        assertThat(BuiltInTemplateCatalog.all()).hasSize(26);
        for (BuiltInTemplateSpec spec : BuiltInTemplateCatalog.all()) {
            FlowChainCompiler.CompiledFlow compiled =
                FlowChainCompiler.compile(spec.getChain(), spec.getProcessKey(), spec.getProcessName());
            String summary = BpmnTemplateValidator.validate(compiled.bpmnXml().getBytes(StandardCharsets.UTF_8));
            assertThat(summary)
                .as("模板 %s 编译产物必须受控", spec.getProcessKey())
                .contains("userTask:");
            assertThat(compiled.bpmnXml()).contains("<process id=\"" + spec.getProcessKey() + "\"");
        }
    }

    /** 仓库里的 BPMN 参考样本（受控白名单回归用例）也必须持续合法 */
    @Test
    void classpathBpmnFixturesStillPassValidation() throws Exception {
        for (String resource : new String[]{
            "workflow/bpmn/leave.bpmn20.xml", "workflow/bpmn/overtime.bpmn20.xml",
            "workflow/bpmn/correction.bpmn20.xml", "workflow/bpmn/reimburse.bpmn20.xml",
            "workflow/bpmn/regularize.bpmn20.xml", "workflow/bpmn/offboard.bpmn20.xml",
            "workflow/bpmn/countersign.bpmn20.xml", "workflow/bpmn/eitherSign.bpmn20.xml"}) {
            byte[] xml = new ClassPathResource(resource).getInputStream().readAllBytes();
            assertThat(BpmnTemplateValidator.validate(xml)).contains("userTask:");
        }
    }

    @Test
    void rejectsUnknownElementsAndExpressionAttacks() {
        String expressionAttack = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://agentoa.org/workflow">
              <process id="evil" name="evil" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="t1"/>
                <userTask id="t1" name="t1">
                  <extensionElements>
                    <flowable:taskListener event="create" class="org.springframework.beans.factory.config.MethodInvokingFactoryBean">
                      <flowable:field name="rule"><flowable:string>SELF</flowable:string></flowable:field>
                    </flowable:taskListener>
                  </extensionElements>
                </userTask>
                <sequenceFlow id="f2" sourceRef="t1" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(expressionAttack.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未授权的监听器类");

        String scriptAttack = expressionAttack
            .replace("org.springframework.beans.factory.config.MethodInvokingFactoryBean",
                "org.dromara.agentoa.workflow.assigner.AssigneeTaskListener")
            .replace("<startEvent id=\"start\"/>",
                "<startEvent id=\"start\"/>\n<scriptTask id=\"hack\" scriptFormat=\"groovy\"><script>println 1</script></scriptTask>");
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(scriptAttack.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未授权元素");

        String conditionAttack = expressionAttack
            .replace("org.springframework.beans.factory.config.MethodInvokingFactoryBean",
                "org.dromara.agentoa.workflow.assigner.AssigneeTaskListener")
            .replace("<sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"end\"/>",
                "<exclusiveGateway id=\"gw\"/>\n<sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"gw\"><conditionExpression>${T(java.lang.Runtime).getRuntime().exec('id')}</conditionExpression></sequenceFlow>\n<sequenceFlow id=\"f3\" sourceRef=\"gw\" targetRef=\"end\"/>");
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(conditionAttack.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件表达式不在白名单");
    }

    @Test
    void rejectsUnknownAssigneeRule() {
        String unknownRule = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://agentoa.org/workflow">
              <process id="p" name="p" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="t1"/>
                <userTask id="t1" name="t1">
                  <extensionElements>
                    <flowable:taskListener event="create" class="org.dromara.agentoa.workflow.assigner.AssigneeTaskListener">
                      <flowable:field name="rule"><flowable:string>BEAN:evilBean.run</flowable:string></flowable:field>
                    </flowable:taskListener>
                  </extensionElements>
                </userTask>
                <sequenceFlow id="f2" sourceRef="t1" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(unknownRule.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("办理人规则不在白名单");
    }

    private static final String MI_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://agentoa.org/workflow">
          <process id="mi" name="mi" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="t1"/>
            <userTask id="t1" name="t1">
              %s
              <extensionElements>
                <flowable:taskListener event="create" class="org.dromara.agentoa.workflow.assigner.AssigneeTaskListener">
                  <flowable:field name="rule"><flowable:string>%s</flowable:string></flowable:field>
                </flowable:taskListener>
              </extensionElements>
            </userTask>
            <sequenceFlow id="f2" sourceRef="t1" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    private static String mi(String condition, String rule) {
        return MI_TEMPLATE.formatted("""
            <multiInstanceLoopCharacteristics isSequential="false"
                                              flowable:collection="${panelAssignees}"
                                              flowable:elementVariable="assignee">
              <completionCondition>%s</completionCondition>
            </multiInstanceLoopCharacteristics>
            """.formatted(condition), rule);
    }

    @Test
    void multiInstanceWhitelistAcceptsCountersignAndEitherSign() {
        assertThat(BpmnTemplateValidator.validate(
            mi("${nrOfCompletedInstances == nrOfInstances}", "ROLE:dept_manager").getBytes(StandardCharsets.UTF_8)))
            .contains("userTask:ROLE:dept_manager(MI)");
        assertThat(BpmnTemplateValidator.validate(
            mi("${nrOfCompletedInstances >= 1}", "WHITELIST:1,2").getBytes(StandardCharsets.UTF_8)))
            .contains("(MI)");
    }

    @Test
    void multiInstanceRejectsForgedCompletionConditionAndScriptCardinality() {
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(
            mi("${execution.getVariable('x')}", "ROLE:dept_manager").getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("完成条件不在白名单");

        String scriptCardinality = MI_TEMPLATE.formatted("""
            <multiInstanceLoopCharacteristics isSequential="false"
                                              flowable:collection="${panelAssignees}"
                                              flowable:elementVariable="assignee">
              <loopCardinality>${java.lang.Runtime.getRuntime().exec('id')}</loopCardinality>
              <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
            </multiInstanceLoopCharacteristics>
            """, "ROLE:dept_manager");
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(scriptCardinality.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("loopCardinality");
    }

    @Test
    void multiInstanceRejectsSequentialAndSinglePersonRules() {
        String sequential = MI_TEMPLATE.formatted("""
            <multiInstanceLoopCharacteristics isSequential="true"
                                              flowable:collection="${panelAssignees}"
                                              flowable:elementVariable="assignee">
              <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
            </multiInstanceLoopCharacteristics>
            """, "ROLE:dept_manager");
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(sequential.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("仅允许并行");

        assertThatThrownBy(() -> BpmnTemplateValidator.validate(
            mi("${nrOfCompletedInstances == nrOfInstances}", "LEADER").getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("仅允许集合型规则");
    }

    @Test
    void multiInstanceRejectsScriptedCollectionExpression() {
        String scripted = MI_TEMPLATE.formatted("""
            <multiInstanceLoopCharacteristics isSequential="false"
                                              flowable:collection="${T(java.lang.Runtime).getRuntime().exec('id')}"
                                              flowable:elementVariable="assignee">
              <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
            </multiInstanceLoopCharacteristics>
            """, "ROLE:dept_manager");
        assertThatThrownBy(() -> BpmnTemplateValidator.validate(scripted.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("collection");
    }
}
