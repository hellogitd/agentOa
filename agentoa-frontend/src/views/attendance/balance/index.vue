<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">我的余额</span>
          </el-col>
          <el-col :span="1.5">
            <el-date-picker v-model="year" type="year" value-format="YYYY" placeholder="选择年度" class="year-picker" @change="handleYearChange" />
          </el-col>
          <el-col v-hasPermi="['at:leave:query']" :span="1.5">
            <el-input
              v-model="otherUserId"
              placeholder="查询他人用户ID"
              clearable
              class="other-user-input"
              @keyup.enter="handleQueryOther"
              @clear="handleQueryOther"
            >
              <template #append>
                <el-button icon="Search" @click="handleQueryOther">查询</el-button>
              </template>
            </el-input>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:leave:grant']" type="primary" plain icon="Plus" @click="handleGrant">发放额度</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="balanceList">
        <el-table-column label="假种" align="center" prop="typeName" />
        <el-table-column label="额度类型" align="center" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.quotaLimited ? 'warning' : 'info'">{{ scope.row.quotaLimited ? '限额' : '不限额' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总额度(分钟)" align="center" prop="totalMinutes" width="120">
          <template #default="scope">{{ formatMinutes(scope.row.totalMinutes) }}</template>
        </el-table-column>
        <el-table-column label="冻结(分钟)" align="center" prop="frozenMinutes" width="110">
          <template #default="scope">{{ formatMinutes(scope.row.frozenMinutes) }}</template>
        </el-table-column>
        <el-table-column label="已用(分钟)" align="center" prop="usedMinutes" width="110">
          <template #default="scope">{{ formatMinutes(scope.row.usedMinutes) }}</template>
        </el-table-column>
        <el-table-column label="可用(分钟)" align="center" prop="availableMinutes" width="110">
          <template #default="scope">{{ formatMinutes(scope.row.availableMinutes) }}</template>
        </el-table-column>
        <el-table-column label="过期日期" align="center" prop="expireDate" width="120">
          <template #default="scope">{{ scope.row.expireDate || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="hover" class="mt-2">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">额度批次</span>
          </el-col>
          <el-col :span="1.5">
            <el-select v-model="batchQuery.leaveType" placeholder="假种" clearable class="leave-type-select" @change="handleBatchQuery">
              <el-option v-for="dict in at_leave_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-col>
          <el-col v-hasPermi="['at:leave:grant']" :span="1.5">
            <el-button plain icon="Refresh" @click="handleExpireScan">过期扫描</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="batchLoading" border :data="batchList">
        <el-table-column label="批次号" align="center" prop="batchNo" width="180" />
        <el-table-column label="假种" align="center" prop="leaveType" width="110" />
        <el-table-column label="额度(分钟)" align="center" prop="grantMinutes" width="110">
          <template #default="scope">{{ formatMinutes(scope.row.grantMinutes) }}</template>
        </el-table-column>
        <el-table-column label="冻结" align="center" prop="frozenMinutes" width="90">
          <template #default="scope">{{ formatMinutes(scope.row.frozenMinutes) }}</template>
        </el-table-column>
        <el-table-column label="已用" align="center" prop="usedMinutes" width="90">
          <template #default="scope">{{ formatMinutes(scope.row.usedMinutes) }}</template>
        </el-table-column>
        <el-table-column label="已过期" align="center" prop="expiredMinutes" width="90">
          <template #default="scope">{{ formatMinutes(scope.row.expiredMinutes) }}</template>
        </el-table-column>
        <el-table-column label="剩余" align="center" prop="availableMinutes" width="90">
          <template #default="scope">{{ formatMinutes(scope.row.availableMinutes) }}</template>
        </el-table-column>
        <el-table-column label="有效期至" align="center" width="120">
          <template #default="scope">{{ scope.row.expireDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : scope.row.status === 2 ? 'info' : 'warning'">
              {{ scope.row.status === 1 ? '有效' : scope.row.status === 2 ? '已过期' : '已用尽' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="hover" class="mt-2">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">额度流水</span>
          </el-col>
          <el-col :span="1.5">
            <el-select v-model="ledgerQuery.leaveType" placeholder="假种" clearable class="leave-type-select" @change="handleLedgerQuery">
              <el-option v-for="dict in at_leave_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="ledgerLoading" border :data="ledgerList">
        <el-table-column label="时间" align="center" prop="createTime" width="160" />
        <el-table-column label="动作" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="at_leave_action" :value="scope.row.action" />
          </template>
        </el-table-column>
        <el-table-column label="业务类型" align="center" prop="businessType" width="120" />
        <el-table-column label="单据号" align="center" prop="submissionNo" width="100" />
        <el-table-column label="请假分钟" align="center" prop="leaveMinutes" width="100">
          <template #default="scope">{{ formatMinutes(scope.row.leaveMinutes) }}</template>
        </el-table-column>
        <el-table-column label="总额变动" align="center" prop="totalDelta" width="100">
          <template #default="scope">{{ formatMinutes(scope.row.totalDelta) }}</template>
        </el-table-column>
        <el-table-column label="冻结变动" align="center" prop="frozenDelta" width="100">
          <template #default="scope">{{ formatMinutes(scope.row.frozenDelta) }}</template>
        </el-table-column>
        <el-table-column label="已用变动" align="center" prop="usedDelta" width="100">
          <template #default="scope">{{ formatMinutes(scope.row.usedDelta) }}</template>
        </el-table-column>
        <el-table-column label="操作人" align="center" prop="operatorName" />
      </el-table>

      <pagination
        v-show="ledgerTotal > 0"
        v-model:page="ledgerQuery.pageNum"
        v-model:limit="ledgerQuery.pageSize"
        :total="ledgerTotal"
        @pagination="getLedgerList"
      />
    </el-card>

    <el-dialog title="发放额度" v-model="grantDialog.visible" width="480px" append-to-body>
      <el-form ref="grantFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户ID" prop="userId">
          <el-input-number v-model="form.userId" :min="1" controls-position="right" class="w-full" />
        </el-form-item>
        <el-form-item label="年度" prop="year">
          <el-date-picker v-model="form.year" type="year" value-format="YYYY" placeholder="请选择年度" />
        </el-form-item>
        <el-form-item label="假种" prop="leaveType">
          <el-select v-model="form.leaveType" placeholder="请选择假种" clearable>
            <el-option v-for="dict in at_leave_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="额度分钟" prop="minutes">
          <el-input-number v-model="form.minutes" controls-position="right" class="w-full" />
          <div class="text-xs text-gray-400">正数发放、负数回收调整</div>
        </el-form-item>
        <el-form-item label="有效起始日" prop="validFrom">
          <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="默认当天" />
        </el-form-item>
        <el-form-item label="有效截止日" prop="expireDate">
          <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="调休默认+3个月、年假默认当年12-31" />
          <div class="text-xs text-gray-400">调休按批次滚动过期（AT-06）；留空按默认有效期建批次</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitGrant">确 定</el-button>
          <el-button @click="grantDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AttendanceBalance" lang="ts">
import {
  listMyBalance,
  listUserBalance,
  listLedger,
  listBatches,
  expireBatches,
  grantBalance,
  BalanceVO,
  BatchVO,
  LedgerVO,
  BalanceGrantForm
} from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { at_leave_type, at_leave_action } = toRefs<any>(proxy?.useDict('at_leave_type', 'at_leave_action'));

const balanceList = ref<BalanceVO[]>([]);
const ledgerList = ref<LedgerVO[]>([]);
const batchList = ref<BatchVO[]>([]);
const loading = ref(true);
const ledgerLoading = ref(true);
const batchLoading = ref(true);
const ledgerTotal = ref(0);
const year = ref<string>(String(new Date().getFullYear()));
const otherUserId = ref<string>('');
const grantDialog = reactive<DialogOption>({ title: '发放额度', visible: false });

const ledgerQuery = ref<any>({ pageNum: 1, pageSize: 10, userId: undefined, year: undefined, leaveType: undefined });
const batchQuery = ref<any>({ leaveType: undefined });

const initForm: BalanceGrantForm = {
  userId: undefined as any,
  year: undefined as any,
  leaveType: '',
  minutes: 0,
  validFrom: undefined,
  expireDate: undefined
};
const form = ref<BalanceGrantForm>({ ...initForm });

const rules = ref<any>({
  userId: [{ required: true, message: '用户ID不能为空', trigger: 'blur' }],
  year: [{ required: true, message: '年度不能为空', trigger: 'change' }],
  leaveType: [{ required: true, message: '假种不能为空', trigger: 'change' }],
  minutes: [{ required: true, message: '额度分钟不能为空', trigger: 'blur' }]
});

const formatMinutes = (value?: number | null) => (value === undefined || value === null ? '-' : value);

const activeYear = () => (year.value ? Number(year.value) : undefined);

const getBalanceList = async () => {
  loading.value = true;
  try {
    const res: any = otherUserId.value ? await listUserBalance(otherUserId.value, activeYear()) : await listMyBalance(activeYear());
    balanceList.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const getLedgerList = async () => {
  ledgerLoading.value = true;
  try {
    ledgerQuery.value.userId = otherUserId.value || undefined;
    ledgerQuery.value.year = activeYear();
    const res: any = await listLedger(ledgerQuery.value);
    ledgerList.value = res.data?.records ?? [];
    ledgerTotal.value = res.data?.total ?? 0;
  } finally {
    ledgerLoading.value = false;
  }
};

const getBatchList = async () => {
  batchLoading.value = true;
  try {
    const res: any = await listBatches({
      userId: otherUserId.value || undefined,
      year: activeYear(),
      leaveType: batchQuery.value.leaveType || undefined
    });
    batchList.value = res.data ?? [];
  } finally {
    batchLoading.value = false;
  }
};

const handleYearChange = () => {
  getBalanceList();
  getBatchList();
  handleLedgerQuery();
};

const handleLedgerQuery = () => {
  ledgerQuery.value.pageNum = 1;
  getLedgerList();
};

const handleBatchQuery = () => {
  getBatchList();
};

const handleQueryOther = () => {
  getBalanceList();
  getBatchList();
  handleLedgerQuery();
};

const handleExpireScan = async () => {
  const res: any = await expireBatches();
  proxy?.$modal.msgSuccess(`过期扫描完成：${res.data ?? 0} 个批次`);
  await Promise.all([getBalanceList(), getBatchList(), getLedgerList()]);
};

const handleGrant = () => {
  form.value = { ...initForm, year: year.value ? Number(year.value) : (new Date().getFullYear() as any) };
  grantDialog.visible = true;
};

const submitGrant = async () => {
  const valid = await (proxy?.$refs['grantFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  await grantBalance(form.value);
  proxy?.$modal.msgSuccess('发放成功');
  grantDialog.visible = false;
  await Promise.all([getBalanceList(), getBatchList(), getLedgerList()]);
};

onMounted(() => {
  getBalanceList();
  getBatchList();
  getLedgerList();
});
</script>

<style scoped>
.card-header-label {
  font-weight: 600;
}
.year-picker {
  width: 110px;
}
.other-user-input {
  width: 220px;
}
.leave-type-select {
  width: 160px;
}
</style>
