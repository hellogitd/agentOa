<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">公告管理</span></el-col>
          <el-col v-hasPermi="['nt:notice:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreate">新建公告</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="标题" align="center" prop="title" min-width="200">
          <template #default="scope">
            <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="nc_notice_type" :value="scope.row.noticeType" />
          </template>
        </el-table-column>
        <el-table-column label="范围" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="nc_scope_type" :value="scope.row.scopeType" />
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="nc_notice_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="已读/受众" align="center" width="110">
          <template #default="scope">{{ scope.row.readCount }}/{{ scope.row.audienceCount }}</template>
        </el-table-column>
        <el-table-column label="发布时间" align="center" prop="publishTime" width="170" />
        <el-table-column label="操作" align="center" width="240" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 1"
              v-hasPermi="['nt:notice:add']"
              link
              type="primary"
              icon="Promotion"
              @click="handlePublish(scope.row)"
              >发布</el-button
            >
            <el-button v-if="scope.row.status === 1" v-hasPermi="['nt:notice:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)"
              >修改</el-button
            >
            <el-button
              v-if="scope.row.status === 2"
              v-hasPermi="['nt:notice:recall']"
              link
              type="warning"
              icon="RefreshLeft"
              @click="handleRevoke(scope.row)"
              >撤回</el-button
            >
            <el-button v-hasPermi="['nt:notice:read']" link type="primary" icon="View" @click="handleReadStatus(scope.row)">已读</el-button>
            <el-button
              v-if="scope.row.status === 1"
              v-hasPermi="['nt:notice:remove']"
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
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="公告类型">
              <el-select v-model="form.noticeType" placeholder="请选择" class="w-full">
                <el-option v-for="dict in nc_notice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="受众范围" prop="scopeType">
              <el-select v-model="form.scopeType" placeholder="请选择" class="w-full">
                <el-option v-for="dict in nc_scope_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.scopeType && form.scopeType !== '1'" label="范围值">
          <el-input v-model="form.scopeValues" :placeholder="scopePlaceholder" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <div class="field-ai-bar">
            <el-button v-hasPermi="['ai:copilot:use']" size="small" type="primary" plain icon="MagicStick" @click="openAiDraft">AI 起草</el-button>
          </div>
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请输入公告内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="公告详情" size="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="nc_notice_status" :value="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="发布人">{{ detail.publisherName }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detail.publishTime }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ detail.content }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <el-dialog title="已读统计" v-model="readStatusVisible" width="560px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="受众人数">{{ readStatus.audienceCount }}</el-descriptions-item>
        <el-descriptions-item label="已读人数">{{ readStatus.readCount }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">未读</el-divider>
      <el-tag v-for="user in readStatus.unreadUsers" :key="user.userId" class="mr-1 mb-1">{{ user.nickname || user.userId }}</el-tag>
      <el-divider content-position="left">已读</el-divider>
      <el-tag v-for="user in readStatus.readUsers" :key="user.userId" type="success" class="mr-1 mb-1">{{ user.nickname || user.userId }}</el-tag>
    </el-dialog>

    <!-- M4-03 通知起草：生成草稿，人工修改后再发布（docs/21 §6.1） -->
    <AiCopilotDrawer
      ref="aiDrawer"
      scene="notice-draft"
      title="AI 起草"
      action-label="填入内容"
      instruction-placeholder="如：起草一则系统升级通知，要求 3 条要点"
      @apply="applyAiDraft"
    />
  </div>
</template>

<script setup name="NoticeAnnouncement" lang="ts">
import {
  listAnnouncement,
  getAnnouncement,
  addAnnouncement,
  updateAnnouncement,
  delAnnouncement,
  publishAnnouncement,
  revokeAnnouncement,
  getReadStatus,
  AnnouncementVO,
  AnnouncementForm,
  ReadStatusVO
} from '@/api/notice';
import AiCopilotDrawer from '@/components/AiCopilotDrawer/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const aiDrawer = ref<InstanceType<typeof AiCopilotDrawer>>();

/** M4-03：按要点起草通知草稿，填入编辑框后仍需人工修改再发布 */
const openAiDraft = () => {
  aiDrawer.value?.open({ instruction: '', content: form.value.title ? `标题：${form.value.title}` : '' });
};
const applyAiDraft = (text: string) => {
  form.value.content = text;
  proxy?.$modal.msgSuccess('已填入内容，请人工核对后再发布');
};
const { nc_notice_type, nc_scope_type, nc_notice_status } = toRefs<any>(proxy?.useDict('nc_notice_type', 'nc_scope_type', 'nc_notice_status'));

const loading = ref(true);
const list = ref<AnnouncementVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10 });

const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<AnnouncementForm>({ title: '', content: '', noticeType: 'company', scopeType: '1' });
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  scopeType: [{ required: true, message: '请选择受众范围', trigger: 'change' }]
};

const detailVisible = ref(false);
const detail = ref<any>({});
const readStatusVisible = ref(false);
const readStatus = ref<ReadStatusVO>({ noticeId: '', audienceCount: 0, readCount: 0, readUsers: [], unreadUsers: [] });

const scopePlaceholder = computed(() => {
  if (form.value.scopeType === '2') return '部门 ID，逗号分隔，如 100,103';
  if (form.value.scopeType === '3') return '角色 key，逗号分隔，如 hr,finance';
  return '用户 ID，逗号分隔';
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listAnnouncement(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleCreate = () => {
  form.value = { title: '', content: '', noticeType: 'company', scopeType: '1' };
  dialog.title = '新建公告';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: AnnouncementVO) => {
  form.value = {
    title: row.title,
    content: row.content,
    noticeType: row.noticeType,
    scopeType: String(row.scopeType),
    scopeValues: row.scopeValues
  };
  dialog.title = '修改公告';
  dialog.id = row.id;
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (dialog.id) {
    await updateAnnouncement(dialog.id, form.value);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await addAnnouncement(form.value);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handlePublish = async (row: AnnouncementVO) => {
  await proxy?.$modal.confirm(`确认发布「${row.title}」并生成受众快照？`);
  await publishAnnouncement(row.id);
  proxy?.$modal.msgSuccess('发布成功');
  await getList();
};

const handleRevoke = async (row: AnnouncementVO) => {
  await proxy?.$modal.confirm(`确认撤回「${row.title}」？撤回后接收人不可见。`);
  await revokeAnnouncement(row.id);
  proxy?.$modal.msgSuccess('已撤回');
  await getList();
};

const handleDelete = async (row: AnnouncementVO) => {
  await proxy?.$modal.confirm(`确认删除「${row.title}」？`);
  await delAnnouncement(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleDetail = async (row: AnnouncementVO) => {
  const res: any = await getAnnouncement(row.id);
  detail.value = res.data;
  detailVisible.value = true;
};

const handleReadStatus = async (row: AnnouncementVO) => {
  const res: any = await getReadStatus(row.id);
  readStatus.value = res.data;
  readStatusVisible.value = true;
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.field-ai-bar {
  margin-bottom: 8px;
}
</style>
