<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">工具与 MCP</span></el-col>
          <el-col v-hasPermi="['ai:tool:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新建工具</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="工具码" align="left" prop="code" min-width="150" />
        <el-table-column label="名称" align="left" prop="name" min-width="130" />
        <el-table-column label="类型" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="ai_tool_type" :value="scope.row.type" />
          </template>
        </el-table-column>
        <el-table-column label="用途" align="left" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="写类" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.writeFlag === 1" type="warning" size="small">需确认</el-tag>
            <el-tag v-else type="info" size="small">只读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接" align="left" width="180" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.type === 'mcp'">{{ scope.row.endpoint }}</span>
            <el-tag v-else-if="scope.row.isBuiltin === 1" type="success" size="small">内置</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-switch
              v-hasPermi="['ai:tool:edit']"
              :model-value="scope.row.enabled === 1"
              :disabled="scope.row.isBuiltin === 1 && false"
              active-text="启用"
              inactive-text="停用"
              @change="(value: boolean) => handleStatus(scope.row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:tool:test']" link type="primary" icon="Connection" @click="handleTest(scope.row)">测试</el-button>
            <el-button
              v-if="scope.row.type === 'mcp'"
              v-hasPermi="['ai:tool:query']"
              link
              type="primary"
              icon="Search"
              @click="handleDiscover(scope.row)"
              >发现</el-button
            >
            <el-button v-hasPermi="['ai:tool:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button v-hasPermi="['ai:tool:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="660px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="工具码" prop="code">
          <el-input v-model="form.code" placeholder="如：knowledge_search" maxlength="64" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio v-for="item in ai_tool_type" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" placeholder="给模型看的用途说明" />
        </el-form-item>
        <el-form-item v-if="form.type === 'function'" label="参数 Schema">
          <el-input v-model="form.schemaJson" type="textarea" :rows="5" maxlength="4000" placeholder='{"type":"object","properties":{...}}' />
        </el-form-item>
        <el-form-item v-else label="MCP 配置">
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="5"
            maxlength="4000"
            placeholder='{"transport":"http","url":"https://.../mcp","headers":{"Authorization":"..."}}'
          />
          <div class="text-gray-400 text-xs">鉴权头只写不读，回显仅给连接地址与是否已配置鉴权头</div>
        </el-form-item>
        <el-form-item label="写类工具">
          <el-switch v-model="form.writeFlag" :active-value="1" :inactive-value="0" />
          <span class="text-gray-400 text-xs ml-2">写类需 ai:tool:write + 人工确认后执行</span>
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

    <el-dialog title="连通性测试" v-model="testVisible" width="520px" append-to-body>
      <el-alert v-if="testResult" :type="testResult.ok ? 'success' : 'error'" :closable="false">
        <template #title>
          {{ testResult.ok ? '测试通过' : '测试失败' }}
          <span v-if="testResult.latencyMs != null">（{{ testResult.latencyMs }} ms）</span>
          <span v-if="testResult.toolCount != null">，工具数 {{ testResult.toolCount }}</span>
        </template>
        <div>{{ testResult.message }}</div>
      </el-alert>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="testLoading" @click="runTest">测 试</el-button>
          <el-button @click="testVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="发现的 MCP 工具" v-model="discoverVisible" width="620px" append-to-body>
      <div class="mb-2">
        <el-button v-hasPermi="['ai:tool:add']" type="primary" plain :disabled="!discovered.length" :loading="importing" @click="handleImport"
          >纳入工具清单</el-button
        >
        <span class="text-gray-400 text-xs ml-2">已登记的工具码会自动跳过</span>
      </div>
      <el-table :data="discovered" border>
        <el-table-column label="名称" align="left" prop="name" min-width="140" />
        <el-table-column label="描述" align="left" prop="description" min-width="220" show-overflow-tooltip />
        <el-table-column label="参数 Schema" align="left" prop="parametersJsonSchema" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="AiTool" lang="ts">
import {
  listTools,
  addTool,
  updateTool,
  deleteTool,
  testTool,
  discoverTools,
  importDiscoveredTools,
  ToolForm,
  ToolTestVO,
  ToolVO,
  ToolDiscoveryVO
} from '@/api/ai';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_tool_type } = toRefs<any>(proxy?.useDict('ai_tool_type'));

const list = ref<ToolVO[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 20 });
const dialog = reactive({ visible: false, title: '' });
const formRef = ref();
const form = reactive<ToolForm>({
  code: '',
  name: '',
  type: 'function',
  description: '',
  schemaJson: '',
  configJson: '',
  writeFlag: 0,
  enabled: 1,
  remark: ''
});
const rules = reactive({
  code: [{ required: true, message: '工具码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '工具名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '工具类型不能为空', trigger: 'change' }]
});

const testVisible = ref(false);
const testLoading = ref(false);
const testTargetId = ref('');
const testResult = ref<ToolTestVO | null>(null);

const discoverVisible = ref(false);
const discoverTargetId = ref('');
const discovered = ref<ToolDiscoveryVO[]>([]);
const importing = ref(false);

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listTools(query);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  form.id = undefined;
  form.code = '';
  form.name = '';
  form.type = 'function';
  form.description = '';
  form.schemaJson = '';
  form.configJson = '';
  form.writeFlag = 0;
  form.enabled = 1;
  form.remark = '';
};

const handleAdd = () => {
  resetForm();
  dialog.title = '新建工具';
  dialog.visible = true;
};

const handleEdit = (row: ToolVO) => {
  resetForm();
  form.id = row.id;
  form.code = row.code;
  form.name = row.name;
  form.type = row.type;
  form.description = row.description ?? '';
  form.schemaJson = row.schemaJson ?? '';
  form.writeFlag = row.writeFlag;
  form.enabled = row.enabled;
  form.remark = row.remark ?? '';
  dialog.title = '修改工具';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const payload: ToolForm = { ...form };
  if (form.id) {
    await updateTool(form.id, payload);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addTool(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleStatus = async (row: ToolVO, enabled: boolean) => {
  await updateTool(row.id, { ...row, enabled: enabled ? 1 : 0 } as ToolForm);
  proxy?.$modal.msgSuccess(enabled ? '已启用' : '已停用');
  await getList();
};

const handleDelete = async (row: ToolVO) => {
  await proxy?.$modal.confirm(`确认删除工具「${row.name}」？内置工具不可删除`);
  await deleteTool(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleTest = (row: ToolVO) => {
  testTargetId.value = row.id;
  testResult.value = null;
  testVisible.value = true;
};

const runTest = async () => {
  testLoading.value = true;
  try {
    const res: any = await testTool(testTargetId.value);
    testResult.value = res.data;
  } finally {
    testLoading.value = false;
  }
};

const handleDiscover = async (row: ToolVO) => {
  discoverTargetId.value = row.id;
  const res: any = await discoverTools(row.id);
  discovered.value = res.data ?? [];
  discoverVisible.value = true;
};

/** M5-02：把发现的工具纳入工具清单（已存在的工具码跳过） */
const handleImport = async () => {
  importing.value = true;
  try {
    const res: any = await importDiscoveredTools(discoverTargetId.value);
    proxy?.$modal.msgSuccess(`已纳入 ${res.data?.length ?? 0} 个工具`);
    discoverVisible.value = false;
    await getList();
  } finally {
    importing.value = false;
  }
};

onMounted(() => {
  getList();
});
</script>
