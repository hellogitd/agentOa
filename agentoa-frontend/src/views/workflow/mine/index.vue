<template>
  <div class="app-container">
    <el-card>
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">发起申请</el-button>
      </div>
      <el-table v-loading="loading" :data="instances" border>
        <el-table-column prop="title" label="流程标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="toDetail(row.id)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="processName" label="流程模板" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentTaskName" label="当前节点" width="120" />
        <el-table-column prop="startTime" label="发起时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="danger" @click="doRevoke(row)">撤销</el-button>
            <el-button link type="info" @click="toDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="pageNum" v-model:limit="pageSize" :total="total" @pagination="load" />
    </el-card>

    <el-dialog v-model="createVisible" title="发起申请" width="640px">
      <el-form label-width="100px">
        <el-form-item label="申请模板" required>
          <el-select v-model="selectedType" style="width: 100%" @change="onTemplateChange">
            <el-option v-for="d in definitions" :key="d.processKey" :label="d.processName" :value="d.businessType" />
          </el-select>
        </el-form-item>
      </el-form>
      <FormSchemaView v-if="formSchema" :schema="formSchema" v-model="formData" />
      <el-empty v-else description="请选择申请模板" :image-size="60" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  genericApi,
  getForm,
  listDefinitions,
  listInstances,
  requestApiFor,
  revokeInstance,
  type DefinitionVO,
  type FormSchema,
  type InstanceVO
} from '@/api/workflow';
import FormSchemaView from '../components/FormSchema.vue';

const router = useRouter();
const loading = ref(false);
const instances = ref<InstanceVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

const createVisible = ref(false);
const submitting = ref(false);
const definitions = ref<DefinitionVO[]>([]);
const selectedType = ref('');
const formSchema = ref<FormSchema | null>(null);
const formData = ref<Record<string, any>>({});

const STATUS_LABELS: Record<number, string> = {
  1: '审批中',
  2: '已通过',
  3: '已拒绝',
  4: '已撤销',
  5: '已挂起',
  6: '已终止'
};

const statusLabel = (status: number) => STATUS_LABELS[status] || String(status);
const statusType = (status: number) => (status === 2 ? 'success' : status === 1 ? 'primary' : 'info');

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function toDetail(instanceId: string) {
  router.push({ path: '/workflow/detail', query: { id: instanceId } });
}

async function openCreate() {
  if (!definitions.value.length) {
    const res: any = await listDefinitions();
    definitions.value = res.data;
  }
  selectedType.value = '';
  formSchema.value = null;
  formData.value = {};
  createVisible.value = true;
}

async function onTemplateChange(type: string) {
  const definition = definitions.value.find((d) => d.businessType === type);
  if (!definition) return;
  const res: any = await getForm(definition.formKey);
  formSchema.value = JSON.parse(res.data.schema);
  formData.value = {};
}

function requiredMissing(): boolean {
  for (const field of formSchema.value?.fields || []) {
    if (!field.required || field.readonly) continue;
    const v = formData.value[field.key];
    if (v === undefined || v === null || v === '' || (Array.isArray(v) && v.length === 0)) {
      ElMessage.warning(`请填写${field.label}`);
      return true;
    }
  }
  return false;
}

function buildPayload(definition: DefinitionVO, generic: boolean): any {
  const payload: Record<string, any> = { ...formData.value };
  if (selectedType.value === 'reimburse') {
    const details = Array.isArray(formData.value.details) ? formData.value.details : [];
    payload.details = details;
    const total = details.reduce((sum: number, d: any) => sum + Number(d.amount || 0), 0);
    payload.totalAmount = total.toFixed(2);
    delete payload.durationMinutes;
  } else if (selectedType.value === 'regularize' || selectedType.value === 'offboard') {
    const detail: Record<string, any> = { ...formData.value };
    delete detail.effectiveDate;
    return {
      requestType: selectedType.value === 'regularize' ? 'REGULARIZE' : 'OFFBOARD',
      effectiveDate: formData.value.effectiveDate,
      formData: JSON.stringify(detail)
    };
  }
  delete payload.durationMinutes;
  if (!generic) {
    return payload;
  }
  // 通用申请（GenericRequestBo 契约）：definitionId 必填，表单字段整体收敛进 formData
  const title = typeof payload.title === 'string' && payload.title.trim() ? payload.title.trim() : definition.processName;
  return {
    definitionId: definition.id,
    title,
    formData: JSON.stringify(payload)
  };
}

async function submitCreate() {
  if (!formSchema.value || requiredMissing()) return;
  const definition = definitions.value.find((d) => d.businessType === selectedType.value);
  if (!definition) {
    ElMessage.error('未找到所选申请模板，请重新选择');
    return;
  }
  submitting.value = true;
  try {
    const api = requestApiFor(selectedType.value);
    const draft: any = await api.create(buildPayload(definition, api === genericApi));
    await api.submit(String(draft.data.id), draft.data.lockVersion);
    ElMessage.success('申请已提交');
    createVisible.value = false;
    await load();
  } finally {
    submitting.value = false;
  }
}

async function doRevoke(row: InstanceVO) {
  await ElMessageBox.confirm(`确认撤销「${row.title}」？`, '撤销流程', { type: 'warning' });
  await revokeInstance(row.id, row.lockVersion);
  ElMessage.success('已撤销');
  await load();
}

async function load() {
  loading.value = true;
  try {
    const res: any = await listInstances({ scope: 'mine', pageNum: pageNum.value, pageSize: pageSize.value });
    instances.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
