<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:post:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:post:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:post:remove']" type="danger" plain icon="Delete" :disabled="single" @click="handleDelete()">删除</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="岗位编码" prop="positionCode">
            <el-input v-model="queryParams.positionCode" placeholder="请输入岗位编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="岗位名称" prop="positionName">
            <el-input v-model="queryParams.positionName" placeholder="请输入岗位名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="岗位状态" clearable>
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="postList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="岗位编码" align="center" prop="positionCode" />
        <el-table-column label="岗位名称" align="center" prop="positionName" />
        <el-table-column label="默认职级" align="center" prop="positionLevel" />
        <el-table-column label="排序" align="center" prop="positionSort" width="80" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="操作" width="180" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['hr:post:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['hr:post:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="postFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="岗位编码" prop="positionCode">
          <el-input v-model="form.positionCode" placeholder="请输入岗位编码" maxlength="64" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="positionName">
          <el-input v-model="form.positionName" placeholder="请输入岗位名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="默认职级" prop="positionLevel">
          <el-input v-model="form.positionLevel" placeholder="如 P6" maxlength="32" />
        </el-form-item>
        <el-form-item label="显示排序" prop="positionSort">
          <el-input-number v-model="form.positionSort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="HrPost" lang="ts">
import { listPost, addPost, updatePost, delPost, PostForm, PostVO } from '@/api/hr';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const postList = ref<PostVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string>>([]);
const single = ref(true);
const total = ref(0);
const dialog = reactive<DialogOption>({ title: '', visible: false });

const initForm: PostForm = { positionCode: '', positionName: '', positionLevel: '', positionSort: 0, status: '0', remark: '' };
const data = reactive<{ form: PostForm; queryParams: any }>({
  form: { ...initForm },
  queryParams: { pageNum: 1, pageSize: 10, positionCode: undefined, positionName: undefined, status: undefined }
});
const { queryParams, form } = toRefs(data);

const rules = ref<any>({
  positionCode: [{ required: true, message: '岗位编码不能为空', trigger: 'blur' }],
  positionName: [{ required: true, message: '岗位名称不能为空', trigger: 'blur' }],
  positionSort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listPost(queryParams.value);
    postList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 10, positionCode: undefined, positionName: undefined, status: undefined };
  handleQuery();
};

const handleSelectionChange = (selection: PostVO[]) => {
  ids.value = selection.map((item) => item.id);
  single.value = selection.length !== 1;
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['postFormRef'] as any)?.resetFields();
};

const handleAdd = () => {
  reset();
  dialog.title = '新增岗位';
  dialog.visible = true;
};

const handleUpdate = (row?: PostVO) => {
  reset();
  const id = row?.id ?? ids.value[0];
  const target = postList.value.find((item) => item.id === id);
  if (target) {
    form.value = { ...target };
  }
  dialog.title = '修改岗位';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['postFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updatePost(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addPost(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const handleDelete = async (row?: PostVO) => {
  const deleteIds = row ? [row.id] : ids.value;
  await proxy?.$modal.confirm(`确认删除选中的岗位吗？`);
  await delPost(deleteIds[0]);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
