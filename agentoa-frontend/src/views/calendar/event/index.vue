<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">我的日程</span></el-col>
          <el-col :span="1.5">
            <el-button plain icon="ArrowLeft" @click="shiftDay(-1)">前一天</el-button>
          </el-col>
          <el-col :span="2.5">
            <el-date-picker v-model="day" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" @change="getList" />
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="ArrowRight" @click="shiftDay(1)">后一天</el-button>
          </el-col>
          <el-col :span="2">
            <el-select v-model="query.scope" class="w-full" @change="getList">
              <el-option label="我可见的" value="self" />
              <el-option label="我组织的" value="organized" />
            </el-select>
          </el-col>
          <el-col :span="2.5">
            <el-input v-model="query.keyword" placeholder="标题关键字" clearable @keyup.enter="getList" />
          </el-col>
          <el-col v-hasPermi="['cl:event:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreate">新建日程</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="时间" align="center" width="150">
          <template #default="scope">
            <span v-if="scope.row.isAllDay === 1">全天</span>
            <span v-else>{{ formatTime(scope.row.startTime) }} - {{ formatTime(scope.row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标题" align="center" prop="title" min-width="180">
          <template #default="scope">
            <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="90">
          <template #default="scope">
            <dict-tag :options="cl_event_type" :value="scope.row.eventType" />
          </template>
        </el-table-column>
        <el-table-column label="地点/会议室" align="center" min-width="120">
          <template #default="scope">{{ scope.row.roomName || scope.row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="可见范围" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="cl_visibility" :value="scope.row.visibility" />
          </template>
        </el-table-column>
        <el-table-column label="组织者" align="center" prop="organizerName" width="100" />
        <el-table-column label="参与人" align="center" min-width="140">
          <template #default="scope">
            <el-tag v-for="a in scope.row.attendees" :key="a.userId" size="small" class="mr-1 mb-1">
              {{ a.nickname || a.userId }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的响应" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.myResponse === 'ACCEPTED'" type="success" size="small">已接受</el-tag>
            <el-tag v-else-if="scope.row.myResponse === 'REJECTED'" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else-if="scope.row.myResponse === 'PENDING'" type="warning" size="small">待响应</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 2" type="info" size="small">已取消</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-if="scope.row.myResponse === 'PENDING'" link type="success" icon="Check" @click="handleAccept(scope.row)">接受</el-button>
            <el-button v-if="scope.row.myResponse === 'PENDING'" link type="danger" icon="Close" @click="handleReject(scope.row)">拒绝</el-button>
            <el-button
              v-if="scope.row.canManage && scope.row.status === 1"
              v-hasPermi="['cl:event:edit']"
              link
              type="primary"
              icon="Edit"
              @click="handleEdit(scope.row)"
              >修改</el-button
            >
            <el-button v-if="scope.row.canManage" v-hasPermi="['cl:event:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入日程标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.eventType" class="w-full">
                <el-option v-for="dict in cl_event_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可见范围">
              <el-select v-model="form.visibility" class="w-full">
                <el-option v-for="dict in cl_visibility" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="起止时间" prop="range">
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            class="w-full"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地点">
              <el-input v-model="form.location" placeholder="线下地点（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="会议室">
              <el-select v-model="form.roomId" clearable placeholder="可选" class="w-full">
                <el-option v-for="room in rooms" :key="room.id" :label="room.name" :value="String(room.id)" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="参与人">
          <el-select v-model="form.attendeeIds" multiple filterable placeholder="选择参与人" class="w-full">
            <el-option v-for="u in users" :key="u.userId" :label="u.nickName" :value="String(u.userId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <div class="field-ai-bar">
            <el-button v-hasPermi="['ai:copilot:use']" size="small" type="primary" plain icon="MagicStick" @click="openAiMinutes">AI 纪要</el-button>
          </div>
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="日程描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="日程详情" size="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(detail.startTime) }} - {{ formatTime(detail.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.eventType === 2 ? '会议' : '日程' }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ detail.roomName || detail.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="组织者">{{ detail.organizerName }}</el-descriptions-item>
        <el-descriptions-item label="参与人">
          <el-tag v-for="a in detail.attendees" :key="a.userId" size="small" class="mr-1 mb-1">
            {{ a.nickname || a.userId }}（{{ a.responseStatus }}）
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ detail.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- M4-05 会议纪要：生成草稿，人工确认后写入日程描述（docs/21 §6.1） -->
    <AiCopilotDrawer
      ref="aiDrawer"
      scene="minutes"
      title="AI 纪要"
      action-label="写入描述"
      instruction-placeholder="如：把这段发言整理成会议纪要草稿"
      @apply="applyAiMinutes"
    />
  </div>
</template>

<script setup name="CalendarEvent" lang="ts">
import { listEvent, getEvent, addEvent, updateEvent, delEvent, acceptEvent, rejectEvent, allRoom, EventVO, EventForm } from '@/api/collaboration';
import { listUser } from '@/api/system/user';
import AiCopilotDrawer from '@/components/AiCopilotDrawer/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const aiDrawer = ref<InstanceType<typeof AiCopilotDrawer>>();

/** M4-05：把会议记录转写成纪要草稿，人工确认后写入日程描述 */
const openAiMinutes = () => {
  aiDrawer.value?.open({
    bizId: dialog.id,
    instruction: '把粘贴的会议记录转写成结构化纪要草稿',
    content: form.value.title ? `会议主题：${form.value.title}` : ''
  });
};
const applyAiMinutes = (text: string) => {
  form.value.description = text;
  proxy?.$modal.msgSuccess('纪要草稿已写入描述，请人工核对后保存');
};
const { cl_event_type, cl_visibility } = toRefs<any>(proxy?.useDict('cl_event_type', 'cl_visibility'));

const loading = ref(true);
const list = ref<EventVO[]>([]);
const total = ref(0);
const day = ref(new Date().toISOString().slice(0, 10));
const query = ref<any>({ pageNum: 1, pageSize: 10, scope: 'self' });

const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<any>({ title: '', eventType: '1', visibility: '2', attendeeIds: [], range: [] });
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  range: [{ required: true, message: '请选择起止时间', trigger: 'change' }]
};

const detailVisible = ref(false);
const detail = ref<any>({});
const rooms = ref<any[]>([]);
const users = ref<any[]>([]);

const formatTime = (time: string) => {
  if (!time) return '';
  return new Date(time).toLocaleString('zh-CN', { hour12: false });
};

const shiftDay = (delta: number) => {
  const date = new Date(day.value);
  date.setDate(date.getDate() + delta);
  day.value = date.toISOString().slice(0, 10);
  getList();
};

const getList = async () => {
  loading.value = true;
  try {
    const start = `${day.value}T00:00:00Z`;
    const end = `${day.value}T23:59:59Z`;
    const res: any = await listEvent({ ...query.value, start, end });
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadOptions = async () => {
  const roomRes: any = await allRoom();
  rooms.value = roomRes.data ?? [];
  const userRes: any = await listUser({ pageNum: 1, pageSize: 100 });
  users.value = userRes.rows ?? userRes.data?.rows ?? [];
};

const handleCreate = () => {
  form.value = { title: '', eventType: '1', visibility: '2', attendeeIds: [], range: [], description: '' };
  dialog.title = '新建日程';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: EventVO) => {
  form.value = {
    title: row.title,
    description: row.description,
    eventType: String(row.eventType),
    visibility: String(row.visibility),
    location: row.location,
    roomId: row.roomId ? String(row.roomId) : undefined,
    attendeeIds: row.attendees.map((a) => String(a.userId)),
    range: [row.startTime, row.endTime],
    lockVersion: row.lockVersion
  };
  dialog.title = '修改日程';
  dialog.id = row.id;
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: EventForm = {
    title: form.value.title,
    description: form.value.description,
    eventType: form.value.eventType,
    startTime: form.value.range[0],
    endTime: form.value.range[1],
    location: form.value.location,
    roomId: form.value.roomId,
    visibility: form.value.visibility,
    attendeeIds: form.value.attendeeIds,
    lockVersion: form.value.lockVersion
  };
  if (dialog.id) {
    await updateEvent(dialog.id, payload);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await addEvent(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleAccept = async (row: EventVO) => {
  await acceptEvent(row.id);
  proxy?.$modal.msgSuccess('已接受邀请');
  await getList();
};

const handleReject = async (row: EventVO) => {
  await proxy?.$modal.confirm(`确认拒绝「${row.title}」的邀请？`);
  await rejectEvent(row.id);
  proxy?.$modal.msgSuccess('已拒绝邀请');
  await getList();
};

const handleDelete = async (row: EventVO) => {
  await proxy?.$modal.confirm(`确认删除「${row.title}」？`);
  await delEvent(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleDetail = async (row: EventVO) => {
  const res: any = await getEvent(row.id);
  detail.value = res.data;
  detailVisible.value = true;
};

onMounted(() => {
  getList();
  loadOptions();
});
</script>

<style scoped>
.field-ai-bar {
  margin-bottom: 8px;
}
</style>
