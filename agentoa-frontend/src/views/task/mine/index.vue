<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">我的任务</span></el-col>
          <el-col :span="2">
            <el-select v-model="query.status" clearable placeholder="状态" class="w-full" @change="getList">
              <el-option v-for="dict in cl_task_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-col>
          <el-col :span="2">
            <el-select v-model="query.scope" class="w-full" @change="getList">
              <el-option label="我负责/参与" value="self" />
              <el-option label="我指派的" value="created" />
            </el-select>
          </el-col>
          <el-col :span="2.5">
            <el-input v-model="query.keyword" placeholder="标题关键字" clearable @keyup.enter="getList" />
          </el-col>
          <el-col v-hasPermi="['cl:task:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreate">新建任务</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Bell" @click="goMessages">通知入口</el-button>
          </el-col>
        </el-row>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="列表" name="list">
          <el-table v-loading="loading" border :data="list">
            <el-table-column label="标题" align="center" prop="title" min-width="180">
              <template #default="scope">
                <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.title }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="优先级" align="center" width="90">
              <template #default="scope">
                <dict-tag :options="cl_task_priority" :value="scope.row.priority" />
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="100">
              <template #default="scope">
                <dict-tag :options="cl_task_status" :value="scope.row.status" />
              </template>
            </el-table-column>
            <el-table-column label="负责人" align="center" prop="assigneeName" width="110" />
            <el-table-column label="指派人" align="center" prop="assignerName" width="110" />
            <el-table-column label="截止日期" align="center" prop="dueDate" width="120" />
            <el-table-column label="进度" align="center" width="150">
              <template #default="scope">
                <el-progress :percentage="scope.row.progress || 0" :stroke-width="10" />
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button
                  v-if="scope.row.canManage && scope.row.status === 1"
                  v-hasPermi="['cl:task:edit']"
                  link
                  type="primary"
                  icon="VideoPlay"
                  @click="handleStatus(scope.row, 2)"
                  >开始</el-button
                >
                <el-button
                  v-if="scope.row.canManage && scope.row.status === 2"
                  v-hasPermi="['cl:task:edit']"
                  link
                  type="success"
                  icon="CircleCheck"
                  @click="handleStatus(scope.row, 4)"
                  >完成</el-button
                >
                <el-button
                  v-if="scope.row.canManage && scope.row.status !== 4 && scope.row.status !== 5"
                  v-hasPermi="['cl:task:edit']"
                  link
                  type="warning"
                  icon="Edit"
                  @click="handleEdit(scope.row)"
                  >修改</el-button
                >
                <el-button
                  v-if="scope.row.canManage && scope.row.status !== 4 && scope.row.status !== 5"
                  v-hasPermi="['cl:task:remove']"
                  link
                  type="danger"
                  icon="Delete"
                  @click="handleDelete(scope.row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>

          <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
        </el-tab-pane>

        <el-tab-pane label="看板" name="board">
          <el-row :gutter="12">
            <el-col v-for="column in board.columns" :key="column.status" :span="4" class="mb8">
              <el-card shadow="never" body-style="min-height:120px">
                <template #header>
                  <dict-tag :options="cl_task_status" :value="column.status" />
                  <span class="text-gray-400 text-xs">{{ column.tasks.length }}</span>
                </template>
                <div v-for="task in column.tasks" :key="task.id" class="board-item" @click="handleDetail(task)">
                  <div>{{ task.title }}</div>
                  <div class="text-gray-400 text-xs">{{ task.assigneeName }} / {{ task.dueDate || '无截止' }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入任务标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人" prop="assigneeId">
              <el-select v-model="form.assigneeId" filterable placeholder="选择负责人" class="w-full">
                <el-option v-for="u in users" :key="u.userId" :label="u.nickName" :value="String(u.userId)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" class="w-full">
                <el-option v-for="dict in cl_task_priority" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="截止日期">
              <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="协作者">
          <el-select v-model="form.memberIds" multiple filterable placeholder="选择协作者" class="w-full">
            <el-option v-for="u in users" :key="u.userId" :label="u.nickName" :value="String(u.userId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔（可选）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="任务描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="任务详情" size="560px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="cl_task_status" :value="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <dict-tag :options="cl_task_priority" :value="detail.priority" />
        </el-descriptions-item>
        <el-descriptions-item label="负责人/指派人">{{ detail.assigneeName }} / {{ detail.assignerName }}</el-descriptions-item>
        <el-descriptions-item label="起止">{{ detail.startDate || '-' }} 至 {{ detail.dueDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="进度">
          <el-progress :percentage="detail.progress || 0" :stroke-width="10" />
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ detail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="协作者">
          <el-tag v-for="m in detail.members" :key="m.userId" size="small" class="mr-1 mb-1">
            {{ m.nickname || m.userId }}
            <el-icon v-if="detail.canManage" class="ml-1" @click="handleRemoveMember(m.userId)"><Close /></el-icon>
          </el-tag>
          <el-select v-if="detail.canManage" v-model="newMemberId" filterable placeholder="添加协作者" class="w-180px ml-1" @change="handleAddMember">
            <el-option v-for="u in users" :key="u.userId" :label="u.nickName" :value="String(u.userId)" />
          </el-select>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">活动与评论</el-divider>
      <el-timeline>
        <el-timeline-item v-for="activity in activities" :key="activity.id" :timestamp="activity.createTime">
          <div class="text-xs text-gray-400">{{ activity.operatorName }} · {{ activityTypeName(activity.activityType) }}</div>
          <div>{{ activity.content }}</div>
        </el-timeline-item>
      </el-timeline>

      <el-input v-model="commentContent" type="textarea" :rows="3" placeholder="输入评论（可@协作者）" />
      <el-button type="primary" class="mt8" @click="handleComment">发表评论</el-button>
    </el-drawer>
  </div>
</template>

<script setup name="TaskMine" lang="ts">
import {
  listTask,
  boardTask,
  getTask,
  addTask,
  updateTask,
  delTask,
  changeTaskStatus,
  addTaskComment,
  listTaskActivities,
  addTaskMember,
  delTaskMember,
  TaskVO,
  TaskForm
} from '@/api/collaboration';
import { listUser } from '@/api/system/user';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { cl_task_status, cl_task_priority } = toRefs<any>(proxy?.useDict('cl_task_status', 'cl_task_priority'));
const router = useRouter();

const activeTab = ref('list');
const loading = ref(true);
const list = ref<TaskVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10, scope: 'self' });
const board = ref<any>({ columns: [] });

const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<any>({ title: '', assigneeId: '', priority: '2', memberIds: [] });
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  assigneeId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
};

const detailVisible = ref(false);
const detail = ref<any>({});
const activities = ref<any[]>([]);
const commentContent = ref('');
const newMemberId = ref('');
const users = ref<any[]>([]);

const activityTypeName = (type: number) => {
  const map: Record<number, string> = { 1: '创建', 2: '状态变更', 3: '进度', 4: '评论', 5: '成员变更' };
  return map[type] || '活动';
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listTask(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadBoard = async () => {
  const res: any = await boardTask();
  board.value = res.data ?? { columns: [] };
};

const loadUsers = async () => {
  const res: any = await listUser({ pageNum: 1, pageSize: 100 });
  users.value = res.rows ?? res.data?.rows ?? [];
};

const handleTabChange = () => {
  if (activeTab.value === 'board') {
    loadBoard();
  } else {
    getList();
  }
};

const goMessages = () => {
  router.push('/notice/message');
};

const handleCreate = () => {
  form.value = { title: '', assigneeId: '', priority: '2', memberIds: [], description: '' };
  dialog.title = '新建任务';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: TaskVO) => {
  form.value = {
    title: row.title,
    description: row.description,
    assigneeId: String(row.assigneeId),
    priority: String(row.priority),
    startDate: row.startDate,
    dueDate: row.dueDate,
    tags: row.tags,
    memberIds: row.members.map((m) => String(m.userId)),
    lockVersion: row.lockVersion
  };
  dialog.title = '修改任务';
  dialog.id = row.id;
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: TaskForm = {
    title: form.value.title,
    description: form.value.description,
    assigneeId: form.value.assigneeId,
    priority: form.value.priority,
    startDate: form.value.startDate,
    dueDate: form.value.dueDate,
    tags: form.value.tags,
    memberIds: form.value.memberIds,
    lockVersion: form.value.lockVersion
  };
  if (dialog.id) {
    await updateTask(dialog.id, payload);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await addTask(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await refresh();
};

const handleStatus = async (row: TaskVO, status: number) => {
  const label = status === 4 ? '完成' : '开始';
  await proxy?.$modal.confirm(`确认将「${row.title}」标记为${label}？`);
  await changeTaskStatus(row.id, { lockVersion: row.lockVersion, status: String(status) });
  proxy?.$modal.msgSuccess('状态已更新');
  await refresh();
};

const handleDelete = async (row: TaskVO) => {
  await proxy?.$modal.confirm(`确认删除「${row.title}」？`);
  await delTask(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await refresh();
};

const handleDetail = async (row: TaskVO) => {
  const res: any = await getTask(row.id);
  detail.value = res.data;
  const act: any = await listTaskActivities(row.id);
  activities.value = act.data ?? [];
  commentContent.value = '';
  detailVisible.value = true;
};

const handleComment = async () => {
  if (!commentContent.value.trim()) return;
  await addTaskComment(detail.value.id, { content: commentContent.value.trim() });
  proxy?.$modal.msgSuccess('评论成功');
  commentContent.value = '';
  await handleDetail(detail.value);
};

const handleAddMember = async (userId: string) => {
  await addTaskMember(detail.value.id, userId);
  newMemberId.value = '';
  await handleDetail(detail.value);
};

const handleRemoveMember = async (userId: string) => {
  await delTaskMember(detail.value.id, userId);
  await handleDetail(detail.value);
};

const refresh = async () => {
  if (activeTab.value === 'board') {
    await loadBoard();
  } else {
    await getList();
  }
};

onMounted(() => {
  getList();
  loadUsers();
});
</script>

<style scoped>
.board-item {
  padding: 6px;
  margin-bottom: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;
}
</style>
