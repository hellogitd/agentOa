<template>
  <div class="p-2 ai-copilot">
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">业务助手</span></el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="loadScenes">刷新</el-button>
          </el-col>
          <el-col :span="12">
            <el-tag type="warning">AI 生成内容仅供参考，人工确认后使用</el-tag>
          </el-col>
        </el-row>
      </template>

      <el-row :gutter="12">
        <el-col v-for="scene in scenes" :key="scene.code" :xs="24" :sm="12" :md="8" class="mb-3">
          <el-card shadow="never" class="scene-card">
            <div class="scene-title">
              <span>{{ scene.name }}</span>
              <el-tag v-if="scene.enabled !== 1" type="info" size="small">已停用</el-tag>
            </div>
            <div class="scene-desc">{{ scene.description }}</div>
            <div class="scene-meta">
              <span>模型：{{ scene.modelName || '全局默认' }}</span>
              <span>模板：{{ scene.promptTemplateName || '内置' }}</span>
            </div>
            <div class="scene-actions">
              <el-button v-hasPermi="['ai:copilot:use']" type="primary" plain size="small" :disabled="scene.enabled !== 1" @click="openRun(scene)"
                >生成</el-button
              >
              <el-button v-hasPermi="['ai:copilot:config']" size="small" @click="openConfig(scene)">配置</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover" class="mt-3">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><span class="card-header-label">生成任务</span></el-col>
          <el-col :span="1.5">
            <el-button icon="Refresh" @click="loadTasks">刷新</el-button>
          </el-col>
        </el-row>
      </template>
      <el-table v-loading="tasksLoading" :data="tasks" border>
        <el-table-column label="场景" align="left" prop="sceneName" min-width="120" />
        <el-table-column label="业务" align="center" width="160">
          <template #default="scope">
            <span v-if="scope.row.bizType">{{ scope.row.bizType }}#{{ scope.row.bizId ?? '-' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <dict-tag :options="ai_copilot_task" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="tokens" align="center" prop="totalTokens" width="90" />
        <el-table-column label="失败原因" align="left" prop="errorMsg" min-width="160" show-overflow-tooltip />
        <el-table-column label="完成时间" align="center" prop="finishTime" width="170" />
        <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ai:copilot:use']" link type="primary" icon="View" @click="viewTask(scope.row)">查看</el-button>
            <el-button
              v-hasPermi="['ai:copilot:use']"
              link
              type="warning"
              icon="RefreshRight"
              :disabled="scope.row.status === 'running'"
              @click="handleRetry(scope.row)"
              >重试</el-button
            >
            <el-button v-hasPermi="['ai:copilot:use']" link type="danger" icon="Delete" @click="handleDeleteTask(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="taskTotal > 0"
        v-model:page="taskQuery.pageNum"
        v-model:limit="taskQuery.pageSize"
        :total="taskTotal"
        @pagination="loadTasks"
      />
    </el-card>

    <!-- 生成抽屉 -->
    <el-drawer v-model="runVisible" :title="runScene?.name ?? '业务助手'" size="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="用户意图">
          <el-input v-model="runForm.instruction" type="textarea" :rows="3" maxlength="4000" placeholder="想让 AI 做什么，如：请把要点整理成通知" />
        </el-form-item>
        <el-form-item label="业务摘要">
          <el-input v-model="runForm.content" type="textarea" :rows="6" maxlength="20000" placeholder="可粘贴表单字段/指标/会议记录等只读摘要" />
        </el-form-item>
        <el-form-item label="业务 ID">
          <el-input v-model="runForm.bizId" placeholder="可选；填写后服务端只读补充业务摘要" maxlength="20" />
        </el-form-item>
      </el-form>
      <div class="mb-2">
        <el-button type="primary" :loading="streaming" @click="startRun">生成</el-button>
        <el-button v-if="streaming" type="danger" @click="stopRun">停止</el-button>
      </div>
      <el-alert type="warning" :closable="false" class="mb-2" title="AI 生成内容仅供参考，请人工核对后使用" />
      <el-input v-model="runOutput" type="textarea" :rows="12" readonly placeholder="生成结果将在此展示" />
    </el-drawer>

    <!-- 配置弹窗 -->
    <el-dialog title="场景配置" v-model="configVisible" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="启停">
          <el-switch v-model="configForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="生成模型">
          <el-select v-model="configForm.modelId" clearable placeholder="全局默认模型" class="w-full">
            <el-option v-for="m in modelOptions" :key="m.id" :label="m.alias || m.modelKey" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词模板">
          <el-select v-model="configForm.promptTemplateId" clearable placeholder="内置模板" class="w-full">
            <el-option v-for="t in templateOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitConfig">确 定</el-button>
          <el-button @click="configVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 任务详情 -->
    <el-dialog title="任务结果" v-model="taskVisible" width="620px" append-to-body>
      <el-alert type="warning" :closable="false" class="mb-2" title="AI 生成内容仅供参考，请人工核对后使用" />
      <el-input :model-value="taskOutput" type="textarea" :rows="14" readonly />
    </el-dialog>
  </div>
</template>

<script setup name="AiCopilot" lang="ts">
import {
  listCopilotScenes,
  updateCopilotScene,
  listCopilotTasks,
  getCopilotTask,
  retryCopilotTask,
  deleteCopilotTask,
  listModels,
  listPromptTemplates,
  CopilotSceneVO,
  CopilotTaskVO,
  CopilotSceneForm
} from '@/api/ai';
import { streamCopilotGenerate } from '@/api/ai/stream';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { ai_copilot_task } = toRefs<any>(proxy?.useDict('ai_copilot_task'));

const scenes = ref<CopilotSceneVO[]>([]);
const tasks = ref<CopilotTaskVO[]>([]);
const tasksLoading = ref(false);
const taskTotal = ref(0);
const taskQuery = reactive({ pageNum: 1, pageSize: 10 });

const runVisible = ref(false);
const streaming = ref(false);
const runScene = ref<CopilotSceneVO | null>(null);
const runOutput = ref('');
const runForm = reactive({ instruction: '', content: '', bizId: '' });
let activeStream: AbortController | null = null;

const configVisible = ref(false);
const configSceneCode = ref('');
const configForm = reactive<CopilotSceneForm>({ enabled: 1, modelId: undefined, promptTemplateId: undefined });
const modelOptions = ref<any[]>([]);
const templateOptions = ref<any[]>([]);

const taskVisible = ref(false);
const taskOutput = ref('');

const loadScenes = async () => {
  const res: any = await listCopilotScenes();
  scenes.value = res.data ?? [];
};

const loadTasks = async () => {
  tasksLoading.value = true;
  try {
    const res: any = await listCopilotTasks(taskQuery);
    tasks.value = res.data.records;
    taskTotal.value = res.data.total;
  } finally {
    tasksLoading.value = false;
  }
};

const openRun = (scene: CopilotSceneVO) => {
  runScene.value = scene;
  runForm.instruction = '';
  runForm.content = '';
  runForm.bizId = '';
  runOutput.value = '';
  runVisible.value = true;
};

const startRun = () => {
  if (!runScene.value) {
    return;
  }
  if (!runForm.instruction.trim() && !runForm.content.trim() && !runForm.bizId.trim()) {
    proxy?.$modal.msgError('请填写业务摘要或业务 ID');
    return;
  }
  streaming.value = true;
  runOutput.value = '';
  activeStream = streamCopilotGenerate(
    runScene.value.code,
    {
      instruction: runForm.instruction,
      content: runForm.content,
      bizId: runForm.bizId ? runForm.bizId : undefined
    },
    {
      onDelta: (text) => {
        runOutput.value += text;
      },
      onDone: () => {
        streaming.value = false;
        loadTasks();
      },
      onError: (error) => {
        streaming.value = false;
        proxy?.$modal.msgError(error.msg || 'AI 生成失败');
      }
    }
  );
};

const stopRun = () => {
  activeStream?.abort();
  activeStream = null;
  streaming.value = false;
};

const openConfig = async (scene: CopilotSceneVO) => {
  configSceneCode.value = scene.code;
  configForm.enabled = scene.enabled;
  configForm.modelId = scene.modelId;
  configForm.promptTemplateId = scene.promptTemplateId;
  if (!modelOptions.value.length) {
    const res: any = await listModels({ pageNum: 1, pageSize: 100 });
    modelOptions.value = res.data.records ?? [];
  }
  if (!templateOptions.value.length) {
    const res: any = await listPromptTemplates({ pageNum: 1, pageSize: 100 });
    templateOptions.value = res.data.records ?? [];
  }
  configVisible.value = true;
};

const submitConfig = async () => {
  await updateCopilotScene(configSceneCode.value, { ...configForm });
  proxy?.$modal.msgSuccess('配置已更新');
  configVisible.value = false;
  await loadScenes();
};

const viewTask = async (row: CopilotTaskVO) => {
  const res: any = await getCopilotTask(row.id);
  taskOutput.value = res.data.output || '（无内容）';
  taskVisible.value = true;
};

const handleRetry = async (row: CopilotTaskVO) => {
  await retryCopilotTask(row.id);
  proxy?.$modal.msgSuccess('已提交重试');
  await loadTasks();
};

const handleDeleteTask = async (row: CopilotTaskVO) => {
  await proxy?.$modal.confirm(`确认删除任务「${row.sceneName ?? row.sceneCode}」？`);
  await deleteCopilotTask(row.id);
  proxy?.$modal.msgSuccess('删除成功');
  await loadTasks();
};

onMounted(() => {
  loadScenes();
  loadTasks();
});

onBeforeUnmount(() => activeStream?.abort());
</script>

<style scoped lang="scss">
.ai-copilot {
  .scene-card {
    height: 100%;
  }
  .scene-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
  }
  .scene-desc {
    margin-top: 8px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    min-height: 32px;
  }
  .scene-meta {
    margin-top: 8px;
    display: flex;
    gap: 12px;
    color: var(--el-text-color-placeholder);
    font-size: 12px;
  }
  .scene-actions {
    margin-top: 12px;
    display: flex;
    gap: 8px;
  }
}
</style>
