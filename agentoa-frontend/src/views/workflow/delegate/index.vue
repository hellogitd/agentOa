<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd">新增委托</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="delegateList">
        <el-table-column label="委托人" align="center" prop="ownerName" width="120" />
        <el-table-column label="受托人" align="center" prop="delegateName" width="120" />
        <el-table-column label="开始日期" align="center" prop="startDate" width="110" />
        <el-table-column label="结束日期" align="center" prop="endDate" width="110" />
        <el-table-column label="适用流程" align="center" prop="processKeys" :show-overflow-tooltip="true" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="delegateFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="受托人ID" prop="delegateId">
          <el-input v-model="form.delegateId" placeholder="受托人账号ID" />
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择开始日期" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择结束日期" />
        </el-form-item>
        <el-form-item label="适用流程" prop="processKeys">
          <el-input v-model="form.processKeys" placeholder="逗号分隔，如 leave,reimburse；空=全部" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" maxlength="500" />
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

<script setup name="WfDelegate" lang="ts">
import { listDelegates, addDelegate, updateDelegate, delDelegate, DelegateForm, DelegateVO } from '@/api/workflow';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const delegateList = ref<DelegateVO[]>([]);
const loading = ref(true);
const dialog = reactive<DialogOption>({ title: '', visible: false });

const initForm: DelegateForm = {
  delegateId: '',
  startDate: '',
  endDate: '',
  processKeys: undefined,
  status: 1,
  remark: undefined
};
const form = ref<DelegateForm & { id?: string }>({ ...initForm });

const rules = ref<any>({
  delegateId: [{ required: true, message: '受托人不能为空', trigger: 'blur' }],
  startDate: [{ required: true, message: '开始日期不能为空', trigger: 'change' }],
  endDate: [{ required: true, message: '结束日期不能为空', trigger: 'change' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listDelegates();
    delegateList.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['delegateFormRef'] as any)?.resetFields();
};

const handleAdd = () => {
  reset();
  dialog.title = '新增委托';
  dialog.visible = true;
};

const handleUpdate = (row: DelegateVO) => {
  reset();
  form.value = { ...row } as any;
  dialog.title = '修改委托';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['delegateFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updateDelegate(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addDelegate(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const handleDelete = async (row: DelegateVO) => {
  await proxy?.$modal.confirm('确认删除该委托？');
  await delDelegate(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
