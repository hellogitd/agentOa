<template>
  <div class="ai-chat">
    <aside class="chat-sidebar">
      <el-button type="primary" plain icon="Plus" class="w-full mb-2" @click="handleNewConversation">新对话</el-button>
      <div v-loading="conversationLoading" class="conversation-list">
        <div
          v-for="item in conversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: item.id === currentConversationId }"
          @click="selectConversation(item)"
        >
          <div class="conversation-title" :title="item.title">{{ item.title || '新对话' }}</div>
          <div class="conversation-time">{{ item.lastMessageTime || item.createTime }}</div>
          <div class="conversation-actions">
            <el-icon title="重命名会话" @click.stop="handleRenameConversation(item)">
              <edit />
            </el-icon>
            <el-icon title="删除会话" @click.stop="handleDeleteConversation(item)">
              <delete />
            </el-icon>
          </div>
        </div>
        <el-empty v-if="!conversationLoading && conversations.length === 0" description="暂无会话" :image-size="60" />
      </div>
    </aside>

    <section class="chat-main">
      <div class="chat-toolbar">
        <!-- M5-06：AI 对话页可选 Agent 模式（docs/21 §7.1） -->
        <el-radio-group v-model="chatMode" class="w-180px" @change="handleModeChange">
          <el-radio-button value="chat">对话</el-radio-button>
          <el-radio-button value="agent">Agent</el-radio-button>
        </el-radio-group>
        <el-select v-if="chatMode === 'agent'" v-model="selectedAgentId" placeholder="选择 Agent" class="w-220px">
          <el-option v-for="item in agents" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="selectedModelId" placeholder="选择模型" class="w-220px" @change="handleModelChange">
          <el-option v-for="item in models" :key="item.id" :label="modelLabel(item)" :value="item.id" />
        </el-select>
        <el-select v-model="selectedTemplateId" placeholder="提示词模板（可选）" clearable class="w-220px" @change="handleTemplateChange">
          <el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-tag type="info" effect="plain">AI 生成内容仅供参考</el-tag>
      </div>

      <div ref="messageAreaRef" class="message-area">
        <el-empty v-if="messages.length === 0" description="开始和 AI 助手对话吧" />
        <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
          <div class="message-bubble">
            <div class="message-meta">
              <el-tag size="small" :type="message.role === 'user' ? 'primary' : 'success'">
                {{ message.role === 'user' ? '我' : message.agentMode ? 'Agent' : 'AI 助手' }}
              </el-tag>
              <el-tag v-if="message.status === 'streaming'" size="small" type="warning">生成中</el-tag>
              <el-tag v-else-if="message.status === 'stopped'" size="small" type="info">已停止</el-tag>
              <el-tag v-else-if="message.status === 'error'" size="small" type="danger">
                {{ message.errorCode || '生成失败' }}
              </el-tag>
            </div>
            <!-- Agent 多步执行轨迹（M5-04） -->
            <div v-if="message.trace?.length" class="message-trace">
              <div v-for="(step, index) in message.trace" :key="index" class="trace-step">
                <el-tag size="small" :type="step.type === 'tool_call' ? 'warning' : step.type === 'final' ? 'success' : 'info'">
                  {{ step.type }}
                </el-tag>
                <span v-if="step.tool" class="trace-tool-name">{{ step.tool }}</span>
                <el-tag v-if="step.writeTool" size="small" type="danger">写类</el-tag>
                <span v-if="step.result" class="trace-result">{{ step.result }}</span>
              </div>
            </div>
            <div class="message-content" v-text="message.content || (message.status === 'streaming' ? '…' : '')"></div>
            <div v-if="message.role === 'assistant' && message.status === 'streaming'" class="message-actions">
              <el-button link type="primary" icon="VideoPause" @click="handleStop(message)">停止生成</el-button>
            </div>
            <div v-else-if="message.role === 'assistant' && canRegenerate(message)" class="message-actions">
              <el-button link type="primary" icon="Refresh" @click="handleRegenerate(message)">重新生成</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div v-if="attachments.length" class="attachment-list">
          <el-tag v-for="item in attachments" :key="item.fileId" closable class="mr-1" @close="removeAttachment(item)">
            {{ item.fileName }}
          </el-tag>
        </div>
        <div class="input-row">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行；可上传图片（≤5 张，单张 ≤10 MiB）"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="input-buttons">
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              :before-upload="beforeUpload"
              accept=".jpg,.jpeg,.png,.webp"
              :disabled="attachments.length >= 5 || streaming"
            >
              <el-button
                icon="Picture"
                :disabled="attachments.length >= 5 || streaming"
                title="上传图片"
                aria-label="上传图片（最多 5 张，单张不超过 10 MiB）"
              />
            </el-upload>
            <el-button v-if="streaming" type="danger" icon="VideoPause" @click="handleStopCurrent">停止</el-button>
            <el-button v-else type="primary" icon="Promotion" :disabled="!draft.trim()" @click="handleSend">发送</el-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup name="AiChat" lang="ts">
import {
  listConversations,
  createConversation,
  updateConversation,
  deleteConversation,
  listMessages,
  stopMessage,
  listEnabledModels,
  listChatTemplates,
  listAgents,
  uploadChatAttachment,
  ConversationVO,
  MessageVO,
  ModelVO,
  PromptTemplateVO,
  AgentVO,
  AgentTraceStepVO,
  ChatRequest
} from '@/api/ai';
import { streamChatCompletion, streamRegenerate, streamAgentRun } from '@/api/ai/stream';
import { checkChatImage } from '@/utils/upload';

/** 会话消息视图：Agent 模式额外携带执行轨迹（M5-04） */
type ChatMessageView = MessageVO & { trace?: AgentTraceStepVO[]; agentMode?: boolean };

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const conversations = ref<ConversationVO[]>([]);
const conversationLoading = ref(false);
const currentConversationId = ref('');
const messages = ref<ChatMessageView[]>([]);
const models = ref<ModelVO[]>([]);
const templates = ref<PromptTemplateVO[]>([]);
const agents = ref<AgentVO[]>([]);
const chatMode = ref<'chat' | 'agent'>('chat');
const selectedAgentId = ref('');
const selectedModelId = ref('');
const selectedTemplateId = ref('');
const draft = ref('');
const streaming = ref(false);
const attachments = ref<{ fileId: string; fileName: string }[]>([]);
const messageAreaRef = ref<HTMLElement>();
let activeStream: AbortController | null = null;
let streamingMessageId = '';

const modelLabel = (item: ModelVO) => item.alias || item.modelKey;

const scrollToBottom = () => {
  nextTick(() => {
    const el = messageAreaRef.value;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  });
};

const loadConversations = async () => {
  conversationLoading.value = true;
  try {
    const res: any = await listConversations({ pageNum: 1, pageSize: 50 });
    conversations.value = res.data.records;
  } finally {
    conversationLoading.value = false;
  }
};

const loadModels = async () => {
  const res: any = await listEnabledModels();
  models.value = res.data;
  if (!selectedModelId.value && models.value.length) {
    const fallback = models.value.find((item) => item.isDefault === 1) ?? models.value[0];
    selectedModelId.value = fallback.id;
  }
};

const loadTemplates = async () => {
  const res: any = await listChatTemplates();
  templates.value = res.data;
};

/** M5-06：Agent 模式可选的 Agent 列表 */
const loadAgents = async () => {
  try {
    const res: any = await listAgents({ pageNum: 1, pageSize: 50 });
    agents.value = (res.data.records ?? []).filter((item: AgentVO) => item.enabled === 1);
    if (!selectedAgentId.value && agents.value.length) {
      selectedAgentId.value = agents.value[0].id;
    }
  } catch {
    agents.value = [];
  }
};

const handleModeChange = () => {
  if (chatMode.value === 'agent' && !agents.value.length) {
    loadAgents();
  }
};

/** M2-01：会话重命名 */
const handleRenameConversation = async (item: ConversationVO) => {
  const result = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
    inputValue: item.title || '新对话',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValidator: (value: string) => (value && value.trim() ? true : '标题不能为空')
  }).catch(() => null);
  if (!result || !result.value || !result.value.trim()) {
    return;
  }
  await updateConversation(item.id, { title: result.value.trim() });
  proxy?.$modal.msgSuccess('已重命名');
  await loadConversations();
};

const loadMessages = async (conversationId: string) => {
  const res: any = await listMessages(conversationId, { pageNum: 1, pageSize: 100 });
  messages.value = res.data.records;
  scrollToBottom();
};

const selectConversation = async (item: ConversationVO) => {
  currentConversationId.value = item.id;
  if (item.modelId && models.value.some((model) => model.id === item.modelId)) {
    selectedModelId.value = item.modelId;
  }
  selectedTemplateId.value = item.promptTemplateId || '';
  await loadMessages(item.id);
};

const handleNewConversation = () => {
  currentConversationId.value = '';
  messages.value = [];
  draft.value = '';
  attachments.value = [];
};

const handleDeleteConversation = async (item: ConversationVO) => {
  await proxy?.$modal.confirm(`确认删除会话「${item.title || '新对话'}」？`);
  await deleteConversation(item.id);
  proxy?.$modal.msgSuccess('删除成功');
  if (currentConversationId.value === item.id) {
    handleNewConversation();
  }
  await loadConversations();
};

const handleModelChange = async () => {
  if (!currentConversationId.value) {
    return;
  }
  await updateConversation(currentConversationId.value, { modelId: selectedModelId.value });
};

const handleTemplateChange = async () => {
  if (!currentConversationId.value) {
    return;
  }
  await updateConversation(currentConversationId.value, {
    promptTemplateId: selectedTemplateId.value || undefined
  });
};

/** 前端预检：张数/扩展名/大小（魔数校验由服务端 AiAttachmentRules 兜底） */
const beforeUpload = (file: File) => {
  const { ok, reason } = checkChatImage(file, attachments.value.length);
  if (!ok) {
    proxy?.$modal.msgError(reason);
    return false;
  }
  return true;
};

const handleUpload = async (options: any) => {
  try {
    const res: any = await uploadChatAttachment(options.file as File);
    attachments.value.push({ fileId: res.data.fileId, fileName: res.data.fileName });
    options.onSuccess(res.data);
  } catch (error: any) {
    proxy?.$modal.msgError(error?.msg || '图片上传失败');
    options.onError(error);
  }
};

const removeAttachment = (item: { fileId: string }) => {
  attachments.value = attachments.value.filter((item2) => item2.fileId !== item.fileId);
};

const canRegenerate = (message: MessageVO) => {
  return messages.value[messages.value.length - 1]?.id === message.id && !streaming.value;
};

const handleSend = () => {
  const content = draft.value.trim();
  if (!content || streaming.value) {
    return;
  }
  if (chatMode.value === 'agent') {
    if (!selectedAgentId.value) {
      proxy?.$modal.msgError('请选择 Agent');
      return;
    }
    draft.value = '';
    attachments.value = [];
    const userMessage: ChatMessageView = {
      id: `local-user-${Date.now()}`,
      conversationId: currentConversationId.value,
      role: 'user',
      content,
      promptTokens: 0,
      completionTokens: 0,
      status: 'done'
    };
    messages.value.push(userMessage);
    startAgentRun(content);
    return;
  }
  const request: ChatRequest = {
    conversationId: currentConversationId.value || undefined,
    modelId: selectedModelId.value || undefined,
    promptTemplateId: selectedTemplateId.value || undefined,
    content,
    attachmentIds: attachments.value.map((item) => item.fileId)
  };
  draft.value = '';
  attachments.value = [];
  const userMessage: MessageVO = {
    id: `local-user-${Date.now()}`,
    conversationId: currentConversationId.value,
    role: 'user',
    content,
    promptTokens: 0,
    completionTokens: 0,
    status: 'done'
  };
  messages.value.push(userMessage);
  startStream(request);
};

const startStream = (request: ChatRequest) => {
  streaming.value = true;
  const placeholder: ChatMessageView = {
    id: `local-assistant-${Date.now()}`,
    conversationId: currentConversationId.value,
    role: 'assistant',
    content: '',
    promptTokens: 0,
    completionTokens: 0,
    status: 'streaming'
  };
  messages.value.push(placeholder);
  streamingMessageId = placeholder.id;
  scrollToBottom();

  activeStream = streamChatCompletion(request, {
    onMeta: (meta) => {
      placeholder.id = meta.messageId;
      placeholder.conversationId = meta.conversationId;
      currentConversationId.value = meta.conversationId;
      streamingMessageId = meta.messageId;
    },
    onDelta: (text) => {
      placeholder.content += text;
      scrollToBottom();
    },
    onUsage: (usage) => {
      placeholder.promptTokens = usage.promptTokens;
      placeholder.completionTokens = usage.completionTokens;
    },
    onDone: (result) => {
      placeholder.content = result.content || placeholder.content;
      placeholder.status = 'done';
      finishStream();
    },
    onError: (error) => {
      placeholder.status = 'error';
      placeholder.errorCode = error.code;
      if (!placeholder.content) {
        placeholder.content = error.msg || '生成失败';
      }
      proxy?.$modal.msgError(error.msg || 'AI 生成失败');
      finishStream();
    }
  });
};

/** M5-06：Agent 模式运行（SSE 含 step 轨迹事件），执行轨迹逐步渲染在气泡内 */
const startAgentRun = (input: string) => {
  streaming.value = true;
  const placeholder: ChatMessageView = {
    id: `local-agent-${Date.now()}`,
    conversationId: currentConversationId.value,
    role: 'assistant',
    content: '',
    promptTokens: 0,
    completionTokens: 0,
    status: 'streaming',
    agentMode: true,
    trace: []
  };
  messages.value.push(placeholder);
  streamingMessageId = placeholder.id;
  scrollToBottom();

  activeStream = streamAgentRun(
    selectedAgentId.value,
    { input, conversationId: currentConversationId.value || undefined },
    {
      onStep: (step) => {
        placeholder.trace?.push(step as AgentTraceStepVO);
        scrollToBottom();
      },
      onDelta: (text) => {
        placeholder.content += text;
        scrollToBottom();
      },
      onUsage: (usage) => {
        placeholder.promptTokens = usage.promptTokens;
        placeholder.completionTokens = usage.completionTokens;
      },
      onDone: (run: any) => {
        placeholder.content = run?.output || placeholder.content;
        placeholder.status = 'done';
        finishStream();
      },
      onError: (error) => {
        placeholder.status = 'error';
        placeholder.errorCode = error.code;
        if (!placeholder.content) {
          placeholder.content = error.msg || 'Agent 运行失败';
        }
        proxy?.$modal.msgError(error.msg || 'Agent 运行失败');
        finishStream();
      }
    }
  );
};

const finishStream = () => {
  streaming.value = false;
  activeStream = null;
  scrollToBottom();
  loadConversations();
};

const handleStop = (message: MessageVO) => {
  stopMessage(message.id).finally(() => {
    message.status = 'stopped';
    finishStream();
  });
};

const handleStopCurrent = () => {
  if (streamingMessageId) {
    handleStop(messages.value.find((item) => item.id === streamingMessageId) ?? ({} as MessageVO));
  }
  activeStream?.abort();
};

const handleRegenerate = (message: MessageVO) => {
  if (streaming.value) {
    return;
  }
  const placeholder: MessageVO = {
    id: `local-assistant-${Date.now()}`,
    conversationId: message.conversationId,
    role: 'assistant',
    content: '',
    promptTokens: 0,
    completionTokens: 0,
    status: 'streaming'
  };
  messages.value.push(placeholder);
  streaming.value = true;
  streamingMessageId = placeholder.id;
  scrollToBottom();

  activeStream = streamRegenerate(message.id, {
    onMeta: (meta) => {
      placeholder.id = meta.messageId;
      streamingMessageId = meta.messageId;
    },
    onDelta: (text) => {
      placeholder.content += text;
      scrollToBottom();
    },
    onUsage: (usage) => {
      placeholder.promptTokens = usage.promptTokens;
      placeholder.completionTokens = usage.completionTokens;
    },
    onDone: (result) => {
      placeholder.content = result.content || placeholder.content;
      placeholder.status = 'done';
      finishStream();
    },
    onError: (error) => {
      placeholder.status = 'error';
      placeholder.errorCode = error.code;
      placeholder.content = placeholder.content || error.msg || '生成失败';
      proxy?.$modal.msgError(error.msg || 'AI 生成失败');
      finishStream();
    }
  });
};

onMounted(async () => {
  await Promise.all([loadModels(), loadTemplates(), loadConversations(), loadAgents()]);
  if (conversations.value.length) {
    await selectConversation(conversations.value[0]);
  }
});

onBeforeUnmount(() => {
  activeStream?.abort();
});
</script>

<style scoped>
.ai-chat {
  display: flex;
  gap: 12px;
  height: calc(100vh - 120px);
  padding: 8px;
}
.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.conversation-list {
  flex: 1;
  overflow-y: auto;
}
.conversation-item {
  position: relative;
  padding: 10px 56px 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}
.conversation-item:hover {
  background: #f1f5f9;
}
.conversation-item.active {
  background: #e0e7ff;
}
.conversation-title {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conversation-time {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.conversation-delete {
  position: absolute;
  right: 8px;
  top: 12px;
  display: none;
  color: #94a3b8;
}
.conversation-actions {
  position: absolute;
  right: 6px;
  top: 10px;
  display: none;
  gap: 4px;
  color: #94a3b8;
}
.conversation-actions .el-icon {
  cursor: pointer;
}
.conversation-actions .el-icon:hover {
  color: #ef4444;
}
.conversation-item:hover .conversation-actions {
  display: inline-flex;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  background: #f8fafc;
  border-radius: 8px;
}
.message-row {
  display: flex;
  margin-bottom: 12px;
}
.message-row.user {
  justify-content: flex-end;
}
.message-bubble {
  max-width: 72%;
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}
.message-row.user .message-bubble {
  background: #e0e7ff;
}
.message-meta {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
}
.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
}
.message-actions {
  margin-top: 6px;
}
.message-trace {
  margin: 4px 0 8px;
  padding: 6px 8px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 12px;
}
.trace-step {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  color: #475569;
}
.trace-tool-name {
  font-weight: 600;
}
.trace-result {
  color: #94a3b8;
  word-break: break-all;
  flex: 1;
}
.input-area {
  margin-top: 8px;
}
.attachment-list {
  margin-bottom: 6px;
}
.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.input-row .el-textarea {
  flex: 1;
}
.input-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
