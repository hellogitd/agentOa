<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:shift:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:shift:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:shift:remove']" type="danger" plain icon="Delete" :disabled="single" @click="handleDelete()">删除</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="班次编码" prop="shiftCode">
            <el-input v-model="queryParams.shiftCode" placeholder="请输入班次编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="班次名称" prop="shiftName">
            <el-input v-model="queryParams.shiftName" placeholder="请输入班次名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="班次状态" clearable>
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="shiftList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="班次编码" align="center" prop="shiftCode" width="120" />
        <el-table-column label="班次名称" align="center" prop="shiftName" width="120" />
        <el-table-column label="上班时间" align="center" prop="workStartTime" width="100" />
        <el-table-column label="下班时间" align="center" prop="workEndTime" width="100" />
        <el-table-column label="休息开始" align="center" prop="restStartTime" width="100" />
        <el-table-column label="休息结束" align="center" prop="restEndTime" width="100" />
        <el-table-column label="跨天" align="center" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.isCrossDay === 1 ? 'warning' : 'info'">{{ scope.row.isCrossDay === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="弹性(分钟)" align="center" prop="flexibleMinutes" width="100" />
        <el-table-column label="宽限(分钟)" align="center" prop="graceMinutes" width="100" />
        <el-table-column label="打卡窗口" align="center" width="140">
          <template #default="scope">{{ scope.row.punchWindowStart }} ~ {{ scope.row.punchWindowEnd }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="操作" width="180" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['at:shift:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['at:shift:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="620px" append-to-body>
      <el-form ref="shiftFormRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="班次编码" prop="shiftCode">
              <el-input v-model="form.shiftCode" placeholder="请输入班次编码" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次名称" prop="shiftName">
              <el-input v-model="form.shiftName" placeholder="请输入班次名称" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上班时间" prop="workStartTime">
              <el-time-picker v-model="form.workStartTime" value-format="HH:mm:ss" placeholder="请选择上班时间" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下班时间" prop="workEndTime">
              <el-time-picker v-model="form.workEndTime" value-format="HH:mm:ss" placeholder="请选择下班时间" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="休息开始" prop="restStartTime">
              <el-time-picker v-model="form.restStartTime" value-format="HH:mm:ss" placeholder="可留空" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="休息结束" prop="restEndTime">
              <el-time-picker v-model="form.restEndTime" value-format="HH:mm:ss" placeholder="可留空" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跨天班次" prop="isCrossDay">
              <el-switch v-model="form.isCrossDay" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="弹性分钟" prop="flexibleMinutes">
              <el-input-number v-model="form.flexibleMinutes" :min="0" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="宽限分钟" prop="graceMinutes">
              <el-input-number v-model="form.graceMinutes" :min="0" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="打卡窗口开始" prop="punchWindowStart">
              <el-input-number v-model="form.punchWindowStart" :min="0" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="打卡窗口结束" prop="punchWindowEnd">
              <el-input-number v-model="form.punchWindowEnd" :min="0" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AttendanceShift" lang="ts">
import { listShift, addShift, updateShift, delShift, ShiftForm, ShiftVO } from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const shiftList = ref<ShiftVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string>>([]);
const single = ref(true);
const total = ref(0);
const dialog = reactive<DialogOption>({ title: '', visible: false });

const initForm: ShiftForm = {
  shiftCode: '',
  shiftName: '',
  workStartTime: '',
  workEndTime: '',
  restStartTime: undefined,
  restEndTime: undefined,
  isCrossDay: 0,
  flexibleMinutes: 0,
  graceMinutes: 0,
  punchWindowStart: 120,
  punchWindowEnd: 240,
  status: '0',
  remark: ''
};
const data = reactive<{ form: ShiftForm; queryParams: any }>({
  form: { ...initForm },
  queryParams: { pageNum: 1, pageSize: 10, shiftCode: undefined, shiftName: undefined, status: undefined }
});
const { queryParams, form } = toRefs(data);

const rules = ref<any>({
  shiftCode: [{ required: true, message: '班次编码不能为空', trigger: 'blur' }],
  shiftName: [{ required: true, message: '班次名称不能为空', trigger: 'blur' }],
  workStartTime: [{ required: true, message: '上班时间不能为空', trigger: 'change' }],
  workEndTime: [{ required: true, message: '下班时间不能为空', trigger: 'change' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listShift(queryParams.value);
    shiftList.value = res.data?.records ?? [];
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
  queryParams.value = { pageNum: 1, pageSize: 10, shiftCode: undefined, shiftName: undefined, status: undefined };
  handleQuery();
};

const handleSelectionChange = (selection: ShiftVO[]) => {
  ids.value = selection.map((item) => item.id);
  single.value = selection.length !== 1;
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['shiftFormRef'] as any)?.resetFields();
};

const handleAdd = () => {
  reset();
  dialog.title = '新增班次';
  dialog.visible = true;
};

const handleUpdate = (row?: ShiftVO) => {
  reset();
  const id = row?.id ?? ids.value[0];
  const target = shiftList.value.find((item) => item.id === id);
  if (target) {
    form.value = { ...target } as ShiftForm;
  }
  dialog.title = '修改班次';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['shiftFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updateShift(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addShift(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const handleDelete = async (row?: ShiftVO) => {
  const deleteIds = row ? [row.id] : ids.value;
  await proxy?.$modal.confirm(`确认删除选中的班次吗？`);
  await delShift(deleteIds[0]);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
