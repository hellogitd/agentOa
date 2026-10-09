<template>
  <el-form :label-width="labelWidth" :disabled="readonly">
    <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :required="!readonly && !!field.required">
      <template v-if="field.type === 'textarea'">
        <el-input v-model="value[field.key]" type="textarea" :rows="3" />
      </template>
      <template v-else-if="field.type === 'number'">
        <el-input-number v-model="value[field.key]" :disabled="readonly || !!field.readonly" :controls="false" />
      </template>
      <template v-else-if="field.type === 'select'">
        <el-select v-model="value[field.key]" clearable style="width: 100%">
          <el-option v-for="opt in field.options || []" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
        </el-select>
      </template>
      <template v-else-if="field.type === 'date'">
        <el-date-picker v-model="value[field.key]" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </template>
      <template v-else-if="field.type === 'datetime'">
        <el-date-picker v-model="value[field.key]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
      </template>
      <template v-else-if="field.type === 'list'">
        <div class="form-list">
          <el-table :data="listRows(field.key)" size="small" border>
            <el-table-column v-for="item in field.itemFields || []" :key="item.key" :label="item.label">
              <template #default="{ row }">
                <el-date-picker v-if="item.type === 'date'" v-model="row[item.key]" type="date" value-format="YYYY-MM-DD" size="small" />
                <span v-else-if="item.type === 'file' || item.type === 'image'">
                  <el-upload
                    v-if="!readonly"
                    :action="uploadUrl"
                    :http-request="customUpload"
                    :limit="1"
                    :show-file-list="false"
                    :accept="item.type === 'image' ? 'image/*' : undefined"
                    :on-success="(res: any) => onRowUploaded(row, item, res)"
                  >
                    <el-button link type="primary" size="small" :aria-label="`${field.label} - ${item.label}`">
                      {{ row[item.key] ? '重传' : '上传' }}
                    </el-button>
                  </el-upload>
                  <el-button v-if="row[item.key]" link type="primary" size="small" @click="downloadAttachment(String(row[item.key]))">
                    附件
                  </el-button>
                </span>
                <el-input v-else v-model="row[item.key]" size="small" :aria-label="`${field.label} - ${item.label}`" />
              </template>
            </el-table-column>
            <el-table-column v-if="!readonly" label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeRow(field.key, $index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button v-if="!readonly" link type="primary" @click="addRow(field)">添加明细</el-button>
        </div>
      </template>
      <template v-else-if="field.type === 'file' || field.type === 'image'">
        <div class="form-attachment">
          <el-upload
            v-if="!readonly && !field.readonly"
            :action="uploadUrl"
            :http-request="customUpload"
            :limit="1"
            :show-file-list="false"
            :accept="field.type === 'image' ? 'image/*' : undefined"
            :on-success="(res: any) => onAttachmentUploaded(field, res)"
          >
            <el-button link type="primary">{{ value[field.key] ? '重新上传' : field.type === 'image' ? '上传图片' : '上传附件' }}</el-button>
          </el-upload>
          <span v-if="value[field.key]" class="form-attachment-value">
            <el-button link type="primary" @click="downloadAttachment(String(value[field.key]))">下载附件</el-button>
            <el-button v-if="!readonly && !field.readonly" link type="danger" @click="clearAttachment(field)">移除</el-button>
          </span>
        </div>
      </template>
      <template v-else>
        <el-input v-model="value[field.key]" :disabled="readonly || !!field.readonly" />
      </template>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import service, { download } from '@/utils/request';
import type { FormField, FormSchema } from '@/api/workflow';

const uploadUrl = import.meta.env.VITE_APP_BASE_API + '/api/v1/files';

const props = withDefaults(
  defineProps<{
    schema?: FormSchema | null;
    modelValue: Record<string, any>;
    readonly?: boolean;
    labelWidth?: string;
  }>(),
  { schema: null, readonly: false, labelWidth: '110px' }
);

const emit = defineEmits<{ (e: 'update:modelValue', value: Record<string, any>): void }>();

const fields = computed<FormField[]>(() => props.schema?.fields || []);

const value = computed<Record<string, any>>({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
});

function listRows(key: string): Record<string, any>[] {
  if (!Array.isArray(value.value[key])) {
    value.value = { ...value.value, [key]: [] };
  }
  return value.value[key];
}

function addRow(field: FormField) {
  const row: Record<string, any> = {};
  for (const item of field.itemFields || []) {
    row[item.key] = '';
  }
  value.value = { ...value.value, [field.key]: [...listRows(field.key), row] };
}

function removeRow(key: string, index: number) {
  const rows = [...listRows(key)];
  rows.splice(index, 1);
  value.value = { ...value.value, [key]: rows };
}

/** 附件上传走 axios 服务（带鉴权与 clientid），docs/23 H5-H3-01 PC 侧 */
async function customUpload(options: { file: File; onSuccess: (res: any) => void; onError: (err: any) => void }) {
  try {
    const formData = new FormData();
    formData.append('file', options.file);
    const res: any = await service.post('/api/v1/files', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
    options.onSuccess(res);
  } catch (err) {
    options.onError(err);
  }
}

function onAttachmentUploaded(field: FormField, res: any) {
  const fileId = res?.data?.fileId ?? res?.fileId ?? '';
  if (fileId) {
    value.value = { ...value.value, [field.key]: String(fileId) };
  }
}

function onRowUploaded(row: Record<string, any>, item: FormField, res: any) {
  const fileId = res?.data?.fileId ?? res?.fileId ?? '';
  if (fileId) {
    row[item.key] = String(fileId);
  }
}

function clearAttachment(field: FormField) {
  value.value = { ...value.value, [field.key]: '' };
}

function downloadAttachment(fileId: string) {
  download(`/api/v1/files/${fileId}/download`, {}, `attachment-${fileId}`);
}
</script>

<style scoped>
.form-list {
  width: 100%;
}
.form-attachment {
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-attachment-value {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
