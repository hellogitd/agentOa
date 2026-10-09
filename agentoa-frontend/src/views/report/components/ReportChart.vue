<template>
  <div ref="chartEl" class="report-chart" :style="{ height: `${height}px` }"></div>
</template>

<script setup name="ReportChart" lang="ts">
import { echarts, type EChartsCoreOption, type EChartsType } from '@/utils/echarts';

interface Series {
  name: string;
  values: (number | null)[];
}

const props = withDefaults(
  defineProps<{
    type: 'line' | 'bar' | 'pie';
    title: string;
    labels: string[];
    series: Series[];
    height?: number;
  }>(),
  { height: 260 }
);

const chartEl = ref<HTMLElement>();
let chart: EChartsType | undefined;

const render = () => {
  if (!chartEl.value) return;
  if (!chart) {
    chart = echarts.init(chartEl.value);
  }
  const legend = props.series.map((item) => item.name);
  let option: EChartsCoreOption;
  if (props.type === 'pie') {
    option = {
      title: { text: props.title, left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['35%', '60%'],
          data: props.labels.map((label, index) => ({ name: label, value: props.series[0]?.values[index] ?? 0 }))
        }
      ]
    };
  } else {
    option = {
      title: { text: props.title, left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0, data: legend },
      grid: { left: 48, right: 16, top: 36, bottom: 52 },
      xAxis: { type: 'category', data: props.labels, axisLabel: { rotate: props.labels.length > 8 ? 40 : 0 } },
      yAxis: { type: 'value' },
      series: props.series.map((item) => ({
        name: item.name,
        type: props.type,
        smooth: true,
        data: item.values
      }))
    };
  }
  chart.setOption(option, true);
};

onMounted(() => {
  render();
  window.addEventListener('resize', resize);
});

const resize = () => chart?.resize();

watch(
  () => [props.labels, props.series],
  () => nextTick(render),
  { deep: true }
);

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  chart?.dispose();
  chart = undefined;
});
</script>

<style scoped>
.report-chart {
  width: 100%;
}
</style>
