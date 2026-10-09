<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="12"><span class="card-header-label">今日打卡</span></el-col>
          <el-col :span="12" class="text-right">
            <el-button icon="Refresh" circle @click="loadToday"></el-button>
          </el-col>
        </el-row>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="考勤日期">{{ today.attendanceDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出勤状态">
          <dict-tag v-if="today.workStatus !== undefined && today.workStatus !== null" :options="at_work_status" :value="String(today.workStatus)" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="是否异常">
          <el-tag v-if="today.isAbnormal" type="danger">异常</el-tag>
          <el-tag v-else type="success">正常</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上班打卡">{{ today.punchInTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下班打卡">{{ today.punchOutTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="异常原因">{{ today.abnormalReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="迟到分钟">{{ today.lateMinutes ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="早退分钟">{{ today.earlyMinutes ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="请假分钟">{{ today.leaveMinutes ?? 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-row class="mt-4" justify="center" :gutter="40">
        <el-col :span="6">
          <el-button type="primary" size="large" class="punch-btn" :loading="punching" :disabled="!!today.punchInTime" @click="handlePunch(1)">
            打卡上班
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="success" size="large" class="punch-btn" :loading="punching" :disabled="!!today.punchOutTime" @click="handlePunch(2)">
            打卡下班
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="warning" size="large" class="punch-btn" :loading="punching" @click="openFieldDialog"> 外勤打卡 </el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-dialog title="外勤打卡" v-model="fieldDialog.visible" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="经度">
          <el-input v-model="fieldDialog.lng" placeholder="如 116.397128" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input v-model="fieldDialog.lat" placeholder="如 39.916527" />
        </el-form-item>
        <el-form-item label="定位">
          <el-button plain icon="Location" @click="captureLocation">获取定位</el-button>
          <span v-if="fieldDialog.locationTip" class="ml-2 text-gray-500">{{ fieldDialog.locationTip }}</span>
        </el-form-item>
        <el-form-item label="现场照片">
          <el-upload :action="uploadUrl" :headers="uploadHeaders" :limit="1" accept="image/*" :on-success="handlePhotoSuccess">
            <el-button plain icon="Upload">上传照片</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="fieldDialog.address" placeholder="现场地址" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="punching" @click="submitFieldPunch">提交</el-button>
          <el-button @click="fieldDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-card shadow="hover" class="mt-2">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">我的打卡记录</span>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="打卡日期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              @change="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="recordList">
        <el-table-column label="打卡日期" align="center" prop="punchDate" width="110" />
        <el-table-column label="打卡时间" align="center" prop="punchTime" width="160" />
        <el-table-column label="打卡类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="at_punch_type" :value="String(scope.row.punchType)" />
          </template>
        </el-table-column>
        <el-table-column label="迟到" align="center" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.isLate" type="danger">迟到 {{ scope.row.lateMinutes ?? 0 }} 分</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="早退" align="center" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.isEarly" type="warning">早退 {{ scope.row.earlyMinutes ?? 0 }} 分</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="打卡地点" align="center" prop="address" :show-overflow-tooltip="true" />
        <el-table-column label="设备" align="center" prop="device" :show-overflow-tooltip="true" />
        <el-table-column label="IP" align="center" prop="ip" width="130" />
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup name="AttendancePunch" lang="ts">
import { punch, punchToday, listPunchRecord, PunchTodayVO, PunchRecordVO } from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { at_work_status, at_punch_type } = toRefs<any>(proxy?.useDict('at_work_status', 'at_punch_type'));

const today = ref<PunchTodayVO>({});
const punching = ref(false);
const recordList = ref<PunchRecordVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const dateRange = ref<string[]>([]);

const uploadUrl = import.meta.env.VITE_APP_BASE_API + '/api/v1/files';
const uploadHeaders = ref<Record<string, string>>({});
const fieldDialog = reactive({
  visible: false,
  lng: '',
  lat: '',
  address: '',
  photoFileId: '',
  locationTip: ''
});

const queryParams = ref<any>({ pageNum: 1, pageSize: 10, dateFrom: undefined, dateTo: undefined });

const loadToday = async () => {
  const res: any = await punchToday();
  today.value = res.data ?? {};
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listPunchRecord(queryParams.value);
    recordList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.dateFrom = dateRange.value?.[0];
  queryParams.value.dateTo = dateRange.value?.[1];
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  dateRange.value = [];
  queryParams.value = { pageNum: 1, pageSize: 10, dateFrom: undefined, dateTo: undefined };
  getList();
};

const handlePunch = async (punchType: 1 | 2) => {
  punching.value = true;
  try {
    const res: any = await punch({ punchType });
    const time = res.data?.punchTime ?? '';
    proxy?.$modal.msgSuccess(punchType === 1 ? `上班打卡成功 ${time}` : `下班打卡成功 ${time}`);
    await Promise.all([loadToday(), getList()]);
  } finally {
    punching.value = false;
  }
};

const openFieldDialog = () => {
  fieldDialog.lng = '';
  fieldDialog.lat = '';
  fieldDialog.address = '';
  fieldDialog.photoFileId = '';
  fieldDialog.locationTip = '';
  captureLocation();
  fieldDialog.visible = true;
};

const captureLocation = () => {
  if (!navigator.geolocation) {
    fieldDialog.locationTip = '当前环境不支持定位，可手动填写';
    return;
  }
  fieldDialog.locationTip = '定位中…';
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      fieldDialog.lng = String(pos.coords.longitude);
      fieldDialog.lat = String(pos.coords.latitude);
      fieldDialog.locationTip = `精度 ${Math.round(pos.coords.accuracy)} 米`;
    },
    () => {
      fieldDialog.locationTip = '定位被拒绝或失败，请手动填写';
    }
  );
};

const handlePhotoSuccess = (response: any) => {
  fieldDialog.photoFileId = response?.data?.fileId ?? '';
};

const submitFieldPunch = async () => {
  if (!fieldDialog.lng || !fieldDialog.lat) {
    proxy?.$modal.msgWarning('外勤打卡必须提供定位');
    return;
  }
  if (!fieldDialog.photoFileId) {
    proxy?.$modal.msgWarning('外勤打卡必须上传现场照片');
    return;
  }
  punching.value = true;
  try {
    const res: any = await punch({
      punchType: 3,
      lng: Number(fieldDialog.lng),
      lat: Number(fieldDialog.lat),
      address: fieldDialog.address,
      photoFileId: fieldDialog.photoFileId,
      source: 2
    });
    proxy?.$modal.msgSuccess(`外勤打卡成功 ${res.data?.punchTime ?? ''}`);
    fieldDialog.visible = false;
    await Promise.all([loadToday(), getList()]);
  } finally {
    punching.value = false;
  }
};

onMounted(() => {
  loadToday();
  getList();
});
</script>

<style scoped>
.card-header-label {
  font-weight: 600;
}
.punch-btn {
  width: 100%;
  height: 64px;
  font-size: 18px;
}
</style>
