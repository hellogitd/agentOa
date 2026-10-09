<template>
  <!-- 入职登记 -->
  <el-dialog title="入职登记" :model-value="onboardDialog.visible" width="640px" append-to-body @update:model-value="onboardDialog.visible = $event">
    <el-form ref="onboardFormRef" :model="onboardForm" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="onboardForm.name" placeholder="请输入姓名" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工号" prop="employeeNo">
            <el-input v-model="onboardForm.employeeNo" placeholder="留空自动生成" maxlength="32" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="onboardForm.phone" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="onboardForm.email" placeholder="请输入邮箱" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="部门" prop="deptId">
            <el-tree-select
              v-model="onboardForm.deptId"
              :data="deptOptions"
              :props="{ value: 'id', label: 'name', children: 'children' } as any"
              value-key="id"
              placeholder="请选择部门"
              check-strictly
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="岗位" prop="postId">
            <el-select v-model="onboardForm.postId" placeholder="请选择岗位" clearable>
              <el-option v-for="item in postOptions" :key="item.id" :label="item.positionName" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="入职日期" prop="entryDate">
            <el-date-picker v-model="onboardForm.entryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择入职日期" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="试用期至" prop="probationEndDate">
            <el-date-picker v-model="onboardForm.probationEndDate" type="date" value-format="YYYY-MM-DD" placeholder="可留空" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitOnboard">确 定</el-button>
        <el-button @click="onboardDialog.visible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 转正 -->
  <el-dialog
    title="办理转正"
    :model-value="regularizeDialog.visible"
    width="420px"
    append-to-body
    @update:model-value="regularizeDialog.visible = $event"
  >
    <el-form label-width="90px">
      <el-form-item label="转正日期">
        <el-date-picker v-model="regularizeForm.regularDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="regularizeForm.remark" type="textarea" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitRegularize">确 定</el-button>
      <el-button @click="regularizeDialog.visible = false">取 消</el-button>
    </template>
  </el-dialog>

  <!-- 离职 -->
  <el-dialog
    title="办理离职"
    :model-value="offboardDialog.visible"
    width="420px"
    append-to-body
    @update:model-value="offboardDialog.visible = $event"
  >
    <el-form label-width="100px">
      <el-form-item label="最后工作日">
        <el-date-picker v-model="offboardForm.lastWorkingDay" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" />
      </el-form-item>
      <el-form-item label="离职原因">
        <el-input v-model="offboardForm.reason" type="textarea" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitOffboard">确 定</el-button>
      <el-button @click="offboardDialog.visible = false">取 消</el-button>
    </template>
  </el-dialog>
</template>

<script setup name="EmployeeLifecycleDialogs" lang="ts">
import type { PropType } from 'vue';
import { onboard, regularize, offboard, DeptTreeNode, EmployeeVO, OnboardForm } from '@/api/hr';

defineProps({
  deptOptions: { type: Array as PropType<DeptTreeNode[]>, default: () => [] },
  postOptions: { type: Array as PropType<any[]>, default: () => [] }
});
const emit = defineEmits(['success']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const initForm: OnboardForm = {
  name: '',
  employeeNo: '',
  phone: '',
  email: '',
  deptId: '',
  postId: undefined,
  entryDate: undefined,
  probationEndDate: undefined
};
const rules = ref<any>({
  name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  deptId: [{ required: true, message: '部门不能为空', trigger: 'change' }]
});

const onboardDialog = reactive<DialogOption>({ visible: false });
const onboardFormRef = ref();
const onboardForm = ref<OnboardForm>({ ...initForm });

const currentId = ref('');
const regularizeDialog = reactive<DialogOption>({ visible: false });
const regularizeForm = ref<{ regularDate?: string; remark?: string }>({});
const offboardDialog = reactive<DialogOption>({ visible: false });
const offboardForm = ref<{ lastWorkingDay?: string; reason?: string }>({});

const openOnboard = () => {
  onboardForm.value = { ...initForm, entryDate: undefined };
  onboardDialog.visible = true;
  nextTick(() => onboardFormRef.value?.clearValidate());
};

const openRegularize = (row: EmployeeVO) => {
  currentId.value = row.id;
  regularizeForm.value = { regularDate: undefined, remark: '' };
  regularizeDialog.visible = true;
};

const openOffboard = (row: EmployeeVO) => {
  currentId.value = row.id;
  offboardForm.value = { lastWorkingDay: undefined, reason: '' };
  offboardDialog.visible = true;
};

const submitOnboard = async () => {
  await onboard(onboardForm.value);
  proxy?.$modal.msgSuccess('入职办理成功');
  onboardDialog.visible = false;
  emit('success');
};

const submitRegularize = async () => {
  await regularize({ employeeId: currentId.value, ...regularizeForm.value });
  proxy?.$modal.msgSuccess('转正成功');
  regularizeDialog.visible = false;
  emit('success');
};

const submitOffboard = async () => {
  await offboard({ employeeId: currentId.value, ...offboardForm.value });
  proxy?.$modal.msgSuccess('离职办理成功');
  offboardDialog.visible = false;
  emit('success');
};

defineExpose({ openOnboard, openRegularize, openOffboard });
</script>
