<template>
  <div class="chain-nodes" :class="{ nested: depth > 0 }">
    <div v-for="(node, index) in nodes" :key="node.id" class="chain-node">
      <div class="chain-node-head" :class="node.type">
        <span class="chain-node-icon">{{ iconOf(node) }}</span> <span class="chain-node-name">{{ node.name || '未命名节点' }}</span>
        <span class="chain-node-meta">{{ metaOf(node) }}</span>
        <span class="chain-node-actions" @click.stop>
          <el-button link size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button>
          <el-button link size="small" :disabled="index === nodes.length - 1" @click="move(index, 1)">下移</el-button>
          <el-button link size="small" type="danger" @click="remove(index)">删除</el-button>
        </span>
      </div>

      <!-- 审批人 / 抄送人 配置 -->
      <div v-if="node.type === 'approve' || node.type === 'cc'" class="chain-node-body">
        <el-form label-width="90px" size="small" inline>
          <el-form-item label="节点名称" required>
            <el-input v-model="node.name" style="width: 180px" @change="touch" />
          </el-form-item>
          <el-form-item label="办理人" required>
            <el-select :model-value="ruleKindOf(node)" style="width: 150px" @change="(v: any) => onRuleKindChange(node, v)">
              <el-option v-for="opt in ruleKinds" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-select
              v-if="ruleKindOf(node) === 'ROLE'"
              :model-value="roleKeyOf(node)"
              style="width: 150px; margin-left: 6px"
              @change="(v: any) => onRoleChange(node, v)"
            >
              <el-option v-for="opt in roleOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <template v-if="ruleKindOf(node) === 'WHITELIST'">
              <el-input
                :model-value="whitelistLabel(node)"
                readonly
                placeholder="点击选择成员"
                style="width: 220px; margin-left: 6px"
                @click="openUserPicker(node)"
              />
              <el-button link size="small" style="margin-left: 4px" @click="openUserPicker(node)">选择成员</el-button>
            </template>
          </el-form-item>
          <el-form-item v-if="node.type === 'approve'" label="签署方式">
            <el-radio-group v-model="node.signMode" size="small" @change="touch">
              <el-radio-button value="SINGLE">单人</el-radio-button>
              <el-radio-button value="COUNTERSIGN">会签</el-radio-button>
              <el-radio-button value="EITHERSIGN">或签</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <div v-if="node.type === 'approve' && node.signMode !== 'SINGLE'" class="chain-hint">
          会签/或签为并行多实例，办理人必须是角色、指定成员或发起人自选（不支持「直属主管」等单人规则）。
        </div>
        <div v-if="node.type === 'approve' && ruleKindOf(node) === 'USER_SELECT'" class="chain-hint">
          发起人自选：发起申请时由发起人选定审批人。单人签署选 1 人；会签/或签选 ≥1 人（多选）。
        </div>
        <div v-if="node.type === 'cc' && ruleKindOf(node) === 'USER_SELECT'" class="chain-hint">抄送节点不支持发起人自选。</div>
      </div>

      <!-- 条件分支配置 -->
      <div v-else-if="node.type === 'branch'" class="chain-node-body">
        <el-form label-width="90px" size="small" inline>
          <el-form-item label="分支名称">
            <el-input v-model="node.name" style="width: 180px" @change="touch" />
          </el-form-item>
        </el-form>
        <div class="chain-branches">
          <div v-for="(branch, bi) in node.branches || []" :key="branch.id" class="chain-branch">
            <div class="chain-branch-head">
              <span>分支 {{ bi + 1 }}</span>
              <el-input v-model="branch.name" size="small" placeholder="分支名称" style="width: 130px; margin-left: 6px" @change="touch" />
              <el-button link size="small" type="danger" style="margin-left: auto" @click="removeBranch(node, bi)">删除</el-button>
            </div>

            <div class="chain-branch-cond">
              <template v-if="isDefaultBranch(branch)">
                <el-tag type="info">默认分支（其余情况）</el-tag>
                <el-button link size="small" @click="enableCondition(branch)">改为条件分支</el-button>
              </template>
              <template v-else-if="branch.condition">
                <span class="cond-label">当</span>
                <el-select v-model="branch.condition.field" size="small" style="width: 90px" @change="touch">
                  <el-option label="金额" value="amount" />
                </el-select>
                <el-select v-model="branch.condition.op" size="small" style="width: 100px" @change="touch">
                  <el-option label="大于" value="gt" />
                  <el-option label="大于等于" value="ge" />
                  <el-option label="小于" value="lt" />
                  <el-option label="小于等于" value="le" />
                  <el-option label="等于" value="eq" />
                  <el-option label="不等于" value="ne" />
                </el-select>
                <el-input-number
                  v-model="branch.condition.value"
                  size="small"
                  :min="0"
                  :max="1000000000"
                  :precision="0"
                  style="width: 140px"
                  @change="touch"
                />
                <span class="cond-label">元</span>
                <el-button link size="small" @click="disableCondition(branch)">改为默认分支</el-button>
              </template>
            </div>

            <ChainNodes v-model="branch.nodes" :depth="depth + 1" />
          </div>
        </div>
        <el-button link type="primary" size="small" @click="addBranch(node)">添加分支</el-button>
      </div>
    </div>

    <div class="chain-add">
      <el-button size="small" @click="addNode('approve')">+ 审批人</el-button>
      <el-button size="small" @click="addNode('approve', 'handle')">+ 办理人</el-button>
      <el-button size="small" @click="addNode('cc')">+ 抄送人</el-button>
      <el-button size="small" @click="addNode('branch')">+ 条件分支</el-button>
    </div>

    <UserSelect ref="userSelectRef" :multiple="true" :data="pickerIds" @confirm-callback="onUsersPicked" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { FlowBranch, FlowChainNode, FlowNodeType, FlowSignMode } from '@/api/workflow';
import userApi from '@/api/system/user';
import roleApi from '@/api/system/role';

const props = withDefaults(defineProps<{ modelValue: FlowChainNode[]; depth?: number }>(), { depth: 0 });
const emit = defineEmits<{ (e: 'update:modelValue', value: FlowChainNode[]): void }>();

const nodes = computed<FlowChainNode[]>({
  get: () => props.modelValue || [],
  set: (v) => emit('update:modelValue', v)
});

const ruleKinds = [
  { value: 'SELF', label: '发起人自己' },
  { value: 'LEADER', label: '直属主管' },
  { value: 'DEPT_HEAD', label: '部门负责人' },
  { value: 'USER_SELECT', label: '发起人自选' },
  { value: 'ROLE', label: '角色' },
  { value: 'WHITELIST', label: '指定成员' }
];

/** 角色选项：优先取系统角色列表，失败回退内置 7 项（FlowChainCompiler 角色白名单） */
const roleOptions = ref([
  { value: 'admin', label: '行政' },
  { value: 'gm', label: '总经理' },
  { value: 'hr', label: '人力资源' },
  { value: 'finance', label: '财务' },
  { value: 'cashier', label: '出纳' },
  { value: 'director', label: '总监' },
  { value: 'dept_manager', label: '部门经理' }
]);

onMounted(async () => {
  try {
    const res: any = await roleApi.listRole({ pageNum: 1, pageSize: 100 } as any);
    const rows = (res && (res.rows || res.data)) || [];
    const opts = rows
      .filter((r: any) => r && r.roleKey && (r.status === undefined || r.status === '0'))
      .map((r: any) => ({ value: String(r.roleKey), label: r.roleName || String(r.roleKey) }));
    if (opts.length) roleOptions.value = opts;
  } catch {
    // 无 system:role:list 权限时保留内置选项
  }
});

// ---- 指定成员选人（UserSelect 复用，存储格式仍是 WHITELIST:id,id） ----

const userSelectRef = ref();
const pickerNode = ref<FlowChainNode | null>(null);
const pickerIds = ref('');
/** userId -> 显示名，回显缓存 */
const userNames = ref<Record<string, string>>({});

function openUserPicker(node: FlowChainNode) {
  pickerNode.value = node;
  pickerIds.value = whitelistOf(node);
  userSelectRef.value?.open();
}

function onUsersPicked(users: any[]) {
  const node = pickerNode.value;
  if (!node) return;
  const ids = (users || []).map((u) => String(u.userId)).filter(Boolean);
  (users || []).forEach((u: any) => {
    userNames.value[String(u.userId)] = u.nickName || u.userName || String(u.userId);
  });
  node.assigneeRule = `WHITELIST:${ids.join(',')}`;
  pickerNode.value = null;
  touch();
}

function whitelistLabel(node: FlowChainNode) {
  const ids = whitelistOf(node)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  if (!ids.length) return '';
  return ids.map((id) => userNames.value[id] || id).join('、');
}

/** 回显姓名：对链上尚未缓存的 WHITELIST ID 批量拉取 */
async function hydrateUserNames() {
  const ids = new Set<string>();
  const walk = (list: FlowChainNode[]) => {
    (list || []).forEach((n) => {
      if (n.type === 'approve' || n.type === 'cc') {
        if (ruleKindOf(n) === 'WHITELIST') {
          whitelistOf(n)
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
            .forEach((id) => {
              if (!userNames.value[id]) ids.add(id);
            });
        }
      }
      (n.branches || []).forEach((b) => walk(b.nodes || []));
    });
  };
  walk(nodes.value);
  if (!ids.size) return;
  try {
    const { data } = await userApi.optionSelect([...ids]);
    (data || []).forEach((u: any) => {
      userNames.value[String(u.userId)] = u.nickName || u.userName || String(u.userId);
    });
  } catch {
    // 无权限时回退展示 ID
  }
}

function touch() {
  emit('update:modelValue', [...nodes.value]);
}

function iconOf(node: FlowChainNode) {
  if (node.type === 'approve') return node.kind === 'handle' ? '办' : '审';
  return node.type === 'cc' ? '抄' : '支';
}

function metaOf(node: FlowChainNode) {
  if (node.type === 'approve') {
    const sign = node.signMode === 'COUNTERSIGN' ? '会签' : node.signMode === 'EITHERSIGN' ? '或签' : '单人';
    return `${sign} · ${node.assigneeRule || '-'}`;
  }
  if (node.type === 'cc') return `抄送 · ${node.assigneeRule || '-'}`;
  return `${(node.branches || []).length} 个分支`;
}

// ---- 办理人规则 <-> UI 状态 ----

const ruleKindOf = (node: FlowChainNode) => {
  const rule = node.assigneeRule || 'LEADER';
  if (rule === 'SELF' || rule === 'LEADER' || rule === 'DEPT_HEAD' || rule === 'USER_SELECT') return rule;
  if (rule.startsWith('ROLE:')) return 'ROLE';
  return 'WHITELIST';
};

const roleKeyOf = (node: FlowChainNode) => (node.assigneeRule || '').replace(/^ROLE:/, '') || 'hr';

const whitelistOf = (node: FlowChainNode) => (node.assigneeRule || '').replace(/^WHITELIST:/, '');

function onRuleKindChange(node: FlowChainNode, kind: string) {
  if (kind === 'ROLE') {
    node.assigneeRule = `ROLE:${roleOptions.value[0]?.value || 'hr'}`;
  } else if (kind === 'WHITELIST') {
    node.assigneeRule = 'WHITELIST:';
    touch();
    openUserPicker(node);
    return;
  } else {
    node.assigneeRule = kind;
  }
  touch();
}

function onRoleChange(node: FlowChainNode, roleKey: string) {
  node.assigneeRule = `ROLE:${roleKey}`;
  touch();
}

// ---- 节点操作 ----

let seq = 0;
function newNode(type: FlowNodeType, kind?: string): FlowChainNode {
  seq += 1;
  const id = `${type}${Date.now().toString(36)}${seq}`;
  if (type === 'approve') {
    const node: FlowChainNode = {
      id,
      type,
      name: kind === 'handle' ? '办理' : '审批人',
      assigneeRule: 'LEADER',
      signMode: 'SINGLE' as FlowSignMode
    };
    if (kind) node.kind = kind;
    return node;
  }
  if (type === 'cc') {
    return { id, type, name: '抄送人', assigneeRule: 'ROLE:hr' };
  }
  return {
    id,
    type,
    name: '条件分支',
    branches: [
      {
        id: `${id}_b1`,
        name: '满足条件',
        condition: { field: 'amount', op: 'gt', value: 50000 },
        nodes: []
      },
      { id: `${id}_b2`, name: '其他', condition: null, nodes: [] }
    ]
  };
}

function addNode(type: FlowNodeType, kind?: string) {
  emit('update:modelValue', [...nodes.value, newNode(type, kind)]);
}

function remove(index: number) {
  const next = [...nodes.value];
  next.splice(index, 1);
  emit('update:modelValue', next);
}

function move(index: number, delta: number) {
  const next = [...nodes.value];
  const target = index + delta;
  if (target < 0 || target >= next.length) return;
  [next[index], next[target]] = [next[target], next[index]];
  emit('update:modelValue', next);
}

// ---- 分支操作 ----

function isDefaultBranch(branch: FlowBranch) {
  return !branch.condition || !branch.condition.field;
}

function enableCondition(branch: FlowBranch) {
  branch.condition = { field: 'amount', op: 'gt', value: 50000 };
  touch();
}

function disableCondition(branch: FlowBranch) {
  branch.condition = null;
  touch();
}

function addBranch(node: FlowChainNode) {
  const branches = [...(node.branches || [])];
  const n = branches.length + 1;
  branches.push({ id: `${node.id}_b${Date.now().toString(36)}${n}`, name: `分支${n}`, condition: null, nodes: [] });
  node.branches = branches;
  touch();
}

function removeBranch(node: FlowChainNode, index: number) {
  const branches = [...(node.branches || [])];
  branches.splice(index, 1);
  node.branches = branches;
  touch();
}

watch(
  () => props.modelValue,
  () => hydrateUserNames(),
  { immediate: true, deep: true }
);
</script>

<style scoped>
.chain-nodes {
  width: 100%;
}
.chain-nodes.nested {
  padding-left: 12px;
  border-left: 2px dashed var(--el-border-color-lighter);
}
.chain-node {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 8px;
  background: var(--el-fill-color-blank);
}
.chain-node-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px 6px 0 0;
  background: var(--el-fill-color-light);
}
.chain-node-head.approve {
  border-left: 3px solid var(--el-color-primary);
}
.chain-node-head.cc {
  border-left: 3px solid var(--el-color-warning);
}
.chain-node-head.branch {
  border-left: 3px solid var(--el-color-success);
}
.chain-node-name {
  font-weight: 600;
}
.chain-node-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 12px;
  flex-shrink: 0;
}
.chain-node-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.chain-node-actions {
  margin-left: auto;
}
.chain-node-body {
  padding: 10px 12px 4px;
}
.chain-hint {
  color: var(--el-color-warning);
  font-size: 12px;
  margin-bottom: 8px;
}
.chain-branches {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 4px;
}
.chain-branch {
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
}
.chain-branch-head {
  display: flex;
  align-items: center;
  font-size: 13px;
  margin-bottom: 6px;
}
.chain-branch-cond {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.cond-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.chain-add {
  display: flex;
  gap: 8px;
}
</style>
