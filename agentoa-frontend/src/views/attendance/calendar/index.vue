<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">工作日历</span>
          </el-col>
          <el-col :span="1.5">
            <el-date-picker v-model="year" type="year" value-format="YYYY" placeholder="选择年份" class="year-picker" @change="handleQuery" />
          </el-col>
          <el-col :span="1.5">
            <el-select v-model="month" placeholder="选择月份" clearable class="month-select" @change="handleQuery">
              <el-option v-for="item in monthOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:calendar:edit']" type="primary" plain icon="Check" :disabled="changedRows.length === 0" @click="handleSave">
              保存{{ changedRows.length > 0 ? `（${changedRows.length}）` : '' }}
            </el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getCalendarList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="calendarList">
        <el-table-column label="日期" align="center" prop="workDate" width="120" />
        <el-table-column label="星期" align="center" width="80">
          <template #default="scope">{{ weekDayText(scope.row.workDate) }}</template>
        </el-table-column>
        <el-table-column label="日期类型" align="center" width="160">
          <template #default="scope">
            <el-select v-model="scope.row.dayType" placeholder="请选择" size="small">
              <el-option v-for="dict in at_day_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="说明" align="center">
          <template #default="scope">
            <el-input v-model="scope.row.description" placeholder="请输入说明" maxlength="128" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="变更" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="isRowChanged(scope.row)" type="warning">待保存</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="hover" class="mt-2">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <span class="card-header-label">节假日</span>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:calendar:edit']" type="primary" plain icon="Plus" @click="handleAddHoliday">新增</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="holidayLoading" border :data="holidayList">
        <el-table-column label="日期" align="center" prop="holidayDate" width="130" />
        <el-table-column label="名称" align="center" prop="holidayName" />
        <el-table-column label="类型" align="center" width="130">
          <template #default="scope">
            <el-tag :type="scope.row.holidayType === 'MAKEUP' ? 'warning' : 'danger'">
              {{ scope.row.holidayType === 'MAKEUP' ? '调休上班' : '节假日' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['at:calendar:edit']" link type="danger" icon="Delete" @click="handleDeleteHoliday(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="登记节假日" v-model="holidayDialog.visible" width="480px" append-to-body>
      <el-form ref="holidayFormRef" :model="holidayForm" :rules="holidayRules" label-width="90px">
        <el-form-item label="日期" prop="holidayDate">
          <el-date-picker v-model="holidayForm.holidayDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" />
        </el-form-item>
        <el-form-item label="名称" prop="holidayName">
          <el-input v-model="holidayForm.holidayName" placeholder="请输入名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型" prop="holidayType">
          <el-radio-group v-model="holidayForm.holidayType">
            <el-radio value="HOLIDAY">节假日</el-radio>
            <el-radio value="MAKEUP">调休上班</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitHoliday">确 定</el-button>
          <el-button @click="holidayDialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AttendanceCalendar" lang="ts">
import { listCalendar, saveCalendar, listHoliday, addHoliday, delHoliday, CalendarVO, CalendarForm, HolidayVO, HolidayForm } from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { at_day_type } = toRefs<any>(proxy?.useDict('at_day_type'));

const calendarList = ref<CalendarVO[]>([]);
const holidayList = ref<HolidayVO[]>([]);
const loading = ref(true);
const holidayLoading = ref(true);
const showSearch = ref(true);
const originalMap = ref<Record<string, { dayType: string; description: string }>>({});

const now = new Date();
const year = ref<string>(String(now.getFullYear()));
const month = ref<number>(now.getMonth() + 1);

const monthOptions = Array.from({ length: 12 }, (_, index) => ({ value: index + 1, label: `${index + 1}月` }));

const holidayDialog = reactive<DialogOption>({ title: '登记节假日', visible: false });
const initHolidayForm: HolidayForm = { holidayDate: '', holidayName: '', holidayType: 'HOLIDAY' };
const holidayForm = ref<HolidayForm>({ ...initHolidayForm });

const holidayRules = ref<any>({
  holidayDate: [{ required: true, message: '日期不能为空', trigger: 'change' }],
  holidayName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  holidayType: [{ required: true, message: '类型不能为空', trigger: 'change' }]
});

const weekDayText = (workDate?: string) => {
  if (!workDate) return '-';
  const date = new Date(`${workDate}T00:00:00`);
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()] ?? '-';
};

const isRowChanged = (row: CalendarVO) => {
  const original = originalMap.value[row.workDate];
  if (!original) return false;
  return row.dayType !== original.dayType || (row.description ?? '') !== (original.description ?? '');
};

const changedRows = computed<CalendarVO[]>(() => calendarList.value.filter((row) => isRowChanged(row)));

const getCalendarList = async () => {
  loading.value = true;
  try {
    const params: { year: number; month?: number } = { year: Number(year.value) };
    if (month.value) params.month = Number(month.value);
    const res: any = await listCalendar(params);
    const rows: CalendarVO[] = res.data ?? [];
    const map: Record<string, { dayType: string; description: string }> = {};
    rows.forEach((row) => {
      map[row.workDate] = { dayType: row.dayType, description: row.description ?? '' };
    });
    originalMap.value = map;
    calendarList.value = rows;
  } finally {
    loading.value = false;
  }
};

const getHolidayList = async () => {
  holidayLoading.value = true;
  try {
    const res: any = await listHoliday(year.value ? Number(year.value) : undefined);
    holidayList.value = res.data ?? [];
  } finally {
    holidayLoading.value = false;
  }
};

const handleQuery = () => {
  getCalendarList();
  getHolidayList();
};

const handleSave = async () => {
  const rows: CalendarForm[] = changedRows.value.map((row) => ({
    workDate: row.workDate,
    dayType: row.dayType,
    description: row.description
  }));
  if (rows.length === 0) return;
  await saveCalendar(rows);
  proxy?.$modal.msgSuccess('保存成功');
  await getCalendarList();
};

const handleAddHoliday = () => {
  holidayForm.value = { ...initHolidayForm };
  holidayDialog.visible = true;
};

const submitHoliday = async () => {
  const valid = await (proxy?.$refs['holidayFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  await addHoliday(holidayForm.value);
  proxy?.$modal.msgSuccess('登记成功');
  holidayDialog.visible = false;
  await getHolidayList();
};

const handleDeleteHoliday = async (row: HolidayVO) => {
  await proxy?.$modal.confirm(`确认删除节假日"${row.holidayName}"吗？`);
  await delHoliday(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getHolidayList();
};

onMounted(() => {
  getCalendarList();
  getHolidayList();
});
</script>

<style scoped>
.card-header-label {
  font-weight: 600;
}
.year-picker {
  width: 110px;
}
.month-select {
  width: 110px;
}
</style>
