<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">费用类型</span></el-col>
          <el-col v-hasPermi="['fn:expense-type:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd()">新增类型</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="tree" border row-key="id" default-expand-all>
        <el-table-column label="类型名称" align="left" prop="name" min-width="160" />
        <el-table-column label="编码" align="center" prop="code" width="140" />
        <el-table-column label="排序" align="center" prop="sort" width="80" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" min-width="140" />
        <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['fn:expense-type:add']" link type="primary" icon="Plus" @click="handleAdd(scope.row)">新增</el-button>
            <el-button v-hasPermi="['fn:expense-type:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button v-hasPermi="['fn:expense-type:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="上级类型">
          <el-tree-select
            v-model="form.parentId"
            :data="treeOptions"
            :props="treeProps"
            check-strictly
            clearable
            placeholder="顶级类型"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="如 travel" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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

<script setup name="FinanceExpenseType" lang="ts">
import { listExpenseType, addExpenseType, updateExpenseType, delExpenseType, ExpenseTypeVO, ExpenseTypeForm } from '@/api/finance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(true);
const tree = ref<ExpenseTypeVO[]>([]);
const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<ExpenseTypeForm>({ name: '', code: '', status: '0', sort: 0 });
const rules = {
  name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }]
};

const treeOptions = computed(() => [{ id: '0', name: '顶级类型', children: tree.value }]);
const treeProps: any = { value: 'id', label: 'name', children: 'children' };

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listExpenseType();
    tree.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleAdd = (parent?: ExpenseTypeVO) => {
  form.value = { name: '', code: '', status: '0', sort: 0, parentId: parent?.id };
  dialog.title = '新增费用类型';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: ExpenseTypeVO) => {
  form.value = { ...row };
  dialog.title = '修改费用类型';
  dialog.id = row.id;
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (dialog.id) {
    await updateExpenseType(dialog.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addExpenseType(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: ExpenseTypeVO) => {
  await proxy?.$modal.confirm(`确认删除费用类型「${row.name}」？`);
  await delExpenseType(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
