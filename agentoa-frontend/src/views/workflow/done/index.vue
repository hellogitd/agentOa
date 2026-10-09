<template>
  <div class="app-container">
    <el-card>
      <el-table v-loading="loading" :data="tasks" border>
        <el-table-column prop="title" label="流程标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="toDetail(row.instanceId)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="办理节点" width="120" />
        <el-table-column prop="businessType" label="业务类型" width="110" />
        <el-table-column prop="initiatorName" label="发起人" width="100" />
        <el-table-column prop="endTime" label="办理时间" width="170">
          <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="toDetail(row.instanceId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="pageNum" v-model:limit="pageSize" :total="total" @pagination="load" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listDone, type TaskVO } from '@/api/workflow';

const router = useRouter();
const loading = ref(false);
const tasks = ref<TaskVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function toDetail(instanceId: string) {
  router.push({ path: '/workflow/detail', query: { id: instanceId } });
}

async function load() {
  loading.value = true;
  try {
    const res: any = await listDone({ pageNum: pageNum.value, pageSize: pageSize.value });
    tasks.value = res.data.records;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
