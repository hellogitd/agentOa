<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['fn:budget:add']" type="primary" plain icon="Plus" @click="handleAdd">新增预算</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <div v-show="showSearch" class="mb-[10px]">
        <el-form :model="queryParams" inline>
          <el-form-item label="年度" prop="year">
            <el-input v-model="queryParams.year" placeholder="如 2026" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="类型" prop="budgetType">
            <el-select v-model="queryParams.budgetType" placeholder="预算类型" clearable>
              <el-option label="部门" :value="1" />
              <el-option label="项目" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" border :data="budgetList">
        <el-table-column label="预算编号" align="center" prop="budgetCode" width="140" />
        <el-table-column label="名称" align="center" prop="budgetName" />
        <el-table-column label="类型" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.budgetType === 1 ? 'primary' : 'warning'">{{ scope.row.budgetType === 1 ? '部门' : '项目' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="年度" align="center" prop="year" width="80" />
        <el-table-column label="总额" align="center" prop="totalAmount" width="110" />
        <el-table-column label="已用" align="center" prop="usedAmount" width="110" />
        <el-table-column label="冻结" align="center" prop="frozenAmount" width="110" />
        <el-table-column label="可用" align="center" prop="availableAmount" width="110" />
        <el-table-column label="预警" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.warn" type="danger">超阈值</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '执行' : '关闭' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['fn:budget:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="关闭" placement="top">
              <el-button v-hasPermi="['fn:budget:remove']" link type="danger" icon="CircleClose" @click="handleClose(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="520px" append-to-body>
      <el-form ref="budgetFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="预算编号" prop="budgetCode">
          <el-input v-model="form.budgetCode" placeholder="可空自动生成" maxlength="32" />
        </el-form-item>
        <el-form-item label="名称" prop="budgetName">
          <el-input v-model="form.budgetName" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型" prop="budgetType">
          <el-radio-group v-model="form.budgetType">
            <el-radio :value="1">部门</el-radio>
            <el-radio :value="2">项目</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="归属ID" prop="ownerId">
          <el-input v-model="form.ownerId" placeholder="部门 ID / 项目 ID" />
        </el-form-item>
        <el-form-item label="年度" prop="year">
          <el-input-number v-model="form.year" :min="2020" :max="2100" controls-position="right" />
        </el-form-item>
        <el-form-item label="季度" prop="quarter">
          <el-select v-model="form.quarter" placeholder="空=年度预算" clearable>
            <el-option v-for="q in [1, 2, 3, 4]" :key="q" :label="'Q' + q" :value="q" />
          </el-select>
        </el-form-item>
        <el-form-item label="总额" prop="totalAmount">
          <el-input v-model="form.totalAmount" placeholder="如 100000.00" maxlength="16" />
        </el-form-item>
        <el-form-item label="预警阈值%" prop="warnThreshold">
          <el-input-number v-model="form.warnThreshold" :min="1" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="FinanceBudget" lang="ts">
import { listBudget, addBudget, updateBudget, closeBudget, BudgetForm, BudgetVO } from '@/api/finance';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const budgetList = ref<BudgetVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const dialog = reactive({ title: '', visible: false });
const queryParams = ref<any>({ year: undefined, budgetType: undefined });

const initForm: BudgetForm & { id?: string } = {
  budgetCode: undefined,
  budgetName: undefined,
  budgetType: 1,
  ownerId: '',
  year: new Date().getFullYear(),
  quarter: undefined,
  totalAmount: '',
  warnThreshold: 80,
  remark: undefined
};
const form = ref<BudgetForm & { id?: string }>({ ...initForm });

const rules = ref<any>({
  ownerId: [{ required: true, message: '归属不能为空', trigger: 'blur' }],
  year: [{ required: true, message: '年度不能为空', trigger: 'blur' }],
  totalAmount: [{ required: true, message: '总额不能为空', trigger: 'blur' }]
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listBudget(queryParams.value);
    budgetList.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => getList();

const resetQuery = () => {
  queryParams.value = { year: undefined, budgetType: undefined };
  getList();
};

const handleAdd = () => {
  form.value = { ...initForm };
  dialog.title = '新增预算';
  dialog.visible = true;
};

const handleUpdate = (row: BudgetVO) => {
  form.value = { ...row } as any;
  dialog.title = '修改预算';
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['budgetFormRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  if (form.value.id) {
    await updateBudget(form.value.id, form.value);
    proxy?.$modal.msgSuccess('修改成功');
  } else {
    await addBudget(form.value);
    proxy?.$modal.msgSuccess('新增成功');
  }
  dialog.visible = false;
  await getList();
};

const handleClose = async (row: BudgetVO) => {
  await proxy?.$modal.confirm(`确认关闭预算「${row.budgetCode}」？`);
  await closeBudget(row.id);
  proxy?.$modal.msgSuccess('已关闭');
  await getList();
};

onMounted(() => {
  getList();
});
</script>
