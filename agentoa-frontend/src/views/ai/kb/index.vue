<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">知识库管理</span></el-col>
          <el-col v-hasPermi="['ai:kb:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd">新建知识域</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="query" inline>
          <el-form-item label="名称">
            <el-input v-model="query.name" placeholder="知识域名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="知识域" align="left" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="可见性" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="ai_kb_visibility" :value="scope.row.visibility" />
          </template>
        </el-table-column>
        <el-table-column label="向量模型" align="center" prop="embeddingModelName" width="140" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.embeddingModelName || '未配置' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据源" align="center" prop="sourceCount" width="80" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">
              {{ scope.row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建者" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.owner" type="primary">本人</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="300" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:kb:query']" link type="primary" icon="DocumentAdd" @click="openSources(scope.row)">数据源</el-button>
            <el-button v-hasPermi="['ai:kb:index']" link type="primary" icon="Search" @click="openSearchTest(scope.row)">检索测试</el-button>
            <el-button v-hasPermi="['ai:kb:edit']" link type="primary" icon="User" :disabled="!scope.row.owner" @click="openMembers(scope.row)"
              >成员</el-button
            >
            <el-button v-hasPermi="['ai:kb:edit']" link type="primary" icon="Edit" :disabled="!scope.row.owner" @click="handleEdit(scope.row)"
              >修改</el-button
            >
            <el-button v-hasPermi="['ai:kb:remove']" link type="danger" icon="Delete" :disabled="!scope.row.owner" @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 新增/编辑知识域 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：人事制度库" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="可见性" prop="visibility">
          <el-radio-group v-model="form.visibility">
            <el-radio v-for="item in ai_kb_visibility" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
          <div class="text-gray-400 text-xs">全员可见：所有人可检索；指定人/私有：仅创建者与显式成员可检索</div>
        </el-form-item>
        <el-form-item label="成员">
          <el-input v-model="memberInput" placeholder="成员账号 ID，多个用逗号分隔" />
          <div class="text-gray-400 text-xs">成员授权服务端过滤，越权知识域/文档不会出现在检索与引用中</div>
        </el-form-item>
        <el-form-item label="向量模型" prop="embeddingModelId">
          <el-select v-model="form.embeddingModelId" placeholder="选择能力含 embedding 的模型" class="w-full" clearable>
            <el-option v-for="model in embeddingModels" :key="model.id" :label="model.alias || model.modelKey" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
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

    <kb-member-dialog v-model:visible="memberDialogVisible" :kb="currentKb" @changed="getList" />
    <kb-source-dialog v-model:visible="sourceDialogVisible" :kb="currentKb" @changed="getList" />
    <kb-search-test-dialog v-model:visible="searchDialogVisible" :kb="currentKb" @navigate="handleNavigate" />
  </div>
</template>

<script setup name="AiKb" lang="ts">
import { listKb, getKb, addKb, updateKb, deleteKb, listEnabledModels, type KbVO, type KbForm } from '@/api/ai';
import KbMemberDialog from './components/KbMemberDialog.vue';
import KbSourceDialog from './components/KbSourceDialog.vue';
import KbSearchTestDialog from './components/KbSearchTestDialog.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_kb_visibility } = toRefs<any>(proxy?.useDict('ai_kb_visibility'));
const router = useRouter();

const loading = ref(true);
const showSearch = ref(true);
const list = ref<KbVO[]>([]);
const total = ref(0);
const query = reactive<any>({ pageNum: 1, pageSize: 10, name: undefined });
const dialog = reactive<DialogOption>({ title: '', visible: false });
const formRef = ref<any>();
const initForm: KbForm = {
  name: '',
  description: '',
  visibility: 'private',
  memberUserIds: [],
  embeddingModelId: undefined,
  status: 'active',
  remark: ''
};
const form = ref<KbForm>({ ...initForm });
const memberInput = ref('');
const embeddingModels = ref<any[]>([]);
const rules = ref<any>({
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  visibility: [{ required: true, message: '可见性不能为空', trigger: 'change' }]
});

const currentKb = ref<KbVO | null>(null);
const memberDialogVisible = ref(false);
const sourceDialogVisible = ref(false);
const searchDialogVisible = ref(false);

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listKb(query);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  query.pageNum = 1;
  getList();
};

const resetQuery = () => {
  query.name = undefined;
  handleQuery();
};

const loadModels = async () => {
  const res: any = await listEnabledModels();
  embeddingModels.value = (res.data ?? []).filter((model: any) => (model.capability ?? []).includes('embedding'));
};

const reset = () => {
  form.value = { ...initForm };
  memberInput.value = '';
  nextTick(() => formRef.value?.clearValidate?.());
};

const handleAdd = () => {
  reset();
  dialog.title = '新建知识域';
  dialog.visible = true;
};

const handleEdit = (row: KbVO) => {
  reset();
  dialog.title = '修改知识域';
  dialog.visible = true;
  nextTick(async () => {
    const res: any = await getKb(row.id);
    const kb = res.data;
    form.value = {
      id: kb.id,
      name: kb.name,
      description: kb.description,
      visibility: kb.visibility,
      memberUserIds: kb.memberUserIds ?? [],
      embeddingModelId: kb.embeddingModelId,
      status: kb.status,
      remark: kb.remark
    };
    memberInput.value = (kb.memberUserIds ?? []).join(',');
  });
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: KbForm = {
    ...form.value,
    memberUserIds: memberInput.value
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item)
  } as any;
  if (form.value.id) {
    await updateKb(form.value.id, payload);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addKb(payload);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: KbVO) => {
  await proxy?.$modal.confirm(`确认删除知识域「${row.name}」及其全部索引吗？`);
  await deleteKb(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const openMembers = (row: KbVO) => {
  currentKb.value = row;
  memberDialogVisible.value = true;
};

const openSources = (row: KbVO) => {
  currentKb.value = row;
  sourceDialogVisible.value = true;
};

const openSearchTest = (row: KbVO) => {
  currentKb.value = row;
  searchDialogVisible.value = true;
};

/** 引用跳转（检索测试命中链接）：站内路由关闭全部弹窗后跳转 */
const handleNavigate = (link: string) => {
  sourceDialogVisible.value = false;
  router.push(link);
};

onMounted(() => {
  getList();
  loadModels();
});
</script>
