<template>
  <el-row :gutter="12">
    <el-col v-for="card in cards" :key="card.code" :xs="12" :sm="12" :md="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-name">{{ card.name }}</div>
        <div class="stat-value">{{ card.value ?? '—' }}</div>
        <div class="stat-unit">{{ unitLabel(card.unit) }}</div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup name="StatCards" lang="ts">
import type { StatCardVO } from '@/api/report';

defineProps<{ cards: StatCardVO[] }>();

const unitLabel = (unit?: string) => {
  switch (unit) {
    case 'count':
      return '个/次';
    case 'minutes':
      return '分钟';
    case 'days':
      return '天';
    case 'hours':
      return '小时';
    case 'amount':
      return '元';
    case 'ratio':
      return '比率';
    default:
      return '';
  }
};
</script>

<style scoped>
.stat-card {
  margin-bottom: 12px;
  text-align: center;
}
.stat-name {
  color: #64748b;
  font-size: 13px;
}
.stat-value {
  margin: 8px 0 2px;
  font-size: 24px;
  font-weight: 600;
  color: #1f2d3d;
}
.stat-unit {
  color: #94a3b8;
  font-size: 12px;
}
</style>
