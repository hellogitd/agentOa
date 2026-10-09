<template>
  <el-dialog
    :title="`检索测试 - ${kb?.name ?? ''}`"
    :model-value="visible"
    width="760px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form inline>
      <el-form-item label="问题">
        <el-input v-model="searchQuery" placeholder="输入测试问题" style="width: 380px" @keyup.enter="handleDoSearchTest" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" :loading="searchLoading" @click="handleDoSearchTest">检索</el-button>
      </el-form-item>
    </el-form>
    <div class="text-gray-400 text-xs mb-2">命中片段与相似度分布（向量 + 关键词融合排序）；检索范围按提问人权限服务端过滤</div>
    <el-empty v-if="searchHits.length === 0 && searched" description="未命中" />
    <div v-for="hit in searchHits" :key="hit.chunkId" class="hit-item">
      <div class="hit-head">
        <span class="font-bold">{{ hit.title || '未命名来源' }}</span>
        <span v-if="hit.heading" class="text-gray-500 ml-2">{{ hit.heading }}</span>
        <el-tag class="ml-2" size="small">score {{ (hit.score ?? 0).toFixed(3) }}</el-tag>
        <el-tag v-if="hit.vectorScore != null" size="small" type="info">vector {{ hit.vectorScore.toFixed(3) }}</el-tag>
        <el-link v-if="hit.link" class="ml-2" type="primary" @click="openLink(hit.link)">跳原文</el-link>
      </div>
      <div class="hit-snippet">{{ hit.snippet }}</div>
      <el-slider :model-value="Math.round((hit.score ?? 0) * 100)" disabled :show-tooltip="false" />
    </div>
  </el-dialog>
</template>

<script setup name="KbSearchTestDialog" lang="ts">
import type { PropType } from 'vue';
import { kbSearchTest, type KbVO, type KbSearchHitVO } from '@/api/ai';

const props = defineProps({
  visible: { type: Boolean, default: false },
  kb: { type: Object as PropType<KbVO | null>, default: null }
});
const emit = defineEmits(['update:visible', 'navigate']);

const searchQuery = ref('');
const searchHits = ref<KbSearchHitVO[]>([]);
const searchLoading = ref(false);
const searched = ref(false);

watch(
  () => props.visible,
  (open) => {
    if (open) {
      searchQuery.value = '';
      searchHits.value = [];
      searched.value = false;
    }
  }
);

const handleDoSearchTest = async () => {
  if (!props.kb || !searchQuery.value.trim()) return;
  searchLoading.value = true;
  try {
    const res: any = await kbSearchTest(props.kb.id, { query: searchQuery.value.trim(), topK: 5 });
    searchHits.value = res.data ?? [];
    searched.value = true;
  } finally {
    searchLoading.value = false;
  }
};

const openLink = (link: string) => {
  if (link.startsWith('/')) {
    emit('update:visible', false);
    emit('navigate', link);
  } else {
    window.open(link, '_blank');
  }
};
</script>

<style scoped lang="scss">
.hit-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.hit-head {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}
.hit-snippet {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: pre-wrap;
  margin-bottom: 6px;
}
</style>
