<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">消息中心</span></el-col>
          <el-col :span="1.5">
            <el-radio-group v-model="query.unread" @change="getList">
              <el-radio-button :value="undefined">全部</el-radio-button>
              <el-radio-button :value="true">未读</el-radio-button>
            </el-radio-group>
          </el-col>
          <el-col :span="1.5">
            <el-select v-model="query.type" placeholder="消息类型" clearable class="w-120px" @change="getList">
              <el-option v-for="dict in nc_msg_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Finished" @click="handleReadAll">全部已读</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-badge :value="unread.total || 0" class="ml-2">
              <el-tag>未读 {{ unread.total || 0 }}</el-tag>
            </el-badge>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="nc_msg_type" :value="scope.row.type" />
          </template>
        </el-table-column>
        <el-table-column label="标题" align="center" prop="title" min-width="220" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.isRead ? 'info' : 'danger'">{{ scope.row.isRead ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" align="center" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-if="!scope.row.isRead" link type="primary" icon="Check" @click="handleRead(scope.row)">标为已读</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-card v-hasPermi="['nt:outbox:list']" shadow="hover" class="mt-2">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">消息事件（outbox）</span></el-col>
          <el-col :span="1.5">
            <el-select v-model="outboxQuery.eventStatus" placeholder="事件状态" clearable class="w-120px" @change="getOutbox">
              <el-option label="待投递" value="PENDING" />
              <el-option label="已完成" value="DONE" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getOutbox">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="outboxLoading" border :data="outboxList">
        <el-table-column label="事件ID" align="center" prop="eventId" min-width="180" />
        <el-table-column label="标题" align="center" prop="title" min-width="150" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'FAILED' ? 'danger' : scope.row.status === 'DONE' ? 'success' : 'warning'">{{
              scope.row.status
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试/重投" align="center" width="110">
          <template #default="scope">{{ scope.row.retryCount }}/{{ scope.row.redeliverCount }}</template>
        </el-table-column>
        <el-table-column label="最后错误" align="center" prop="lastError" min-width="140" />
        <el-table-column label="操作" align="center" width="110" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'FAILED'"
              v-hasPermi="['nt:outbox:redeliver']"
              link
              type="primary"
              icon="RefreshRight"
              @click="handleRedeliver(scope.row)"
              >重投</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="outboxTotal > 0"
        v-model:page="outboxQuery.pageNum"
        v-model:limit="outboxQuery.pageSize"
        :total="outboxTotal"
        @pagination="getOutbox"
      />
    </el-card>
  </div>
</template>

<script setup name="NoticeMessage" lang="ts">
import {
  listMessage,
  markMessageRead,
  markAllRead,
  delMessage,
  listOutboxEvents,
  redeliverOutboxEvent,
  MessageVO,
  UnreadCountVO,
  OutboxEventVO
} from '@/api/notice';
import { useNoticeStore } from '@/store/modules/notice';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { nc_msg_type } = toRefs<any>(proxy?.useDict('nc_msg_type'));

// 未读角标与推送同源（docs/22 FE-F3-02 双轨去重）：推送经 store 触发拉取，页面动作后同样刷新
const noticeStore = useNoticeStore();
const unread = computed<UnreadCountVO>(() => noticeStore.unread);

const loading = ref(true);
const list = ref<MessageVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10, unread: undefined, type: undefined });

const outboxLoading = ref(false);
const outboxList = ref<OutboxEventVO[]>([]);
const outboxTotal = ref(0);
const outboxQuery = ref<any>({ pageNum: 1, pageSize: 10, eventStatus: 'FAILED' });

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listMessage(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const getUnread = () => noticeStore.refreshUnread();

const getOutbox = async () => {
  outboxLoading.value = true;
  try {
    const res: any = await listOutboxEvents(outboxQuery.value);
    outboxList.value = res.data?.records ?? [];
    outboxTotal.value = res.data?.total ?? 0;
  } finally {
    outboxLoading.value = false;
  }
};

const handleRead = async (row: MessageVO) => {
  await markMessageRead(row.id);
  const pushed = noticeStore.state.notices.find((item) => item.id === String(row.id));
  if (pushed) noticeStore.markLocalRead(pushed);
  await Promise.all([getList(), getUnread()]);
};

const handleReadAll = async () => {
  await proxy?.$modal.confirm('确认将全部消息标记为已读？');
  await markAllRead();
  noticeStore.readAll();
  proxy?.$modal.msgSuccess('已全部标记为已读');
  await Promise.all([getList(), getUnread()]);
};

const handleDelete = async (row: MessageVO) => {
  await proxy?.$modal.confirm('确认删除该消息？');
  await delMessage(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await Promise.all([getList(), getUnread()]);
};

const handleRedeliver = async (row: OutboxEventVO) => {
  await proxy?.$modal.confirm(`确认重投事件 ${row.eventId}？`);
  await redeliverOutboxEvent(row.id);
  proxy?.$modal.msgSuccess('已重投');
  await getOutbox();
};

onMounted(() => {
  getList();
  getUnread();
  getOutbox();
});
</script>
