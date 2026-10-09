<template>
  <div class="p-2">
    <el-card shadow="hover">
      <el-table v-loading="loading" border :data="ccList">
        <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" />
        <el-table-column label="业务类型" align="center" prop="businessType" width="110" />
        <el-table-column label="抄送人" align="center" prop="senderName" width="120" />
        <el-table-column label="留言" align="center" prop="comment" :show-overflow-tooltip="true" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="wf_instance_status" :value="String(scope.row.instanceStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="抄送时间" align="center" prop="createTime" width="160" />
        <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="详情" placement="top">
              <el-button link type="primary" icon="View" @click="handleDetail(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup name="WfCc" lang="ts">
import { listCc, CcVO } from '@/api/workflow';
import { useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wf_instance_status } = toRefs<any>(proxy?.useDict('wf_instance_status'));
const router = useRouter();

const ccList = ref<CcVO[]>([]);
const loading = ref(true);
const total = ref(0);
const queryParams = ref({ pageNum: 1, pageSize: 10 });

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listCc(queryParams.value);
    ccList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleDetail = (row: CcVO) => {
  router.push('/workflow/detail/index?instanceId=' + row.instanceId);
};

onMounted(() => {
  getList();
});
</script>
