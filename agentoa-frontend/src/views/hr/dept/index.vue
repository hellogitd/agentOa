<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:dept:add']" type="primary" plain icon="Plus" @click="handleAdd()">新增部门</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getTree">刷新</el-button>
          </el-col>
        </el-row>
      </template>
      <el-table v-loading="loading" :data="deptList" row-key="id" border default-expand-all :tree-props="{ children: 'children' } as any">
        <el-table-column label="部门名称" prop="name" min-width="180" />
        <el-table-column label="排序" prop="sort" align="center" width="80" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="scope">
            <el-tooltip content="新增子部门" placement="top">
              <el-button v-hasPermi="['hr:dept:add']" link type="primary" icon="Plus" @click="handleAdd(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['hr:dept:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip :content="scope.row.status === '0' ? '停用' : '启用'" placement="top">
              <el-button
                v-hasPermi="['hr:dept:edit']"
                link
                :type="scope.row.status === '0' ? 'warning' : 'success'"
                icon="CircleClose"
                @click="handleToggleStatus(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['hr:dept:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="deptFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ value: 'id', label: 'name', children: 'children' } as any"
            value-key="id"
            placeholder="请选择上级部门"
            check-strictly
            default-expand-all
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="请输入部门名称" maxlength="30" />
        </el-form-item>
        <el-form-item label="显示排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
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

<script setup name="HrDept" lang="ts">
import { deptTree, addDept, updateDept, updateDeptStatus, delDept, DeptForm, DeptTreeNode } from '@/api/hr';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const loading = ref(true);
const deptList = ref<DeptTreeNode[]>([]);
const dialog = reactive<DialogOption>({ title: '', visible: false });

const initForm: DeptForm = { parentId: '0', deptName: '', orderNum: 0, phone: '', email: '', status: '0' };
const form = ref<DeptForm>({ ...initForm });

const rules = ref<any>({
  parentId: [{ required: true, message: '上级部门不能为空', trigger: 'change' }],
  deptName: [{ required: true, message: '部门名称不能为空', trigger: 'blur' }],
  orderNum: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
});

const parentOptions = computed(() => [{ id: '0', name: '顶级部门', children: deptList.value }]);

const getTree = async () => {
  loading.value = true;
  try {
    const res: any = await deptTree();
    deptList.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleAdd = (row?: DeptTreeNode) => {
  reset();
  dialog.title = '新增部门';
  dialog.visible = true;
  if (row) {
    form.value.parentId = row.id;
  }
};

const handleUpdate = (row: DeptTreeNode) => {
  reset();
  dialog.title = '修改部门';
  dialog.visible = true;
  form.value = { ...form.value, deptId: row.id, parentId: row.parentId, deptName: row.name, orderNum: row.sort, status: row.status };
};

const handleToggleStatus = async (row: DeptTreeNode) => {
  const next = row.status === '0' ? '1' : '0';
  await proxy?.$modal.confirm(next === '1' ? `确认停用部门"${row.name}"吗？` : `确认启用部门"${row.name}"吗？`);
  await updateDeptStatus(row.id, next);
  proxy?.$modal.msgSuccess(next === '1' ? '已停用' : '已启用');
  await getTree();
};

const handleDelete = async (row: DeptTreeNode) => {
  await proxy?.$modal.confirm(`确认删除部门"${row.name}"吗？`);
  await delDept(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getTree();
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['deptFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.deptId) {
    await updateDept(form.value.deptId, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addDept(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getTree();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['deptFormRef'] as any)?.resetFields();
};

onMounted(() => {
  getTree();
});
</script>
