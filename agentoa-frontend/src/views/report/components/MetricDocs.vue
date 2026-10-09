<template>
  <el-card shadow="hover">
    <template #header>
      <span class="card-header-label">指标口径（版本）</span>
    </template>
    <el-table :data="metrics" border size="small">
      <el-table-column label="指标" prop="metricName" width="160" />
      <el-table-column label="编码" prop="metricCode" width="200" />
      <el-table-column label="版本" prop="currentVersion" width="70" align="center" />
      <el-table-column label="单位" prop="unit" width="90" align="center" />
      <el-table-column label="口径摘要" prop="description" :show-overflow-tooltip="true" />
    </el-table>
  </el-card>
</template>

<script setup name="MetricDocs" lang="ts">
import type { MetricVO } from '@/api/report';

defineProps<{ metrics: MetricVO[] }>();
</script>
