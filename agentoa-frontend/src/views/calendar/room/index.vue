<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">会议室预约</span></el-col>
          <el-col v-hasPermi="['cl:room:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreateRoom">新增会议室</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list" @row-click="handleSelectRoom">
        <el-table-column label="名称" align="center" prop="name" min-width="140" />
        <el-table-column label="位置" align="center" prop="location" min-width="140" />
        <el-table-column label="容量" align="center" prop="capacity" width="90" />
        <el-table-column label="设备" align="center" prop="equipment" min-width="140" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="cl_room_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['cl:room:book']" link type="primary" icon="Calendar" @click.stop="handleSelectRoom(scope.row)"
              >查看预约</el-button
            >
            <el-button v-hasPermi="['cl:room:edit']" link type="primary" icon="Edit" @click.stop="handleEditRoom(scope.row)">修改</el-button>
            <el-button v-hasPermi="['cl:room:remove']" link type="danger" icon="Delete" @click.stop="handleDeleteRoom(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-card shadow="hover" class="mt8">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"
            ><span class="card-header-label">{{ currentRoom ? `预约：${currentRoom.name}` : '预约情况（点击左侧会议室）' }}</span></el-col
          >
          <el-col :span="4">
            <el-date-picker v-model="day" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="w-full" @change="loadBookings" />
          </el-col>
          <el-col v-if="currentRoom" v-hasPermi="['cl:room:book']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreateBooking">新建预约</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="bookingLoading" border :data="bookings">
        <el-table-column label="时间" align="center" width="220">
          <template #default="scope">{{ formatTime(scope.row.startTime) }} - {{ formatTime(scope.row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="主题" align="center" prop="title" min-width="160" />
        <el-table-column label="预订人" align="center" prop="bookerName" width="110" />
        <el-table-column label="签到时间" align="center" width="170">
          <template #default="scope">{{ scope.row.checkinTime ? formatTime(scope.row.checkinTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="cl_booking_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 1 && scope.row.canManage"
              v-hasPermi="['cl:room:book']"
              link
              type="success"
              icon="Position"
              @click="handleCheckin(scope.row)"
              >签到</el-button
            >
            <el-button
              v-if="(scope.row.status === 1 || scope.row.status === 2) && scope.row.canManage"
              v-hasPermi="['cl:room:book']"
              link
              type="danger"
              icon="Close"
              @click="handleCancelBooking(scope.row)"
              >取消</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="roomDialog.title" v-model="roomDialog.visible" width="560px" append-to-body>
      <el-form ref="roomFormRef" :model="roomForm" :rules="roomRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="roomForm.name" placeholder="请输入会议室名称" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="roomForm.location" placeholder="位置（可选）" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="容量">
              <el-input-number v-model="roomForm.capacity" :min="1" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="roomForm.status" class="w-full">
                <el-option v-for="dict in cl_room_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="设备">
          <el-input v-model="roomForm.equipment" placeholder="投影/视频/白板等（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="submitRoom">确 定</el-button>
      </template>
    </el-dialog>

    <el-dialog title="新建预约" v-model="bookingDialog.visible" width="560px" append-to-body>
      <el-form ref="bookingFormRef" :model="bookingForm" :rules="bookingRules" label-width="90px">
        <el-form-item label="主题" prop="title">
          <el-input v-model="bookingForm.title" placeholder="请输入会议主题" />
        </el-form-item>
        <el-form-item label="起止时间" prop="range">
          <el-date-picker
            v-model="bookingForm.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            class="w-full"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bookingDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="submitBooking">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CalendarRoom" lang="ts">
import {
  listRoom,
  addRoom,
  updateRoom,
  delRoom,
  listBooking,
  bookRoom,
  checkinBooking,
  cancelBooking,
  RoomVO,
  RoomForm,
  BookingVO
} from '@/api/collaboration';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { cl_room_status, cl_booking_status } = toRefs<any>(proxy?.useDict('cl_room_status', 'cl_booking_status'));

const loading = ref(true);
const list = ref<RoomVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10 });

const currentRoom = ref<RoomVO | null>(null);
const day = ref(new Date().toISOString().slice(0, 10));
const bookingLoading = ref(false);
const bookings = ref<BookingVO[]>([]);

const roomDialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const roomFormRef = ref();
const roomForm = ref<RoomForm>({ name: '', status: '1' });
const roomRules = { name: [{ required: true, message: '请输入会议室名称', trigger: 'blur' }] };

const bookingDialog = reactive<any>({ visible: false });
const bookingFormRef = ref();
const bookingForm = ref<any>({ title: '', range: [] });
const bookingRules = {
  title: [{ required: true, message: '请输入主题', trigger: 'blur' }],
  range: [{ required: true, message: '请选择起止时间', trigger: 'change' }]
};

const formatTime = (time: string) => {
  if (!time) return '';
  return new Date(time).toLocaleString('zh-CN', { hour12: false });
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listRoom(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadBookings = async () => {
  if (!currentRoom.value) return;
  bookingLoading.value = true;
  try {
    const start = `${day.value}T00:00:00Z`;
    const end = `${day.value}T23:59:59Z`;
    const res: any = await listBooking(String(currentRoom.value.id), { start, end, pageNum: 1, pageSize: 100 });
    bookings.value = res.data?.records ?? [];
  } finally {
    bookingLoading.value = false;
  }
};

const handleSelectRoom = (row: RoomVO) => {
  currentRoom.value = row;
  loadBookings();
};

const handleCreateRoom = () => {
  roomForm.value = { name: '', status: '1' };
  roomDialog.title = '新增会议室';
  roomDialog.id = undefined;
  roomDialog.visible = true;
};

const handleEditRoom = (row: RoomVO) => {
  roomForm.value = {
    name: row.name,
    location: row.location,
    capacity: row.capacity,
    equipment: row.equipment,
    status: String(row.status)
  };
  roomDialog.title = '修改会议室';
  roomDialog.id = row.id;
  roomDialog.visible = true;
};

const submitRoom = async () => {
  const valid = await (proxy?.$refs['roomFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (roomDialog.id) {
    await updateRoom(roomDialog.id, roomForm.value);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await addRoom(roomForm.value);
    proxy?.$modal.msgSuccess('创建成功');
  }
  roomDialog.visible = false;
  await getList();
};

const handleDeleteRoom = async (row: RoomVO) => {
  await proxy?.$modal.confirm(`确认删除会议室「${row.name}」？`);
  await delRoom(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleCreateBooking = () => {
  bookingForm.value = { title: '', range: [`${day.value}T09:00:00Z`, `${day.value}T10:00:00Z`] };
  bookingDialog.visible = true;
};

const submitBooking = async () => {
  const valid = await (proxy?.$refs['bookingFormRef'] as any)?.validate().catch(() => false);
  if (!valid || !currentRoom.value) return;
  await bookRoom(String(currentRoom.value.id), {
    title: bookingForm.value.title,
    startTime: bookingForm.value.range[0],
    endTime: bookingForm.value.range[1]
  });
  proxy?.$modal.msgSuccess('预约成功');
  bookingDialog.visible = false;
  await loadBookings();
};

const handleCheckin = async (row: BookingVO) => {
  await checkinBooking(row.id);
  proxy?.$modal.msgSuccess('签到成功');
  await loadBookings();
};

const handleCancelBooking = async (row: BookingVO) => {
  await proxy?.$modal.confirm(`确认取消预约「${row.title}」？`);
  await cancelBooking(row.id);
  proxy?.$modal.msgSuccess('已取消');
  await loadBookings();
};

onMounted(() => {
  getList();
});
</script>
