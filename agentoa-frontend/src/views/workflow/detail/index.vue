<template>
  <div class="app-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>{{ detail.title || '流程详情' }}</span>
          <div class="card-header-actions">
            <el-tag :type="statusType">{{ statusLabel }}</el-tag>
            <el-button v-hasPermi="['ai:copilot:use']" type="primary" plain icon="MagicStick" @click="openAiSummary">AI 摘要</el-button>
          </div>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="流程模板">{{ detail.processName }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ detail.businessType }}</el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ detail.businessKey }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detail.initiatorName }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ formatTime(detail.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(detail.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="定义版本">v{{ detail.definitionVersionNo }}</el-descriptions-item>
        <el-descriptions-item label="表单版本">v{{ detail.formVersionNo }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ detail.currentTaskName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">表单内容</el-divider>
      <FormSchema :schema="formSchema" :model-value="formData" readonly />

      <el-divider content-position="left">当前任务</el-divider>
      <el-table v-if="detail.currentTasks?.length" :data="detail.currentTasks" size="small" border>
        <el-table-column prop="taskName" label="节点" />
        <el-table-column label="办理人">
          <template #default="{ row }">{{ row.assigneeName || '待认领' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="无进行中的任务" :image-size="60" />

      <el-divider content-position="left">审批历史</el-divider>
      <el-timeline v-if="detail.actions?.length">
        <el-timeline-item
          v-for="(action, index) in detail.actions"
          :key="index"
          :timestamp="formatTime(action.actionTime)"
          :type="action.action === 'reject' ? 'danger' : action.action === 'transfer' ? 'warning' : 'primary'"
        >
          <div>
            <b>{{ action.taskName }}</b>
            <el-tag size="small" style="margin-left: 8px">{{ actionLabel(action.action) }}</el-tag>
            <span style="margin-left: 8px">{{ action.operatorName }}</span>
            <span v-if="action.action === 'transfer'" style="margin-left: 8px">
              转办：{{ action.oldAssigneeName }} → {{ action.newAssigneeName }}
            </span>
          </div>
          <div v-if="action.comment" class="action-comment">{{ action.comment }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审批记录" :image-size="60" />

      <el-divider content-position="left">流程图</el-divider>
      <el-image v-if="diagramUrl" :src="diagramUrl" alt="流程图" fit="contain" style="max-height: 420px" />
      <el-empty v-else description="流程图加载失败" :image-size="60" />
    </el-card>

    <!-- M4-01 审批摘要：只读建议，人工确认后可复制（docs/21 §6.1） -->
    <AiCopilotDrawer ref="aiDrawer" scene="approve-summary" title="AI 摘要" @apply="copyAiOutput" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { getInstance, getInstanceDiagram, type FormSchema, type InstanceDetailVO } from '@/api/workflow';
import FormSchemaView from '../components/FormSchema.vue';
import AiCopilotDrawer from '@/components/AiCopilotDrawer/index.vue';

const route = useRoute();
const loading = ref(false);
const detail = ref<InstanceDetailVO>({} as InstanceDetailVO);
const diagramUrl = ref('');
const aiDrawer = ref<InstanceType<typeof AiCopilotDrawer>>();

const STATUS_LABELS: Record<number, string> = {
  1: '审批中',
  2: '已通过',
  3: '已拒绝',
  4: '已撤销',
  5: '已挂起',
  6: '已终止'
};

const statusLabel = computed(() => STATUS_LABELS[detail.value.status] || String(detail.value.status ?? ''));
const statusType = computed(() => (detail.value.status === 2 ? 'success' : detail.value.status === 1 ? 'primary' : 'info'));

const formSchema = computed<FormSchema | null>(() => {
  if (!detail.value.formSchema) return null;
  try {
    return JSON.parse(detail.value.formSchema);
  } catch {
    return null;
  }
});

const formData = computed<Record<string, any>>(() => {
  if (!detail.value.formData) return {};
  try {
    return JSON.parse(detail.value.formData);
  } catch {
    return {};
  }
});

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function actionLabel(action: string) {
  return { agree: '同意', reject: '拒绝', transfer: '转办', cancel: '撤销' }[action] || action;
}

async function load() {
  const instanceId = route.query.id as string;
  if (!instanceId) return;
  loading.value = true;
  try {
    const res: any = await getInstance(instanceId);
    detail.value = res.data;
    const diagram: any = await getInstanceDiagram(instanceId);
    diagramUrl.value = URL.createObjectURL(diagram as Blob);
  } finally {
    loading.value = false;
  }
}

/** M4-01：只读摘要（表单关键字段 + 历史意见 + 耗时），AI 不改业务数据 */
function openAiSummary() {
  aiDrawer.value?.open({ bizId: route.query.id as string });
}

function copyAiOutput(text: string) {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('摘要已复制，可粘贴到审批意见'),
    () => ElMessage.warning('复制失败，请手动选择文本')
  );
}

onMounted(load);
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.action-comment {
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
