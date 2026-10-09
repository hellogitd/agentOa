<template>
  <div class="app-container">
    <el-card>
      <el-table v-loading="loading" :data="tasks" border>
        <el-table-column prop="title" label="流程标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link
              type="primary"
              role="link"
              tabindex="0"
              :aria-label="`查看流程详情：${row.title}`"
              @click="toDetail(row.instanceId)"
              @keydown.enter.prevent="toDetail(row.instanceId)"
              >{{ row.title }}</el-link
            >
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="当前节点" width="120" />
        <el-table-column prop="businessType" label="业务类型" width="110" />
        <el-table-column prop="initiatorName" label="发起人" width="100" />
        <el-table-column prop="createTime" label="到达时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openHandle(row)">处理</el-button>
            <el-button link type="warning" @click="handleUrge(row)">催办</el-button>
            <el-button link type="info" @click="toDetail(row.instanceId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="pageNum" v-model:limit="pageSize" :total="total" @pagination="load" />
    </el-card>

    <el-dialog v-model="handleVisible" :title="`处理任务 - ${current?.taskName || ''}`" width="560px">
      <el-form label-width="90px">
        <el-form-item label="办理方式">
          <el-radio-group v-model="handleAction">
            <el-radio value="agree">同意</el-radio>
            <el-radio value="reject">拒绝</el-radio>
            <el-radio value="transfer">转办</el-radio>
            <el-radio value="return">退回</el-radio>
            <el-radio value="addsign">加签</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="handleAction === 'transfer'" label="转办给">
          <el-input
            :model-value="transferUser?.name || ''"
            readonly
            placeholder="点击选择目标办理人"
            style="width: 260px"
            aria-label="目标办理人，按回车选择"
            @click="openUserPicker('transfer')"
            @keydown.enter.prevent="openUserPicker('transfer')"
            @keydown.space.prevent="openUserPicker('transfer')"
          />
          <el-button link style="margin-left: 6px" @click="openUserPicker('transfer')">选择</el-button>
        </el-form-item>
        <template v-if="handleAction === 'return'">
          <el-form-item label="退回目标">
            <el-radio-group v-model="returnToInitiator">
              <el-radio :value="true">发起节点</el-radio>
              <el-radio :value="false">历史节点</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="!returnToInitiator" label="目标节点">
            <el-input v-model="returnTargetKey" placeholder="历史节点 taskDefinitionKey，如 leaderApprove" />
          </el-form-item>
        </template>
        <template v-if="handleAction === 'addsign'">
          <el-form-item label="加签位置">
            <el-radio-group v-model="addsignPosition">
              <el-radio value="before">前加签（共同办理）</el-radio>
              <el-radio value="after">后加签（顺序续办）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="加签人">
            <el-input
              :model-value="addsignUsers.map((u) => u.name).join('、')"
              readonly
              placeholder="点击选择加签人（可多选）"
              style="width: 260px"
              aria-label="加签人，按回车选择"
              @click="openUserPicker('addsign')"
              @keydown.enter.prevent="openUserPicker('addsign')"
              @keydown.space.prevent="openUserPicker('addsign')"
            />
            <el-button link style="margin-left: 6px" @click="openUserPicker('addsign')">选择</el-button>
          </el-form-item>
        </template>
        <el-form-item :label="handleAction === 'transfer' ? '转办原因' : '审批意见'">
          <el-input v-model="comment" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>

    <UserSelect ref="userSelectRef" :multiple="pickerMode === 'addsign'" :data="pickerData" @confirm-callback="onUsersPicked" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { completeTask, listTodo, transferTask, returnTask, addsignTask, urgeTask, type TaskVO } from '@/api/workflow';

const router = useRouter();
const loading = ref(false);
const tasks = ref<TaskVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

const handleVisible = ref(false);
const submitting = ref(false);
const current = ref<TaskVO | null>(null);
const handleAction = ref<'agree' | 'reject' | 'transfer' | 'return' | 'addsign'>('agree');
const comment = ref('');
const transferUser = ref<{ userId: string; name: string } | null>(null);
const returnToInitiator = ref(true);
const returnTargetKey = ref('');
const addsignPosition = ref<'before' | 'after'>('before');
const addsignUsers = ref<{ userId: string; name: string }[]>([]);

// ---- 选人（复用 UserSelect，替代手输账号 ID） ----
const userSelectRef = ref();
const pickerMode = ref<'transfer' | 'addsign'>('transfer');
const pickerData = ref<string | string[]>([]);

function openUserPicker(mode: 'transfer' | 'addsign') {
  pickerMode.value = mode;
  pickerData.value = mode === 'transfer' ? (transferUser.value ? [transferUser.value.userId] : []) : addsignUsers.value.map((u) => u.userId);
  userSelectRef.value?.open();
}

function onUsersPicked(users: any[]) {
  const mapped = (users || []).map((u: any) => ({
    userId: String(u.userId),
    name: u.nickName || u.userName || String(u.userId)
  }));
  if (pickerMode.value === 'transfer') {
    transferUser.value = mapped[0] || null;
  } else {
    addsignUsers.value = mapped;
  }
}

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function toDetail(instanceId: string) {
  router.push({ path: '/workflow/detail', query: { id: instanceId } });
}

function openHandle(row: TaskVO) {
  current.value = row;
  handleAction.value = 'agree';
  comment.value = '';
  transferUser.value = null;
  returnToInitiator.value = true;
  returnTargetKey.value = '';
  addsignPosition.value = 'before';
  addsignUsers.value = [];
  handleVisible.value = true;
}

async function handleUrge(row: TaskVO) {
  await urgeTask(row.taskId, '请尽快处理');
  ElMessage.success('已催办');
}

async function submitHandle() {
  if (!current.value) return;
  submitting.value = true;
  try {
    if (handleAction.value === 'transfer') {
      if (!transferUser.value) {
        ElMessage.warning('请选择目标办理人');
        return;
      }
      await transferTask(current.value.taskId, { targetUserId: transferUser.value.userId, reason: comment.value });
      ElMessage.success('转办成功');
    } else if (handleAction.value === 'return') {
      await returnTask(current.value.taskId, {
        toInitiator: returnToInitiator.value,
        targetTaskKey: returnToInitiator.value ? undefined : returnTargetKey.value,
        comment: comment.value
      });
      ElMessage.success('已退回');
    } else if (handleAction.value === 'addsign') {
      const ids = addsignUsers.value.map((u) => u.userId);
      if (!ids.length) {
        ElMessage.warning('请选择加签人');
        return;
      }
      await addsignTask(current.value.taskId, { assigneeIds: ids, position: addsignPosition.value, reason: comment.value });
      ElMessage.success('已加签');
    } else {
      await completeTask(current.value.taskId, { action: handleAction.value, comment: comment.value });
      ElMessage.success(handleAction.value === 'agree' ? '已同意' : '已拒绝');
    }
    handleVisible.value = false;
    await load();
  } finally {
    submitting.value = false;
  }
}

async function load() {
  loading.value = true;
  try {
    const res: any = await listTodo({ pageNum: pageNum.value, pageSize: pageSize.value });
    tasks.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
