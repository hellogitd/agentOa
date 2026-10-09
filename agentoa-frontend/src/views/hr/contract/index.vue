<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:contract:add']" type="primary" plain icon="Plus" @click="handleAdd">新增合同</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:contract:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:contract:remove']" type="danger" plain icon="Delete" :disabled="single" @click="handleDelete()"
              >删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['hr:contract:query']" type="warning" plain icon="AlarmClock" @click="handleExpiring">续签提醒</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="员工ID" prop="employeeId">
            <el-input v-model="queryParams.employeeId" placeholder="请输入员工ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="合同编号" prop="contractNo">
            <el-input v-model="queryParams.contractNo" placeholder="请输入合同编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="合同类型" prop="contractType">
            <el-select v-model="queryParams.contractType" placeholder="合同类型" clearable>
              <el-option v-for="dict in hr_contract_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="合同状态" clearable>
              <el-option v-for="dict in hr_contract_status" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="contractList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="员工" align="center" prop="employeeName" width="110" />
        <el-table-column label="工号" align="center" prop="employeeNo" width="120" />
        <el-table-column label="合同编号" align="center" prop="contractNo" width="130" />
        <el-table-column label="合同类型" align="center" width="130">
          <template #default="scope">
            <dict-tag :options="hr_contract_type" :value="scope.row.contractType" />
          </template>
        </el-table-column>
        <el-table-column label="开始日期" align="center" prop="startDate" width="110" />
        <el-table-column label="结束日期" align="center" prop="endDate" width="110" />
        <el-table-column label="续签次数" align="center" prop="renewCount" width="90" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <dict-tag :options="hr_contract_status" :value="String(scope.row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="距到期" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.daysToExpire !== null && scope.row.daysToExpire <= 30" type="warning"> {{ scope.row.daysToExpire }} 天 </el-tag>
            <span v-else>{{ scope.row.daysToExpire ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['hr:contract:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['hr:contract:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="contractFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="员工ID" prop="employeeId">
          <el-input v-model="form.employeeId" placeholder="员工档案ID" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="合同编号" prop="contractNo">
          <el-input v-model="form.contractNo" placeholder="可空自动生成" maxlength="64" />
        </el-form-item>
        <el-form-item label="合同类型" prop="contractType">
          <el-select v-model="form.contractType" placeholder="请选择合同类型">
            <el-option v-for="dict in hr_contract_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择开始日期" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="无固定期限可空" />
        </el-form-item>
        <el-form-item label="签订日期" prop="signDate">
          <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择签订日期" />
        </el-form-item>
        <el-form-item label="续签次数" prop="renewCount">
          <el-input-number v-model="form.renewCount" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="合同状态">
            <el-option v-for="dict in hr_contract_status" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
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

    <el-dialog title="续签提醒（30 天内到期）" v-model="expiringDialog.visible" width="640px" append-to-body>
      <el-table border :data="expiringList">
        <el-table-column label="员工" align="center" prop="employeeName" />
        <el-table-column label="合同编号" align="center" prop="contractNo" />
        <el-table-column label="结束日期" align="center" prop="endDate" />
        <el-table-column label="距到期" align="center" prop="daysToExpire" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="HrContract" lang="ts">
import { listContract, addContract, updateContract, delContract, expiringContracts, ContractForm, ContractVO } from '@/api/hr';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { hr_contract_type, hr_contract_status } = toRefs<any>(proxy?.useDict('hr_contract_type', 'hr_contract_status'));

const contractList = ref<ContractVO[]>([]);
const expiringList = ref<ContractVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string>>([]);
const single = ref(true);
const total = ref(0);
const dialog = reactive<DialogOption>({ title: '', visible: false });
const expiringDialog = reactive({ visible: false });

const initForm: ContractForm = {
  employeeId: '',
  contractNo: undefined,
  contractType: 'FIXED_TERM',
  startDate: '',
  endDate: undefined,
  signDate: undefined,
  renewCount: 0,
  status: 1,
  remark: undefined
};
const data = reactive<{ form: ContractForm; queryParams: any }>({
  form: { ...initForm },
  queryParams: { pageNum: 1, pageSize: 10, employeeId: undefined, contractNo: undefined, contractType: undefined, status: undefined }
});
const { queryParams, form } = toRefs(data);

const rules = ref<any>({
  employeeId: [{ required: true, message: '员工不能为空', trigger: 'blur' }],
  contractType: [{ required: true, message: '合同类型不能为空', trigger: 'change' }],
  startDate: [{ required: true, message: '开始日期不能为空', trigger: 'change' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listContract(queryParams.value);
    contractList.value = res.data?.records ?? [];
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
  queryParams.value = { pageNum: 1, pageSize: 10, employeeId: undefined, contractNo: undefined, contractType: undefined, status: undefined };
  handleQuery();
};

const handleSelectionChange = (selection: ContractVO[]) => {
  ids.value = selection.map((item) => item.id);
  single.value = selection.length !== 1;
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['contractFormRef'] as any)?.resetFields();
};

const handleAdd = () => {
  reset();
  dialog.title = '新增合同';
  dialog.visible = true;
};

const handleUpdate = (row?: ContractVO) => {
  reset();
  const id = row?.id ?? ids.value[0];
  const target = contractList.value.find((item) => item.id === id);
  if (target) {
    form.value = { ...target } as ContractForm;
  }
  dialog.title = '修改合同';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['contractFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updateContract(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addContract(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const handleDelete = async (row?: ContractVO) => {
  const deleteIds = row ? [row.id] : ids.value;
  await proxy?.$modal.confirm(`确认删除选中的合同？`);
  await delContract(deleteIds[0]);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleExpiring = async () => {
  const res: any = await expiringContracts(30);
  expiringList.value = res.data ?? [];
  expiringDialog.visible = true;
};

onMounted(() => {
  getList();
});
</script>
