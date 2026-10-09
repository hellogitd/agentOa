<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">提示词模板</span></el-col>
          <el-col v-hasPermi="['ai:prompt:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新建模板</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="编码" align="left" prop="code" min-width="130" />
        <el-table-column label="名称" align="left" prop="name" min-width="120" />
        <el-table-column label="分类" align="center" prop="category" width="100" />
        <el-table-column label="内容" align="left" prop="content" min-width="240" show-overflow-tooltip />
        <el-table-column label="内置" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.isBuiltin === 1" type="warning">内置</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:prompt:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button
              v-hasPermi="['ai:prompt:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="scope.row.isBuiltin === 1"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如：official_doc" maxlength="64" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" maxlength="64" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="8000" placeholder="系统提示词内容" />
          <div class="text-gray-400 text-xs">AI 生成内容仅供参考，最终以人工确认为准</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
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
  </div>
</template>

<script setup name="AiPrompt" lang="ts">
import { listPromptTemplates, addPromptTemplate, updatePromptTemplate, deletePromptTemplate, PromptTemplateForm, PromptTemplateVO } from '@/api/ai';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const list = ref<PromptTemplateVO[]>([]);
const loading = ref(false);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 20 });
const dialog = reactive({ visible: false, title: '' });
const form = reactive<PromptTemplateForm>({
  code: '',
  name: '',
  category: '',
  content: '',
  enabled: 1,
  remark: ''
});
const rules = reactive({
  code: [{ required: true, message: '编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  content: [{ required: true, message: '内容不能为空', trigger: 'blur' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listPromptTemplates(query);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  form.id = undefined;
  form.code = '';
  form.name = '';
  form.category = '';
  form.content = '';
  form.enabled = 1;
  form.remark = '';
  dialog.title = '新建提示词模板';
  dialog.visible = true;
};

const handleEdit = (row: PromptTemplateVO) => {
  form.id = row.id;
  form.code = row.code;
  form.name = row.name;
  form.category = row.category ?? '';
  form.content = row.content;
  form.enabled = row.enabled;
  form.remark = row.remark ?? '';
  dialog.title = '修改提示词模板';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (form.id) {
    await updatePromptTemplate(form.id, { ...form });
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addPromptTemplate({ ...form });
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: PromptTemplateVO) => {
  await proxy?.$modal.confirm(`确认删除模板「${row.name}」？`);
  await deletePromptTemplate(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
