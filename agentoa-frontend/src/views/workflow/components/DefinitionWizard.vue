<template>
  <el-dialog
    :model-value="modelValue"
    :title="definition ? `编辑流程 - ${definition.processName}` : '新建流程'"
    width="960px"
    top="6vh"
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <el-steps :active="step" finish-status="success" simple style="margin-bottom: 16px">
      <el-step title="基础信息" />
      <el-step title="表单设计" />
      <el-step title="审批链配置" />
    </el-steps>

    <!-- 步骤 1：基础信息 -->
    <el-form v-show="step === 0" :model="basic" label-width="110px">
      <el-form-item label="流程 Key" required>
        <el-input v-model="basic.processKey" :disabled="!!definition" placeholder="字母开头，仅字母数字下划线" maxlength="64" />
        <div class="wizard-hint">创建后不可修改；内置模板的 Key 不可占用。</div>
      </el-form-item>
      <el-form-item label="流程名称" required>
        <el-input v-model="basic.processName" maxlength="64" />
      </el-form-item>
      <el-form-item label="流程分类" required>
        <el-select v-model="basic.categoryId" style="width: 100%">
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型">
        <el-input v-model="basic.businessType" placeholder="默认 generic" maxlength="32" />
      </el-form-item>
      <el-form-item label="图标">
        <el-input v-model="basic.icon" maxlength="64" placeholder="图标标识，可留空" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="basic.sort" :min="0" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="basic.remark" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>
    </el-form>

    <!-- 步骤 2：表单设计 -->
    <div v-show="step === 1">
      <el-form label-width="110px" inline style="margin-bottom: 12px">
        <el-form-item label="表单 Key" required>
          <el-input v-model="formDesign.formKey" placeholder="字母开头，仅字母数字下划线" style="width: 220px" :disabled="!!definition" />
        </el-form-item>
        <el-form-item label="表单名称" required>
          <el-input v-model="formDesign.formName" style="width: 220px" />
        </el-form-item>
      </el-form>
      <FormDesigner v-model="formDesign.schema" />
    </div>

    <!-- 步骤 3：审批链配置 -->
    <div v-show="step === 2">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title> 审批链按顺序执行；条件分支互斥，最多一个默认分支。抄送节点在流程发起时生成抄送记录。 </template>
      </el-alert>
      <ChainNodes v-model="chain.nodes" />
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button v-if="step > 0" @click="step -= 1">上一步</el-button>
      <el-button v-if="step < 2" type="primary" @click="next">下一步</el-button>
      <el-button v-else type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import FormDesigner from './FormDesigner.vue';
import ChainNodes from './ChainNodes.vue';
import {
  createDefinition,
  listCategories,
  updateDefinition,
  type CategoryVO,
  type DefinitionVO,
  type FlowChainConfig,
  type FlowChainNode,
  type FormSchema
} from '@/api/workflow';

const props = defineProps<{ modelValue: boolean; definition?: DefinitionVO | null }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'saved'): void }>();

const step = ref(0);
const saving = ref(false);
const categories = ref<CategoryVO[]>([]);

const basic = reactive({
  processKey: '',
  processName: '',
  categoryId: '',
  businessType: 'generic',
  icon: '',
  sort: 0,
  remark: ''
});

const formDesign = reactive<{ formKey: string; formName: string; schema: FormSchema }>({
  formKey: '',
  formName: '',
  schema: { schemaVersion: 1, fields: [] }
});

const chain = ref<FlowChainConfig>({ chainVersion: 1, nodes: [] });

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    step.value = 0;
    await loadCategories();
    reset();
    if (props.definition) {
      fillFrom(props.definition);
    }
  }
);

async function loadCategories() {
  const res: any = await listCategories();
  categories.value = res.data || [];
}

function reset() {
  basic.processKey = '';
  basic.processName = '';
  basic.categoryId = '';
  basic.businessType = 'generic';
  basic.icon = '';
  basic.sort = 0;
  basic.remark = '';
  formDesign.formKey = '';
  formDesign.formName = '';
  formDesign.schema = { schemaVersion: 1, fields: [] };
  chain.value = { chainVersion: 1, nodes: [] };
}

function fillFrom(definition: DefinitionVO) {
  basic.processKey = definition.processKey;
  basic.processName = definition.processName;
  basic.categoryId = definition.categoryId;
  basic.businessType = definition.businessType;
  basic.icon = definition.icon || '';
  basic.sort = definition.sort ?? 0;
  basic.remark = definition.remark || '';
  if (definition.form) {
    formDesign.formKey = definition.form.formKey;
    formDesign.formName = definition.form.formName;
    try {
      const parsed = typeof definition.form.schema === 'string' ? JSON.parse(definition.form.schema) : definition.form.schema;
      formDesign.schema = { schemaVersion: parsed?.schemaVersion ?? 1, fields: parsed?.fields ?? [] };
    } catch {
      formDesign.schema = { schemaVersion: 1, fields: [] };
    }
  }
  if (definition.chain) {
    try {
      const parsed = typeof definition.chain === 'string' ? JSON.parse(definition.chain) : definition.chain;
      chain.value = { chainVersion: parsed?.chainVersion ?? 1, nodes: parsed?.nodes ?? [] };
    } catch {
      chain.value = { chainVersion: 1, nodes: [] };
    }
  }
}

function validateStep(current: number): boolean {
  if (current === 0) {
    if (!props.definition && !basic.processKey.trim()) {
      ElMessage.warning('请填写流程 Key');
      return false;
    }
    if (!basic.processName.trim()) {
      ElMessage.warning('请填写流程名称');
      return false;
    }
    if (!basic.categoryId) {
      ElMessage.warning('请选择流程分类');
      return false;
    }
  }
  if (current === 1) {
    if (!formDesign.formKey.trim()) {
      ElMessage.warning('请填写表单 Key');
      return false;
    }
    if (!formDesign.formName.trim()) {
      ElMessage.warning('请填写表单名称');
      return false;
    }
    if (!formDesign.schema.fields.length) {
      ElMessage.warning('请至少添加一个表单字段');
      return false;
    }
  }
  return true;
}

function next() {
  if (!validateStep(step.value)) return;
  step.value += 1;
}

// ---- 审批链本地校验（与 FlowChainCompiler 编译错误对齐，提交前拦截） ----

const NODE_ID_RE = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;
const ROLE_KEY_RE = /^[a-z][a-z0-9_]{0,31}$/;
const WHITELIST_ID_RE = /^\d{1,19}$/;
const SIMPLE_RULES = ['SELF', 'LEADER', 'DEPT_HEAD', 'USER_SELECT'];
const SINGLE_PERSON_RULES = ['SELF', 'LEADER', 'DEPT_HEAD'];
const CONDITION_OPS = ['gt', 'ge', 'lt', 'le', 'eq', 'ne'];
const MAX_NODES_PER_LEVEL = 24;
const MAX_TOTAL_TASKS = 20;
const MAX_BRANCHES = 5;
const MAX_BRANCH_DEPTH = 3;
const MAX_BRANCH_NODES = 10;
const MAX_THRESHOLD = 1000000000;

interface ChainCtx {
  ids: Set<string>;
  tasks: number;
}

function validateChain(nodes: FlowChainNode[] | undefined, depth: number, ctx: ChainCtx): string | null {
  if (depth > MAX_BRANCH_DEPTH) return `条件分支嵌套过深（最多 ${MAX_BRANCH_DEPTH} 层）`;
  if (!nodes || !nodes.length) return null;
  if (nodes.length > MAX_NODES_PER_LEVEL) return `单层节点数量超过上限 ${MAX_NODES_PER_LEVEL}`;
  for (const node of nodes) {
    if (!node || !node.id || !NODE_ID_RE.test(node.id)) return `节点 ID 非法: ${node && node.id}`;
    if (ctx.ids.has(node.id)) return `节点 ID 重复: ${node.id}`;
    ctx.ids.add(node.id);
    if (!node.name || !node.name.trim() || node.name.length > 64) return `节点名称非法: ${node.id}`;
    if (node.type === 'approve' || node.type === 'cc') {
      const ruleError = validateRule(node);
      if (ruleError) return ruleError;
      const rule = (node.assigneeRule || '').trim();
      if (node.type === 'approve') {
        const multiSign = node.signMode === 'COUNTERSIGN' || node.signMode === 'EITHERSIGN';
        if (multiSign && SINGLE_PERSON_RULES.includes(rule)) {
          return `会签/或签节点仅允许集合型规则（角色、指定成员或发起人自选）: ${node.name}`;
        }
        ctx.tasks += 1;
        if (ctx.tasks > MAX_TOTAL_TASKS) return `审批节点数量超过上限 ${MAX_TOTAL_TASKS}`;
      } else if (rule === 'USER_SELECT') {
        return `抄送节点不支持发起人自选: ${node.name}`;
      }
    } else if (node.type === 'branch') {
      const branchError = validateBranch(node, depth, ctx);
      if (branchError) return branchError;
    } else {
      return `未知节点类型: ${node.type}`;
    }
  }
  return null;
}

function validateRule(node: FlowChainNode): string | null {
  const rule = (node.assigneeRule || '').trim();
  if (!rule) return `节点「${node.name || node.id}」缺少办理人规则`;
  if (SIMPLE_RULES.includes(rule)) return null;
  if (rule.startsWith('ROLE:')) {
    return ROLE_KEY_RE.test(rule.slice('ROLE:'.length)) ? null : `节点「${node.name}」角色标识非法`;
  }
  if (rule.startsWith('WHITELIST:')) {
    const ids = rule
      .slice('WHITELIST:'.length)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (!ids.length) return `节点「${node.name}」未选择指定成员`;
    if (ids.some((id) => !WHITELIST_ID_RE.test(id))) return `节点「${node.name}」指定成员 ID 非法`;
    return null;
  }
  return `节点「${node.name}」办理人规则非法`;
}

function validateBranch(node: FlowChainNode, depth: number, ctx: ChainCtx): string | null {
  const branches = node.branches || [];
  if (!branches.length || branches.length > MAX_BRANCHES) {
    return `条件分支数量必须在 1~${MAX_BRANCHES} 之间: ${node.name}`;
  }
  let defaults = 0;
  for (const branch of branches) {
    if (!branch.id || !NODE_ID_RE.test(branch.id)) return `条件分支 ID 非法: ${branch.id}`;
    if (ctx.ids.has(branch.id)) return `条件分支 ID 重复: ${branch.id}`;
    ctx.ids.add(branch.id);
    const cond = branch.condition;
    if (!cond || !cond.field) {
      defaults += 1;
    } else {
      if (cond.field !== 'amount') return `条件字段仅支持金额: ${branch.name}`;
      if (!CONDITION_OPS.includes(cond.op)) return `条件算子非法: ${branch.name}`;
      const value = Number(cond.value);
      if (!Number.isInteger(value) || value < 0 || value > MAX_THRESHOLD) {
        return `条件阈值必须是 0~${MAX_THRESHOLD} 的整数: ${branch.name}`;
      }
    }
    const inner = branch.nodes || [];
    if (inner.length > MAX_BRANCH_NODES) return `分支内节点数量超过上限 ${MAX_BRANCH_NODES}: ${branch.name}`;
    const innerError = validateChain(inner, depth + 1, ctx);
    if (innerError) return innerError;
  }
  if (defaults > 1) return `条件分支最多只能有一个默认分支: ${node.name}`;
  return null;
}

async function submit() {
  if (!validateStep(0) || !validateStep(1)) {
    step.value = 0;
    return;
  }
  if (!chain.value.nodes.length) {
    ElMessage.warning('请至少添加一个审批链节点');
    return;
  }
  const chainError = validateChain(chain.value.nodes, 0, { ids: new Set(), tasks: 0 });
  if (chainError) {
    ElMessage.warning(chainError);
    return;
  }
  saving.value = true;
  try {
    const payload = {
      processName: basic.processName.trim(),
      categoryId: basic.categoryId,
      businessType: basic.businessType.trim() || 'generic',
      icon: basic.icon,
      sort: basic.sort,
      remark: basic.remark,
      form: {
        formKey: formDesign.formKey.trim(),
        formName: formDesign.formName.trim(),
        schema: formDesign.schema
      },
      chain: chain.value
    };
    if (props.definition) {
      await updateDefinition(props.definition.id, payload);
    } else {
      await createDefinition({ processKey: basic.processKey.trim(), ...payload });
    }
    ElMessage.success('已保存');
    emit('saved');
    close();
  } finally {
    saving.value = false;
  }
}

function close() {
  emit('update:modelValue', false);
}
</script>

<style scoped>
.wizard-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}
</style>
