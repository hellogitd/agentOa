<template>
  <div class="app-container">
    <el-card>
      <div class="toolbar">
        <el-button v-hasPermi="['wf:definition:add']" type="primary" @click="openWizard()">新建流程</el-button>
        <el-button v-hasPermi="['wf:definition:enable']" type="success" @click="enableAll">一键启用模板</el-button>
        <el-button v-hasPermi="['wf:category:add']" @click="openCategories">分类管理</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="definitions" border>
        <el-table-column prop="processKey" label="流程Key" width="150" />
        <el-table-column prop="processName" label="流程名称" width="150" />
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column prop="businessType" label="业务类型" width="110" />
        <el-table-column prop="currentVersionNo" label="当前版本" width="90">
          <template #default="{ row }">v{{ row.currentVersionNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'RETIRED' ? 'info' : 'warning'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.builtin" size="small" type="info">内置</el-tag>
            <el-tag v-else size="small">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['wf:definition:edit']" link type="primary" @click="openWizard(row)">编辑</el-button>
            <el-button v-hasPermi="['wf:definition:edit']" link :type="row.status === 'PUBLISHED' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'PUBLISHED' ? '停用' : '启用' }}
            </el-button>
            <el-button v-hasPermi="['wf:definition:query']" link type="primary" @click="openVersions(row)">版本</el-button>
            <el-button v-hasPermi="['wf:definition:query']" link type="primary" @click="showXml(row)">BPMN</el-button>
            <el-button v-hasPermi="['wf:definition:query']" link type="primary" @click="showDiagram(row)">流程图</el-button>
            <el-button v-hasPermi="['wf:definition:remove']" link type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <DefinitionWizard v-model="wizardVisible" :definition="editing" @saved="load" />

    <el-dialog v-model="versionsVisible" :title="`版本 - ${current?.processName || ''}`" width="760px">
      <el-table :data="current?.versions || []" border size="small">
        <el-table-column prop="versionNo" label="版本" width="70">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.compiled" size="small" type="success">编译产物</el-tag>
            <el-tag v-else size="small" type="info">内置模板</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bpmnResource" label="BPMN 来源" min-width="200" show-overflow-tooltip />
        <el-table-column prop="validationSummary" label="校验摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="publishedTime" label="发布时间" width="160">
          <template #default="{ row }">{{ formatTime(row.publishedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['wf:definition:add']" link type="primary" @click="doPublish(row)"> 发布 </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="xmlVisible" title="BPMN XML" width="760px">
      <el-input type="textarea" :rows="18" :model-value="xmlContent" readonly />
    </el-dialog>

    <el-dialog v-model="diagramVisible" title="流程图" width="760px">
      <el-image v-if="diagramUrl" :src="diagramUrl" fit="contain" style="max-height: 420px" />
    </el-dialog>

    <el-dialog v-model="categoriesVisible" title="流程分类" width="640px">
      <div class="toolbar">
        <el-button v-hasPermi="['wf:category:add']" type="primary" size="small" @click="openCategoryForm()">新增分类</el-button>
      </div>
      <el-table :data="categories" border size="small">
        <el-table-column prop="code" label="编码" width="110" />
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column prop="status" label="状态" width="70">
          <template #default="{ row }">{{ row.status === '0' ? '正常' : '停用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button v-hasPermi="['wf:category:edit']" link type="primary" @click="openCategoryForm(row)">修改</el-button>
            <el-button v-hasPermi="['wf:category:remove']" link type="danger" @click="doDeleteCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="categoryFormVisible" :title="categoryForm.id ? '修改分类' : '新增分类'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编码" required>
          <el-input v-model="categoryForm.code" :disabled="!!categoryForm.id" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="categoryForm.name" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import DefinitionWizard from '../components/DefinitionWizard.vue';
import {
  addCategory,
  changeDefinitionStatus,
  delCategory,
  enableBuiltinTemplates,
  getDefinitionDiagram,
  getDefinitionXml,
  listCategories,
  listDefinitions,
  publishDefinition,
  removeDefinition,
  updateCategory,
  type CategoryVO,
  type DefinitionVO,
  type DefinitionVersionVO
} from '@/api/workflow';

const loading = ref(false);
const definitions = ref<DefinitionVO[]>([]);
const categories = ref<CategoryVO[]>([]);

const wizardVisible = ref(false);
const editing = ref<DefinitionVO | null>(null);
const versionsVisible = ref(false);
const xmlVisible = ref(false);
const diagramVisible = ref(false);
const categoriesVisible = ref(false);
const categoryFormVisible = ref(false);
const current = ref<DefinitionVO | null>(null);
const xmlContent = ref('');
const diagramUrl = ref('');

const categoryForm = reactive<{ id?: string; code: string; name: string; sort: number }>({
  id: undefined,
  code: '',
  name: '',
  sort: 0
});

function statusLabel(status: string) {
  return status === 'PUBLISHED' ? '已启用' : status === 'RETIRED' ? '已停用' : '草稿';
}

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function openWizard(row?: DefinitionVO) {
  editing.value = row || null;
  wizardVisible.value = true;
}

async function toggleStatus(row: DefinitionVO) {
  const enabling = row.status !== 'PUBLISHED';
  await ElMessageBox.confirm(
    enabling ? `确认启用流程「${row.processName}」？` : `确认停用流程「${row.processName}」？停用后不可发起新流程，在途实例继续。`,
    enabling ? '启用流程' : '停用流程',
    { type: 'warning' }
  );
  await changeDefinitionStatus(row.id, enabling ? 'PUBLISHED' : 'RETIRED');
  ElMessage.success(enabling ? '已启用' : '已停用');
  await load();
}

async function enableAll() {
  await ElMessageBox.confirm('确认一键启用全部内置流程模板？已启用的模板不受影响。', '一键启用模板', { type: 'warning' });
  const res: any = await enableBuiltinTemplates();
  ElMessage.success(res.data ? `已启用 ${res.data} 个模板` : '模板均已启用');
  await load();
}

async function doDelete(row: DefinitionVO) {
  await ElMessageBox.confirm(`确认删除流程「${row.processName}」？删除后不可恢复。内置模板与已有实例的流程无法删除。`, '删除流程', {
    type: 'warning'
  });
  await removeDefinition(row.id);
  ElMessage.success('已删除');
  await load();
}

function openVersions(row: DefinitionVO) {
  current.value = row;
  versionsVisible.value = true;
}

async function showXml(row: DefinitionVO) {
  const res: any = await getDefinitionXml(row.id);
  xmlContent.value = res.data;
  xmlVisible.value = true;
}

async function showDiagram(row: DefinitionVO) {
  const res: any = await getDefinitionDiagram(row.id);
  if (diagramUrl.value) URL.revokeObjectURL(diagramUrl.value);
  diagramUrl.value = URL.createObjectURL(res as Blob);
  diagramVisible.value = true;
}

async function doPublish(row: DefinitionVersionVO) {
  if (!current.value) return;
  await ElMessageBox.confirm(`确认发布 v${row.versionNo}？发布后版本不可变。`, '发布版本', { type: 'warning' });
  await publishDefinition(current.value.id, row.versionNo);
  ElMessage.success('已发布');
  versionsVisible.value = false;
  await load();
}

async function openCategories() {
  const res: any = await listCategories();
  categories.value = res.data;
  categoriesVisible.value = true;
}

function openCategoryForm(row?: CategoryVO) {
  categoryForm.id = row?.id;
  categoryForm.code = row?.code || '';
  categoryForm.name = row?.name || '';
  categoryForm.sort = row?.sort ?? 0;
  categoryFormVisible.value = true;
}

async function saveCategory() {
  const payload = { code: categoryForm.code, name: categoryForm.name, sort: categoryForm.sort, status: '0' };
  if (categoryForm.id) {
    await updateCategory(categoryForm.id, payload);
  } else {
    await addCategory(payload);
  }
  ElMessage.success('已保存');
  categoryFormVisible.value = false;
  await openCategories();
}

async function doDeleteCategory(row: CategoryVO) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '删除分类', { type: 'warning' });
  await delCategory(row.id);
  ElMessage.success('已删除');
  await openCategories();
}

async function load() {
  loading.value = true;
  try {
    const res: any = await listDefinitions();
    definitions.value = res.data;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
