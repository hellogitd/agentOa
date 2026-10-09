<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">用量与配额</span></el-col>
          <el-col :span="1.5">
            <el-select v-model="filter.modelKey" placeholder="模型" clearable class="w-160px" @change="loadAll">
              <el-option v-for="item in stat?.byModel ?? []" :key="item.modelKey" :label="item.modelKey" :value="item.modelKey" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              class="w-260px"
              @change="loadAll"
            />
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="loadAll">刷新</el-button>
          </el-col>
          <el-col v-hasPermi="['ai:usage:export']" :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
          </el-col>
        </el-row>
      </template>

      <el-row :gutter="12" class="mb-3">
        <el-col :xs="12" :sm="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-name">请求数</div>
            <div class="stat-value">{{ stat?.requests ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-name">输入 token</div>
            <div class="stat-value">{{ stat?.promptTokens ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-name">输出 token</div>
            <div class="stat-value">{{ stat?.completionTokens ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-name">合计 token</div>
            <div class="stat-value">{{ stat?.totalTokens ?? 0 }}</div>
          </el-card>
        </el-col>
      </el-row>

      <report-chart type="line" title="token 曲线（按日）" :labels="dayLabels" :series="daySeries" :height="280" />
    </el-card>

    <el-card shadow="hover" class="mt-2">
      <template #header>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="调用明细" name="logs" />
          <el-tab-pane label="配额管理" name="quotas" />
        </el-tabs>
      </template>

      <template v-if="activeTab === 'logs'">
        <el-table v-loading="logsLoading" :data="logs" border>
          <el-table-column label="时间" align="center" prop="createTime" width="170" />
          <el-table-column label="账号" align="center" prop="username" width="110" />
          <el-table-column label="模型" align="left" prop="modelKey" min-width="130" show-overflow-tooltip />
          <el-table-column label="业务" align="center" prop="bizType" width="80" />
          <el-table-column label="输入" align="center" prop="promptTokens" width="80" />
          <el-table-column label="输出" align="center" prop="completionTokens" width="80" />
          <el-table-column label="合计" align="center" prop="totalTokens" width="80" />
          <el-table-column label="耗时(ms)" align="center" prop="latencyMs" width="90" />
          <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
              <dict-tag :options="ai_usage_status" :value="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column label="错误码" align="center" prop="errorCode" width="150" show-overflow-tooltip />
        </el-table>
        <pagination
          v-show="logsTotal > 0"
          v-model:page="logQuery.pageNum"
          v-model:limit="logQuery.pageSize"
          :total="logsTotal"
          @pagination="loadLogs"
        />
      </template>

      <template v-else>
        <el-row :gutter="10" class="mb8">
          <el-col v-hasPermi="['ai:quota:edit']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAddQuota()">新建配额</el-button>
          </el-col>
        </el-row>
        <el-table v-loading="quotaLoading" :data="quotas" border>
          <el-table-column label="主体类型" align="center" width="100">
            <template #default="scope">
              <dict-tag :options="ai_quota_scope" :value="scope.row.scopeType" />
            </template>
          </el-table-column>
          <el-table-column label="主体" align="left" prop="scopeName" min-width="130">
            <template #default="scope">{{ scope.row.scopeName || scope.row.scopeId }}</template>
          </el-table-column>
          <el-table-column label="周期" align="center" width="90">
            <template #default="scope">
              <dict-tag :options="ai_quota_period" :value="scope.row.periodType" />
            </template>
          </el-table-column>
          <el-table-column label="token 限额" align="center" prop="tokenLimit" width="110" />
          <el-table-column label="次数限额" align="center" prop="requestLimit" width="100" />
          <el-table-column label="状态" align="center" width="80">
            <template #default="scope">
              <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button v-hasPermi="['ai:quota:edit']" link type="primary" icon="Edit" @click="handleEditQuota(scope.row)">修改</el-button>
              <el-button v-hasPermi="['ai:quota:edit']" link type="danger" icon="Delete" @click="handleDeleteQuota(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <el-dialog :title="quotaDialog.title" v-model="quotaDialog.visible" width="520px" append-to-body>
      <el-form ref="quotaFormRef" :model="quotaForm" :rules="quotaRules" label-width="110px">
        <el-form-item label="主体类型" prop="scopeType">
          <el-radio-group v-model="quotaForm.scopeType">
            <el-radio v-for="item in ai_quota_scope" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主体 ID" prop="scopeId">
          <el-input v-model="quotaForm.scopeId" placeholder="账号 ID 或角色 ID" maxlength="20" />
        </el-form-item>
        <el-form-item label="主体名称">
          <el-input v-model="quotaForm.scopeName" maxlength="128" />
        </el-form-item>
        <el-form-item label="周期" prop="periodType">
          <el-radio-group v-model="quotaForm.periodType">
            <el-radio v-for="item in ai_quota_period" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="token 限额">
          <el-input-number v-model="quotaForm.tokenLimit" :min="0" :step="10000" />
          <span class="text-gray-400 text-xs ml-2">0 表示不限</span>
        </el-form-item>
        <el-form-item label="次数限额">
          <el-input-number v-model="quotaForm.requestLimit" :min="0" :step="50" />
          <span class="text-gray-400 text-xs ml-2">0 表示不限</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="quotaForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitQuota">确 定</el-button>
          <el-button @click="quotaDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiUsage" lang="ts">
import ReportChart from '@/views/report/components/ReportChart.vue';
import { usageStats, usageLogs, listQuotas, addQuota, updateQuota, deleteQuota, QuotaForm, QuotaVO, UsageLogVO, UsageStatVO } from '@/api/ai';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_usage_status, ai_quota_scope, ai_quota_period } = toRefs<any>(proxy?.useDict('ai_usage_status', 'ai_quota_scope', 'ai_quota_period'));

const stat = ref<UsageStatVO | null>(null);
const filter = reactive({ modelKey: '' });
const dateRange = ref<string[]>([]);
const activeTab = ref('logs');

const logs = ref<UsageLogVO[]>([]);
const logsLoading = ref(false);
const logsTotal = ref(0);
const logQuery = reactive({ pageNum: 1, pageSize: 20 });

const quotas = ref<QuotaVO[]>([]);
const quotaLoading = ref(false);
const quotaDialog = reactive({ visible: false, title: '' });
const quotaForm = reactive<QuotaForm>({
  scopeType: 'user',
  scopeId: '',
  scopeName: '',
  periodType: 'day',
  tokenLimit: 0,
  requestLimit: 0,
  enabled: 1
});
const quotaRules = reactive({
  scopeType: [{ required: true, message: '主体类型不能为空', trigger: 'change' }],
  scopeId: [{ required: true, message: '主体 ID 不能为空', trigger: 'blur' }],
  periodType: [{ required: true, message: '周期不能为空', trigger: 'change' }]
});

const dayLabels = computed(() => (stat.value?.byDay ?? []).map((item) => item.day));
const daySeries = computed(() => [
  { name: '输入 token', values: (stat.value?.byDay ?? []).map((item) => item.promptTokens) },
  { name: '输出 token', values: (stat.value?.byDay ?? []).map((item) => item.completionTokens) }
]);

const params = () => ({
  modelKey: filter.modelKey || undefined,
  beginTime: dateRange.value?.[0],
  endTime: dateRange.value?.[1]
});

const loadStats = async () => {
  const res: any = await usageStats(params());
  stat.value = res.data;
};

const loadLogs = async () => {
  logsLoading.value = true;
  try {
    const res: any = await usageLogs({ ...params(), ...logQuery });
    logs.value = res.data.records;
    logsTotal.value = res.data.total;
  } finally {
    logsLoading.value = false;
  }
};

const loadQuotas = async () => {
  quotaLoading.value = true;
  try {
    const res: any = await listQuotas({ pageNum: 1, pageSize: 100 });
    quotas.value = res.data.records;
  } finally {
    quotaLoading.value = false;
  }
};

const loadAll = () => {
  loadStats();
  loadLogs();
  loadQuotas();
};

const handleExport = () => {
  proxy?.download('api/v1/ai/usage/export', { ...params() }, `ai-usage-${Date.now()}.xlsx`);
};

const handleAddQuota = () => {
  quotaForm.id = undefined;
  quotaForm.scopeType = 'user';
  quotaForm.scopeId = '';
  quotaForm.scopeName = '';
  quotaForm.periodType = 'day';
  quotaForm.tokenLimit = 0;
  quotaForm.requestLimit = 0;
  quotaForm.enabled = 1;
  quotaDialog.title = '新建配额';
  quotaDialog.visible = true;
};

const handleEditQuota = (row: QuotaVO) => {
  quotaForm.id = row.id;
  quotaForm.scopeType = row.scopeType;
  quotaForm.scopeId = row.scopeId;
  quotaForm.scopeName = row.scopeName ?? '';
  quotaForm.periodType = row.periodType;
  quotaForm.tokenLimit = row.tokenLimit ?? 0;
  quotaForm.requestLimit = row.requestLimit ?? 0;
  quotaForm.enabled = row.enabled;
  quotaDialog.title = '修改配额';
  quotaDialog.visible = true;
};

const submitQuota = async () => {
  const valid = await (proxy?.$refs['quotaFormRef'] as any)?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const payload: QuotaForm = {
    ...quotaForm,
    tokenLimit: quotaForm.tokenLimit ? Number(quotaForm.tokenLimit) : null,
    requestLimit: quotaForm.requestLimit ? Number(quotaForm.requestLimit) : null
  };
  if (quotaForm.id) {
    await updateQuota(quotaForm.id, payload);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addQuota(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  quotaDialog.visible = false;
  await loadQuotas();
};

const handleDeleteQuota = async (row: QuotaVO) => {
  await proxy?.$modal.confirm('确认删除该配额？删除后按全局兜底限额生效');
  await deleteQuota(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await loadQuotas();
};

onMounted(() => {
  loadAll();
});
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-name {
  color: #64748b;
  font-size: 13px;
}
.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
}
</style>
