<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">我的报销</span></el-col>
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreate">新建报销</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="标题" align="center" prop="title" min-width="160" />
        <el-table-column label="总额（元）" align="center" width="120">
          <template #default="scope">{{ parseForm(scope.row).totalAmount || '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="fn_reimburse_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button v-if="[1, 4, 7].includes(scope.row.status)" link type="primary" icon="Edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button v-if="[1, 4, 7].includes(scope.row.status)" link type="primary" icon="Promotion" @click="handleSubmit(scope.row)"
              >提交</el-button
            >
            <el-button v-if="[1, 4, 7].includes(scope.row.status)" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报销类型" prop="reimburseType">
              <el-select v-model="form.reimburseType" placeholder="请选择" class="w-full">
                <el-option v-for="item in reimburseTypes" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="支付方式" prop="payMethod">
              <el-select v-model="form.payMethod" placeholder="请选择" class="w-full">
                <el-option v-for="dict in fn_payment_method" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="费用明细">
          <el-table :data="form.details" border size="small">
            <el-table-column label="费用类型" min-width="120">
              <template #default="scope">
                <el-select v-model="scope.row.expenseTypeId" placeholder="费用类型" clearable class="w-full">
                  <el-option v-for="type in expenseTypes" :key="type.id" :label="type.name" :value="type.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="发生日期" width="150">
              <template #default="scope">
                <el-date-picker v-model="scope.row.occurDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
              </template>
            </el-table-column>
            <el-table-column label="金额（元）" width="130">
              <template #default="scope">
                <el-input v-model="scope.row.amount" placeholder="0.00" />
              </template>
            </el-table-column>
            <el-table-column label="发票" min-width="150">
              <template #default="scope">
                <el-select v-model="scope.row.invoiceId" placeholder="本人空闲发票" clearable class="w-full">
                  <el-option
                    v-for="invoice in freeInvoices"
                    :key="invoice.id"
                    :label="`${invoice.invoiceNo}（${invoice.amount}）`"
                    :value="invoice.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="140">
              <template #default="scope">
                <el-input v-model="scope.row.description" placeholder="说明" />
              </template>
            </el-table-column>
            <el-table-column label="" width="60">
              <template #default="scope">
                <el-button link type="danger" icon="Delete" @click="form.details.splice(scope.$index, 1)" />
              </template>
            </el-table-column>
          </el-table>
          <el-button class="mt-2" plain icon="Plus" @click="addDetail">添加明细</el-button>
        </el-form-item>

        <el-form-item label="合计（元）">
          <el-input :model-value="totalAmount" readonly />
        </el-form-item>
        <el-form-item label="事由" prop="reason">
          <div class="field-ai-bar">
            <el-button v-hasPermi="['ai:copilot:use']" size="small" type="primary" plain icon="MagicStick" @click="openAiSuggest">AI 建议</el-button>
          </div>
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入事由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="报销详情" size="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="fn_reimburse_status" :value="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="总额">{{ parseForm(detail).totalAmount || '0.00' }} 元</el-descriptions-item>
        <el-descriptions-item label="明细">
          <div v-for="(row, index) in parseForm(detail).details || []" :key="index" class="text-xs mb-1">
            {{ row.expenseType || row.expenseTypeId || '费用' }} / {{ row.occurDate || '-' }} / {{ row.amount }} 元
            <span v-if="row.invoiceNo || row.invoiceId"> / 发票 {{ row.invoiceNo || row.invoiceId }}</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- M4-04 表单填写建议：只给建议，提交仍走原表单校验（docs/21 §6.1） -->
    <AiCopilotDrawer
      ref="aiDrawer"
      scene="form-suggest"
      title="AI 建议"
      action-label="采纳到事由"
      instruction-placeholder="如：下周三去上海出差一天，报销高铁票"
      @apply="applyAiSuggest"
    />
  </div>
</template>

<script setup name="FinanceReimburse" lang="ts">
import {
  listReimburses,
  getReimburse,
  createReimburse,
  updateReimburse,
  deleteReimburse,
  submitReimburse,
  listExpenseType,
  listInvoice,
  BusinessRequestVO,
  ReimburseForm,
  ExpenseTypeVO,
  InvoiceVO
} from '@/api/finance';
import AiCopilotDrawer from '@/components/AiCopilotDrawer/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const aiDrawer = ref<InstanceType<typeof AiCopilotDrawer>>();

/** M4-04：按自然语言给出填写建议，采纳后仍走原表单校验 */
const openAiSuggest = () => {
  aiDrawer.value?.open({ instruction: '根据我的描述给出报销单填写建议（类型/事由/明细）' });
};
const applyAiSuggest = (text: string) => {
  form.value.reason = text;
  proxy?.$modal.msgSuccess('建议已采纳到事由，请人工核对后再提交');
};
const { fn_reimburse_status, fn_payment_method } = toRefs<any>(proxy?.useDict('fn_reimburse_status', 'fn_payment_method'));

const loading = ref(true);
const list = ref<BusinessRequestVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10 });
const expenseTypes = ref<ExpenseTypeVO[]>([]);
const freeInvoices = ref<InvoiceVO[]>([]);
const reimburseTypes = [
  { label: '日常费用', value: 'expense' },
  { label: '差旅费用', value: 'travel' },
  { label: '餐费', value: 'meal' },
  { label: '办公用品', value: 'office' },
  { label: '其他', value: 'other' }
];

const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<ReimburseForm>({ details: [{}] as any });
const rules = {
  reimburseType: [{ required: true, message: '请选择报销类型', trigger: 'change' }],
  payMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }]
};

const detailVisible = ref(false);
const detail = ref<any>({});

const totalAmount = computed(() => {
  const sum = (form.value.details || []).reduce((acc, row: any) => acc + (parseFloat(row.amount || '0') || 0), 0);
  return sum.toFixed(2);
});

const parseForm = (row: any) => {
  try {
    return row?.formData ? JSON.parse(row.formData) : {};
  } catch (e) {
    return {};
  }
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listReimburses(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadOptions = async () => {
  const types: any = await listExpenseType();
  expenseTypes.value = flattenTypes(types.data ?? []);
  const invoices: any = await listInvoice({ pageNum: 1, pageSize: 100 });
  freeInvoices.value = (invoices.data?.records ?? []).filter((item: InvoiceVO) => item.occupationStatus === 'FREE');
};

const flattenTypes = (nodes: ExpenseTypeVO[]): ExpenseTypeVO[] => {
  const result: ExpenseTypeVO[] = [];
  const walk = (items: ExpenseTypeVO[]) => {
    items.forEach((item) => {
      result.push(item);
      if (item.children && item.children.length) walk(item.children);
    });
  };
  walk(nodes);
  return result;
};

const resetForm = () => {
  form.value = {
    reimburseType: 'expense',
    payMethod: 'BANK_TRANSFER',
    details: [{ amount: '' }] as any,
    reason: undefined
  } as ReimburseForm;
};

const handleCreate = () => {
  resetForm();
  dialog.title = '新建报销';
  dialog.id = undefined;
  dialog.visible = true;
  loadOptions();
};

const handleEdit = async (row: BusinessRequestVO) => {
  const res: any = await getReimburse(row.id);
  const parsed = parseForm(res.data);
  resetForm();
  form.value = {
    reimburseType: parsed.reimburseType,
    payMethod: parsed.payMethod,
    reason: parsed.reason,
    details: (parsed.details || [{ amount: '' }]).map((item: any) => ({ ...item })),
    lockVersion: res.data.lockVersion
  } as ReimburseForm;
  dialog.title = '编辑报销';
  dialog.id = row.id;
  dialog.visible = true;
  loadOptions();
};

const handleDetail = async (row: BusinessRequestVO) => {
  const res: any = await getReimburse(row.id);
  detail.value = res.data;
  detailVisible.value = true;
};

const addDetail = () => {
  form.value.details.push({ amount: '' } as any);
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: ReimburseForm = {
    ...form.value,
    totalAmount: totalAmount.value,
    details: (form.value.details || [])
      .filter((row: any) => row.amount)
      .map((row: any) => ({
        expenseType: row.expenseType,
        expenseTypeId: row.expenseTypeId,
        occurDate: row.occurDate,
        amount: row.amount,
        invoiceId: row.invoiceId,
        invoiceNo: row.invoiceNo,
        description: row.description
      }))
  };
  if (!payload.details.length) {
    proxy?.$modal.msgError('请至少填写一条明细金额');
    return;
  }
  if (dialog.id) {
    await updateReimburse(dialog.id, payload);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await createReimburse(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleSubmit = async (row: BusinessRequestVO) => {
  await proxy?.$modal.confirm('确认提交该报销单发起审批？');
  await submitReimburse(row.id, row.lockVersion);
  proxy?.$modal.msgSuccess('已提交审批');
  await getList();
};

const handleDelete = async (row: BusinessRequestVO) => {
  await proxy?.$modal.confirm('确认删除该报销草稿？');
  await deleteReimburse(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.field-ai-bar {
  margin-bottom: 8px;
}
</style>
