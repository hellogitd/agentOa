<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">知识文档</span></el-col>
          <el-col :span="1.5">
            <el-select v-model="query.spaceId" placeholder="选择空间" clearable filterable class="w-180px" @change="handleQuery">
              <el-option v-for="s in spaces" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-col>
          <el-col :span="1.5">
            <el-input
              v-model="query.keyword"
              placeholder="标题/标签/正文"
              clearable
              prefix-icon="Search"
              class="w-180px"
              @keyup.enter="handleQuery"
            />
          </el-col>
          <el-col v-hasPermi="['kn:doc:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" :disabled="!query.spaceId" @click="handleAdd()">新建文档</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="标题" align="left" prop="title" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <el-button link type="primary" @click="handleOpen(scope.row)">{{ scope.row.title }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="空间" align="center" prop="spaceName" width="120" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <dict-tag :options="kn_doc_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="版本" align="center" prop="version" width="70" />
        <el-table-column label="我的角色" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.myRole" :options="kn_space_role" :value="scope.row.myRole" />
          </template>
        </el-table-column>
        <el-table-column label="标签" align="center" prop="tags" width="120" show-overflow-tooltip />
        <el-table-column label="最后编辑" align="center" prop="lastEditByName" width="100" />
        <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
        <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handleOpen(scope.row)">查看</el-button>
            <el-button v-hasPermi="['kn:doc:edit']" link type="primary" icon="Edit" :disabled="!canEdit(scope.row)" @click="handleEdit(scope.row)"
              >编辑</el-button
            >
            <el-button link type="primary" icon="Clock" @click="handleVersions(scope.row)">版本</el-button>
            <el-button
              v-hasPermi="['kn:doc:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="!canEdit(scope.row)"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 查看 -->
    <el-drawer v-model="detailVisible" :title="detail?.title" size="60%">
      <el-row :gutter="10" class="mb8">
        <el-col v-hasPermi="['kn:doc:edit']" :span="1.5">
          <el-button type="success" plain icon="Promotion" :disabled="detail?.status !== 1 || !canEdit(detail)" @click="handlePublish(detail!)"
            >发布</el-button
          >
        </el-col>
        <el-col v-hasPermi="['kn:doc:edit']" :span="1.5">
          <el-button type="warning" plain icon="Box" :disabled="detail?.status === 3 || !canEdit(detail)" @click="handleArchive(detail!)"
            >归档</el-button
          >
        </el-col>
      </el-row>
      <el-descriptions :column="3" border class="mb8">
        <el-descriptions-item label="空间">{{ detail?.spaceName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="kn_doc_status" :value="detail?.status ?? 1" />
        </el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail?.version }}</el-descriptions-item>
        <el-descriptions-item label="标签">{{ detail?.tags || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后编辑">{{ detail?.lastEditByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail?.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="md-preview" v-html="renderedDetail"></div>
    </el-drawer>

    <!-- 编辑 -->
    <el-drawer v-model="editVisible" :title="editTitle" size="80%" :before-close="handleEditClose">
      <el-form label-width="80px" class="mb8">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="255" placeholder="文档标题" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" maxlength="255" placeholder="逗号分隔" />
        </el-form-item>
      </el-form>
      <div class="editor-split">
        <textarea v-model="editForm.content" class="editor-area" placeholder="Markdown 正文（支持 # 标题、**加粗**、``` 代码块、表格）"></textarea>
        <div class="md-preview editor-preview" v-html="renderedEdit"></div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <span v-if="editId" class="text-gray-400 text-xs mr-2">基线版本 v{{ editForm.baseVersion }}</span>
          <el-button v-if="editId" @click="handleVersions(editRow!)" icon="Clock">版本历史</el-button>
          <el-button type="primary" @click="submitEdit">保存版本</el-button>
          <el-button @click="editVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 版本历史与对比 -->
    <el-drawer v-model="versionVisible" title="版本历史" size="640px">
      <el-table :data="versions" border>
        <el-table-column label="版本" align="center" prop="version" width="70" />
        <el-table-column label="变更摘要" align="left" prop="changeSummary" show-overflow-tooltip />
        <el-table-column label="提交人" align="center" prop="createByName" width="100" />
        <el-table-column label="时间" align="center" prop="createTime" width="170" />
        <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleCompare(scope.row)">对比</el-button>
            <el-button link type="primary" @click="handleViewVersion(scope.row)">内容</el-button>
            <el-button
              v-hasPermi="['kn:doc:edit']"
              link
              type="warning"
              :disabled="scope.row.version === currentDoc?.version"
              @click="handleRollback(scope.row)"
              >回滚</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="compareVisible" title="版本对比（左：历史版本 / 右：当前内容）" width="80%" append-to-body>
      <div class="diff-wrap">
        <div v-for="(line, index) in diffLines" :key="index" :class="['diff-line', `diff-${line.type}`]">
          <span class="diff-sign">{{ line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' ' }}</span
          >{{ line.text }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="KbDocument" lang="ts">
import {
  listDocument,
  getDocument,
  addDocument,
  updateDocument,
  delDocument,
  listVersion,
  getVersion,
  rollbackVersion,
  publishDocument,
  archiveDocument,
  saveDraft as saveDraftApi,
  listSpace,
  DocumentVO,
  DocumentForm,
  DocumentVersionVO
} from '@/api/knowledge';
import { markdownToHtml, diffLines as computeDiff, DiffLine } from '@/utils/markdown';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { kn_space_role, kn_doc_status } = toRefs<any>(proxy?.useDict('kn_space_role', 'kn_doc_status'));

const loading = ref(true);
const list = ref<DocumentVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10, spaceId: undefined, keyword: undefined, deleted: false });
const spaces = ref<any[]>([]);

const detailVisible = ref(false);
const detail = ref<DocumentVO>();
const renderedDetail = computed(() => markdownToHtml(detail.value?.content ?? ''));

const editVisible = ref(false);
const editId = ref<string>();
const editRow = ref<DocumentVO>();
const editForm = ref<DocumentForm>({ spaceId: '', title: '', content: '', tags: '' });
const editTitle = computed(() => (editId.value ? `编辑文档 - ${editForm.value.title}` : '新建文档'));
const renderedEdit = computed(() => markdownToHtml(editForm.value.content ?? ''));

const versionVisible = ref(false);
const currentDoc = ref<DocumentVO>();
const versions = ref<DocumentVersionVO[]>([]);
const compareVisible = ref(false);
const diffLines = ref<DiffLine[]>([]);

let draftTimer: ReturnType<typeof setInterval> | undefined;

const canEdit = (row?: DocumentVO) => !!row && (row.myRole === 'OWNER' || row.myRole === 'EDITOR');

const loadSpaces = async () => {
  const res: any = await listSpace({ pageNum: 1, pageSize: 100 });
  spaces.value = res.data?.records ?? [];
};

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listDocument(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  query.value.pageNum = 1;
  getList();
};

const handleOpen = async (row: DocumentVO) => {
  const res: any = await getDocument(row.id);
  detail.value = res.data;
  detailVisible.value = true;
};

const handleAdd = () => {
  editId.value = undefined;
  editRow.value = undefined;
  editForm.value = { spaceId: query.value.spaceId, title: '', content: '', tags: '' };
  editVisible.value = true;
  startDraftTimer();
};

const handleEdit = async (row: DocumentVO) => {
  const res: any = await getDocument(row.id);
  const doc: DocumentVO = res.data;
  editId.value = doc.id;
  editRow.value = doc;
  editForm.value = {
    spaceId: doc.spaceId,
    title: doc.title,
    content: doc.content ?? '',
    tags: doc.tags,
    baseVersion: doc.version
  };
  editVisible.value = true;
  startDraftTimer();
};

const startDraftTimer = () => {
  stopDraftTimer();
  draftTimer = setInterval(() => {
    if (editId.value && editVisible.value) {
      saveDraft();
    }
  }, 30000);
};

const stopDraftTimer = () => {
  if (draftTimer) {
    clearInterval(draftTimer);
    draftTimer = undefined;
  }
};

const saveDraft = async () => {
  if (!editId.value) return;
  try {
    await saveDraftApi(editId.value, {
      title: editForm.value.title,
      content: editForm.value.content,
      baseVersion: editForm.value.baseVersion
    });
  } catch {
    /* 草稿静默失败 */
  }
};

const submitEdit = async () => {
  if (!editForm.value.title?.trim()) {
    proxy?.$modal.msgWarning('请填写标题');
    return;
  }
  const payload: DocumentForm = { ...editForm.value };
  try {
    if (editId.value) {
      await updateDocument(editId.value, payload);
      proxy?.$modal.msgSuccess('已生成新版本');
    } else {
      await addDocument(payload);
      proxy?.$modal.msgSuccess('创建成功');
    }
    editVisible.value = false;
    stopDraftTimer();
    await getList();
  } catch (e: any) {
    const msg = e?.message ?? String(e);
    if (msg.includes('VERSION_CONFLICT')) {
      proxy?.$modal.msgError('文档已被他人修改，请刷新后重试（版本冲突）');
    } else {
      throw e;
    }
  }
};

const handleEditClose = (done: () => void) => {
  stopDraftTimer();
  done();
};

const handleDelete = async (row: DocumentVO) => {
  await proxy?.$modal.confirm(`确认删除文档「${row.title}」？将移入回收站 30 天内可恢复`);
  await delDocument(row.id);
  proxy?.$modal.msgSuccess('已移入回收站');
  await getList();
};

const handleVersions = async (row: DocumentVO) => {
  currentDoc.value = row;
  const res: any = await listVersion(row.id);
  versions.value = res.data ?? [];
  versionVisible.value = true;
};

const handleViewVersion = async (row: DocumentVersionVO) => {
  const res: any = await getVersion(row.documentId, row.version);
  detail.value = {
    ...(currentDoc.value as any),
    title: `v${row.version} - ${row.title}`,
    content: res.data?.content,
    status: currentDoc.value?.status,
    spaceName: currentDoc.value?.spaceName,
    version: row.version
  };
  versionVisible.value = false;
  detailVisible.value = true;
};

const handleCompare = async (row: DocumentVersionVO) => {
  const res: any = await getVersion(row.documentId, row.version);
  const oldContent: string = res.data?.content ?? '';
  let newContent: string = (currentDoc.value as any)?.content ?? '';
  if (!newContent) {
    const docRes: any = await getDocument(row.documentId);
    newContent = docRes.data?.content ?? '';
  }
  diffLines.value = computeDiff(oldContent, newContent);
  compareVisible.value = true;
};

const handleRollback = async (row: DocumentVersionVO) => {
  await proxy?.$modal.confirm(`确认回滚到版本 v${row.version}？将生成新版本`);
  await rollbackVersion(row.documentId, row.version);
  proxy?.$modal.msgSuccess('回滚成功');
  versionVisible.value = false;
  await getList();
  if (editId.value === row.documentId) {
    await handleEdit({ id: row.documentId } as DocumentVO);
  }
};

const handlePublish = async (row: DocumentVO) => {
  await publishDocument(row.id);
  proxy?.$modal.msgSuccess('发布成功');
  await getList();
};

const handleArchive = async (row: DocumentVO) => {
  await archiveDocument(row.id);
  proxy?.$modal.msgSuccess('已归档');
  await getList();
};

onMounted(async () => {
  await loadSpaces();
  await getList();
  // AI 知识问答引用深链（docs/21 AI-M3-05）：/knowledge/document?docId=xxx 直达文档
  const docId = useRoute().query?.docId;
  if (docId) {
    await handleOpen({ id: String(docId) } as DocumentVO);
  }
});

onBeforeUnmount(() => stopDraftTimer());

defineExpose({ handlePublish, handleArchive });
</script>

<style scoped lang="scss">
.editor-split {
  display: flex;
  gap: 12px;
  height: 56vh;
}
.editor-area {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.7;
  resize: none;
  outline: none;
}
.editor-preview {
  flex: 1;
  overflow: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
}
.md-preview {
  line-height: 1.7;
}
.diff-wrap {
  max-height: 60vh;
  overflow: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.diff-line {
  padding: 2px 8px;
  white-space: pre-wrap;
}
.diff-add {
  background: #f0f9eb;
  color: #67c23a;
}
.diff-del {
  background: #fef0f0;
  color: #f56c6c;
}
.diff-same {
  color: #606266;
}
.diff-sign {
  display: inline-block;
  width: 16px;
}
</style>
