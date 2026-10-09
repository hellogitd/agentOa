<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">定时推送</span></el-col>
          <el-col v-hasPermi="['nt:schedule:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreate">新建推送</el-button>
          </el-col>
          <el-col v-hasPermi="['nt:schedule:run']" :span="1.5">
            <el-button plain icon="Timer" @click="handleRunNow">执行到期</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="任务名称" align="center" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="nc_push_type" :value="scope.row.pushType" />
          </template>
        </el-table-column>
        <el-table-column label="目标" align="center" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            {{
              scope.row.pushType === 1 ? scope.row.announcementTitle || scope.row.announcementId : scope.row.templateName || scope.row.templateCode
            }}
          </template>
        </el-table-column>
        <el-table-column label="调度" align="center" width="170">
          <template #default="scope">
            <span v-if="scope.row.scheduleType === 1">{{ scope.row.runAt }}</span>
            <el-tag v-else size="small" type="warning">{{ scope.row.cronExpr }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次执行" align="center" prop="nextRunTime" width="160">
          <template #default="scope">{{ scope.row.nextRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="已执行" align="center" prop="runCount" width="80" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '暂停' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" align="center" min-width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.lastError || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['nt:schedule:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button
              v-hasPermi="['nt:schedule:edit']"
              link
              :type="scope.row.status === 1 ? 'warning' : 'success'"
              icon="VideoPause"
              @click="handleToggle(scope.row)"
            >
              {{ scope.row.status === 1 ? '暂停' : '恢复' }}
            </el-button>
            <el-button v-hasPermi="['nt:schedule:list']" link type="primary" icon="Tickets" @click="handleRuns(scope.row)">历史</el-button>
            <el-button v-hasPermi="['nt:schedule:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入任务名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="推送类型" prop="pushType">
              <el-select v-model="form.pushType" class="w-full" @change="handleTypeChange">
                <el-option v-for="dict in nc_push_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="1">启用</el-radio>
                <el-radio value="2">暂停</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item v-if="form.pushType === '1'" label="定时公告" prop="announcementId">
          <el-select v-model="form.announcementId" filterable placeholder="选择草稿公告（到点自动发布）" class="w-full">
            <el-option v-for="a in draftAnnouncements" :key="a.id" :label="a.title" :value="a.id" />
          </el-select>
        </el-form-item>

        <template v-if="form.pushType === '2'">
          <el-form-item label="通知模板" prop="templateId">
            <el-select v-model="form.templateId" class="w-full" @change="handleTemplateChange">
              <el-option v-for="t in templates" :key="t.id" :label="`${t.name}（${t.templateCode}）`" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="受众范围" prop="scopeType">
            <el-select v-model="form.scopeType" class="w-full">
              <el-option v-for="dict in nc_scope_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.scopeType !== '1'" label="范围值">
            <el-input v-model="form.scopeValues" :placeholder="scopePlaceholder" />
          </el-form-item>
          <el-divider content-position="left">模板变量</el-divider>
          <el-form-item v-for="v in currentTemplateVars" :key="v" :label="v">
            <el-input v-model="form.vars[v]" :placeholder="`请输入 ${v} 取值`" />
          </el-form-item>
        </template>

        <el-form-item label="调度方式" prop="scheduleType">
          <el-radio-group v-model="form.scheduleType">
            <el-radio v-for="dict in nc_schedule_type" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scheduleType === '1'" label="执行时间" prop="runAt">
          <el-date-picker v-model="form.runAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="单次执行时间" />
        </el-form-item>
        <el-form-item v-else label="周期表达式" prop="cronExpr">
          <el-input v-model="form.cronExpr" placeholder="5字段 cron 子集，如 0 10 * * 1-5" />
          <div class="form-tip">
            分 时 日 月 周（周 0/7=周日）；支持 *、*/n、数字、a-b、逗号列表；例如：0 10 * * *（每天10点）、0 8 * * 1-5（工作日8点）、0 9 1 *
            *（每月1日9点）。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog :title="`执行历史：${runsOf || ''}`" v-model="runsVisible" width="720px" append-to-body>
      <el-table v-loading="runsLoading" border :data="runs">
        <el-table-column label="计划时间" align="center" prop="slotTime" width="160" />
        <el-table-column label="结果" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">{{ scope.row.status === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投递人数" align="center" prop="receiverCount" width="100" />
        <el-table-column label="错误" align="center" prop="error" show-overflow-tooltip />
        <el-table-column label="执行时间" align="center" prop="createTime" width="160" />
      </el-table>
      <pagination
        v-show="runsTotal > 0"
        v-model:page="runsQuery.pageNum"
        v-model:limit="runsQuery.pageSize"
        :total="runsTotal"
        @pagination="loadRuns"
      />
    </el-dialog>
  </div>
</template>

<script setup name="NoticeSchedule" lang="ts">
import { ComponentInternalInstance, computed, getCurrentInstance, onMounted, reactive, ref, toRefs } from 'vue';
import {
  listPush,
  addPush,
  updatePush,
  delPush,
  pausePush,
  resumePush,
  listPushRuns,
  runPushNow,
  listTemplate,
  listAnnouncement,
  ScheduledPushVO,
  ScheduledPushForm,
  PushRunVO,
  NoticeTemplateVO
} from '@/api/notice';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { nc_push_type, nc_schedule_type, nc_scope_type } = toRefs<any>(proxy?.useDict('nc_push_type', 'nc_schedule_type', 'nc_scope_type'));

const loading = ref(true);
const list = ref<ScheduledPushVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10 });

const templates = ref<NoticeTemplateVO[]>([]);
const draftAnnouncements = ref<any[]>([]);

const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<ScheduledPushForm>({
  name: '',
  pushType: '2',
  templateId: undefined,
  announcementId: undefined,
  scopeType: '1',
  scopeValues: '',
  vars: {},
  scheduleType: '1',
  runAt: undefined,
  cronExpr: '',
  status: '1'
});
const rules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  pushType: [{ required: true, message: '请选择推送类型', trigger: 'change' }],
  scheduleType: [{ required: true, message: '请选择调度方式', trigger: 'change' }]
};

const runsVisible = ref(false);
const runsLoading = ref(false);
const runsOf = ref('');
const runs = ref<PushRunVO[]>([]);
const runsTotal = ref(0);
const runsQuery = ref<any>({ pageNum: 1, pageSize: 10 });

const scopePlaceholder = computed(() => {
  if (form.value.scopeType === '2') return '部门 ID，逗号分隔，如 100,103';
  if (form.value.scopeType === '3') return '角色 key，逗号分隔，如 hr,finance';
  return '用户 ID，逗号分隔';
});

const currentTemplateVars = computed(() => {
  const template = templates.value.find((t) => String(t.id) === String(form.value.templateId));
  return template?.vars || [];
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listPush(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const loadOptions = async () => {
  const tplRes: any = await listTemplate({ pageNum: 1, pageSize: 100 });
  templates.value = tplRes.data?.records ?? [];
  const annRes: any = await listAnnouncement({ pageNum: 1, pageSize: 100, status: 1 });
  draftAnnouncements.value = annRes.data?.records ?? [];
};

const handleCreate = () => {
  form.value = {
    name: '',
    pushType: '2',
    templateId: templates.value[0]?.id,
    announcementId: undefined,
    scopeType: '1',
    scopeValues: '',
    vars: {},
    scheduleType: '1',
    runAt: undefined,
    cronExpr: '',
    status: '1'
  };
  dialog.title = '新建推送';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: ScheduledPushVO) => {
  form.value = {
    name: row.name,
    pushType: String(row.pushType),
    templateId: row.templateId,
    announcementId: row.announcementId,
    scopeType: row.scopeType ? String(row.scopeType) : '1',
    scopeValues: row.scopeValues,
    vars: { ...(row.vars || {}) },
    scheduleType: String(row.scheduleType),
    runAt: row.runAt,
    cronExpr: row.cronExpr,
    status: String(row.status)
  };
  dialog.title = '修改推送';
  dialog.id = row.id;
  dialog.visible = true;
};

const handleTypeChange = () => {
  form.value.templateId = form.value.pushType === '2' ? templates.value[0]?.id : undefined;
  form.value.announcementId = undefined;
};

const handleTemplateChange = () => {
  form.value.vars = {};
  for (const v of currentTemplateVars.value) {
    form.value.vars![v] = '';
  }
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: ScheduledPushForm = { ...form.value };
  if (dialog.id) {
    await updatePush(dialog.id, payload);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await addPush(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleToggle = async (row: ScheduledPushVO) => {
  if (row.status === 1) {
    await proxy?.$modal.confirm(`确认暂停「${row.name}」？`);
    await pausePush(row.id);
    proxy?.$modal.msgSuccess('已暂停');
  } else {
    await proxy?.$modal.confirm(`确认恢复「${row.name}」？`);
    await resumePush(row.id);
    proxy?.$modal.msgSuccess('已恢复');
  }
  await getList();
};

const handleDelete = async (row: ScheduledPushVO) => {
  await proxy?.$modal.confirm(`确认删除「${row.name}」及其执行历史？`);
  await delPush(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleRunNow = async () => {
  const res: any = await runPushNow();
  proxy?.$modal.msgSuccess(`执行完成：${res.data ?? 0} 个任务`);
  await getList();
};

const handleRuns = async (row: ScheduledPushVO) => {
  runsOf.value = row.name;
  runsQuery.value = { pageNum: 1, pageSize: 10 };
  runsVisible.value = true;
  await loadRuns(row.id);
};

let runsPushId = '';
const loadRuns = async (pushId?: string) => {
  if (pushId) runsPushId = pushId;
  runsLoading.value = true;
  try {
    const res: any = await listPushRuns(runsPushId, runsQuery.value);
    runs.value = res.data?.records ?? [];
    runsTotal.value = res.data?.total ?? 0;
  } finally {
    runsLoading.value = false;
  }
};

onMounted(async () => {
  await getList();
  await loadOptions();
});
</script>

<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
