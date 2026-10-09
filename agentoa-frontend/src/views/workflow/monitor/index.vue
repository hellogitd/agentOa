<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['wf:instance:list']" type="warning" plain icon="AlarmClock" @click="handleTimeoutScan">超时扫描</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="业务类型" prop="businessType">
            <el-select v-model="queryParams.businessType" placeholder="业务类型" clearable>
              <el-option label="请假" value="leave" />
              <el-option label="加班" value="overtime" />
              <el-option label="补卡" value="correction" />
              <el-option label="报销" value="reimburse" />
              <el-option label="转正" value="regularize" />
              <el-option label="离职" value="offboard" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="instanceList">
        <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" />
        <el-table-column label="业务类型" align="center" prop="businessType" width="110" />
        <el-table-column label="发起人" align="center" prop="initiatorName" width="110" />
        <el-table-column label="当前节点" align="center" prop="currentTaskName" width="130" :show-overflow-tooltip="true" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="wf_instance_status" :value="String(scope.row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="发起时间" align="center" prop="startTime" width="160" />
        <el-table-column label="操作" width="220" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip v-if="scope.row.status === 1" content="挂起" placement="top">
              <el-button v-hasPermi="['wf:instance:suspend']" link type="warning" icon="Pause" @click="handleSuspend(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip v-if="scope.row.status === 5" content="恢复" placement="top">
              <el-button v-hasPermi="['wf:instance:resume']" link type="success" icon="VideoPlay" @click="handleResume(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip v-if="[1, 5].includes(scope.row.status)" content="强制终止" placement="top">
              <el-button v-hasPermi="['wf:instance:terminate']" link type="danger" icon="CircleClose" @click="handleTerminate(scope.row)"></el-button>
            </el-tooltip>
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

<script setup name="WfMonitor" lang="ts">
import { listInstances, suspendInstance, resumeInstance, terminateInstance, timeoutScan, InstanceVO } from '@/api/workflow';
import { useRoute, useRouter } from 'vue-router';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wf_instance_status } = toRefs<any>(proxy?.useDict('wf_instance_status'));
const route = useRoute();
const router = useRouter();

const instanceList = ref<InstanceVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const queryParams = ref({ pageNum: 1, pageSize: 10, scope: 'all', businessType: undefined });

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listInstances(queryParams.value);
    instanceList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 10, scope: 'all', businessType: undefined };
  handleQuery();
};

const handleSuspend = async (row: InstanceVO) => {
  await proxy?.$modal.confirm(`确认挂起流程「${row.title}」？`);
  await suspendInstance(row.id);
  proxy?.$modal.msgSuccess('已挂起');
  await getList();
};

const handleResume = async (row: InstanceVO) => {
  await resumeInstance(row.id);
  proxy?.$modal.msgSuccess('已恢复');
  await getList();
};

const handleTerminate = async (row: InstanceVO) => {
  await proxy?.$modal.confirm(`确认强制终止流程「${row.title}」？业务单据将归为已撤销。`);
  await terminateInstance(row.id, '管理员强制终止');
  proxy?.$modal.msgSuccess('已终止');
  await getList();
};

const handleTimeoutScan = async () => {
  const res: any = await timeoutScan(24);
  proxy?.$modal.msgSuccess(`本次提醒 ${res.data ?? 0} 条`);
};

const handleDetail = (row: InstanceVO) => {
  router.push('/workflow/detail/index?instanceId=' + row.id);
};

onMounted(() => {
  getList();
});
</script>
