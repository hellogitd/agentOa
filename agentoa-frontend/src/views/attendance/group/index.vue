<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:group:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:group:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['at:group:remove']" type="danger" plain icon="Delete" :disabled="single" @click="handleDelete()">删除</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="考勤组编码" prop="groupCode">
            <el-input v-model="queryParams.groupCode" placeholder="请输入考勤组编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="考勤组名称" prop="groupName">
            <el-input v-model="queryParams.groupName" placeholder="请输入考勤组名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="考勤组状态" clearable>
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="groupList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="考勤组编码" align="center" prop="groupCode" width="130" />
        <el-table-column label="考勤组名称" align="center" prop="groupName" width="140" />
        <el-table-column label="班次" align="center" prop="shiftName" width="120" />
        <el-table-column label="工作日" align="center" prop="workDays">
          <template #default="scope">{{ workDaysText(scope.row.workDays) }}</template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="110" />
        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="成员数" align="center" prop="memberCount" width="80" />
        <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
        <el-table-column label="操作" width="220" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="成员管理" placement="top">
              <el-button v-hasPermi="['at:group:query']" link type="primary" icon="User" @click="handleMembers(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['at:group:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['at:group:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="620px" append-to-body>
      <el-form ref="groupFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="考勤组编码" prop="groupCode">
              <el-input v-model="form.groupCode" placeholder="请输入考勤组编码" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考勤组名称" prop="groupName">
              <el-input v-model="form.groupName" placeholder="请输入考勤组名称" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次" prop="shiftId">
              <el-select v-model="form.shiftId" placeholder="请选择班次" clearable>
                <el-option v-for="item in shiftOptions" :key="item.id" :label="item.shiftName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择生效日期" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="工作日" prop="workDays">
              <el-checkbox-group v-model="workDaysChecked">
                <el-checkbox v-for="item in workDayOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
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

    <el-dialog :title="`成员管理 - ${memberDialog.groupName}`" v-model="memberDialog.visible" width="720px" append-to-body>
      <el-form ref="memberFormRef" :inline="true" :model="memberForm" :rules="memberRules" label-width="80px">
        <el-form-item label="用户ID" prop="userId">
          <el-input-number v-model="memberForm.userId" :min="1" controls-position="right" placeholder="用户ID" />
        </el-form-item>
        <el-form-item label="生效日期" prop="validFrom">
          <el-date-picker v-model="memberForm.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="生效日期" />
        </el-form-item>
        <el-form-item label="失效日期" prop="validTo">
          <el-date-picker v-model="memberForm.validTo" type="date" value-format="YYYY-MM-DD" placeholder="可留空" />
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['at:group:edit']" type="primary" icon="Plus" @click="submitMember">添加成员</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="memberLoading" border :data="memberList">
        <el-table-column label="昵称" align="center" prop="nickname" />
        <el-table-column label="用户ID" align="center" prop="userId" width="110" />
        <el-table-column label="生效日期" align="center" prop="validFrom" width="110" />
        <el-table-column label="失效日期" align="center" prop="validTo" width="110">
          <template #default="scope">{{ scope.row.validTo || '长期' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="移除" placement="top">
              <el-button v-hasPermi="['at:group:edit']" link type="danger" icon="Delete" @click="handleRemoveMember(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="memberTotal > 0"
        v-model:page="memberQuery.pageNum"
        v-model:limit="memberQuery.pageSize"
        :total="memberTotal"
        @pagination="getMemberList"
      />
    </el-dialog>
  </div>
</template>

<script setup name="AttendanceGroup" lang="ts">
import {
  listGroup,
  addGroup,
  updateGroup,
  delGroup,
  listAllShift,
  listGroupMember,
  addGroupMember,
  delGroupMember,
  AttendanceGroupVO,
  AttendanceGroupForm,
  AttendanceMemberVO,
  AttendanceMemberForm,
  ShiftVO
} from '@/api/attendance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const groupList = ref<AttendanceGroupVO[]>([]);
const shiftOptions = ref<ShiftVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string>>([]);
const single = ref(true);
const total = ref(0);
const dialog = reactive<DialogOption>({ title: '', visible: false });

const workDayOptions = [
  { value: '1', label: '周一' },
  { value: '2', label: '周二' },
  { value: '3', label: '周三' },
  { value: '4', label: '周四' },
  { value: '5', label: '周五' },
  { value: '6', label: '周六' },
  { value: '7', label: '周日' }
];

const initForm: AttendanceGroupForm = {
  groupCode: '',
  groupName: '',
  shiftId: '',
  workDays: '',
  effectiveDate: '',
  status: '0',
  remark: ''
};
const data = reactive<{ form: AttendanceGroupForm; queryParams: any }>({
  form: { ...initForm },
  queryParams: { pageNum: 1, pageSize: 10, groupCode: undefined, groupName: undefined, shiftId: undefined, status: undefined }
});
const { queryParams, form } = toRefs(data);

const rules = ref<any>({
  groupCode: [{ required: true, message: '考勤组编码不能为空', trigger: 'blur' }],
  groupName: [{ required: true, message: '考勤组名称不能为空', trigger: 'blur' }],
  shiftId: [{ required: true, message: '班次不能为空', trigger: 'change' }],
  workDays: [{ required: true, message: '工作日不能为空', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '生效日期不能为空', trigger: 'change' }]
});

const workDaysChecked = computed<string[]>({
  get: () => (form.value.workDays ? form.value.workDays.split(',').filter((item: string) => item !== '') : []),
  set: (val: string[]) => {
    form.value.workDays = (val ?? []).join(',');
  }
});

const workDaysText = (value?: string) => {
  if (!value) return '-';
  return value
    .split(',')
    .map((item: string) => workDayOptions.find((option) => option.value === item)?.label ?? item)
    .join('、');
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listGroup(queryParams.value);
    groupList.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadShiftOptions = async () => {
  const res: any = await listAllShift();
  shiftOptions.value = res.data ?? [];
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.value = { pageNum: 1, pageSize: 10, groupCode: undefined, groupName: undefined, shiftId: undefined, status: undefined };
  handleQuery();
};

const handleSelectionChange = (selection: AttendanceGroupVO[]) => {
  ids.value = selection.map((item) => item.id);
  single.value = selection.length !== 1;
};

const reset = () => {
  form.value = { ...initForm };
  (proxy?.$refs['groupFormRef'] as any)?.resetFields();
};

const handleAdd = () => {
  reset();
  dialog.title = '新增考勤组';
  dialog.visible = true;
};

const handleUpdate = (row?: AttendanceGroupVO) => {
  reset();
  const id = row?.id ?? ids.value[0];
  const target = groupList.value.find((item) => item.id === id);
  if (target) {
    form.value = {
      id: target.id,
      groupCode: target.groupCode,
      groupName: target.groupName,
      shiftId: target.shiftId,
      workDays: target.workDays,
      effectiveDate: target.effectiveDate,
      status: target.status,
      remark: target.remark
    };
  }
  dialog.title = '修改考勤组';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['groupFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updateGroup(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addGroup(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const handleDelete = async (row?: AttendanceGroupVO) => {
  const deleteIds = row ? [row.id] : ids.value;
  await proxy?.$modal.confirm(`确认删除选中的考勤组吗？`);
  await delGroup(deleteIds[0]);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const memberDialog = reactive<{ visible: boolean; groupId: string; groupName: string }>({ visible: false, groupId: '', groupName: '' });
const memberList = ref<AttendanceMemberVO[]>([]);
const memberLoading = ref(true);
const memberTotal = ref(0);
const memberQuery = ref<any>({ pageNum: 1, pageSize: 10 });

const initMemberForm: AttendanceMemberForm = { userId: undefined as any, validFrom: '', validTo: undefined };
const memberForm = ref<AttendanceMemberForm>({ ...initMemberForm });

const memberRules = ref<any>({
  userId: [{ required: true, message: '用户ID不能为空', trigger: 'blur' }],
  validFrom: [{ required: true, message: '生效日期不能为空', trigger: 'change' }]
});

const getMemberList = async () => {
  memberLoading.value = true;
  try {
    const res: any = await listGroupMember(memberDialog.groupId, memberQuery.value);
    memberList.value = res.data?.records ?? [];
    memberTotal.value = res.data?.total ?? 0;
  } finally {
    memberLoading.value = false;
  }
};

const handleMembers = (row: AttendanceGroupVO) => {
  memberDialog.groupId = row.id;
  memberDialog.groupName = row.groupName;
  memberQuery.value = { pageNum: 1, pageSize: 10 };
  memberForm.value = { ...initMemberForm };
  memberDialog.visible = true;
  getMemberList();
};

const submitMember = async () => {
  const valid = await (proxy?.$refs['memberFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  await addGroupMember(memberDialog.groupId, memberForm.value);
  proxy?.$modal.msgSuccess('添加成功');
  memberForm.value = { ...initMemberForm };
  await getMemberList();
  await getList();
};

const handleRemoveMember = async (row: AttendanceMemberVO) => {
  await proxy?.$modal.confirm(`确认移除成员"${row.nickname || row.userId}"吗？`);
  await delGroupMember(memberDialog.groupId, row.id);
  proxy?.$modal.msgSuccess('移除成功');
  await getMemberList();
  await getList();
};

onMounted(() => {
  getList();
  loadShiftOptions();
});
</script>
