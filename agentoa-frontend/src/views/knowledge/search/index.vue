<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">知识搜索</span></el-col>
          <el-col :span="8">
            <el-input v-model="keyword" placeholder="搜索标题、正文或标签" clearable prefix-icon="Search" @keyup.enter="handleSearch" />
          </el-col>
          <el-col :span="1.5">
            <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          </el-col>
          <el-col :span="8">
            <span class="text-gray-400 text-xs">结果按空间/文档授权过滤，回收站内容不可见</span>
          </el-col>
        </el-row>
      </template>

      <div v-loading="loading">
        <el-empty v-if="!searched" description="输入关键字搜索知识文档" />
        <template v-else>
          <div v-if="hits.length === 0" class="text-gray-400 text-center py-8">未找到匹配文档</div>
          <div v-for="hit in hits" :key="hit.documentId" class="hit-item" @click="handleOpen(hit)">
            <div class="hit-title">{{ hit.title }}</div>
            <div class="hit-meta">
              <el-tag size="small">{{ hit.spaceName }}</el-tag>
              <span class="text-gray-400 text-xs ml-2">{{ hit.lastEditTime }}</span>
            </div>
            <div class="hit-snippet" v-html="hit.highlight"></div>
          </div>
          <pagination v-show="total > 0" v-model:page="pageNum" v-model:limit="pageSize" :total="total" @pagination="doSearch" />
        </template>
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" :title="detail?.title" size="60%">
      <div class="md-preview" v-html="rendered"></div>
    </el-drawer>
  </div>
</template>

<script setup name="KbSearch" lang="ts">
import { searchDocument, getDocument, SearchHitVO, DocumentVO } from '@/api/knowledge';
import { markdownToHtml } from '@/utils/markdown';

const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const hits = ref<SearchHitVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);

const detailVisible = ref(false);
const detail = ref<DocumentVO>();
const rendered = computed(() => markdownToHtml(detail.value?.content ?? ''));

const doSearch = async () => {
  if (!keyword.value.trim()) return;
  loading.value = true;
  try {
    const res: any = await searchDocument({ keyword: keyword.value.trim(), pageNum: pageNum.value, pageSize: pageSize.value });
    hits.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
    searched.value = true;
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pageNum.value = 1;
  doSearch();
};

const handleOpen = async (hit: SearchHitVO) => {
  const res: any = await getDocument(hit.documentId);
  detail.value = res.data;
  detailVisible.value = true;
};
</script>

<style scoped lang="scss">
.hit-item {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
}
.hit-item:hover {
  background: #f5f7fa;
}
.hit-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.hit-meta {
  margin: 4px 0;
}
.hit-snippet {
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  :deep(em) {
    color: #f56c6c;
    font-style: normal;
  }
}
</style>
