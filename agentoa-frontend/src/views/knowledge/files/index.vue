<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">文件柜</span></el-col>
          <el-col :span="1.5">
            <el-select v-model="query.spaceId" placeholder="选择空间" clearable filterable class="w-180px" @change="handleQuery">
              <el-option v-for="s in spaces" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-input v-model="query.keyword" placeholder="文件名" clearable prefix-icon="Search" class="w-180px" @keyup.enter="handleQuery" />
          </el-col>
          <el-col v-hasPermi="['kn:file:upload']" :span="1.5">
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              :disabled="!query.spaceId || myRole === 'VIEWER' || myRole === 'COMMENTER'"
            >
              <el-button type="primary" plain icon="Upload" :disabled="!query.spaceId">上传文件</el-button>
            </el-upload>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
          <el-col :span="20">
            <span class="text-gray-400 text-xs">单文件 ≤ 20 MiB，支持 PDF/PNG/JPEG/TXT/MD；下载按空间/文档授权并留审计</span>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="文件名" align="left" prop="fileName" min-width="200" show-overflow-tooltip />
        <el-table-column label="空间" align="center" prop="spaceName" width="120" />
        <el-table-column label="关联文档" align="center" prop="documentTitle" width="140" show-overflow-tooltip />
        <el-table-column label="类型" align="center" prop="contentType" width="120" />
        <el-table-column label="大小" align="center" width="100">
          <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="下载次数" align="center" prop="downloadCount" width="90" />
        <el-table-column label="上传人" align="center" prop="createByName" width="100" />
        <el-table-column label="上传时间" align="center" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Download" @click="handleDownload(scope.row)">下载</el-button>
            <el-button v-if="isPreviewable(scope.row)" link type="primary" icon="View" @click="handlePreview(scope.row)">预览</el-button>
            <el-button
              v-hasPermi="['kn:file:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="scope.row.myRole !== 'OWNER' && scope.row.myRole !== 'EDITOR'"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog v-model="previewVisible" :title="previewName" width="70%" append-to-body>
      <img v-if="previewIsImage" :src="previewUrl" style="max-width: 100%" alt="预览" />
      <iframe v-else-if="previewUrl" :src="previewUrl" style="width: 100%; height: 65vh; border: none"></iframe>
    </el-dialog>
  </div>
</template>

<script setup name="KbFiles" lang="ts">
import { listKbFile, uploadKbFile, delKbFile, downloadKbFile, previewKbFile, listSpace, KbFileVO } from '@/api/knowledge';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(true);
const list = ref<KbFileVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10, spaceId: undefined, keyword: undefined, deleted: false });
const spaces = ref<any[]>([]);
const myRole = ref<string>();

const previewVisible = ref(false);
const previewUrl = ref('');
const previewName = ref('');
const previewIsImage = ref(false);

const loadSpaces = async () => {
  const res: any = await listSpace({ pageNum: 1, pageSize: 100 });
  spaces.value = res.data?.records ?? [];
  syncRole();
};

const syncRole = () => {
  const space = spaces.value.find((s: any) => s.id === query.value.spaceId);
  myRole.value = space?.myRole;
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listKbFile(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  query.value.pageNum = 1;
  syncRole();
  getList();
};

const handleUpload = async (options: any) => {
  try {
    await uploadKbFile(query.value.spaceId, options.file);
    proxy?.$modal.msgSuccess('上传成功');
    await getList();
  } catch (e: any) {
    const msg = e?.message ?? String(e);
    proxy?.$modal.msgError(msg.includes('KN_FILE') ? msg : '上传失败');
  }
};

const handleDownload = async (row: KbFileVO) => {
  const data: any = await downloadKbFile(row.id);
  const blob = new Blob([data]);
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = row.fileName;
  link.click();
  URL.revokeObjectURL(link.href);
  await getList();
};

const isPreviewable = (row: KbFileVO) => {
  const type = row.contentType ?? '';
  return type.startsWith('image/') || type === 'application/pdf' || type === 'text/plain';
};

const handlePreview = async (row: KbFileVO) => {
  const data: any = await previewKbFile(row.id);
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
  }
  previewUrl.value = URL.createObjectURL(new Blob([data], { type: row.contentType }));
  previewName.value = row.fileName;
  previewIsImage.value = (row.contentType ?? '').startsWith('image/');
  previewVisible.value = true;
};

const handleDelete = async (row: KbFileVO) => {
  await proxy?.$modal.confirm(`确认删除文件「${row.fileName}」？将移入回收站 30 天内可恢复`);
  await delKbFile(row.id);
  proxy?.$modal.msgSuccess('已移入回收站');
  await getList();
};

const formatSize = (size?: number) => {
  if (!size && size !== 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};

onMounted(async () => {
  const keyword = useRoute().query?.keyword;
  if (keyword) {
    query.value.keyword = String(keyword);
  }
  await loadSpaces();
  await getList();
});
</script>
