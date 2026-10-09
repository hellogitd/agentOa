<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">待付款报销单</span></el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getPending">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="pendingLoading" border :data="pendingList">
        <el-table-column label="报销单号" align="center" prop="reimburseNo" width="150" />
        <el-table-column label="申请人" align="center" prop="applicantName" width="110" />
        <el-table-column label="部门" align="center" prop="deptName" width="120" />
        <el-table-column label="金额（元）" align="center" prop="totalAmount" width="110" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="fn_reimburse_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="130" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['fn:payment:add']" link type="primary" icon="Money" @click="handlePay(scope.row)">登记付款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="pendingTotal > 0"
        v-model:page="pendingQuery.pageNum"
        v-model:limit="pendingQuery.pageSize"
        :total="pendingTotal"
        @pagination="getPending"
      />
    </el-card>

    <el-card shadow="hover" class="mt-2">
      <template #header><span class="card-header-label">付款记录</span></template>
      <el-table v-loading="paymentLoading" border :data="paymentList">
        <el-table-column label="付款单号" align="center" prop="paymentNo" width="160" />
        <el-table-column label="报销单号" align="center" prop="reimburseNo" width="150" />
        <el-table-column label="申请人" align="center" prop="applicantName" width="110" />
        <el-table-column label="金额（元）" align="center" prop="amount" width="110" />
        <el-table-column label="付款日期" align="center" prop="payDate" width="110" />
        <el-table-column label="付款方式" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="fn_payment_method" :value="scope.row.paymentMethod" />
          </template>
        </el-table-column>
        <el-table-column label="凭证号" align="center" prop="voucherNo" width="150" />
        <el-table-column label="登记人" align="center" prop="operatorName" width="110" />
      </el-table>
      <pagination
        v-show="paymentTotal > 0"
        v-model:page="paymentQuery.pageNum"
        v-model:limit="paymentQuery.pageSize"
        :total="paymentTotal"
        @pagination="getPayments"
      />
    </el-card>

    <el-dialog title="登记付款" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="报销单">
          <el-input :model-value="current?.reimburseNo" readonly />
        </el-form-item>
        <el-form-item label="应付金额">
          <el-input :model-value="current?.totalAmount" readonly />
        </el-form-item>
        <el-form-item label="付款金额" prop="amount">
          <el-input v-model="form.amount" placeholder="必须等于已审批金额" />
        </el-form-item>
        <el-form-item label="付款日期" prop="payDate">
          <el-date-picker v-model="form.payDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
        </el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod">
          <el-select v-model="form.paymentMethod" placeholder="请选择" class="w-full">
            <el-option v-for="dict in fn_payment_method" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="凭证号">
          <el-input v-model="form.voucherNo" placeholder="付款凭证号" />
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

<script setup name="FinancePayment" lang="ts">
import { listPendingClaims, payReimburse, listPayment, PendingClaimVO, PaymentVO, PaymentForm } from '@/api/finance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { fn_reimburse_status, fn_payment_method } = toRefs<any>(proxy?.useDict('fn_reimburse_status', 'fn_payment_method'));

const pendingLoading = ref(true);
const pendingList = ref<PendingClaimVO[]>([]);
const pendingTotal = ref(0);
const pendingQuery = ref<any>({ pageNum: 1, pageSize: 10, status: 5 });

const paymentLoading = ref(true);
const paymentList = ref<PaymentVO[]>([]);
const paymentTotal = ref(0);
const paymentQuery = ref<any>({ pageNum: 1, pageSize: 10 });

const dialog = reactive<any>({ visible: false });
const current = ref<PendingClaimVO | null>(null);
const formRef = ref();
const form = ref<PaymentForm>({ amount: '', payDate: '', paymentMethod: 'BANK_TRANSFER' });
const rules = {
  amount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }],
  payDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }]
};

const getPending = async () => {
  pendingLoading.value = true;
  try {
    const res: any = await listPendingClaims(pendingQuery.value);
    pendingList.value = res.data?.records ?? [];
    pendingTotal.value = res.data?.total ?? 0;
  } finally {
    pendingLoading.value = false;
  }
};

const getPayments = async () => {
  paymentLoading.value = true;
  try {
    const res: any = await listPayment(paymentQuery.value);
    paymentList.value = res.data?.records ?? [];
    paymentTotal.value = res.data?.total ?? 0;
  } finally {
    paymentLoading.value = false;
  }
};

const handlePay = (row: PendingClaimVO) => {
  current.value = row;
  form.value = { amount: row.totalAmount, payDate: '', paymentMethod: 'BANK_TRANSFER' };
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid || !current.value) return;
  await payReimburse(current.value.id, form.value);
  proxy?.$modal.msgSuccess('付款登记成功');
  dialog.visible = false;
  await getPending();
  await getPayments();
};

onMounted(() => {
  getPending();
  getPayments();
});
</script>
