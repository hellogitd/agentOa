<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">考勤报表</span>
          </el-col>
        </el-row>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="日报" name="daily">
          <el-form :model="dailyQuery" inline>
            <el-form-item label="日期范围">
              <el-date-picker
                v-model="dailyRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
            </el-form-item>
            <el-form-item label="仅异常">
              <el-switch v-model="dailyQuery.abnormalOnly" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleDailyQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetDailyQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="dailyLoading" border :data="dailyList">
            <el-table-column label="日期" align="center" prop="attendanceDate" width="110" />
            <el-table-column label="姓名" align="center" prop="nickname" width="110" />
            <el-table-column label="出勤状态" align="center" width="100">
              <template #default="scope">
                <dict-tag :options="at_work_status" :value="String(scope.row.workStatus)" />
              </template>
            </el-table-column>
            <el-table-column label="首次打卡" align="center" prop="firstPunchTime" width="160" />
            <el-table-column label="末次打卡" align="center" prop="lastPunchTime" width="160" />
            <el-table-column label="迟到(分钟)" align="center" prop="lateMinutes" width="100" />
            <el-table-column label="早退(分钟)" align="center" prop="earlyMinutes" width="100" />
            <el-table-column label="请假(分钟)" align="center" prop="leaveMinutes" width="100" />
            <el-table-column label="加班(分钟)" align="center" prop="overtimeMinutes" width="100" />
            <el-table-column label="是否异常" align="center" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.isAbnormal ? 'danger' : 'success'">{{ scope.row.isAbnormal ? '异常' : '正常' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="异常原因" align="center" prop="abnormalReason" :show-overflow-tooltip="true" />
          </el-table>

          <pagination
            v-show="dailyTotal > 0"
            v-model:page="dailyQuery.pageNum"
            v-model:limit="dailyQuery.pageSize"
            :total="dailyTotal"
            @pagination="getDailyList"
          />
        </el-tab-pane>

        <el-tab-pane label="月报" name="monthly">
          <el-form :model="monthlyQuery" inline>
            <el-form-item label="月份">
              <el-date-picker v-model="monthlyQuery.yearMonth" type="month" value-format="yyyy-MM" placeholder="选择月份" @change="getMonthlyList" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="getMonthlyList">搜索</el-button>
              <el-button icon="Refresh" @click="resetMonthlyQuery">重置</el-button>
            </el-form-item>
            <el-form-item>
              <el-button v-hasPermi="['at:report:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="monthlyLoading" border :data="monthlyList">
            <el-table-column label="姓名" align="center" prop="nickname" width="110" />
            <el-table-column label="出勤天数" align="center" prop="attendanceDays" width="100" />
            <el-table-column label="迟到次数" align="center" prop="lateCount" width="100" />
            <el-table-column label="早退次数" align="center" prop="earlyCount" width="100" />
            <el-table-column label="旷工次数" align="center" prop="absentCount" width="100" />
            <el-table-column label="请假(分钟)" align="center" prop="leaveMinutes" width="110" />
            <el-table-column label="加班(分钟)" align="center" prop="overtimeMinutes" width="110" />
            <el-table-column label="异常天数" align="center" prop="abnormalCount" width="100" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup name="AttendanceReport" lang="ts">
import { saveAs } from 'file-saver';
import { listDailyStatistics, listMonthlyStatistics, exportStatistics, AttendanceDayVO, MonthlyReportVO } from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { at_work_status } = toRefs<any>(proxy?.useDict('at_work_status'));

const activeTab = ref<string>('daily');

const dailyList = ref<AttendanceDayVO[]>([]);
const dailyLoading = ref(true);
const dailyTotal = ref(0);
const dailyRange = ref<string[]>([]);
const dailyQuery = ref<any>({ pageNum: 1, pageSize: 10, dateFrom: undefined, dateTo: undefined, abnormalOnly: undefined });

const monthlyList = ref<MonthlyReportVO[]>([]);
const monthlyLoading = ref(false);
const monthlyQuery = ref<any>({ yearMonth: undefined });

const getDailyList = async () => {
  dailyLoading.value = true;
  try {
    const res: any = await listDailyStatistics(dailyQuery.value);
    dailyList.value = res.data?.records ?? [];
    dailyTotal.value = res.data?.total ?? 0;
  } finally {
    dailyLoading.value = false;
  }
};

const handleDailyQuery = () => {
  dailyQuery.value.dateFrom = dailyRange.value?.[0];
  dailyQuery.value.dateTo = dailyRange.value?.[1];
  dailyQuery.value.pageNum = 1;
  getDailyList();
};

const resetDailyQuery = () => {
  dailyRange.value = [];
  dailyQuery.value = { pageNum: 1, pageSize: 10, dateFrom: undefined, dateTo: undefined, abnormalOnly: undefined };
  getDailyList();
};

const getMonthlyList = async () => {
  monthlyLoading.value = true;
  try {
    const res: any = await listMonthlyStatistics(monthlyQuery.value);
    monthlyList.value = res.data ?? [];
  } finally {
    monthlyLoading.value = false;
  }
};

const resetMonthlyQuery = () => {
  monthlyQuery.value = { yearMonth: undefined };
  getMonthlyList();
};

const handleExport = async () => {
  const blob: any = await exportStatistics(monthlyQuery.value);
  saveAs(new Blob([blob]), `考勤月报_${monthlyQuery.value.yearMonth || new Date().getTime()}.xlsx`);
};

onMounted(() => {
  getDailyList();
  getMonthlyList();
});
</script>

<style scoped>
.card-header-label {
  font-weight: 600;
}
</style>
