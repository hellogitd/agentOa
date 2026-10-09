<template>
  <el-dialog
    :model-value="visible"
    :title="row ? '修改字典类型' : '添加字典类型'"
    width="500px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="字典名称" prop="dictName">
        <el-input v-model="form.dictName" placeholder="请输入字典名称" />
      </el-form-item>
      <el-form-item prop="dictType">
        <template #label>
          <span>
            <el-tooltip content="数据存储中的Key值，如：sys_user_sex" placement="top">
              <el-icon><question-filled /></el-icon>
            </el-tooltip>
            字典类型
          </span>
        </template>
        <el-input v-model="form.dictType" placeholder="请输入字典类型" maxlength="100" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="DictTypeDialog" lang="ts">
import type { PropType } from 'vue';
import { getType, addType, updateType } from '@/api/system/dict/type';
import { DictTypeForm, DictTypeVO } from '@/api/system/dict/type/types';

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** null=新增；非空=修改（打开时按 id 拉取最新数据） */
  row: { type: Object as PropType<DictTypeVO | null>, default: null }
});
const emit = defineEmits(['update:visible', 'success']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const formRef = ref<ElFormInstance>();
const initFormData: DictTypeForm = {
  dictId: undefined,
  dictName: '',
  dictType: '',
  remark: ''
};
const form = ref<DictTypeForm>({ ...initFormData });
const rules = {
  dictName: [{ required: true, message: '字典名称不能为空', trigger: 'blur' }],
  dictType: [{ required: true, message: '字典类型不能为空', trigger: 'blur' }]
};

watch(
  () => props.visible,
  async (open) => {
    if (!open) return;
    form.value = { ...initFormData };
    formRef.value?.resetFields();
    if (props.row?.dictId) {
      const res = await getType(props.row.dictId);
      Object.assign(form.value, res.data);
    }
  }
);

const cancel = () => {
  emit('update:visible', false);
};

const submitForm = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.dictId ? await updateType(form.value) : await addType(form.value);
      proxy?.$modal.msgSuccess('操作成功');
      emit('update:visible', false);
      emit('success');
    }
  });
};
</script>
