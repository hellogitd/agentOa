<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:employee:add']" type="primary" plain icon="Plus" @click="handleAdd">新增档案</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:employee:add']" type="success" plain icon="Finished" @click="lifecycleRef?.openOnboard()">入职登记</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:employee:import']" type="info" plain icon="Upload" @click="importDialogOpen = true">导入</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:employee:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="工号" prop="employeeNo">
            <el-input v-model="queryParams.employeeNo" placeholder="请输入工号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入姓名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="部门" prop="deptId">
            <el-tree-select
              v-model="queryParams.deptId"
              :data="deptOptions"
              :props="{ value: 'id', label: 'name', children: 'children' } as any"
              value-key="id"
              placeholder="请选择部门"
              check-strictly
              clearable
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="员工状态" clearable>
              <el-option v-for="dict in hr_employee_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="employeeList">
        <el-table-column label="工号" align="center" prop="employeeNo" width="130" />
        <el-table-column label="姓名" align="center" prop="name" width="100" />
        <el-table-column label="部门" align="center" prop="deptName" />
        <el-table-column label="岗位" align="center" prop="postName" />
        <el-table-column label="手机号" align="center" prop="phone" width="130" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="hr_employee_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="入职日期" align="center" prop="entryDate" width="110" />
        <el-table-column label="操作" width="300" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button v-hasPermi="['hr:employee:query']" link type="primary" icon="View" @click="handleDetail(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 'DRAFT'" content="办理入职" placement="top">
              <el-button
                v-hasPermi="['hr:employee:edit']"
                link
                type="success"
                icon="Finished"
                @click="handleStatus(scope.row, 'PROBATION')"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 'PROBATION'" content="转正" placement="top">
              <el-button
                v-hasPermi="['hr:employee:edit']"
                link
                type="success"
                icon="Select"
                @click="lifecycleRef?.openRegularize(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="['PROBATION', 'ACTIVE', 'LEAVE_PENDING'].includes(scope.row.status)" content="离职" placement="top">
              <el-button
                v-hasPermi="['hr:employee:edit']"
                link
                type="danger"
                icon="SwitchButton"
                @click="lifecycleRef?.openOffboard(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 'DRAFT'" content="修改" placement="top">
              <el-button v-hasPermi="['hr:employee:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 'DRAFT'" content="删除" placement="top">
              <el-button v-hasPermi="['hr:employee:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <employee-form-dialog
      v-model:visible="formDialogVisible"
      :title="formDialogTitle"
      :row="formRow"
      :dept-options="deptOptions"
      :post-options="postOptions"
      @success="getList"
    />
    <employee-lifecycle-dialogs ref="lifecycleRef" :dept-options="deptOptions" :post-options="postOptions" @success="getList" />
    <employee-import-dialog v-model:visible="importDialogOpen" @success="getList" />
    <employee-detail-drawer v-model:visible="detail.open" :name="detail.name" :data="detail.data" :changes="detail.changes" />
  </div>
</template>

<script setup name="HrEmployee" lang="ts">
import { saveAs } from 'file-saver';
import {
  listEmployee,
  delEmployee,
  getEmployee,
  getEmployeeChanges,
  updateEmployeeStatus,
  exportEmployees,
  deptTree,
  listPost,
  EmployeeVO,
  EmployeeForm,
  EmployeeChangeVO,
  DeptTreeNode
} from '@/api/hr';
import EmployeeFormDialog from './components/EmployeeFormDialog.vue';
import EmployeeLifecycleDialogs from './components/EmployeeLifecycleDialogs.vue';
import EmployeeImportDialog from './components/EmployeeImportDialog.vue';
import EmployeeDetailDrawer from './components/EmployeeDetailDrawer.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { hr_employee_status } = toRefs<any>(proxy?.useDict('hr_employee_status'));

const employeeList = ref<EmployeeVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const deptOptions = ref<DeptTreeNode[]>([]);
const postOptions = ref<any[]>([]);

const formDialogVisible = ref(false);
const formDialogTitle = ref('新增员工档案');
const formRow = ref<EmployeeForm | null>(null);
const importDialogOpen = ref(false);
const lifecycleRef = ref<InstanceType<typeof EmployeeLifecycleDialogs>>();

const detail = reactive<{ open: boolean; name: string; data: EmployeeVO | null; changes: EmployeeChangeVO[] }>({
  open: false,
  name: '',
  data: null,
  changes: []
});

const queryParams = ref<any>({ pageNum: 1, pageSize: 10, employeeNo: undefined, name: undefined, deptId: undefined, status: undefined });

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listEmployee(queryParams.value);
    employeeList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadOptions = async () => {
  const [deptRes, postRes]: any[] = await Promise.all([deptTree(), listPost({ pageNum: 1, pageSize: 100 })]);
  deptOptions.value = deptRes.data ?? [];
  postOptions.value = postRes.data?.records ?? [];
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 10, employeeNo: undefined, name: undefined, deptId: undefined, status: undefined };
  handleQuery();
};

const handleAdd = () => {
  formRow.value = null;
  formDialogTitle.value = '新增员工档案';
  formDialogVisible.value = true;
};

const handleUpdate = (row: EmployeeVO) => {
  formRow.value = { ...row } as EmployeeForm;
  formDialogTitle.value = '修改员工档案';
  formDialogVisible.value = true;
};

const handleDelete = async (row: EmployeeVO) => {
  await proxy?.$modal.confirm(`确认删除员工"${row.name}"的草稿档案吗？`);
  await delEmployee(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleStatus = async (row: EmployeeVO, status: string) => {
  await proxy?.$modal.confirm(`确认为"${row.name}"办理入职吗？`);
  await updateEmployeeStatus(row.id, { status });
  proxy?.$modal.msgSuccess('办理成功');
  await getList();
};

const handleDetail = async (row: EmployeeVO) => {
  const [info, changes]: any[] = await Promise.all([getEmployee(row.id), getEmployeeChanges(row.id)]);
  detail.data = info.data;
  detail.changes = changes.data ?? [];
  detail.name = row.name;
  detail.open = true;
};

const handleExport = async () => {
  const blob: any = await exportEmployees(queryParams.value);
  saveAs(new Blob([blob]), `员工花名册_${new Date().getTime()}.xlsx`);
};

onMounted(() => {
  loadOptions();
  getList();
});
</script>
