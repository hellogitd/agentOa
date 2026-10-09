<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:schedule:add']" type="primary" plain icon="Plus" @click="handleAdd">排班</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="人员ID" prop="userId">
            <el-input v-model="queryParams.userId" placeholder="账号 ID" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="日期从" prop="dateFrom">
            <el-date-picker v-model="queryParams.dateFrom" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="到" prop="dateTo">
            <el-date-picker v-model="queryParams.dateTo" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="scheduleList">
        <el-table-column label="日期" align="center" prop="workDate" width="120" />
        <el-table-column label="人员" align="center" prop="userName" width="130" />
        <el-table-column label="账号ID" align="center" prop="userId" width="120" />
        <el-table-column label="班次" align="center" prop="shiftName" />
        <el-table-column label="来源" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.source === 2 ? 'warning' : 'info'">{{ scope.row.source === 2 ? '轮班' : '手工' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['at:schedule:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="排班指派" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="scheduleFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账号ID" prop="userId">
          <el-input v-model="form.userId" placeholder="人员账号 ID" />
        </el-form-item>
        <el-form-item label="班次ID" prop="shiftId">
          <el-input v-model="form.shiftId" placeholder="班次 ID（班次管理页查询）" />
        </el-form-item>
        <el-form-item label="单日">
          <el-date-picker v-model="form.workDate" type="date" value-format="YYYY-MM-DD" placeholder="单日排班" />
        </el-form-item>
        <el-form-item label="区间">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AttendanceSchedule" lang="ts">
import { listSchedule, addSchedule, delSchedule, ScheduleAssignmentForm, ScheduleAssignmentVO } from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const scheduleList = ref<ScheduleAssignmentVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const dialog = reactive({ visible: false });
const range = ref<string[]>([]);

const queryParams = ref({ userId: undefined, dateFrom: undefined, dateTo: undefined });
const form = ref<ScheduleAssignmentForm>({ userId: '', shiftId: '', workDate: undefined, remark: undefined });

const rules = ref<any>({
  userId: [{ required: true, message: '人员不能为空', trigger: 'blur' }],
  shiftId: [{ required: true, message: '班次不能为空', trigger: 'blur' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listSchedule(queryParams.value);
    scheduleList.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => getList();

const resetQuery = () => {
  queryParams.value = { userId: undefined, dateFrom: undefined, dateTo: undefined };
  getList();
};

const handleAdd = () => {
  form.value = { userId: '', shiftId: '', workDate: undefined, remark: undefined };
  range.value = [];
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['scheduleFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: ScheduleAssignmentForm = { ...form.value };
  if (!payload.workDate && range.value?.length === 2) {
    payload.dateFrom = range.value[0];
    payload.dateTo = range.value[1];
  }
  await addSchedule(payload);
  proxy?.$modal.msgSuccess('排班成功');
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: ScheduleAssignmentVO) => {
  await proxy?.$modal.confirm('确认删除该排班？');
  await delSchedule(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
