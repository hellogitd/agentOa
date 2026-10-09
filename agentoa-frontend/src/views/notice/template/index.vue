<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">通知模板</span></el-col>
          <el-col v-hasPermi="['nt:template:add']" :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleCreate">新建模板</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="Refresh" @click="getList">刷新</el-button>
          </el-col>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="list">
        <el-table-column label="编码" align="center" prop="templateCode" width="160" />
        <el-table-column label="名称" align="center" prop="name" width="140" />
        <el-table-column label="标题模板" align="center" prop="titleTpl" min-width="180" show-overflow-tooltip />
        <el-table-column label="消息类型" align="center" width="100">
          <template #default="scope">
            <dict-tag :options="nc_msg_type" :value="scope.row.msgType" />
          </template>
        </el-table-column>
        <el-table-column label="变量" align="center" min-width="160">
          <template #default="scope">
            <el-tag v-for="v in scope.row.vars || []" :key="v" size="small" class="mr-1 mb-1">{{ '{' + v + '}' }}</el-tag>
            <span v-if="!(scope.row.vars || []).length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['nt:template:send']" link type="primary" icon="Promotion" @click="handleSend(scope.row)">发送</el-button>
            <el-button v-hasPermi="['nt:template:edit']" link type="primary" icon="Edit" @click="handleEdit(scope.row)">修改</el-button>
            <el-button v-hasPermi="['nt:template:remove']" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="form.templateCode" placeholder="小写字母/数字/下划线，如 onboarding_welcome" :disabled="!!dialog.id" />
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="消息类型" prop="msgType">
              <el-select v-model="form.msgType" class="w-full">
                <el-option v-for="dict in nc_msg_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio value="1">启用</el-radio>
                <el-radio value="2">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题模板" prop="titleTpl">
          <el-input v-model="form.titleTpl" placeholder="支持 {变量} 占位符" />
        </el-form-item>
        <el-form-item label="内容模板" prop="contentTpl">
          <el-input v-model="form.contentTpl" type="textarea" :rows="5" placeholder="支持 {变量} 占位符，不支持表达式" />
        </el-form-item>
        <el-form-item label="变量声明">
          <el-input v-model="varsText" placeholder="逗号分隔，如 name,dept,date（{变量} 必须先声明）" />
          <div class="form-tip">占位符白名单替换，未声明变量保存时拒绝；不支持表达式/脚本。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog :title="`发送模板：${sending.name || ''}`" v-model="sendVisible" width="560px" append-to-body>
      <el-form :model="sendForm" label-width="100px">
        <el-form-item label="受众范围" required>
          <el-select v-model="sendForm.scopeType" class="w-full">
            <el-option v-for="dict in nc_scope_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="sendForm.scopeType !== '1'" label="范围值">
          <el-input v-model="sendForm.scopeValues" :placeholder="scopePlaceholder" />
        </el-form-item>
        <el-divider content-position="left">模板变量</el-divider>
        <el-form-item v-for="v in sending.vars || []" :key="v" :label="v">
          <el-input v-model="sendForm.vars[v]" :placeholder="`请输入 ${v} 取值`" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitSend">发 送</el-button>
          <el-button @click="sendVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="NoticeTemplate" lang="ts">
import { ComponentInternalInstance, computed, getCurrentInstance, onMounted, reactive, ref, toRefs } from 'vue';
import { listTemplate, addTemplate, updateTemplate, delTemplate, sendTemplate, NoticeTemplateVO, NoticeTemplateForm } from '@/api/notice';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { nc_msg_type, nc_scope_type } = toRefs<any>(proxy?.useDict('nc_msg_type', 'nc_scope_type'));

const loading = ref(true);
const list = ref<NoticeTemplateVO[]>([]);
const total = ref(0);
const query = ref<any>({ pageNum: 1, pageSize: 10 });

const dialog = reactive<any>({ visible: false, title: '', id: undefined as string | undefined });
const formRef = ref();
const form = ref<NoticeTemplateForm>({ templateCode: '', name: '', titleTpl: '', contentTpl: '', msgType: 'NOTICE', vars: [], status: '1' });
const varsText = ref('');
const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  titleTpl: [{ required: true, message: '请输入标题模板', trigger: 'blur' }],
  contentTpl: [{ required: true, message: '请输入内容模板', trigger: 'blur' }],
  msgType: [{ required: true, message: '请选择消息类型', trigger: 'change' }]
};

const sendVisible = ref(false);
const sending = ref<NoticeTemplateVO>({ id: '', templateCode: '', name: '', titleTpl: '', contentTpl: '', msgType: 'NOTICE', vars: [], status: 1 });
const sendForm = ref<any>({ scopeType: '1', scopeValues: '', vars: {} });

const scopePlaceholder = computed(() => {
  if (sendForm.value.scopeType === '2') return '部门 ID，逗号分隔，如 100,103';
  if (sendForm.value.scopeType === '3') return '角色 key，逗号分隔，如 hr,finance';
  return '用户 ID，逗号分隔';
});

const getList = async () => {
  loading.value = true;
  try {
    const res: any = await listTemplate(query.value);
    list.value = res.data?.records ?? [];
    total.value = res.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const parseVars = (text: string): string[] =>
  text
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

const handleCreate = () => {
  form.value = { templateCode: '', name: '', titleTpl: '', contentTpl: '', msgType: 'NOTICE', vars: [], status: '1' };
  varsText.value = '';
  dialog.title = '新建模板';
  dialog.id = undefined;
  dialog.visible = true;
};

const handleEdit = (row: NoticeTemplateVO) => {
  form.value = {
    templateCode: row.templateCode,
    name: row.name,
    titleTpl: row.titleTpl,
    contentTpl: row.contentTpl,
    msgType: row.msgType,
    vars: [...(row.vars || [])],
    status: String(row.status),
    remark: row.remark
  };
  varsText.value = (row.vars || []).join(',');
  dialog.title = '修改模板';
  dialog.id = row.id;
  dialog.visible = true;
};

const submitForm = async () => {
  const valid = await (proxy?.$refs['formRef'] as any)?.validate().catch(() => false);
  if (!valid) return;
  const payload: NoticeTemplateForm = { ...form.value, vars: parseVars(varsText.value) };
  if (dialog.id) {
    await updateTemplate(dialog.id, payload);
    proxy?.$modal.msgSuccess('保存成功');
  } else {
    await addTemplate(payload);
    proxy?.$modal.msgSuccess('创建成功');
  }
  dialog.visible = false;
  await getList();
};

const handleDelete = async (row: NoticeTemplateVO) => {
  await proxy?.$modal.confirm(`确认删除模板「${row.name}」？`);
  await delTemplate(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

const handleSend = (row: NoticeTemplateVO) => {
  sending.value = row;
  sendForm.value = { scopeType: '1', scopeValues: '', vars: {} };
  for (const v of row.vars || []) {
    sendForm.value.vars[v] = '';
  }
  sendVisible.value = true;
};

const submitSend = async () => {
  await sendTemplate(sending.value.templateCode, sendForm.value);
  proxy?.$modal.msgSuccess('发送成功');
  sendVisible.value = false;
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
