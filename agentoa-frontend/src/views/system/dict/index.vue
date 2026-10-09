<template>
  <div class="p-2 dict-page">
    <el-row :gutter="16" class="dict-grid">
      <!-- 字典类型 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="dict-card">
          <template #header>
            <div class="dict-card__header">
              <div class="dict-card__title">字典管理</div>
              <right-toolbar v-model:show-search="showTypeSearch" @query-table="getTypeList" />
            </div>
          </template>

          <div v-show="showTypeSearch" class="dict-form-scroll">
            <el-form ref="typeQueryFormRef" :model="typeQueryParams" :inline="true">
              <el-form-item label="字典名称" prop="dictName">
                <el-input v-model="typeQueryParams.dictName" placeholder="请输入字典名称" clearable @keyup.enter="handleTypeQuery" />
              </el-form-item>
              <el-form-item label="字典类型" prop="dictType">
                <el-input v-model="typeQueryParams.dictType" placeholder="请输入字典类型" clearable @keyup.enter="handleTypeQuery" />
              </el-form-item>
              <el-form-item label="创建时间" style="width: 308px">
                <el-date-picker
                  v-model="dateRange"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  type="daterange"
                  range-separator="-"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
                ></el-date-picker>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleTypeQuery">搜索</el-button>
                <el-button icon="Refresh" @click="handleTypeResetQuery">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="dict-actions">
            <el-button v-hasPermi="['system:dict:add']" type="primary" plain icon="Plus" @click="handleTypeAdd">新增</el-button>
            <el-button v-hasPermi="['system:dict:edit']" type="success" plain icon="Edit" :disabled="typeSingle" @click="handleTypeUpdate()"
              >修改</el-button
            >
            <el-button v-hasPermi="['system:dict:remove']" type="danger" plain icon="Delete" :disabled="typeMultiple" @click="handleTypeDelete()"
              >删除</el-button
            >
            <el-button v-hasPermi="['system:dict:export']" type="warning" plain icon="Download" @click="handleTypeExport">导出</el-button>
            <el-button v-hasPermi="['system:dict:remove']" type="danger" plain icon="Refresh" @click="handleRefreshCache">刷新缓存</el-button>
          </div>

          <div class="dict-table-wrap">
            <el-table
              ref="typeTableRef"
              v-loading="typeLoading"
              border
              :data="typeList"
              highlight-current-row
              @row-click="handleTypeRowClick"
              @selection-change="handleTypeSelectionChange"
            >
              <el-table-column type="selection" width="55" align="center" />
              <el-table-column v-if="false" label="字典编号" align="center" prop="dictId" />
              <el-table-column label="字典名称" align="center" prop="dictName" width="120" />
              <el-table-column label="字典类型" align="center" prop="dictType" width="160">
                <template #default="scope">
                  <span class="link-type" @click.stop="handleTypeRowClick(scope.row)">{{ scope.row.dictType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="备注" align="center" prop="remark" width="160" />
              <el-table-column label="创建时间" align="center" prop="createTime" width="180">
                <template #default="scope">
                  <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" fixed="right" align="center" width="120" class-name="small-padding fixed-width">
                <template #default="scope">
                  <el-tooltip content="修改" placement="top">
                    <el-button v-hasPermi="['system:dict:edit']" link type="primary" icon="Edit" @click="handleTypeUpdate(scope.row)"></el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button v-hasPermi="['system:dict:remove']" link type="primary" icon="Delete" @click="handleTypeDelete(scope.row)"></el-button>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <pagination
            v-show="typeTotal > 0"
            v-model:page="typeQueryParams.pageNum"
            v-model:limit="typeQueryParams.pageSize"
            :total="typeTotal"
            @pagination="getTypeList"
          />
        </el-card>
      </el-col>

      <!-- 字典数据 -->
      <el-col :xs="24" :lg="12">
        <dict-data-panel :dict="currentDict" :select-token="selectToken" />
      </el-col>
    </el-row>

    <!-- 字典类型对话框 -->
    <dict-type-dialog v-model:visible="typeDialogVisible" :row="typeDialogRow" @success="getTypeList" />
  </div>
</template>

<script setup name="Dict" lang="ts">
import { useDictStore } from '@/store/modules/dict';
import { listType, delType, refreshCache } from '@/api/system/dict/type';
import { DictTypeForm, DictTypeQuery, DictTypeVO } from '@/api/system/dict/type/types';
import DictTypeDialog from './components/DictTypeDialog.vue';
import DictDataPanel from './components/DictDataPanel.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const typeList = ref<DictTypeVO[]>([]);
const typeLoading = ref(true);
const showTypeSearch = ref(true);
const typeIds = ref<Array<number | string>>([]);
const typeSingle = ref(true);
const typeMultiple = ref(true);
const typeTotal = ref(0);
const dateRange = ref<[string, string]>(['', '']);

const typeQueryFormRef = ref<ElFormInstance>();
const typeTableRef = ref<ElTableInstance>();

const typeDialogVisible = ref(false);
const typeDialogRow = ref<DictTypeVO | null>(null);

const typeState = reactive<PageData<DictTypeForm, DictTypeQuery>>({
  form: { dictId: undefined, dictName: '', dictType: '', remark: '' },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    dictName: '',
    dictType: ''
  },
  rules: {}
});

const { queryParams: typeQueryParams } = toRefs(typeState);

const currentDict = ref<DictTypeVO | null>(null);
/** 每次显式选中字典 +1：驱动数据面板重置检索并加载（同字典重选也生效） */
const selectToken = ref(0);

const getTypeList = () => {
  typeLoading.value = true;
  listType(proxy?.addDateRange(typeQueryParams.value, dateRange.value)).then((res) => {
    typeList.value = res.rows;
    typeTotal.value = res.total;
    typeLoading.value = false;
    ensureCurrentType();
  });
};

const ensureCurrentType = () => {
  if (!typeList.value.length) {
    currentDict.value = null;
    return;
  }

  const current = currentDict.value && typeList.value.find((item) => item.dictId === currentDict.value?.dictId);
  const nextRow = current || typeList.value[0];
  setCurrentType(nextRow);
};

const setCurrentType = (row: DictTypeVO) => {
  currentDict.value = row;
  selectToken.value += 1;
  nextTick(() => typeTableRef.value?.setCurrentRow(row));
};

const handleTypeRowClick = (row: DictTypeVO) => {
  setCurrentType(row);
};

const handleTypeQuery = () => {
  typeQueryParams.value.pageNum = 1;
  getTypeList();
};

const handleTypeResetQuery = () => {
  dateRange.value = ['', ''];
  typeQueryFormRef.value?.resetFields();
  handleTypeQuery();
};

const handleTypeAdd = () => {
  typeDialogRow.value = null;
  typeDialogVisible.value = true;
};

const handleTypeSelectionChange = (selection: DictTypeVO[]) => {
  typeIds.value = selection.map((item) => item.dictId);
  typeSingle.value = selection.length != 1;
  typeMultiple.value = !selection.length;
};

const handleTypeUpdate = (row?: DictTypeVO) => {
  const dictId = row?.dictId || typeIds.value[0];
  typeDialogRow.value = typeList.value.find((item) => item.dictId === dictId) ?? null;
  typeDialogVisible.value = true;
};

const handleTypeDelete = async (row?: DictTypeVO) => {
  const dictIds = row?.dictId || typeIds.value;
  await proxy?.$modal.confirm('是否确认删除字典编号为"' + dictIds + '"的数据项？');
  await delType(dictIds);
  getTypeList();
  proxy?.$modal.msgSuccess('删除成功');
};

const handleTypeExport = () => {
  proxy?.download(
    'system/dict/type/export',
    {
      ...typeQueryParams.value
    },
    `dict_${new Date().getTime()}.xlsx`
  );
};

const handleRefreshCache = async () => {
  await refreshCache();
  proxy?.$modal.msgSuccess('刷新成功');
  useDictStore().cleanDict();
};

onMounted(() => {
  getTypeList();
});
</script>

<style lang="scss" scoped>
.dict-grid {
  row-gap: 16px;
}

.dict-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.dict-card__title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-weight: 600;
}

.dict-form-scroll {
  max-height: 200px;
  overflow: auto;
  margin-bottom: 12px;
  padding-right: 6px;
  padding-bottom: 4px;
}

.dict-form-scroll :deep(.el-form) {
  display: flex;
  flex-wrap: wrap;
  column-gap: 12px;
  row-gap: 10px;
}

.dict-form-scroll :deep(.el-form-item) {
  margin: 0;
}

.dict-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 8px 0 12px;
}

.dict-actions :deep(.el-button) {
  height: 32px;
  padding: 0 14px;
}

.dict-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dict-table-wrap {
  overflow-x: auto;
}
</style>
