<template>
  <div class="form-designer">
    <div class="fd-palette">
      <div class="fd-title">添加字段</div>
      <el-button v-for="item in palette" :key="item.type" size="small" class="fd-palette-item" @click="addField(item.type)">
        {{ item.label }}
      </el-button>
    </div>

    <div class="fd-fields">
      <div class="fd-title">
        字段列表
        <el-button v-if="!fields.length" link type="primary" size="small" @click="addField('input')">添加第一个字段</el-button>
      </div>
      <el-empty v-if="!fields.length" description="从左侧添加字段" :image-size="60" />
      <div v-for="(field, index) in fields" :key="field.key + '-' + index" class="fd-field" :class="{ active: activeIndex === index }">
        <div class="fd-field-head" @click="activeIndex = index">
          <span class="fd-field-label">{{ field.label || '未命名字段' }}</span>
          <span class="fd-field-key">{{ field.key }}</span>
          <el-tag size="small" type="info">{{ typeLabel(field.type) }}</el-tag>
          <el-tag v-if="field.required" size="small" type="danger">必填</el-tag>
          <span class="fd-field-actions" @click.stop>
            <el-button link size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button>
            <el-button link size="small" :disabled="index === fields.length - 1" @click="move(index, 1)">下移</el-button>
            <el-button link size="small" type="danger" @click="removeField(index)">删除</el-button>
          </span>
        </div>

        <div v-if="activeIndex === index" class="fd-field-body">
          <el-form label-width="80px" size="small">
            <el-form-item label="字段标识" required>
              <el-input v-model="field.key" placeholder="字母开头，仅字母数字下划线" @change="touch" />
            </el-form-item>
            <el-form-item label="字段名称" required>
              <el-input v-model="field.label" @change="touch" />
            </el-form-item>
            <el-form-item label="字段类型">
              <el-select v-model="field.type" style="width: 100%" @change="onTypeChange(field)">
                <el-option v-for="item in palette" :key="item.type" :label="item.label" :value="item.type" />
              </el-select>
            </el-form-item>
            <el-form-item label="必填">
              <el-switch v-model="field.required" @change="touch" />
            </el-form-item>
            <el-form-item label="只读">
              <el-switch v-model="field.readonly" @change="touch" />
            </el-form-item>

            <template v-if="field.type === 'select'">
              <el-form-item label="选项">
                <div class="fd-options">
                  <div v-for="(opt, oi) in field.options || []" :key="oi" class="fd-option-row">
                    <el-input v-model="opt.label" placeholder="显示名" size="small" @change="touch" />
                    <el-input v-model="opt.value" placeholder="值" size="small" @change="touch" />
                    <el-button link type="danger" size="small" @click="removeOption(field, oi)">删除</el-button>
                  </div>
                  <el-button link type="primary" size="small" @click="addOption(field)">添加选项</el-button>
                </div>
              </el-form-item>
            </template>

            <template v-if="field.type === 'list'">
              <el-form-item label="明细字段">
                <div class="fd-options">
                  <div v-for="(item, ii) in field.itemFields || []" :key="ii" class="fd-option-row">
                    <el-input v-model="item.key" placeholder="标识" size="small" @change="touch" />
                    <el-input v-model="item.label" placeholder="名称" size="small" @change="touch" />
                    <el-select v-model="item.type" size="small" style="width: 110px" @change="touch">
                      <el-option label="文本" value="input" />
                      <el-option label="数字" value="number" />
                      <el-option label="日期" value="date" />
                    </el-select>
                    <el-button link type="danger" size="small" @click="removeItemField(field, ii)">删除</el-button>
                  </div>
                  <el-button link type="primary" size="small" @click="addItemField(field)">添加明细字段</el-button>
                </div>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </div>
    </div>

    <div class="fd-preview">
      <div class="fd-title">实时预览</div>
      <div class="fd-preview-body">
        <FormSchemaView :schema="previewSchema" :model-value="previewData" readonly label-width="90px" />
        <el-empty v-if="!fields.length" description="暂无字段" :image-size="60" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import FormSchemaView from './FormSchema.vue';
import type { FormField, FormSchema } from '@/api/workflow';

const props = defineProps<{ modelValue: FormSchema }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: FormSchema): void }>();

const palette: { type: FormField['type']; label: string }[] = [
  { type: 'input', label: '单行文本' },
  { type: 'textarea', label: '多行文本' },
  { type: 'number', label: '数字' },
  { type: 'select', label: '下拉选择' },
  { type: 'date', label: '日期' },
  { type: 'datetime', label: '日期时间' },
  { type: 'list', label: '明细列表' },
  { type: 'file', label: '附件' },
  { type: 'image', label: '图片' }
];

const fields = computed<FormField[]>(() => local.value.fields);
const activeIndex = ref(-1);
const previewData = reactive<Record<string, any>>({});
const local = ref<FormSchema>({ schemaVersion: 1, fields: [] });

watch(
  () => props.modelValue,
  (v) => {
    if (v && v.fields) {
      local.value = JSON.parse(JSON.stringify(v));
    }
  },
  { immediate: true, deep: true }
);

const previewSchema = computed<FormSchema>(() => ({ schemaVersion: 1, fields: fields.value }));

function touch() {
  emit('update:modelValue', JSON.parse(JSON.stringify(local.value)));
}

function typeLabel(type: FormField['type']) {
  return palette.find((p) => p.type === type)?.label || type;
}

function nextKey(type: string) {
  const base = type.replace(/[^a-z0-9]/gi, '') || 'field';
  let candidate = `f${base}${fields.value.length + 1}`;
  let n = fields.value.length + 1;
  while (fields.value.some((f) => f.key === candidate)) {
    n += 1;
    candidate = `f${base}${n}`;
  }
  return candidate;
}

function addField(type: FormField['type']) {
  const field: FormField = {
    key: nextKey(type),
    label: typeLabel(type),
    type,
    required: false,
    readonly: false,
    options: type === 'select' ? [{ label: '选项一', value: '1' }] : [],
    itemFields: type === 'list' ? [{ key: 'col1', label: '列1', type: 'input' }] : []
  };
  local.value = { ...local.value, fields: [...fields.value, field] };
  activeIndex.value = fields.value.length;
  touch();
}

function onTypeChange(field: FormField) {
  if (field.type === 'select' && (!field.options || !field.options.length)) {
    field.options = [{ label: '选项一', value: '1' }];
  }
  if (field.type !== 'select') {
    field.options = [];
  }
  if (field.type === 'list' && (!field.itemFields || !field.itemFields.length)) {
    field.itemFields = [{ key: 'col1', label: '列1', type: 'input' }];
  }
  if (field.type !== 'list') {
    field.itemFields = [];
  }
  touch();
}

function removeField(index: number) {
  const next = [...fields.value];
  next.splice(index, 1);
  local.value = { ...local.value, fields: next };
  if (activeIndex.value === index) activeIndex.value = -1;
  touch();
}

function move(index: number, delta: number) {
  const next = [...fields.value];
  const target = index + delta;
  if (target < 0 || target >= next.length) return;
  [next[index], next[target]] = [next[target], next[index]];
  local.value = { ...local.value, fields: next };
  touch();
}

function addOption(field: FormField) {
  field.options = [...(field.options || []), { label: '', value: '' }];
  touch();
}

function removeOption(field: FormField, index: number) {
  const next = [...(field.options || [])];
  next.splice(index, 1);
  field.options = next;
  touch();
}

function addItemField(field: FormField) {
  const n = (field.itemFields || []).length + 1;
  field.itemFields = [...(field.itemFields || []), { key: `col${n}`, label: `列${n}`, type: 'input' }];
  touch();
}

function removeItemField(field: FormField, index: number) {
  const next = [...(field.itemFields || [])];
  next.splice(index, 1);
  field.itemFields = next;
  touch();
}
</script>

<style scoped>
.form-designer {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.fd-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.fd-palette {
  width: 130px;
  flex-shrink: 0;
}
.fd-palette-item {
  width: 100%;
  margin: 0 0 6px 0;
}
.fd-fields {
  flex: 1;
  min-width: 0;
}
.fd-preview {
  width: 320px;
  flex-shrink: 0;
}
.fd-preview-body {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
  background: var(--el-fill-color-blank);
}
.fd-field {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  margin-bottom: 6px;
}
.fd-field.active {
  border-color: var(--el-color-primary);
}
.fd-field-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
}
.fd-field-label {
  font-weight: 500;
}
.fd-field-key {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.fd-field-actions {
  margin-left: auto;
}
.fd-field-body {
  padding: 4px 10px 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.fd-options {
  width: 100%;
}
.fd-option-row {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}
</style>
