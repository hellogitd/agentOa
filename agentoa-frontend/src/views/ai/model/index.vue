<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">模型管理</span></el-col>
          <el-col v-hasPermi="['ai:model:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新建模型</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-input v-model="query.modelKey" placeholder="模型标识" clearable prefix-icon="Search" class="w-180px" @keyup.enter="getList" />
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="模型标识" align="left" prop="modelKey" min-width="160" />
        <el-table-column label="别名" align="left" prop="alias" min-width="110" show-overflow-tooltip />
        <el-table-column label="所属渠道" align="left" prop="providerName" min-width="120" show-overflow-tooltip />
        <el-table-column label="能力" align="center" width="170">
          <template #default="scope">
            <el-tag v-for="cap in scope.row.capability" :key="cap" size="small" class="mr-1">{{ capabilityLabel(cap) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上下文" align="center" prop="contextWindow" width="90" />
        <el-table-column label="最大输出" align="center" prop="maxTokens" width="90" />
        <el-table-column label="默认模型" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.isDefault === 1" type="success">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:model:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button v-hasPermi="['ai:model:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="所属渠道" prop="providerId">
          <el-select v-model="form.providerId" placeholder="请选择渠道" class="w-full">
            <el-option v-for="item in providers" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型标识" prop="modelKey">
          <el-input v-model="form.modelKey" placeholder="如：deepseek-chat" maxlength="128" />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="form.alias" maxlength="128" />
        </el-form-item>
        <el-form-item label="能力" prop="capability">
          <el-checkbox-group v-model="form.capability">
            <el-checkbox v-for="item in ai_model_capability" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
          <div class="text-gray-400 text-xs">含「视觉」才允许图片输入，否则返回 MODEL_CAPABILITY_MISMATCH</div>
        </el-form-item>
        <el-form-item label="上下文窗口" prop="contextWindow">
          <el-input-number v-model="form.contextWindow" :min="512" :max="1000000" :step="1024" />
        </el-form-item>
        <el-form-item label="默认温度">
          <el-input-number v-model="form.defaultTemperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="最大输出">
          <el-input-number v-model="form.maxTokens" :min="16" :max="32768" :step="256" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="全局默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <span class="text-gray-400 text-xs ml-2">未指定模型时使用（唯一）</span>
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
  </div>
</template>

<script setup name="AiModel" lang="ts">
import { listModels, addModel, updateModel, deleteModel, listProviders, ModelForm, ModelVO, ProviderVO } from '@/api/ai';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_model_capability } = toRefs<any>(proxy?.useDict('ai_model_capability'));

const list = ref<ModelVO[]>([]);
const providers = ref<ProviderVO[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ modelKey: '', pageNum: 1, pageSize: 20 });
const dialog = reactive({ visible: false, title: '' });
const form = reactive<ModelForm>({
  providerId: '',
  modelKey: '',
  alias: '',
  capability: ['chat'],
  contextWindow: 8192,
  defaultTemperature: 0.7,
  maxTokens: 2048,
  enabled: 1,
  isDefault: 0,
  remark: ''
});
const rules = reactive({
  providerId: [{ required: true, message: '所属渠道不能为空', trigger: 'change' }],
  modelKey: [{ required: true, message: '模型标识不能为空', trigger: 'blur' }],
  capability: [{ required: true, message: '至少选择一项能力', trigger: 'change' }],
  contextWindow: [{ required: true, message: '上下文窗口不能为空', trigger: 'blur' }]
});

const capabilityLabel = (code: string) => {
  const hit = (ai_model_capability.value ?? []).find((item: any) => item.value === code);
  return hit?.label ?? code;
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listModels(query);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const loadProviders = async () => {
  const res: any = await listProviders({ pageNum: 1, pageSize: 100 });
  providers.value = res.data.records;
};

const resetForm = () => {
  form.id = undefined;
  form.providerId = '';
  form.modelKey = '';
  form.alias = '';
  form.capability = ['chat'];
  form.contextWindow = 8192;
  form.defaultTemperature = 0.7;
  form.maxTokens = 2048;
  form.enabled = 1;
  form.isDefault = 0;
  form.remark = '';
};

const handleAdd = async () => {
  resetForm();
  await loadProviders();
  dialog.title = '新建模型';
  dialog.visible = true;
};

const handleEdit = async (row: ModelVO) => {
  resetForm();
  form.id = row.id;
  form.providerId = row.providerId;
  form.modelKey = row.modelKey;
  form.alias = row.alias ?? '';
  form.capability = [...row.capability];
  form.contextWindow = row.contextWindow;
  form.defaultTemperature = Number(row.defaultTemperature ?? 0.7);
  form.maxTokens = row.maxTokens ?? 2048;
  form.enabled = row.enabled;
  form.isDefault = row.isDefault;
  form.remark = row.remark ?? '';
  await loadProviders();
  dialog.title = '修改模型';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.id) {
    await updateModel(form.id, { ...form });
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addModel({ ...form });
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: ModelVO) => {
  await proxy?.$modal.confirm(`确认删除模型「${row.modelKey}」？`);
  await deleteModel(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
