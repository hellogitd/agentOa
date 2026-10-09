<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">考勤看板</span>
          </el-col>
          <el-col :span="8">
            <el-date-picker
              v-model="range"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
            />
          </el-col>
          <el-col :span="6">
            <el-button type="primary" icon="Search" @click="load">查询</el-button>
            <el-button v-hasPermi="['rp:report:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
          </el-col>
        </el-row>
      </template>

      <stat-cards :cards="data?.cards ?? []" />

      <el-row :gutter="12">
        <el-col :md="12" :xs="24">
          <report-chart
            type="line"
            title="近 30 天出勤率趋势"
            :labels="(data?.trend30 ?? []).map((point) => point.date)"
            :series="[{ name: '出勤率', values: (data?.trend30 ?? []).map((point) => point.value) }]"
          />
        </el-col>
        <el-col :md="12" :xs="24">
          <report-chart
            type="bar"
            title="部门迟到 Top5（分钟）"
            :labels="chartLabels(data?.deptLateTop)"
            :series="chartSeries('迟到分钟', data?.deptLateTop)"
          />
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :md="12" :xs="24">
          <report-chart
            type="pie"
            title="请假类型分布"
            :labels="chartLabels(data?.leaveTypeDistribution)"
            :series="chartSeries('请假分钟', data?.leaveTypeDistribution)"
          />
        </el-col>
        <el-col :md="12" :xs="24">
          <report-chart
            type="bar"
            title="加班时长 Top5（分钟）"
            :labels="chartLabels(data?.overtimeTop)"
            :series="chartSeries('加班分钟', data?.overtimeTop)"
          />
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="mt8">
      <template #header>
        <span class="card-header-label">异常列表</span>
      </template>
      <el-table border :data="data?.exceptions ?? []">
        <el-table-column label="对象" prop="label" width="220" />
        <el-table-column label="说明" prop="detail" :show-overflow-tooltip="true" />
        <el-table-column label="时间" prop="time" width="160" />
      </el-table>
    </el-card>

    <el-card shadow="hover" class="mt8">
      <template #header>
        <span class="card-header-label">考勤明细（钻取）</span>
      </template>
      <el-table v-loading="detailLoading" border :data="detail.rows">
        <el-table-column
          v-for="header in detail.headers"
          :key="header.key"
          :label="header.label"
          :prop="header.key"
          align="center"
          :show-overflow-tooltip="true"
        />
      </el-table>
      <pagination
        v-show="detail.total > 0"
        v-model:page="detailQuery.pageNum"
        v-model:limit="detailQuery.pageSize"
        :total="detail.total"
        @pagination="loadDetails"
      />
    </el-card>

    <metric-docs class="mt8" :metrics="data?.metrics ?? []" />
  </div>
</template>

<script setup name="ReportAttendance" lang="ts">
import StatCards from '../components/StatCards.vue';
import ReportChart from '../components/ReportChart.vue';
import MetricDocs from '../components/MetricDocs.vue';
import { getAttendanceStatistics, listDetails, exportReport, AttendanceStatisticsVO, DetailTableVO, LabelValueVO } from '@/api/report';
import { saveAs } from 'file-saver';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const range = ref<string[]>([]);
const data = ref<AttendanceStatisticsVO>();
const detailLoading = ref(false);
const detail = ref<DetailTableVO>({ headers: [], rows: [], total: 0 });
const detailQuery = ref<any>({ pageNum: 1, pageSize: 10 });

const query = () => ({
  startDate: range.value?.[0],
  endDate: range.value?.[1]
});

const chartLabels = (rows?: LabelValueVO[]) => (rows ?? []).map((row) => row.label);
const chartSeries = (name: string, rows?: LabelValueVO[]) => [{ name, values: (rows ?? []).map((row) => row.value) }];

const load = async () => {
  const res: any = await getAttendanceStatistics(query());
  data.value = res.data;
  loadDetails();
};

const loadDetails = async () => {
  detailLoading.value = true;
  try {
    const res: any = await listDetails('attendance', { ...query(), ...detailQuery.value });
    detail.value = res.data ?? { headers: [], rows: [], total: 0 };
  } finally {
    detailLoading.value = false;
  }
};

const handleExport = async () => {
  const blob: any = await exportReport('attendance', query(), 'XLSX');
  saveAs(blob, `考勤看板-${new Date().toISOString().slice(0, 10)}.xlsx`);
  proxy?.$modal.msgSuccess('导出成功');
};

onMounted(load);
</script>

<style scoped>
.mt8 {
  margin-top: 12px;
}
</style>
