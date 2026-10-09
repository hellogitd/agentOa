<template>
  <el-card shadow="hover" class="dict-card">
    <template #header>
      <div class="dict-card__header">
        <div class="dict-card__title">
          字典数据
          <span class="dict-card__subtitle">{{ currentDictLabel }}</span>
        </div>
        <right-toolbar v-model:show-search="showDataSearch" @query-table="getDataList" />
      </div>
    </template>

    <div v-show="showDataSearch" class="dict-form-scroll">
      <el-form ref="dataQueryFormRef" :model="dataQueryParams" :inline="true">
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input
            v-model="dataQueryParams.dictLabel"
            placeholder="请输入字典标签"
            clearable
            :disabled="!hasCurrentDict"
            @keyup.enter="handleDataQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" :disabled="!hasCurrentDict" @click="handleDataQuery">搜索</el-button>
          <el-button icon="Refresh" :disabled="!hasCurrentDict" @click="handleDataResetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="dict-actions">
      <el-button v-hasPermi="['system:dict:add']" type="primary" plain icon="Plus" :disabled="!hasCurrentDict" @click="handleDataAdd">新增</el-button>
      <el-button
        v-hasPermi="['system:dict:edit']"
        type="success"
        plain
        icon="Edit"
        :disabled="dataSingle || !hasCurrentDict"
        @click="handleDataUpdate()"
        >修改</el-button
      >
      <el-button
        v-hasPermi="['system:dict:remove']"
        type="danger"
        plain
        icon="Delete"
        :disabled="dataMultiple || !hasCurrentDict"
        @click="handleDataDelete()"
        >删除</el-button
      >
      <el-button v-hasPermi="['system:dict:export']" type="warning" plain icon="Download" :disabled="!hasCurrentDict" @click="handleDataExport"
        >导出</el-button
      >
    </div>

    <div class="dict-table-wrap">
      <el-table v-loading="dataLoading" border :data="dataList" @selection-change="handleDataSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column v-if="false" label="字典编码" align="center" prop="dictCode" />
        <el-table-column label="字典标签" align="center" prop="dictLabel" width="80">
          <template #default="scope">
            <span
              v-if="(scope.row.listClass === '' || scope.row.listClass === 'default') && (scope.row.cssClass === '' || scope.row.cssClass == null)"
              >{{ scope.row.dictLabel }}</span
            >
            <el-tag
              v-else
              :type="scope.row.listClass === 'primary' || scope.row.listClass === 'default' ? 'primary' : scope.row.listClass"
              :class="scope.row.cssClass"
              >{{ scope.row.dictLabel }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="字典键值" align="center" prop="dictValue" width="80" />
        <el-table-column label="字典排序" align="center" prop="dictSort" width="80" />
        <el-table-column label="备注" align="center" prop="remark" width="100" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" align="center" width="120" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['system:dict:edit']" link type="primary" icon="Edit" @click="handleDataUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['system:dict:remove']" link type="primary" icon="Delete" @click="handleDataDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <pagination
      v-show="dataTotal > 0"
      v-model:page="dataQueryParams.pageNum"
      v-model:limit="dataQueryParams.pageSize"
      :total="dataTotal"
      @pagination="getDataList"
    />
  </el-card>

  <!-- 字典数据对话框 -->
  <el-dialog v-model="dataDialog.visible" :title="dataDialog.title" width="500px" append-to-body>
    <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="80px">
      <el-form-item label="字典类型">
        <el-input v-model="dataForm.dictType" :disabled="true" />
      </el-form-item>
      <el-form-item label="数据标签" prop="dictLabel">
        <el-input v-model="dataForm.dictLabel" placeholder="请输入数据标签" />
      </el-form-item>
      <el-form-item label="数据键值" prop="dictValue">
        <el-input v-model="dataForm.dictValue" placeholder="请输入数据键值" />
      </el-form-item>
      <el-form-item label="样式属性" prop="cssClass">
        <el-input v-model="dataForm.cssClass" placeholder="请输入样式属性" />
      </el-form-item>
      <el-form-item label="显示排序" prop="dictSort">
        <el-input-number v-model="dataForm.dictSort" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item label="回显样式" prop="listClass">
        <el-select v-model="dataForm.listClass">
          <el-option v-for="item in listClassOptions" :key="item.value" :label="item.label + '(' + item.value + ')'" :value="item.value"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="dataForm.remark" type="textarea" placeholder="请输入内容"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitDataForm">确 定</el-button>
        <el-button @click="cancelData">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="DictDataPanel" lang="ts">
import type { PropType } from 'vue';
import { useDictStore } from '@/store/modules/dict';
import { listData, getData, delData, addData, updateData } from '@/api/system/dict/data';
import { DictTypeVO } from '@/api/system/dict/type/types';
import { DictDataForm, DictDataQuery, DictDataVO } from '@/api/system/dict/data/types';

const props = defineProps({
  /** 当前选中的字典类型（null=未选择） */
  dict: { type: Object as PropType<DictTypeVO | null>, default: null },
  /** 显式选中计数（同字典重选也触发检索重置） */
  selectToken: { type: Number, default: 0 }
});

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const hasCurrentDict = computed(() => !!props.dict);
const currentDictLabel = computed(() => {
  if (!props.dict) return '请先选择字典';
  return `${props.dict.dictName} / ${props.dict.dictType}`;
});

const dataList = ref<DictDataVO[]>([]);
const dataLoading = ref(false);
const showDataSearch = ref(true);
const dataIds = ref<Array<string | number>>([]);
const dataSingle = ref(true);
const dataMultiple = ref(true);
const dataTotal = ref(0);

const dataFormRef = ref<ElFormInstance>();
const dataQueryFormRef = ref<ElFormInstance>();

const dataDialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const listClassOptions = ref<Array<{ value: string; label: string }>>([
  { value: 'default', label: '默认' },
  { value: 'primary', label: '主要' },
  { value: 'success', label: '成功' },
  { value: 'info', label: '信息' },
  { value: 'warning', label: '警告' },
  { value: 'danger', label: '危险' }
]);

const dataInitFormData: DictDataForm = {
  dictCode: undefined,
  dictLabel: '',
  dictValue: '',
  cssClass: '',
  listClass: 'primary',
  dictSort: 0,
  remark: ''
};

const dataState = reactive<PageData<DictDataForm, DictDataQuery>>({
  form: { ...dataInitFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    dictName: '',
    dictType: '',
    dictLabel: ''
  },
  rules: {
    dictLabel: [{ required: true, message: '数据标签不能为空', trigger: 'blur' }],
    dictValue: [{ required: true, message: '数据键值不能为空', trigger: 'blur' }],
    dictSort: [{ required: true, message: '数据顺序不能为空', trigger: 'blur' }]
  }
});

const { queryParams: dataQueryParams, form: dataForm, rules: dataRules } = toRefs(dataState);

// 字典切换（含清空/同字典重选）：重置检索条件并按新类型加载
watch(
  () => [props.dict, props.selectToken] as const,
  ([dict]) => {
    if (!dict) {
      dataQueryParams.value.dictType = '';
      dataList.value = [];
      dataTotal.value = 0;
      return;
    }
    dataQueryParams.value.dictType = dict.dictType;
    dataQueryParams.value.pageNum = 1;
    dataQueryParams.value.dictLabel = '';
    getDataList();
  }
);

const getDataList = async () => {
  if (!props.dict) {
    dataList.value = [];
    dataTotal.value = 0;
    dataLoading.value = false;
    return;
  }
  dataLoading.value = true;
  const res = await listData(dataQueryParams.value);
  dataList.value = res.rows;
  dataTotal.value = res.total;
  dataLoading.value = false;
};

const cancelData = () => {
  dataDialog.visible = false;
  resetDataForm();
};

const resetDataForm = () => {
  dataForm.value = { ...dataInitFormData };
  dataFormRef.value?.resetFields();
};

const handleDataQuery = () => {
  if (!props.dict) return;
  dataQueryParams.value.pageNum = 1;
  getDataList();
};

const handleDataResetQuery = () => {
  dataQueryFormRef.value?.resetFields();
  dataQueryParams.value.dictLabel = '';
  handleDataQuery();
};

const handleDataAdd = () => {
  if (!props.dict) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  resetDataForm();
  dataForm.value.dictType = props.dict.dictType;
  dataDialog.visible = true;
  dataDialog.title = '添加字典数据';
};

const handleDataSelectionChange = (selection: DictDataVO[]) => {
  dataIds.value = selection.map((item) => item.dictCode);
  dataSingle.value = selection.length != 1;
  dataMultiple.value = !selection.length;
};

const handleDataUpdate = async (row?: DictDataVO) => {
  if (!props.dict) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  resetDataForm();
  const dictCode = row?.dictCode || dataIds.value[0];
  const res = await getData(dictCode);
  Object.assign(dataForm.value, res.data);
  dataDialog.visible = true;
  dataDialog.title = '修改字典数据';
};

const submitDataForm = () => {
  dataFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      dataForm.value.dictCode ? await updateData(dataForm.value) : await addData(dataForm.value);
      useDictStore().removeDict(dataQueryParams.value.dictType);
      proxy?.$modal.msgSuccess('操作成功');
      dataDialog.visible = false;
      await getDataList();
    }
  });
};

const handleDataDelete = async (row?: DictDataVO) => {
  if (!props.dict) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  const dictCodes = row?.dictCode || dataIds.value;
  await proxy?.$modal.confirm('是否确认删除字典编码为"' + dictCodes + '"的数据项？');
  await delData(dictCodes);
  await getDataList();
  proxy?.$modal.msgSuccess('删除成功');
  useDictStore().removeDict(dataQueryParams.value.dictType);
};

const handleDataExport = () => {
  if (!props.dict) {
    proxy?.$modal.msgWarning('请先选择字典');
    return;
  }
  proxy?.download(
    'system/dict/data/export',
    {
      ...dataQueryParams.value
    },
    `dict_data_${new Date().getTime()}.xlsx`
  );
};
</script>

<style lang="scss" scoped>
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

.dict-card__subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
