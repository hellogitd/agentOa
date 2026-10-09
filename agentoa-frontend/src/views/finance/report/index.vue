<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">费用报表</span></el-col>
          <el-col :span="1.5">
            <el-date-picker
              v-model="range"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
            />
          </el-col>
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Search" @click="getList">查询</el-button>
          </el-col>
          <el-col v-hasPermi="['fn:report:export']" :span="1.5">
            <el-button plain icon="Download" @click="handleExport">导出</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="rows">
        <el-table-column label="部门" align="center" prop="deptName" min-width="140" />
        <el-table-column label="费用类型" align="center" prop="expenseTypeName" min-width="120" />
        <el-table-column label="明细笔数" align="center" prop="itemCount" width="110" />
        <el-table-column label="金额合计（元）" align="center" prop="totalAmount" width="140" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="FinanceReport" lang="ts">
import { listExpenseReport, exportExpenseReport, ExpenseReportVO } from '@/api/finance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(true);
const rows = ref<ExpenseReportVO[]>([]);
const range = ref<string[]>([]);

const queryParams = () => ({
  startDate: range.value?.[0],
  endDate: range.value?.[1]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listExpenseReport(queryParams());
    rows.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleExport = async () => {
  const res: any = await exportExpenseReport(queryParams());
  const blob = new Blob([res]);
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = '费用统计.xlsx';
  link.click();
  URL.revokeObjectURL(link.href);
  proxy?.$modal.msgSuccess('导出成功');
};

onMounted(() => {
  getList();
});
</script>
