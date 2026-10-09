<template>
  <el-dialog
    :title="`数据源 - ${kb?.name ?? ''}`"
    :model-value="visible"
    width="860px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <el-row :gutter="10" class="mb8">
      <el-col :span="11">
        <el-select
          v-model="docIdInput"
          filterable
          remote
          clearable
          placeholder="搜索知识库文档并接入"
          :remote-method="searchDocs"
          :loading="docLoading"
          class="w-full"
        >
          <el-option v-for="doc in docOptions" :key="doc.id" :label="doc.title" :value="doc.id" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-button v-hasPermi="['ai:kb:add']" type="primary" plain @click="handleAddDocSource">接入文档</el-button>
      </el-col>
      <el-col :span="6">
        <el-upload :show-file-list="false" :http-request="handleUploadFile" accept=".pdf,.docx,.txt,.md">
          <el-button v-hasPermi="['ai:kb:add']" icon="Upload">上传文件（PDF/DOCX/TXT/MD）</el-button>
        </el-upload>
      </el-col>
      <el-col :span="3">
        <el-button icon="Refresh" @click="loadSources">刷新</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="sourceLoading" :data="sources" border>
      <el-table-column label="标题" align="left" prop="title" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.sourceType === 'document' ? 'primary' : 'warning'">
            {{ scope.row.sourceType === 'document' ? '文档' : '文件' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="索引状态" align="center" width="110">
        <template #default="scope">
          <dict-tag :options="ai_kb_index_status" :value="scope.row.indexStatus" />
        </template>
      </el-table-column>
      <el-table-column label="分块数" align="center" prop="chunkCount" width="80" />
      <el-table-column label="索引时间" align="center" prop="indexedAt" width="160" />
      <el-table-column label="失败原因" align="left" prop="errorMsg" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="180">
        <template #default="scope">
          <el-button v-hasPermi="['ai:kb:index']" link type="primary" icon="Refresh" @click="handleReindex(scope.row)">重建索引</el-button>
          <el-button v-hasPermi="['ai:kb:query']" link type="primary" icon="View" @click="handleChunks(scope.row)">分块</el-button>
          <el-button v-hasPermi="['ai:kb:remove']" link type="danger" icon="Delete" @click="handleRemoveSource(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="chunkList.length">
      <el-divider content-position="left">分块预览（{{ chunkTitle }}）</el-divider>
      <el-timeline>
        <el-timeline-item v-for="chunk in chunkList" :key="chunk.id" :timestamp="`#${chunk.seq} · ${chunk.tokenCount} tokens`">
          <div v-if="chunk.heading" class="font-bold">{{ chunk.heading }}</div>
          <div class="chunk-content">{{ chunk.content }}</div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-dialog>
</template>

<script setup name="KbSourceDialog" lang="ts">
import type { PropType } from 'vue';
import {
  listKbSources,
  addKbDocumentSource,
  addKbFileSource,
  deleteKbSource,
  reindexKbSource,
  listKbChunks,
  type KbVO,
  type KbSourceVO,
  type KbChunkVO
} from '@/api/ai';
import { listDocument } from '@/api/knowledge';

const props = defineProps({
  visible: { type: Boolean, default: false },
  kb: { type: Object as PropType<KbVO | null>, default: null }
});
const emit = defineEmits(['update:visible', 'changed']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_kb_index_status } = toRefs<any>(proxy?.useDict('ai_kb_index_status'));

const sourceLoading = ref(false);
const sources = ref<KbSourceVO[]>([]);
const docIdInput = ref('');
const docOptions = ref<any[]>([]);
const docLoading = ref(false);
const chunkList = ref<KbChunkVO[]>([]);
const chunkTitle = ref('');

watch(
  () => props.visible,
  async (open) => {
    if (open) {
      chunkList.value = [];
      docIdInput.value = '';
      await loadSources();
      await searchDocs('');
    }
  }
);

const searchDocs = async (keyword: string) => {
  docLoading.value = true;
  try {
    const res: any = await listDocument({ pageNum: 1, pageSize: 20, keyword: keyword || undefined });
    docOptions.value = res.data?.records ?? [];
  } finally {
    docLoading.value = false;
  }
};

const loadSources = async () => {
  if (!props.kb) return;
  sourceLoading.value = true;
  try {
    const res: any = await listKbSources(props.kb.id, { pageNum: 1, pageSize: 50 });
    sources.value = res.data?.records ?? [];
  } finally {
    sourceLoading.value = false;
  }
};

const handleAddDocSource = async () => {
  if (!props.kb || !docIdInput.value) return;
  try {
    await addKbDocumentSource(props.kb.id, String(docIdInput.value));
    proxy?.$modal.msgSuccess('已接入，索引进行中');
    docIdInput.value = '';
    await loadSources();
    emit('changed');
  } catch {
    // 错误提示由统一拦截器处理
  }
};

const handleUploadFile = async (options: any) => {
  if (!props.kb) return;
  await addKbFileSource(props.kb.id, options.file);
  proxy?.$modal.msgSuccess('已上传，索引进行中');
  await loadSources();
  emit('changed');
};

const handleReindex = async (row: KbSourceVO) => {
  if (!props.kb) return;
  await reindexKbSource(props.kb.id, row.id);
  proxy?.$modal.msgSuccess('重建索引已提交');
  await loadSources();
};

const handleRemoveSource = async (row: KbSourceVO) => {
  if (!props.kb) return;
  await proxy?.$modal.confirm(`确认删除数据源「${row.title ?? row.id}」及其分块吗？`);
  await deleteKbSource(props.kb.id, row.id);
  proxy?.$modal.msgSuccess('已删除');
  chunkList.value = [];
  await loadSources();
  emit('changed');
};

const handleChunks = async (row: KbSourceVO) => {
  if (!props.kb) return;
  const res: any = await listKbChunks(props.kb.id, { sourceId: row.id, pageNum: 1, pageSize: 50 });
  chunkList.value = res.data?.records ?? [];
  chunkTitle.value = row.title ?? '';
};
</script>

<style scoped lang="scss">
.chunk-content {
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
</style>
