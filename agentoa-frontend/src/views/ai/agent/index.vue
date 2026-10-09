<template>
  <div class="p-2 ai-agent">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">Agent 管理</span></el-col>
          <el-col v-hasPermi="['ai:agent:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新建 Agent</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
          <el-col :span="6">
            <el-tag type="warning">AI 生成内容仅供参考</el-tag>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="编码" align="left" prop="code" min-width="140" />
        <el-table-column label="名称" align="left" prop="name" min-width="130" />
        <el-table-column label="工具" align="left" min-width="200">
          <template #default="scope">
            <el-tag v-for="code in scope.row.toolCodes" :key="code" size="small" class="mr-1">{{ code }}</el-tag>
            <span v-if="!scope.row.toolCodes || !scope.row.toolCodes.length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="步数/超时" align="center" width="110">
          <template #default="scope">{{ scope.row.maxSteps }} 步 / {{ scope.row.timeoutSec }}s</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="270" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:agent:run']" link type="primary" icon="VideoPlay" @click="openRun(scope.row)">运行</el-button>
            <el-button v-hasPermi="['ai:agent:query']" link type="primary" icon="Tickets" @click="openRuns(scope.row)">记录</el-button>
            <el-button v-hasPermi="['ai:agent:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button v-hasPermi="['ai:agent:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="660px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如：office_assistant" maxlength="64" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="5" maxlength="8000" />
          <div class="text-gray-400 text-xs">AI 生成内容仅供参考，提示词请写明能力边界</div>
        </el-form-item>
        <el-form-item label="默认模型">
          <el-select v-model="form.modelId" clearable placeholder="全局默认模型" class="w-full">
            <el-option v-for="m in modelOptions" :key="m.id" :label="m.alias || m.modelKey" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用工具">
          <el-select v-model="form.toolCodes" multiple filterable placeholder="选择工具" class="w-full">
            <el-option v-for="t in toolOptions" :key="t.code" :label="`${t.name}（${t.code}）`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大步数">
          <el-input-number v-model="form.maxSteps" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="超时（秒）">
          <el-input-number v-model="form.timeoutSec" :min="5" :max="600" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 运行抽屉 -->
    <el-drawer v-model="runVisible" :title="`运行 ${runAgent?.name ?? ''}`" size="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="输入">
          <el-input v-model="runForm.input" type="textarea" :rows="3" maxlength="8000" placeholder="给 Agent 的任务描述" />
        </el-form-item>
        <!-- M5-05：写类工具必须人工确认后才允许执行（docs/21 §7.1） -->
        <el-form-item v-if="writeToolCodes.length" label="写类工具">
          <el-checkbox-group v-model="runForm.approvedWriteTools">
            <el-checkbox v-for="code in writeToolCodes" :key="code" :value="code">
              {{ toolNameOf(code) }}
              <el-tag type="warning" size="small">写类</el-tag>
            </el-checkbox>
          </el-checkbox-group>
          <div class="text-gray-400 text-xs">
            未勾选的写类工具不会执行，轨迹中记为跳过；勾选即代表你确认本次允许其产生副作用（需 ai:tool:write 权限）
          </div>
        </el-form-item>
      </el-form>
      <div class="mb-2">
        <el-button type="primary" :loading="streaming" @click="startRun">运 行</el-button>
        <el-button v-if="streaming" type="danger" @click="stopRun">停止</el-button>
      </div>
      <el-alert type="warning" :closable="false" class="mb-2" title="AI 生成内容仅供参考；写类工具需人工确认后执行" />

      <div class="trace-title">执行轨迹</div>
      <el-timeline v-if="runSteps.length" class="mb-2">
        <el-timeline-item
          v-for="(step, index) in runSteps"
          :key="index"
          :type="step.type === 'tool_call' ? 'warning' : step.type === 'final' ? 'success' : 'primary'"
          :timestamp="`第 ${step.step} 步 · ${step.type}`"
        >
          <div v-if="step.tool" class="trace-tool">
            {{ step.tool }}
            <el-tag v-if="step.writeTool" type="warning" size="small">写类</el-tag>
          </div>
          <div v-if="step.arguments" class="trace-line">参数：{{ step.arguments }}</div>
          <div v-if="step.result" class="trace-line">结果：{{ step.result }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无轨迹" :image-size="60" />

      <div class="trace-title">输出</div>
      <el-input v-model="runOutput" type="textarea" :rows="8" readonly />
    </el-drawer>

    <!-- 运行记录 -->
    <el-dialog title="运行记录" v-model="runsVisible" width="860px" append-to-body>
      <el-table v-loading="runsLoading" :data="runs" border>
        <el-table-column label="Agent" align="left" prop="agentName" min-width="110" />
        <el-table-column label="输入" align="left" prop="input" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)" size="small">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="tokens" align="center" prop="totalTokens" width="90" />
        <el-table-column label="耗时" align="center" prop="durationMs" width="90" />
        <el-table-column label="时间" align="center" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" width="110">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="viewRun(scope.row)">轨迹</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="runsTotal > 0"
        v-model:page="runsQuery.pageNum"
        v-model:limit="runsQuery.pageSize"
        :total="runsTotal"
        @pagination="loadRuns"
      />
    </el-dialog>

    <el-dialog title="执行轨迹回放" v-model="traceVisible" width="720px" append-to-body>
      <el-alert type="warning" :closable="false" class="mb-2" title="AI 生成内容仅供参考" />
      <el-timeline v-if="detailSteps.length">
        <el-timeline-item
          v-for="(step, index) in detailSteps"
          :key="index"
          :type="step.type === 'tool_call' ? 'warning' : step.type === 'final' ? 'success' : 'primary'"
          :timestamp="`第 ${step.step} 步 · ${step.type}`"
        >
          <div v-if="step.tool" class="trace-tool">
            {{ step.tool }}
            <el-tag v-if="step.writeTool" type="warning" size="small">写类</el-tag>
          </div>
          <div v-if="step.arguments" class="trace-line">参数：{{ step.arguments }}</div>
          <div v-if="step.result" class="trace-line">结果：{{ step.result }}</div>
        </el-timeline-item>
      </el-timeline>
      <div class="trace-title">输出</div>
      <el-input :model-value="detailOutput" type="textarea" :rows="8" readonly />
    </el-dialog>
  </div>
</template>

<script setup name="AiAgent" lang="ts">
import {
  listAgents,
  addAgent,
  updateAgent,
  deleteAgent,
  listAgentRuns,
  getAgentRun,
  listTools,
  listModels,
  AgentForm,
  AgentVO,
  AgentRunVO,
  AgentTraceStepVO
} from '@/api/ai';
import { streamAgentRun } from '@/api/ai/stream';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const list = ref<AgentVO[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 20 });
const dialog = reactive({ visible: false, title: '' });
const formRef = ref();
const form = reactive<AgentForm>({
  code: '',
  name: '',
  systemPrompt: '',
  modelId: undefined,
  toolCodes: [],
  maxSteps: 6,
  timeoutSec: 60,
  enabled: 1,
  remark: ''
});
const rules = reactive({
  code: [{ required: true, message: 'Agent 码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '系统提示词不能为空', trigger: 'blur' }]
});

const modelOptions = ref<any[]>([]);
const toolOptions = ref<any[]>([]);

const runVisible = ref(false);
const streaming = ref(false);
const runAgent = ref<AgentVO | null>(null);
const runForm = reactive({ input: '', approvedWriteTools: [] as string[] });
const runOutput = ref('');
const runSteps = ref<AgentTraceStepVO[]>([]);
let activeStream: AbortController | null = null;

/** 当前 Agent 的写类工具（M5-05：需人工确认后才允许执行） */
const writeToolCodes = computed<string[]>(() => {
  const codes = runAgent.value?.toolCodes ?? [];
  return codes.filter((code) => toolOptions.value.some((tool) => tool.code === code && tool.writeFlag === 1));
});

const toolNameOf = (code: string) => toolOptions.value.find((tool) => tool.code === code)?.name ?? code;

const runsVisible = ref(false);
const runsLoading = ref(false);
const runs = ref<AgentRunVO[]>([]);
const runsTotal = ref(0);
const runsQuery = reactive({ pageNum: 1, pageSize: 10, agentId: '' });

const traceVisible = ref(false);
const detailSteps = ref<AgentTraceStepVO[]>([]);
const detailOutput = ref('');

const statusType = (status: string) => {
  if (status === 'done') return 'success';
  if (status === 'failed') return 'danger';
  if (status === 'stopped') return 'info';
  return 'warning';
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listAgents(query);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const loadOptions = async () => {
  const [models, tools] = await Promise.all([listModels({ pageNum: 1, pageSize: 100 }), listTools({ pageNum: 1, pageSize: 100 })]);
  modelOptions.value = (models as any).data.records ?? [];
  toolOptions.value = (tools as any).data.records ?? [];
};

const resetForm = () => {
  form.id = undefined;
  form.code = '';
  form.name = '';
  form.systemPrompt = '';
  form.modelId = undefined;
  form.toolCodes = [];
  form.maxSteps = 6;
  form.timeoutSec = 60;
  form.enabled = 1;
  form.remark = '';
};

const handleAdd = () => {
  resetForm();
  dialog.title = '新建 Agent';
  dialog.visible = true;
};

const handleEdit = (row: AgentVO) => {
  resetForm();
  form.id = row.id;
  form.code = row.code;
  form.name = row.name;
  form.systemPrompt = row.systemPrompt;
  form.modelId = row.modelId;
  form.toolCodes = [...(row.toolCodes ?? [])];
  form.maxSteps = row.maxSteps;
  form.timeoutSec = row.timeoutSec;
  form.enabled = row.enabled;
  form.remark = row.remark ?? '';
  dialog.title = '修改 Agent';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const payload: AgentForm = { ...form };
  if (form.id) {
    await updateAgent(form.id, payload);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addAgent(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: AgentVO) => {
  await proxy?.$modal.confirm(`确认删除 Agent「${row.name}」？内置 Agent 不可删除`);
  await deleteAgent(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const openRun = (row: AgentVO) => {
  runAgent.value = row;
  runForm.input = '';
  runForm.approvedWriteTools = [];
  runOutput.value = '';
  runSteps.value = [];
  runVisible.value = true;
};

const startRun = () => {
  if (!runAgent.value || !runForm.input.trim()) {
    proxy?.$modal.msgError('请输入任务描述');
    return;
  }
  streaming.value = true;
  runOutput.value = '';
  runSteps.value = [];
  activeStream = streamAgentRun(
    runAgent.value.id,
    { input: runForm.input, approvedWriteTools: runForm.approvedWriteTools },
    {
      onStep: (step) => {
        runSteps.value.push(step as AgentTraceStepVO);
      },
      onDelta: (text) => {
        runOutput.value += text;
      },
      onDone: () => {
        streaming.value = false;
      },
      onError: (error) => {
        streaming.value = false;
        proxy?.$modal.msgError(error.msg || 'Agent 运行失败');
      }
    }
  );
};

const stopRun = () => {
  activeStream?.abort();
  activeStream = null;
  streaming.value = false;
};

const openRuns = (row: AgentVO) => {
  runsQuery.agentId = row.id;
  runsQuery.pageNum = 1;
  runsVisible.value = true;
  loadRuns();
};

const loadRuns = async () => {
  runsLoading.value = true;
  try {
    const res: any = await listAgentRuns(runsQuery);
    runs.value = res.data.records;
    runsTotal.value = res.data.total;
  } finally {
    runsLoading.value = false;
  }
};

const viewRun = async (row: AgentRunVO) => {
  const res: any = await getAgentRun(row.id);
  detailSteps.value = res.data.trace ?? [];
  detailOutput.value = res.data.output ?? '';
  traceVisible.value = true;
};

onMounted(() => {
  getList();
  loadOptions();
});

onBeforeUnmount(() => activeStream?.abort());
</script>

<style scoped lang="scss">
.ai-agent {
  .trace-title {
    margin: 12px 0 8px;
    font-weight: 600;
  }
  .trace-tool {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
  }
  .trace-line {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    word-break: break-all;
  }
}
</style>
