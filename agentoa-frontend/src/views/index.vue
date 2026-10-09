<template>
  <div class="app-container foundation-page">
    <h1>办公工作台</h1>
    <p>欢迎，{{ user.nickname || '用户' }}。审批待办、今日打卡、公告与关键余额汇总如下。</p>

    <el-row :gutter="20">
      <el-col :md="8" :xs="24">
        <el-card>
          <template #header
            ><strong>我的待办</strong><el-tag class="connection" size="small">{{ workbench.todoCount ?? 0 }}</el-tag></template
          >
          <el-table :data="workbench.todoRecent ?? []" empty-text="暂无待办" size="small">
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="time" label="时间" width="130" />
          </el-table>
          <el-button class="more" link type="primary" @click="go('/workflow/todo')">查看全部</el-button>
        </el-card>
        <el-card>
          <template #header
            ><strong>我发起的</strong><el-tag class="connection" size="small">{{ workbench.initiatedCount ?? 0 }}</el-tag></template
          >
          <el-table :data="workbench.initiatedRecent ?? []" empty-text="暂无发起" size="small">
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="70" />
          </el-table>
          <el-button class="more" link type="primary" @click="go('/workflow/mine')">查看全部</el-button>
        </el-card>
      </el-col>

      <el-col :md="8" :xs="24">
        <el-card>
          <template #header
            ><strong>考勤状态</strong
            ><el-tag class="connection" :type="workbench.attendance?.todayPunched ? 'success' : 'info'">{{
              workbench.attendance?.todayPunched ? '已打卡' : '未打卡'
            }}</el-tag></template
          >
          <p class="line">上班：{{ workbench.attendance?.punchInTime || '—' }}</p>
          <p class="line">下班：{{ workbench.attendance?.punchOutTime || '—' }}</p>
          <el-button class="more" link type="primary" @click="go('/attendance/punch')">去打卡</el-button>
        </el-card>
        <el-card>
          <template #header><strong>关键余额</strong></template>
          <el-table :data="workbench.leaveBalances ?? []" empty-text="暂无额度" size="small">
            <el-table-column prop="leaveType" label="假种" width="110" />
            <el-table-column label="可用(分钟)" width="110">
              <template #default="scope">{{ scope.row.availableMinutes }}</template>
            </el-table-column>
          </el-table>
          <el-button class="more" link type="primary" @click="go('/attendance/balance')">我的余额</el-button>
        </el-card>
      </el-col>

      <el-col :md="8" :xs="24">
        <el-card>
          <template #header><strong>今日日程</strong></template>
          <el-table :data="workbench.todayEvents ?? []" empty-text="今日无日程" size="small">
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="startTime" label="开始" width="130" />
          </el-table>
          <el-button class="more" link type="primary" @click="go('/collaboration/event')">我的日程</el-button>
        </el-card>
        <el-card>
          <template #header><strong>公告</strong></template>
          <el-table :data="workbench.notices ?? []" empty-text="暂无公告" size="small">
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="publishTime" label="发布时间" width="130" />
          </el-table>
          <el-button class="more" link type="primary" @click="go('/notice/announcement')">公告中心</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header><strong>快捷入口</strong></template>
      <el-space wrap>
        <el-button v-for="link in workbench.quickLinks ?? []" :key="link.url" @click="go(link.url)">{{ link.label }}</el-button>
      </el-space>
    </el-card>

    <el-card v-if="workbench.management">
      <template #header><strong>管理视角</strong></template>
      <el-row :gutter="20">
        <el-col :md="8" :xs="24"
          ><p class="line">本月部门出勤率：{{ workbench.management.deptAttendanceRate ?? '—' }}</p></el-col
        >
        <el-col :md="8" :xs="24"
          ><p class="line">本月报销金额：{{ workbench.management.monthReimburseAmount ?? '—' }} 元</p></el-col
        >
        <el-col :md="8" :xs="24"
          ><p class="line">平均处理时长：{{ workbench.management.avgFlowDurationHours ?? '—' }} 小时</p></el-col
        >
      </el-row>
    </el-card>

    <el-row :gutter="20">
      <el-col :md="12" :xs="24">
        <el-card>
          <template #header><strong>我的文件</strong></template>
          <el-upload :show-file-list="false" :http-request="upload" accept=".pdf,.png,.jpg,.jpeg,.txt">
            <el-button type="primary">上传文件</el-button>
            <template #tip><p>支持 PDF、图片、文本，单个不超过 20 MiB，仅本人可下载。</p></template>
          </el-upload>
          <el-table :data="files" empty-text="暂无文件">
            <el-table-column prop="fileName" label="文件名" />
            <el-table-column label="大小" width="100"
              ><template #default="{ row }">{{ Math.ceil(row.sizeBytes / 1024) }} KB</template></el-table-column
            >
            <el-table-column width="80"
              ><template #default="{ row }"><el-button link type="primary" @click="downloadFile(row)">下载</el-button></template></el-table-column
            >
          </el-table>
        </el-card>
      </el-col>
      <el-col :md="12" :xs="24">
        <el-card>
          <template #header
            ><strong>站内消息</strong><el-tag class="connection" :type="connectionTag.type">{{ connectionTag.text }}</el-tag></template
          >
          <el-table :data="messages" empty-text="暂无消息">
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="content" label="内容" show-overflow-tooltip />
            <el-table-column width="90"
              ><template #default="{ row }"
                ><el-button v-if="!row.isRead" link type="primary" @click="markRead(row.id)">标为已读</el-button><span v-else>已读</span></template
              ></el-table-column
            >
          </el-table>
          <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next" @current-change="loadMessages" />
        </el-card>
        <el-card v-if="user.roles.includes('superadmin')" class="probe">
          <template #header><strong>基础服务验证</strong></template>
          <p>创建一次验证流程，同时记录业务数据并投递站内通知。</p>
          <el-button :loading="probing" @click="startProbe">创建验证流程</el-button>
          <el-button v-if="processId" type="primary" @click="completeProbe">完成本次验证</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { UploadRequestOptions } from 'element-plus';
import request from '@/utils/request';
import { useUserStore } from '@/store/modules/user';
import { useNoticeStore } from '@/store/modules/notice';
import { getWorkbench, WorkbenchVO } from '@/api/report';
const user = useUserStore();
const noticeStore = useNoticeStore();
const router = useRouter();
const workbench = ref<WorkbenchVO>({} as WorkbenchVO);
const files = ref<Array<{ fileId: string; fileName: string; sizeBytes: number }>>([]);
const messages = ref<Array<{ id: string; title: string; content: string; isRead: number }>>([]);
const page = ref(1),
  total = ref(0),
  probing = ref(false),
  processId = ref('');
let poll: ReturnType<typeof setInterval> | undefined;
let disposed = false;

/** 实时连接状态（docs/22 FE-F3-03）：连接/断线提示与降级文案 */
const connectionTag = computed(() => {
  switch (noticeStore.connection.status) {
    case 'connected':
      return { type: 'success' as const, text: '实时连接' };
    case 'connecting':
      return { type: 'warning' as const, text: '连接中' };
    case 'disconnected':
      return { type: 'danger' as const, text: '断线重连中（已降级定时拉取）' };
    default:
      return { type: 'info' as const, text: '定时刷新' };
  }
});

// 推送到达即刷新消息表（与 30s 定时拉取双轨互补）
watch(
  () => noticeStore.connection.lastEventAt,
  () => {
    if (!disposed) void loadMessages();
  }
);
function go(path: string) {
  void router.push(path);
}
async function loadWorkbench() {
  try {
    const res: any = await getWorkbench();
    workbench.value = res.data ?? ({} as WorkbenchVO);
  } catch {
    workbench.value = {} as WorkbenchVO;
  }
}
async function loadFiles() {
  files.value = (await request.get('/api/v1/files')).data;
}
async function loadMessages() {
  const { data } = await request.get('/api/v1/messages', { params: { pageNum: page.value, pageSize: 20 } });
  messages.value = data.records;
  total.value = data.total;
}
async function upload(options: UploadRequestOptions) {
  if (options.file.size > 20 * 1024 * 1024) throw new Error('文件不能超过 20 MiB');
  const form = new FormData();
  form.append('file', options.file);
  await request.post('/api/v1/files', form, { headers: { repeatSubmit: false } });
  ElMessage.success('上传成功');
  await loadFiles();
}
async function downloadFile(file: { fileId: string; fileName: string }) {
  const data: Blob = await request.get(`/api/v1/files/${file.fileId}/download`, { responseType: 'blob' });
  const url = URL.createObjectURL(data);
  const a = document.createElement('a');
  a.href = url;
  a.download = file.fileName;
  a.click();
  URL.revokeObjectURL(url);
}
async function markRead(id: string) {
  await request.put(`/api/v1/messages/${id}/read`);
  await loadMessages();
}
async function startProbe() {
  probing.value = true;
  try {
    processId.value = (await request.post('/api/v1/foundation/probes')).data.processInstanceId;
    ElMessage.success('验证流程已创建');
  } finally {
    probing.value = false;
  }
}
async function completeProbe() {
  await request.post(`/api/v1/foundation/probes/${processId.value}/complete`);
  processId.value = '';
  ElMessage.success('流程已完成');
}
onMounted(async () => {
  await Promise.all([loadWorkbench(), loadFiles(), loadMessages()]);
  await noticeStore.refreshUnread();
  // 拉取兜底：30s 定时刷新消息（实时通道断线期间即降级节奏）
  poll = setInterval(() => {
    if (disposed) return;
    void loadMessages();
    void noticeStore.refreshUnread();
  }, 30000);
});
onBeforeUnmount(() => {
  disposed = true;
  if (poll) clearInterval(poll);
});
</script>
<style scoped>
.foundation-page h1 {
  margin: 0 0 12px;
}
.foundation-page p {
  color: #64748b;
  line-height: 1.7;
}
.el-card {
  margin-top: 20px;
}
.connection {
  float: right;
}
.el-pagination {
  margin-top: 20px;
}
.line {
  margin: 4px 0;
  color: #475569;
}
.more {
  margin-top: 8px;
}
</style>
