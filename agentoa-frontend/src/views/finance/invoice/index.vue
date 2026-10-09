<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">发票管理</span></el-col>
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd">录入发票</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="发票类型" align="center" width="150">
          <template #default="scope">
            <dict-tag :options="fn_invoice_type" :value="scope.row.invoiceType" />
          </template>
        </el-table-column>
        <el-table-column label="发票代码" align="center" prop="invoiceCode" width="120" />
        <el-table-column label="发票号码" align="center" prop="invoiceNo" width="150" />
        <el-table-column label="开票日期" align="center" prop="invoiceDate" width="110" />
        <el-table-column label="金额（元）" align="center" prop="amount" width="110" />
        <el-table-column label="占用状态" align="center" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.occupationStatus === 'FREE' ? 'success' : scope.row.occupationStatus === 'PAID' ? 'info' : 'warning'">
              {{ occupationText(scope.row.occupationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图片" align="center" width="90">
          <template #default="scope">
            <el-button v-if="scope.row.fileId" link type="primary" icon="Download" @click="handleDownload(scope.row)">下载</el-button>
            <span v-else class="text-xs text-gray-400">无</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-if="scope.row.occupationStatus === 'FREE'" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog title="录入发票" v-model="dialog.visible" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="发票类型" prop="invoiceType">
          <el-select v-model="form.invoiceType" placeholder="请选择" class="w-full">
            <el-option v-for="dict in fn_invoice_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票代码">
          <el-input v-model="form.invoiceCode" placeholder="无代码可留空" />
        </el-form-item>
        <el-form-item label="发票号码" prop="invoiceNo">
          <el-input v-model="form.invoiceNo" placeholder="请输入发票号码" />
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" />
        </el-form-item>
        <el-form-item label="金额（元）" prop="amount">
          <el-input v-model="form.amount" placeholder="两位小数，如 2500.00" />
        </el-form-item>
        <el-form-item label="发票图片">
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :limit="1"
            :on-success="handleUploaded"
            :on-remove="() => (form.fileId = undefined)"
          >
            <el-button plain icon="Upload">上传图片</el-button>
          </el-upload>
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

<script setup name="FinanceInvoice" lang="ts">
import { listInvoice, addInvoice, delInvoice, downloadInvoice, InvoiceVO, InvoiceForm } from '@/api/finance';
import { globalHeaders } from '@/utils/request';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { fn_invoice_type } = toRefs<any>(proxy?.useDict('fn_invoice_type'));

const uploadUrl = `${import.meta.env.VITE_APP_BASE_API}/api/v1/files`;
const uploadHeaders = globalHeaders();

const loading = ref(true);
const list = ref<InvoiceVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10 });

const dialog = reactive<any>({ visible: false });
const formRef = ref();
const form = ref<InvoiceForm>({ invoiceType: 'VAT_NORMAL', invoiceCode: '', invoiceNo: '', amount: '' });
const rules = {
  invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }],
  invoiceNo: [{ required: true, message: '请输入发票号码', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }]
};

const occupationText = (status: string) => (status === 'FREE' ? '空闲' : status === 'OCCUPIED' ? '审批占用' : '已付款');

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listInvoice(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  form.value = { invoiceType: 'VAT_NORMAL', invoiceCode: '', invoiceNo: '', amount: '' };
  dialog.visible = true;
};

const handleUploaded = (response: any) => {
  form.value.fileId = response?.data?.fileId;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  await addInvoice(form.value);
  proxy?.$modal.msgSuccess('录入成功');
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: InvoiceVO) => {
  await proxy?.$modal.confirm('确认删除该发票？');
  await delInvoice(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleDownload = async (row: InvoiceVO) => {
  const res: any = await downloadInvoice(row.id);
  const blob = new Blob([res]);
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = row.fileName || `invoice-${row.invoiceNo}`;
  link.click();
  URL.revokeObjectURL(link.href);
};

onMounted(() => {
  getList();
});
</script>
