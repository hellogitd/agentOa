<template>
  <el-drawer v-model="visible" :title="title" size="540px" append-to-body @closed="stop">
    <el-form label-width="90px">
      <el-form-item label="用户意图">
        <el-input v-model="instruction" type="textarea" :rows="2" maxlength="4000" :placeholder="instructionPlaceholder" />
      </el-form-item>
      <el-form-item label="业务摘要">
        <el-input
          v-model="content"
          type="textarea"
          :rows="5"
          maxlength="20000"
          placeholder="可粘贴字段/指标/记录等只读摘要，服务端也会按业务 ID 只读补充"
        />
      </el-form-item>
    </el-form>

    <div class="ai-copilot-actions">
      <el-button type="primary" :loading="streaming" @click="generate">生成</el-button>
      <el-button v-if="streaming" type="danger" @click="stop">停止</el-button>
      <el-button :disabled="!output" @click="copyOutput">复制</el-button>
      <el-button v-if="actionLabel" type="success" :disabled="!output" @click="applyOutput">{{ actionLabel }}</el-button>
    </div>

    <el-alert type="warning" :closable="false" title="AI 生成内容仅供参考，人工核对后再使用" class="mb-2" />
    <el-input v-model="output" type="textarea" :rows="12" readonly placeholder="生成结果将在此展示" />
  </el-drawer>
</template>

<script setup name="AiCopilotDrawer" lang="ts">
import { streamCopilotGenerate } from '@/api/ai/stream';

/** 业务侧边抽屉：M4 五个场景的统一入口（docs/21 §6.1「业务页嵌入按钮 + 侧边抽屉」） */
const props = defineProps({
  /** 场景码：approve-summary / report-insight / notice-draft / form-suggest / minutes */
  scene: { type: String, required: true },
  /** 标题 */
  title: { type: String, default: '业务助手' },
  /** 应用按钮文案（不传则只提供复制） */
  actionLabel: { type: String, default: '' },
  /** 意图输入提示 */
  instructionPlaceholder: { type: String, default: '想让 AI 做什么，如：请把要点整理成正式通知' }
});

const emit = defineEmits(['apply', 'generated']);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const visible = ref(false);
const instruction = ref('');
const content = ref('');
const bizId = ref<string>('');
const output = ref('');
const streaming = ref(false);
let activeStream: AbortController | null = null;

/** 打开抽屉；bizId 为空表示只用 content 生成 */
const open = (options?: { instruction?: string; content?: string; bizId?: string | number }) => {
  instruction.value = options?.instruction ?? '';
  content.value = options?.content ?? '';
  bizId.value = options?.bizId ? String(options.bizId) : '';
  output.value = '';
  visible.value = true;
};

const generate = () => {
  if (!instruction.value.trim() && !content.value.trim() && !bizId.value) {
    proxy?.$modal.msgError('请填写业务摘要或业务 ID');
    return;
  }
  streaming.value = true;
  output.value = '';
  activeStream = streamCopilotGenerate(
    props.scene,
    { instruction: instruction.value, content: content.value, bizId: bizId.value || undefined },
    {
      onDelta: (text) => {
        output.value += text;
      },
      onDone: () => {
        streaming.value = false;
        emit('generated', output.value);
      },
      onError: (error) => {
        streaming.value = false;
        proxy?.$modal.msgError(error.msg || 'AI 生成失败');
      }
    }
  );
};

const stop = () => {
  activeStream?.abort();
  activeStream = null;
  streaming.value = false;
};

const copyOutput = async () => {
  try {
    await navigator.clipboard.writeText(output.value);
    proxy?.$modal.msgSuccess('已复制到剪贴板');
  } catch {
    proxy?.$modal.msgError('复制失败，请手动选择文本');
  }
};

/** 人工确认后应用到业务页（由业务页决定写入哪个字段） */
const applyOutput = () => {
  if (!output.value) {
    return;
  }
  emit('apply', output.value);
  visible.value = false;
};

onBeforeUnmount(() => activeStream?.abort());

defineExpose({ open });
</script>

<style scoped>
.ai-copilot-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
