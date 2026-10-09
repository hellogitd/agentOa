<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">财务看板</span>
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
            <el-button v-hasPermi="['ai:copilot:use']" plain icon="MagicStick" @click="openAiInsight">AI 解读</el-button>
          </el-col>
        </el-row>
      </template>

      <stat-cards :cards="data?.cards ?? []" />

      <el-row :gutter="12">
        <el-col :md="12" :xs="24">
          <report-chart
            type="line"
            title="近 12 个月报销趋势（元）"
            :labels="(data?.trend12m ?? []).map((point) => point.date)"
            :series="[
              { name: '申请额', values: (data?.trend12m ?? []).map((point) => point.value) },
              { name: '批准额', values: (data?.trend12m ?? []).map((point) => point.value2) }
            ]"
          />
        </el-col>
        <el-col :md="12" :xs="24">
          <report-chart
            type="pie"
            title="费用类型分布"
            :labels="chartLabels(data?.typeDistribution)"
            :series="chartSeries('金额', data?.typeDistribution)"
          />
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :md="12" :xs="24">
          <report-chart
            type="bar"
            title="部门报销排名 Top5（元）"
            :labels="chartLabels(data?.deptTop)"
            :series="chartSeries('金额', data?.deptTop)"
          />
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="mt8">
      <template #header>
        <span class="card-header-label">报销明细（钻取）</span>
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

    <!-- M4-02 报表解读：只读指标摘要给结论，不改业务数据（docs/21 §6.1） -->
    <AiCopilotDrawer
      ref="aiDrawer"
      scene="report-insight"
      title="AI 解读"
      instruction-placeholder="如：重点看异常项并给出改进建议"
      @apply="copyAiInsight"
    />
  </div>
</template>

<script setup name="ReportFinance" lang="ts">
import StatCards from '../components/StatCards.vue';
import ReportChart from '../components/ReportChart.vue';
import MetricDocs from '../components/MetricDocs.vue';
import { getFinanceStatistics, listDetails, exportReport, DetailTableVO, FinanceStatisticsVO, LabelValueVO } from '@/api/report';
import { saveAs } from 'file-saver';
import AiCopilotDrawer from '@/components/AiCopilotDrawer/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const aiDrawer = ref<InstanceType<typeof AiCopilotDrawer>>();

/** M4-02：把当前看板指标整理为只读摘要交给模型解读 */
const openAiInsight = () => {
  const cards = (data.value as any)?.cards ?? [];
  const summary = cards.map((card: any) => `${card.label ?? card.name ?? '指标'}：${card.value ?? '-'}`).join('\n');
  aiDrawer.value?.open({
    instruction: '对指标同比/环比、对比值、异常给出关键结论与建议',
    content: summary || '（暂无指标数据，请先查询）'
  });
};
const copyAiInsight = (text: string) => {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('解读已复制'),
    () => ElMessage.warning('复制失败，请手动选择文本')
  );
};

const range = ref<string[]>([]);
const data = ref<FinanceStatisticsVO>();
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
  const res: any = await getFinanceStatistics(query());
  data.value = res.data;
  loadDetails();
};

const loadDetails = async () => {
  detailLoading.value = true;
  try {
    const res: any = await listDetails('finance', { ...query(), ...detailQuery.value });
    detail.value = res.data ?? { headers: [], rows: [], total: 0 };
  } finally {
    detailLoading.value = false;
  }
};

const handleExport = async () => {
  const blob: any = await exportReport('finance', query(), 'XLSX');
  saveAs(blob, `财务看板-${new Date().toISOString().slice(0, 10)}.xlsx`);
  proxy?.$modal.msgSuccess('导出成功');
};

onMounted(load);
</script>

<style scoped>
.mt8 {
  margin-top: 12px;
}
</style>
