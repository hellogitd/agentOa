package org.dromara.agentoa.workflow.support;

import org.dromara.agentoa.workflow.domain.bo.FormSchemaBo;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.chain.FlowChainNode;
import org.dromara.agentoa.workflow.domain.chain.FlowCondition;
import org.dromara.agentoa.workflow.service.support.FlowChainCompiler;
import org.dromara.agentoa.workflow.service.support.FormSchemaSupport;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M6 · 链校验攻击用例：结构化审批链是唯一流程结构输入，
 * 任何注入（XML/SpEL/Bean/脚本/白名单绕过）都必须在编译期被拒绝，产物必须始终受控。
 */
class FlowChainAttackTest {

    // ------------------------------------------------------------------ 构造工具

    private static FlowChainNode approve(String id, String name, String rule) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_APPROVE);
        node.setName(name);
        node.setAssigneeRule(rule);
        node.setSignMode("SINGLE");
        return node;
    }

    private static FlowChainNode cc(String id, String name, String rule) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_CC);
        node.setName(name);
        node.setAssigneeRule(rule);
        return node;
    }

    private static FlowChainNode branch(String id, FlowCondition condition, List<FlowChainNode> inner) {
        FlowChainNode node = new FlowChainNode();
        node.setId(id);
        node.setType(FlowChainNode.TYPE_BRANCH);
        node.setName(id);
        FlowChainNode.FlowBranch only = new FlowChainNode.FlowBranch();
        only.setId(id + "_b1");
        only.setCondition(condition);
        only.setNodes(inner);
        node.setBranches(List.of(only));
        return node;
    }

    private static FlowCondition condition(String field, String op, Number value) {
        FlowCondition c = new FlowCondition();
        c.setField(field);
        c.setOp(op);
        c.setValue(value);
        return c;
    }

    private static FlowChainConfig chain(List<FlowChainNode> nodes) {
        FlowChainConfig config = new FlowChainConfig();
        config.setChainVersion(1);
        config.setNodes(nodes);
        return config;
    }

    private static String compile(FlowChainConfig config) {
        return FlowChainCompiler.compile(config, "p1", "测试流程").bpmnXml();
    }

    // ------------------------------------------------------------------ XML / 表达式注入

    @Test
    void rejectsXmlInjectionThroughNodeId() {
        String[] payloads = {
            "n1\"></userTask><scriptTask id=\"x\"/>",
            "n1</id><script>java.lang.Runtime</script>",
            "n1&#x3C;script&#x3E;",
            "n1\n<scriptTask/>"
        };
        for (String payload : payloads) {
            assertThatThrownBy(() -> compile(chain(List.of(approve(payload, "x", "SELF")))))
                .as("节点 ID 注入必须被拒绝: %s", payload)
                .isInstanceOf(ServiceException.class);
        }
    }

    @Test
    void nodeNameInjectionIsEscapedNotExecuted() {
        // 名称是展示字段：接受任意文本，但必须做 XML 转义，绝不允许注入出新元素
        String[] payloads = {
            "n1\"></userTask><scriptTask id=\"x\"/>",
            "A & B < C > D",
            "quote\" and 'apos'"
        };
        for (String payload : payloads) {
            String xml = compile(chain(List.of(approve("n1", payload, "SELF"))));
            assertThat(xml).as("名称注入必须被转义: %s", payload).doesNotContain("<scriptTask");
            assertThat(xml).doesNotContain("java.lang.Runtime");
            assertThat(xml).contains("userTask id=\"n1\"");
        }
    }

    @Test
    void compiledBpmnNeverContainsExecutableConstructs() {
        String xml = compile(chain(List.of(
            approve("n1", "主管审批", "LEADER"),
            cc("n2", "抄送人事", "ROLE:hr"),
            branch("n3", condition("amount", "gt", 50000), List.of(approve("n4", "总经理审批", "ROLE:gm"))))));
        assertThat(xml).doesNotContain("<scriptTask");
        assertThat(xml).doesNotContain("<serviceTask");
        assertThat(xml).doesNotContain("<script");
        assertThat(xml).doesNotContain("java.lang.Runtime");
        assertThat(xml).doesNotContain("T(java");
        assertThat(xml).doesNotContain("MethodInvoking");
        assertThat(xml).doesNotContain("classLoader");
    }

    @Test
    void escapesSpecialCharactersInNodeName() {
        String xml = compile(chain(List.of(approve("n1", "A & B < C > D", "LEADER"))));
        assertThat(xml).contains("A &amp; B &lt; C &gt; D");
        assertThat(xml).doesNotContain("A & B < C > D");
    }

    // ------------------------------------------------------------------ 条件表达式注入

    @Test
    void rejectsNonNumericAndExpressionConditionValues() {
        // value 是 Number 类型，Jackson 解析 "T(java...)" 会在进入编译前失败；
        // 这里覆盖编译层对小数/负数/超界的拒绝
        assertThatThrownBy(() -> compile(chain(List.of(branch("b", condition("amount", "gt", -1), List.of())))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件阈值");
        assertThatThrownBy(() -> compile(chain(List.of(branch("b", condition("amount", "gt", 1.5), List.of())))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件阈值");
        assertThatThrownBy(() -> compile(chain(List.of(branch("b", condition("amount", "gt", 2_000_000_000L), List.of())))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件阈值");
    }

    @Test
    void rejectsInjectedConditionFieldAndOperator() {
        assertThatThrownBy(() -> compile(chain(List.of(branch("b", condition("T(java.lang.Runtime)", "gt", 1), List.of())))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件字段");
        assertThatThrownBy(() -> compile(chain(List.of(branch("b", condition("amount", "gt;drop", 1), List.of())))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("条件算子");
    }

    // ------------------------------------------------------------------ 办理人规则绕过

    @Test
    void rejectsAssigneeRuleBypassAttempts() {
        String[] badRules = {
            "BEAN:evilBean.run",
            "ROLE:superadmin",
            "ROLE:hr'; DROP TABLE oa_flow_definition;--",
            "ROLE:HR",
            "WHITELIST:1,2); Runtime.exec(",
            "WHITELIST:-1",
            "WHITELIST:abc",
            "SELF<script>",
            "T(java.lang.Runtime).getRuntime()"
        };
        for (String rule : badRules) {
            assertThatThrownBy(() -> compile(chain(List.of(approve("n1", "x", rule)))))
                .as("办理人规则必须被拒绝: %s", rule)
                .isInstanceOf(ServiceException.class);
        }
    }

    @Test
    void rejectsCcRuleInjection() {
        assertThatThrownBy(() -> compile(chain(List.of(cc("n1", "x", "ROLE:hr</flowable:string><script/>")))))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> compile(chain(List.of(cc("n1", "x", "WHITELIST:1;delete")))))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsUserSelectRuleInjection() {
        String[] badRules = {
            "USER_SELECT<script>",
            "USER_SELECT; DROP TABLE oa_flow_instance;--",
            "user_select",
            "USER_SELECT:1,2",
            "USER_SELECT</flowable:string><script/>",
            "USER_SELECTWHITELIST:1",
            "USER_SELECT 1",
            "USER_SELECT\n<scriptTask/>"
        };
        for (String rule : badRules) {
            assertThatThrownBy(() -> compile(chain(List.of(approve("n1", "x", rule)))))
                .as("USER_SELECT 规则注入被拒绝: %s", rule)
                .isInstanceOf(ServiceException.class);
        }
        // 抄送节点即使写成合法 USER_SELECT 也不允许
        assertThatThrownBy(() -> compile(chain(List.of(cc("n1", "x", "USER_SELECT")))))
            .isInstanceOf(ServiceException.class);
    }

    // ------------------------------------------------------------------ 结构约束

    @Test
    void rejectsStructureAbuse() {
        // 重复 ID
        assertThatThrownBy(() -> compile(chain(List.of(approve("dup", "a", "SELF"), approve("dup", "b", "SELF")))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("ID 重复");

        // 非法 ID 形态
        assertThatThrownBy(() -> compile(chain(List.of(approve("1bad", "a", "SELF")))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("节点 ID");
        assertThatThrownBy(() -> compile(chain(List.of(approve("bad-id", "a", "SELF")))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("节点 ID");

        // 未知类型
        FlowChainNode unknown = approve("n1", "a", "SELF");
        unknown.setType("serviceTask");
        assertThatThrownBy(() -> compile(chain(List.of(unknown))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("节点类型");

        // 未支持的链结构版本
        FlowChainConfig v2 = chain(List.of(approve("n1", "a", "SELF")));
        v2.setChainVersion(99);
        assertThatThrownBy(() -> compile(v2))
            .isInstanceOf(ServiceException.class).hasMessageContaining("结构版本");

        // 空链
        assertThatThrownBy(() -> compile(chain(List.of())))
            .isInstanceOf(ServiceException.class);

        // 节点数量上限
        FlowChainNode[] many = new FlowChainNode[25];
        for (int i = 0; i < many.length; i++) {
            many[i] = approve("n" + i, "x", "SELF");
        }
        assertThatThrownBy(() -> compile(chain(List.of(many))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("上限");
    }

    @Test
    void rejectsBranchStructureAbuse() {
        // 两个默认分支
        FlowChainNode b = new FlowChainNode();
        b.setId("b");
        b.setType(FlowChainNode.TYPE_BRANCH);
        b.setName("b");
        FlowChainNode.FlowBranch d1 = new FlowChainNode.FlowBranch();
        d1.setId("d1");
        d1.setCondition(null);
        FlowChainNode.FlowBranch d2 = new FlowChainNode.FlowBranch();
        d2.setId("d2");
        d2.setCondition(null);
        b.setBranches(List.of(d1, d2));
        assertThatThrownBy(() -> compile(chain(List.of(b))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("默认分支");

        // 分支数量上限
        FlowChainNode wide = new FlowChainNode();
        wide.setId("w");
        wide.setType(FlowChainNode.TYPE_BRANCH);
        wide.setName("w");
        wide.setBranches(java.util.stream.IntStream.range(0, 6).mapToObj(i -> {
            FlowChainNode.FlowBranch br = new FlowChainNode.FlowBranch();
            br.setId("wb" + i);
            br.setCondition(i == 0 ? null : condition("amount", "gt", i));
            br.setNodes(List.of());
            return br;
        }).toList());
        assertThatThrownBy(() -> compile(chain(List.of(wide))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("条件分支数量");

        // 空分支集合
        FlowChainNode empty = new FlowChainNode();
        empty.setId("e");
        empty.setType(FlowChainNode.TYPE_BRANCH);
        empty.setName("e");
        empty.setBranches(List.of());
        assertThatThrownBy(() -> compile(chain(List.of(empty))))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsMultiInstanceOnSinglePersonRule() {
        FlowChainNode node = approve("n1", "x", "LEADER");
        node.setSignMode("COUNTERSIGN");
        assertThatThrownBy(() -> compile(chain(List.of(node))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("集合型规则");

        FlowChainNode node2 = approve("n2", "x", "SELF");
        node2.setSignMode("EITHERSIGN");
        assertThatThrownBy(() -> compile(chain(List.of(node2))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("集合型规则");
    }

    // ------------------------------------------------------------------ 表单 Schema

    @Test
    void rejectsFormSchemaInjection() {
        FormSchemaBo.FormFieldBo badType = new FormSchemaBo.FormFieldBo();
        badType.setKey("f1");
        badType.setLabel("x");
        badType.setType("script");
        FormSchemaBo schema = new FormSchemaBo();
        schema.setFields(List.of(badType));
        assertThatThrownBy(() -> FormSchemaSupport.validateAndSerialize(schema))
            .isInstanceOf(ServiceException.class).hasMessageContaining("字段类型");

        FormSchemaBo.FormFieldBo badKey = new FormSchemaBo.FormFieldBo();
        badKey.setKey("f1\"><script>");
        badKey.setLabel("x");
        badKey.setType("input");
        FormSchemaBo schema2 = new FormSchemaBo();
        schema2.setFields(List.of(badKey));
        assertThatThrownBy(() -> FormSchemaSupport.validateAndSerialize(schema2))
            .isInstanceOf(ServiceException.class).hasMessageContaining("字段 Key");

        FormSchemaBo.FormFieldBo optionOnInput = new FormSchemaBo.FormFieldBo();
        optionOnInput.setKey("f1");
        optionOnInput.setLabel("x");
        optionOnInput.setType("input");
        FormSchemaBo.FormOptionBo opt = new FormSchemaBo.FormOptionBo();
        opt.setLabel("a");
        opt.setValue("b");
        optionOnInput.setOptions(List.of(opt));
        FormSchemaBo schema3 = new FormSchemaBo();
        schema3.setFields(List.of(optionOnInput));
        assertThatThrownBy(() -> FormSchemaSupport.validateAndSerialize(schema3))
            .isInstanceOf(ServiceException.class).hasMessageContaining("仅下拉字段");
    }

    @Test
    void acceptsValidFormSchemaAndRoundTrips() {
        FormSchemaBo.FormFieldBo field = new FormSchemaBo.FormFieldBo();
        field.setKey("amount");
        field.setLabel("金额");
        field.setType("number");
        field.setRequired(true);
        FormSchemaBo schema = new FormSchemaBo();
        schema.setFields(List.of(field));
        assertThatCode(() -> FormSchemaSupport.validateAndSerialize(schema)).doesNotThrowAnyException();
    }
}
