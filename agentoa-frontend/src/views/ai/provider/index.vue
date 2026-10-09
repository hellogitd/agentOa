<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">模型渠道</span></el-col>
          <el-col v-hasPermi="['ai:provider:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新建渠道</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="渠道名称" align="left" prop="name" min-width="140" />
        <el-table-column label="厂商" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="ai_provider_type" :value="scope.row.providerType" />
          </template>
        </el-table-column>
        <el-table-column label="Base URL" align="left" prop="baseUrl" min-width="200" show-overflow-tooltip />
        <el-table-column label="API Key" align="center" width="130">
          <template #default="scope">
            <el-tag v-if="scope.row.hasApiKey" type="success">{{ scope.row.apiKeyHint || '已配置' }}</el-tag>
            <el-tag v-else type="info">未配置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="密钥引用" align="center" prop="secretRef" width="130" show-overflow-tooltip />
        <el-table-column label="优先级" align="center" prop="priority" width="80" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-switch
              v-hasPermi="['ai:provider:edit']"
              :model-value="scope.row.enabled === 1"
              active-text="启用"
              inactive-text="停用"
              @change="(value: boolean) => handleStatus(scope.row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:provider:test']" link type="primary" icon="Connection" @click="handleTest(scope.row)">测试</el-button>
            <el-button v-hasPermi="['ai:provider:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button v-hasPermi="['ai:provider:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="渠道名称" prop="name">
          <el-input v-model="form.name" placeholder="如：公司 DeepSeek" maxlength="128" />
        </el-form-item>
        <el-form-item label="厂商类型" prop="providerType">
          <el-select v-model="form.providerType" placeholder="请选择厂商类型" class="w-full">
            <el-option v-for="item in ai_provider_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com/v1" maxlength="255" />
          <div class="text-gray-400 text-xs">仅允许配置的地址出站，禁止外部输入控制出站 URL</div>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="form.id ? '留空则不修改密钥' : 'sk-...'" maxlength="512" />
          <div class="text-gray-400 text-xs">密钥 AES-256-GCM 加密存库，明文不回显、不落日志</div>
        </el-form-item>
        <el-form-item label="密钥引用">
          <el-input v-model="form.secretRef" placeholder="环境变量/configtree 名（优先于密钥）" maxlength="128" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="999" />
          <span class="text-gray-400 text-xs ml-2">同模型多渠道按小值优先调度</span>
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

    <el-dialog title="测试连接" v-model="testVisible" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="模型标识">
          <el-input v-model="testForm.modelKey" placeholder="留空取渠道下第一个启用模型" maxlength="128" />
        </el-form-item>
        <el-form-item label="测试提示词">
          <el-input v-model="testForm.prompt" placeholder="ping" maxlength="500" />
        </el-form-item>
      </el-form>
      <el-alert v-if="testResult" :type="testResult.ok ? 'success' : 'error'" :closable="false" class="mb-2">
        <template #title>
          {{ testResult.ok ? '连接正常' : `连接失败：${testResult.errorCategory ?? '未知'}` }}
          <span v-if="testResult.latencyMs != null">（{{ testResult.latencyMs }} ms）</span>
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
  </div>
</template>

<script setup name="AiProvider" lang="ts">
import {
  listProviders,
  addProvider,
  updateProvider,
  updateProviderStatus,
  deleteProvider,
  testProvider,
  ProviderForm,
  ProviderTestVO,
  ProviderVO
} from '@/api/ai';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_provider_type } = toRefs<any>(proxy?.useDict('ai_provider_type'));

const list = ref<ProviderVO[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 20 });
const dialog = reactive({ visible: false, title: '' });
const formRef = ref();
const form = reactive<ProviderForm>({
  name: '',
  providerType: 'openai',
  baseUrl: '',
  apiKey: '',
  secretRef: '',
  priority: 100,
  enabled: 1,
  remark: ''
});
const rules = reactive({
  name: [{ required: true, message: '渠道名称不能为空', trigger: 'blur' }],
  providerType: [{ required: true, message: '厂商类型不能为空', trigger: 'change' }],
  baseUrl: [{ required: true, message: 'Base URL 不能为空', trigger: 'blur' }]
});

const testVisible = ref(false);
const testLoading = ref(false);
const testTargetId = ref('');
const testForm = reactive({ modelKey: '', prompt: '' });
const testResult = ref<ProviderTestVO | null>(null);

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listProviders(query);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  form.id = undefined;
  form.name = '';
  form.providerType = 'openai';
  form.baseUrl = '';
  form.apiKey = '';
  form.secretRef = '';
  form.priority = 100;
  form.enabled = 1;
  form.remark = '';
};

const handleAdd = () => {
  resetForm();
  dialog.title = '新建模型渠道';
  dialog.visible = true;
};

const handleEdit = (row: ProviderVO) => {
  resetForm();
  form.id = row.id;
  form.name = row.name;
  form.providerType = row.providerType;
  form.baseUrl = row.baseUrl;
  form.secretRef = row.secretRef ?? '';
  form.priority = row.priority;
  form.enabled = row.enabled;
  form.remark = row.remark ?? '';
  dialog.title = '修改模型渠道';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const payload: ProviderForm = { ...form };
  if (!payload.apiKey) {
    delete payload.apiKey;
  }
  if (form.id) {
    await updateProvider(form.id, payload);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addProvider(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleStatus = async (row: ProviderVO, enabled: boolean) => {
  await updateProviderStatus(row.id, enabled ? 1 : 0);
  proxy?.$modal.msgSuccess(enabled ? '已启用' : '已停用');
  await getList();
};

const handleDelete = async (row: ProviderVO) => {
  await proxy?.$modal.confirm(`确认删除渠道「${row.name}」？渠道下有模型时将无法删除`);
  await deleteProvider(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleTest = (row: ProviderVO) => {
  testTargetId.value = row.id;
  testForm.modelKey = '';
  testForm.prompt = '';
  testResult.value = null;
  testVisible.value = true;
};

const runTest = async () => {
  testLoading.value = true;
  try {
    const res: any = await testProvider(testTargetId.value, { modelKey: testForm.modelKey, prompt: testForm.prompt });
    testResult.value = res.data;
  } finally {
    testLoading.value = false;
  }
};

onMounted(() => {
  getList();
});
</script>
