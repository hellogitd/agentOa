<template>
  <el-dialog
    title="员工导入"
    :model-value="visible"
    width="680px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
    @closed="importReport = null"
  >
    <el-upload
      drag
      :action="uploadUrl"
      :headers="uploadHeaders"
      :show-file-list="false"
      accept=".xlsx,.xls"
      :on-success="handleImportResult"
      :on-error="handleImportError"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">将 Excel 文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">
          仅支持 xlsx/xls；导入按整批校验，存在任一错误行时不导入任何数据。
          <el-button link type="primary" @click="downloadTemplate">下载模板</el-button>
        </div>
      </template>
    </el-upload>
    <div v-if="importReport" class="mt-3">
      <el-alert
        :title="importReport.failedRows > 0 ? `存在 ${importReport.failedRows} 行错误，本批未导入` : `成功导入 ${importReport.successRows} 行`"
        :type="importReport.failedRows > 0 ? 'error' : 'success'"
        show-icon
      />
      <el-table :data="importReport.rows" border size="small" max-height="280" class="mt-2">
        <el-table-column label="行号" prop="rowNumber" width="70" align="center" />
        <el-table-column label="工号" prop="employeeNo" width="120" align="center" />
        <el-table-column label="姓名" prop="name" width="100" align="center" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.valid ? 'success' : 'danger'">{{ scope.row.valid ? '通过' : '错误' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误" prop="errors">
          <template #default="scope">{{ (scope.row.errors || []).join('；') }}</template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<script setup name="EmployeeImportDialog" lang="ts">
import { saveAs } from 'file-saver';
import { downloadImportTemplate, ImportReportVO } from '@/api/hr';
import { getToken } from '@/utils/auth';

defineProps({
  visible: { type: Boolean, default: false }
});
const emit = defineEmits(['update:visible', 'success']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const importReport = ref<ImportReportVO | null>(null);
const uploadUrl = `${import.meta.env.VITE_APP_BASE_API}/api/v1/hr/employees/import`;
const uploadHeaders = { Authorization: `Bearer ${getToken()}`, clientid: import.meta.env.VITE_APP_CLIENT_ID };

const handleImportResult = (response: any) => {
  const report: ImportReportVO = response?.data ?? response;
  importReport.value = report;
  if (report?.imported) {
    proxy?.$modal.msgSuccess(`成功导入 ${report.successRows} 行`);
    emit('success');
  }
};

const handleImportError = () => {
  proxy?.$modal.msgError('导入失败，请检查文件格式');
};

const downloadTemplate = async () => {
  const blob: any = await downloadImportTemplate();
  saveAs(new Blob([blob]), '员工导入模板.xlsx');
};
</script>
