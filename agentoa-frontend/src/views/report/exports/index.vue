<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">导出记录</span>
          </el-col>
          <el-col :span="6">
            <el-select v-model="query.reportType" placeholder="报表类型" clearable @change="getList">
              <el-option v-for="item in rp_report_type" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-select v-model="query.status" placeholder="任务状态" clearable @change="getList">
              <el-option v-for="item in rp_export_status" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
            <el-button v-hasPermi="['rp:report:export']" type="success" icon="Plus" @click="openCreate">新建导出</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="导出单号" prop="exportNo" width="200" />
        <el-table-column label="报表类型" width="110" align="center">
          <template #default="scope">
            <dict-tag :options="rp_report_type" :value="scope.row.reportType" />
          </template>
        </el-table-column>
        <el-table-column label="格式" width="90" align="center">
          <template #default="scope">
            <dict-tag :options="rp_export_format" :value="scope.row.format" />
          </template>
        </el-table-column>
        <el-table-column label="口径版本" prop="metricVersion" width="90" align="center" />
        <el-table-column label="行数" prop="rowCount" width="90" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="rp_export_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="过滤条件" prop="filters" :show-overflow-tooltip="true" />
        <el-table-column label="创建时间" prop="createTime" width="160" />
        <el-table-column label="操作" width="110" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.status === 'SUCCESS' && scope.row.fileId" link type="primary" @click="handleDownload(scope.row)"
              >下载</el-button
            >
            <el-button v-if="scope.row.status === 'FAILED'" link type="danger" @click="showError(scope.row)">原因</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog title="新建导出任务" v-model="dialogVisible" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="报表类型" prop="reportType">
          <el-select v-model="form.reportType" placeholder="选择报表类型">
            <el-option v-for="item in rp_report_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出格式" prop="format">
          <el-select v-model="form.format" placeholder="选择格式">
            <el-option v-for="item in rp_export_format" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="formRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ReportExports" lang="ts">
import { createExport, listExports, downloadExportFile, ExportVO } from '@/api/report';
import { saveAs } from 'file-saver';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { rp_report_type, rp_export_format, rp_export_status } = toRefs<any>(proxy?.useDict('rp_report_type', 'rp_export_format', 'rp_export_status'));

const loading = ref(false);
const list = ref<ExportVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10, reportType: undefined, status: undefined });

const dialogVisible = ref(false);
const submitting = ref(false);
const formRange = ref<string[]>([]);
const form = ref<any>({ reportType: 'hr', format: 'CSV' });

const rules = {
  reportType: [{ required: true, message: '请选择报表类型', trigger: 'change' }],
  format: [{ required: true, message: '请选择导出格式', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listExports(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  form.value = { reportType: 'hr', format: 'CSV' };
  formRange.value = [];
  dialogVisible.value = true;
};

const submitForm = async () => {
  submitting.value = true;
  try {
    await createExport({
      reportType: form.value.reportType,
      format: form.value.format,
      startDate: formRange.value?.[0],
      endDate: formRange.value?.[1]
    });
    dialogVisible.value = false;
    proxy?.$modal.msgSuccess('导出任务已创建');
    getList();
  } finally {
    submitting.value = false;
  }
};

const handleDownload = async (row: ExportVO) => {
  const blob: any = await downloadExportFile(row.fileId);
  saveAs(blob, `${row.exportNo}.${String(row.format).toLowerCase()}`);
};

const showError = (row: ExportVO) => {
  proxy?.$modal.msgWarning(row.errorMessage || '导出失败');
};

onMounted(getList);
</script>
