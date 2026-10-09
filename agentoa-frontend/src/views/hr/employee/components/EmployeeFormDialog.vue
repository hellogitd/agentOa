<template>
  <el-dialog :title="title" :model-value="visible" width="640px" append-to-body @update:model-value="close">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" placeholder="请输入姓名" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工号" prop="employeeNo">
            <el-input v-model="form.employeeNo" placeholder="留空自动生成" maxlength="32" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="性别" prop="gender">
            <el-select v-model="form.gender" placeholder="请选择性别" clearable>
              <el-option v-for="dict in sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="出生日期" prop="birthDate">
            <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择出生日期" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="身份证号" prop="idCardNo">
            <el-input v-model="form.idCardNo" placeholder="请输入身份证号" maxlength="18" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="部门" prop="deptId">
            <el-tree-select
              v-model="form.deptId"
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
            <el-select v-model="form.postId" placeholder="请选择岗位" clearable>
              <el-option v-for="item in postOptions" :key="item.id" :label="item.positionName" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="职级" prop="positionLevel">
            <el-input v-model="form.positionLevel" placeholder="如 P6" maxlength="32" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="入职日期" prop="entryDate">
            <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择入职日期" />
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
        <el-button @click="close">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="EmployeeFormDialog" lang="ts">
import type { PropType } from 'vue';
import { addEmployee, updateEmployee, DeptTreeNode, EmployeeForm } from '@/api/hr';

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '新增员工档案' },
  row: { type: Object as PropType<EmployeeForm | null>, default: null },
  deptOptions: { type: Array as PropType<DeptTreeNode[]>, default: () => [] },
  postOptions: { type: Array as PropType<any[]>, default: () => [] }
});
const emit = defineEmits(['update:visible', 'success']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_user_sex } = toRefs<any>(proxy?.useDict('sys_user_sex'));

const formRef = ref();
const initForm: EmployeeForm = {
  name: '',
  employeeNo: '',
  gender: undefined,
  birthDate: undefined,
  idCardNo: '',
  phone: '',
  email: '',
  deptId: '',
  postId: undefined,
  positionLevel: '',
  entryDate: undefined,
  remark: ''
};
const form = ref<EmployeeForm>({ ...initForm });
const rules = ref<any>({
  name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  deptId: [{ required: true, message: '部门不能为空', trigger: 'change' }]
});

watch(
  () => props.visible,
  (open) => {
    if (open) {
      form.value = { ...initForm, ...(props.row ?? {}) };
      nextTick(() => formRef.value?.clearValidate());
    }
  }
);

const close = () => emit('update:visible', false);

const submitForm = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updateEmployee(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addEmployee(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  close();
  emit('success');
};
</script>
