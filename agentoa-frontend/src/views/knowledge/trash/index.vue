<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">回收站</span></el-col>
          <el-col :span="1.5">
            <el-select v-model="spaceId" placeholder="选择空间" clearable filterable class="w-180px" @change="handleQuery">
              <el-option v-for="s in spaces" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-col>
          <el-col :span="12">
            <span class="text-gray-400 text-xs">删除项保留 30 天，逾期不可恢复；恢复需空间编辑权限</span>
          </el-col>
        </el-row>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleQuery">
        <el-tab-pane label="文档" name="document">
          <el-table v-loading="loading" :data="documents" border>
            <el-table-column label="标题" align="left" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="空间" align="center" prop="spaceName" width="130" />
            <el-table-column label="删除时间" align="center" prop="deletedAt" width="180" />
            <el-table-column label="版本" align="center" prop="version" width="70" />
            <el-table-column label="操作" align="center" width="120" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button v-hasPermi="['kn:doc:restore']" link type="primary" icon="RefreshLeft" @click="handleRestoreDoc(scope.row)"
                  >恢复</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="docTotal > 0" v-model:page="pageNum" v-model:limit="pageSize" :total="docTotal" @pagination="loadDocuments" />
        </el-tab-pane>
        <el-tab-pane label="文件" name="file">
          <el-table v-loading="loading" :data="files" border>
            <el-table-column label="文件名" align="left" prop="fileName" min-width="200" show-overflow-tooltip />
            <el-table-column label="空间" align="center" prop="spaceName" width="130" />
            <el-table-column label="大小" align="center" width="100">
              <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="删除时间" align="center" prop="deletedAt" width="180" />
            <el-table-column label="操作" align="center" width="120" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button v-hasPermi="['kn:doc:restore']" link type="primary" icon="RefreshLeft" @click="handleRestoreFile(scope.row)"
                  >恢复</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="fileTotal > 0" v-model:page="pageNum" v-model:limit="pageSize" :total="fileTotal" @pagination="loadFiles" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup name="KbTrash" lang="ts">
import { listDocument, restoreDocument, listKbFile, restoreKbFile, listSpace, DocumentVO, KbFileVO } from '@/api/knowledge';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const activeTab = ref('document');
const loading = ref(true);
const spaceId = ref<string>();
const spaces = ref<any[]>([]);
const documents = ref<DocumentVO[]>([]);
const files = ref<KbFileVO[]>([]);
const docTotal = ref(0);
const fileTotal = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);

const loadSpaces = async () => {
  const res: any = await listSpace({ pageNum: 1, pageSize: 100 });
  spaces.value = res.data?.records ?? [];
};

const loadDocuments = async () => {
  const res: any = await listDocument({ pageNum: pageNum.value, pageSize: pageSize.value, spaceId: spaceId.value, deleted: true });
  documents.value = res.data?.records ?? [];
  docTotal.value = res.data?.total ?? 0;
};

const loadFiles = async () => {
  const res: any = await listKbFile({ pageNum: pageNum.value, pageSize: pageSize.value, spaceId: spaceId.value, deleted: true });
  files.value = res.data?.records ?? [];
  fileTotal.value = res.data?.total ?? 0;
};

const handleQuery = async () => {
  pageNum.value = 1;
  loading.value = true;
  try {
    await Promise.all([loadDocuments(), loadFiles()]);
  } finally {
    loading.value = false;
  }
};

const handleRestoreDoc = async (row: DocumentVO) => {
  await proxy?.$modal.confirm(`确认恢复文档「${row.title}」？`);
  try {
    await restoreDocument(row.id);
    proxy?.$modal.msgSuccess('恢复成功');
    await handleQuery();
  } catch (e: any) {
    const msg = e?.message ?? String(e);
    proxy?.$modal.msgError(msg.includes('KN_STATE_CONFLICT') ? msg : '恢复失败');
  }
};

const handleRestoreFile = async (row: KbFileVO) => {
  await proxy?.$modal.confirm(`确认恢复文件「${row.fileName}」？`);
  try {
    await restoreKbFile(row.id);
    proxy?.$modal.msgSuccess('恢复成功');
    await handleQuery();
  } catch (e: any) {
    const msg = e?.message ?? String(e);
    proxy?.$modal.msgError(msg.includes('KN_STATE_CONFLICT') ? msg : '恢复失败');
  }
};

const formatSize = (size?: number) => {
  if (!size && size !== 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

onMounted(async () => {
  await loadSpaces();
  await handleQuery();
});
</script>
