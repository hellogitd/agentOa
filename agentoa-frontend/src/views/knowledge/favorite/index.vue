<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <span class="card-header-label">我的收藏</span>
      </template>

      <el-table v-loading="loading" border :data="favoriteList">
        <el-table-column label="标题" align="center" prop="title" :show-overflow-tooltip="true" />
        <el-table-column label="类型" align="center" prop="docType" width="110" />
        <el-table-column label="版本" align="center" prop="version" width="80" />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="取消收藏" placement="top">
              <el-button link type="danger" icon="StarFilled" @click="handleUnfavorite(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="KnowledgeFavorite" lang="ts">
import { listKbFavorite, getDocumentsBatch, unfavoriteKbDocument } from '@/api/knowledge';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const favoriteList = ref<any[]>([]);
const loading = ref(true);

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listKbFavorite();
    const ids: string[] = res.data ?? [];
    if (ids.length === 0) {
      favoriteList.value = [];
      return;
    }
    // 批量详情（原逐 id 串行 N+1，FE-F4-02）：无权限/已删除文档由服务端静默过滤
    const detail: any = await getDocumentsBatch(ids);
    favoriteList.value = detail.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleUnfavorite = async (row: any) => {
  await unfavoriteKbDocument(row.id);
  proxy?.$modal.msgSuccess('已取消收藏');
  await getList();
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.card-header-label {
  font-weight: 600;
}
</style>
