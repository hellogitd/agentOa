<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:change:add']" type="primary" plain icon="Plus" @click="handleAdd">发起异动</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:change:add']" type="success" plain icon="RefreshLeft" @click="handleApplyDue">执行到期异动</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="员工ID" prop="employeeId">
            <el-input v-model="queryParams.employeeId" placeholder="请输入员工ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="异动类型" prop="changeType">
            <el-select v-model="queryParams.changeType" placeholder="异动类型" clearable>
              <el-option v-for="dict in hr_change_type" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否生效" prop="applied">
            <el-select v-model="queryParams.applied" placeholder="生效状态" clearable>
              <el-option label="已生效" :value="1" />
              <el-option label="待生效" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="changeList">
        <el-table-column label="员工" align="center" prop="employeeName" width="110" />
        <el-table-column label="异动类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="hr_change_type" :value="String(scope.row.changeType)" />
          </template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="110" />
        <el-table-column label="原部门→新部门" align="center" width="160">
          <template #default="scope">
            <span>{{ scope.row.oldDeptId || '-' }} → {{ scope.row.newDeptId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原岗位→新岗位" align="center" width="160">
          <template #default="scope">
            <span>{{ scope.row.oldPostId || '-' }} → {{ scope.row.newPostId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原职级→新职级" align="center" width="150">
          <template #default="scope">
            <span>{{ scope.row.oldPositionLevel || '-' }} → {{ scope.row.newPositionLevel || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原薪资→新薪资" align="center" width="170">
          <template #default="scope">
            <span>{{ scope.row.oldSalary || '-' }} → {{ scope.row.newSalary || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.applied === 1 ? 'success' : 'warning'">
              {{ scope.row.applied === 1 ? '已生效' : '待生效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原因" align="center" prop="reason" :show-overflow-tooltip="true" />
        <el-table-column label="发起时间" align="center" prop="createTime" width="160" />
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="changeFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="员工ID" prop="employeeId">
          <el-input v-model="form.employeeId" placeholder="员工档案ID" />
        </el-form-item>
        <el-form-item label="异动类型" prop="changeType">
          <el-select v-model="form.changeType" placeholder="请选择异动类型">
            <el-option v-for="dict in hr_change_type" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择生效日期" />
        </el-form-item>
        <el-form-item label="新部门" prop="newDeptId">
          <el-tree-select
            v-model="form.newDeptId"
            :data="deptOptions"
            :props="{ value: 'id', label: 'name', children: 'children' } as any"
            value-key="id"
            placeholder="不调整可空"
            check-strictly
            clearable
          />
        </el-form-item>
        <el-form-item label="新岗位" prop="newPostId">
          <el-select v-model="form.newPostId" placeholder="不调整可空" clearable>
            <el-option v-for="item in postOptions" :key="item.id" :label="item.positionName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="新职级" prop="newPositionLevel">
          <el-input v-model="form.newPositionLevel" placeholder="如 P7" maxlength="32" />
        </el-form-item>
        <el-form-item label="新薪资" prop="newSalary">
          <el-input v-model="form.newSalary" placeholder="如 18000.00" maxlength="16" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" placeholder="请输入异动原因" maxlength="255" />
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

<script setup name="HrChange" lang="ts">
import { listChange, addChange, applyDueChanges, deptTree, listPost, ChangeForm, ChangeVO, DeptTreeNode, PostVO } from '@/api/hr';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { hr_change_type } = toRefs<any>(proxy?.useDict('hr_change_type'));

const changeList = ref<ChangeVO[]>([]);
const deptOptions = ref<DeptTreeNode[]>([]);
const postOptions = ref<PostVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const dialog = reactive<DialogOption>({ title: '', visible: false });

const initForm: ChangeForm = {
  employeeId: '',
  changeType: 3,
  effectiveDate: '',
  newDeptId: undefined,
  newPostId: undefined,
  newPositionLevel: undefined,
  newSalary: undefined,
  reason: undefined
};
const data = reactive<{ form: ChangeForm; queryParams: any }>({
  form: { ...initForm },
  queryParams: { pageNum: 1, pageSize: 10, employeeId: undefined, changeType: undefined, applied: undefined }
});
const { queryParams, form } = toRefs(data);

const rules = ref<any>({
  employeeId: [{ required: true, message: '员工不能为空', trigger: 'blur' }],
  changeType: [{ required: true, message: '异动类型不能为空', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '生效日期不能为空', trigger: 'change' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listChange(queryParams.value);
    changeList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadOptions = async () => {
  const [deptRes, postRes]: any = await Promise.all([deptTree(), listPost({ pageNum: 1, pageSize: 100 })]);
  deptOptions.value = deptRes.data ?? [];
  postOptions.value = postRes.data?.records ?? [];
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 10, employeeId: undefined, changeType: undefined, applied: undefined };
  handleQuery();
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['changeFormRef'] as any)?.resetFields();
};

const handleAdd = () => {
  reset();
  dialog.title = '发起异动';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['changeFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  await addChange(form.value);
  proxy?.$modal.msgSuccess('发起成功');
  dialog.visible = false;
  await getList();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const handleApplyDue = async () => {
  const res: any = await applyDueChanges();
  proxy?.$modal.msgSuccess(`本次生效 ${res.data ?? 0} 条`);
  await getList();
};

onMounted(() => {
  getList();
  loadOptions();
});
</script>
