<template>
  <div class="p-2 qa-page">
    <el-row :gutter="12">
      <!-- 历史 -->
      <el-col :span="6">
        <el-card shadow="hover" class="history-card">
          <template #header>
            <div class="history-head">
              <span class="card-header-label">知识问答</span>
              <el-button type="primary" link icon="Plus" @click="handleNew">新提问</el-button>
            </div>
          </template>
          <div v-loading="historyLoading" class="history-list">
            <div
              v-for="item in history"
              :key="item.messageId"
              class="history-item"
              :class="{ active: item.conversationId === conversationId }"
              @click="handleOpenHistory(item)"
            >
              <div class="history-question">{{ item.question }}</div>
              <div class="history-time">{{ item.createTime }}</div>
            </div>
            <el-empty v-if="!history.length && !historyLoading" description="暂无提问记录" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <!-- 对话 -->
      <el-col :span="18">
        <el-card shadow="hover">
          <template #header>
            <div class="ask-head">
              <el-select
                v-model="selectedKbIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="全部可见知识域"
                class="kb-select"
                clearable
              >
                <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
              <el-tag type="warning" effect="plain">AI 生成内容仅供参考</el-tag>
            </div>
          </template>

          <div ref="streamRef" class="stream-area">
            <el-empty v-if="!messages.length" description="输入问题开始知识问答，回答附引用可跳转原文" />
            <div v-for="(message, index) in messages" :key="index" class="qa-turn">
              <div class="qa-question">
                <el-icon><User /></el-icon>
                <span>{{ message.question }}</span>
              </div>
              <div class="qa-answer">
                <el-icon><MagicStick /></el-icon>
                <div class="qa-answer-body">
                  <span class="answer-text">{{ message.answer }}</span>
                  <span v-if="message.streaming" class="cursor">▍</span>
                  <div v-if="message.error" class="answer-error">{{ message.error }}</div>
                  <div v-if="message.citations?.length" class="citations">
                    <div class="citations-title">引用来源</div>
                    <div v-for="citation in message.citations" :key="citation.index" class="citation-item">
                      <el-tag size="small" type="info">[{{ citation.index }}]</el-tag>
                      <span class="citation-title">{{ citation.title || '未命名' }}</span>
                      <span v-if="citation.heading" class="citation-heading">{{ citation.heading }}</span>
                      <el-link v-if="citation.link" type="primary" :underline="false" @click="openLink(citation.link)">跳原文</el-link>
                      <div class="citation-snippet">{{ citation.snippet }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="ask-bar">
            <el-input
              v-model="question"
              type="textarea"
              :rows="3"
              placeholder="输入问题，Enter 发送，Shift+Enter 换行"
              :disabled="streaming"
              @keydown.enter.exact.prevent="handleAsk"
            />
            <div class="ask-actions">
              <span class="text-gray-400 text-xs">检索范围按您的可见权限服务端过滤</span>
              <el-button v-if="streaming" type="warning" plain @click="handleStop">停止生成</el-button>
              <el-button v-else type="primary" :disabled="!question.trim()" :loading="streaming" @click="handleAsk">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="AiQa" lang="ts">
import { qaHistory, listKb, type KbVO, type QaHistoryVO, type QaCitationVO } from '@/api/ai';
import { streamQaAsk } from '@/api/ai/stream';

const router = useRouter();

interface QaTurn {
  question: string;
  answer: string;
  citations?: QaCitationVO[];
  streaming?: boolean;
  error?: string;
}

const question = ref('');
const messages = ref<QaTurn[]>([]);
const conversationId = ref<string>();
const streaming = ref(false);
const streamRef = ref<HTMLElement>();
let activeStream: AbortController | null = null;

const history = ref<QaHistoryVO[]>([]);
const historyLoading = ref(false);
const kbOptions = ref<KbVO[]>([]);
const selectedKbIds = ref<string[]>([]);

const loadKbs = async () => {
  const res: any = await listKb({ pageNum: 1, pageSize: 50 });
  kbOptions.value = res.data?.records ?? [];
};

const loadHistory = async () => {
  historyLoading.value = true;
  try {
    const res: any = await qaHistory({ pageNum: 1, pageSize: 20 });
    history.value = res.data?.records ?? [];
  } finally {
    historyLoading.value = false;
  }
};

const handleNew = () => {
  conversationId.value = undefined;
  messages.value = [];
  question.value = '';
};

const handleOpenHistory = (item: QaHistoryVO) => {
  conversationId.value = item.conversationId;
  messages.value = [
    {
      question: item.question,
      answer: item.answer,
      citations: item.citations ?? []
    }
  ];
};

const handleAsk = () => {
  const text = question.value.trim();
  if (!text || streaming.value) return;
  const turn: QaTurn = { question: text, answer: '', citations: [], streaming: true };
  messages.value.push(turn);
  question.value = '';
  streaming.value = true;
  scrollToBottom();

  activeStream = streamQaAsk(
    {
      query: text,
      conversationId: conversationId.value,
      kbIds: selectedKbIds.value.length ? selectedKbIds.value : undefined
    },
    {
      onMeta: (payload) => {
        conversationId.value = payload.conversationId;
      },
      onDelta: (delta) => {
        turn.answer += delta;
        scrollToBottom();
      },
      onDone: (payload) => {
        turn.answer = payload.content || turn.answer;
        turn.citations = payload.citations ?? [];
        turn.streaming = false;
        streaming.value = false;
        activeStream = null;
        scrollToBottom();
        loadHistory();
      },
      onError: (payload) => {
        turn.error = payload.msg || 'AI 生成失败';
        turn.streaming = false;
        streaming.value = false;
        activeStream = null;
      }
    }
  );
};

const handleStop = () => {
  activeStream?.abort();
  activeStream = null;
  const current = messages.value[messages.value.length - 1];
  if (current) {
    current.streaming = false;
  }
  streaming.value = false;
};

const openLink = (link: string) => {
  if (link.startsWith('/')) {
    router.push(link);
  } else {
    window.open(link, '_blank');
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    streamRef.value?.scrollTo({ top: streamRef.value.scrollHeight, behavior: 'smooth' });
  });
};

onMounted(() => {
  loadKbs();
  loadHistory();
});
</script>

<style scoped lang="scss">
.qa-page {
  height: calc(100vh - 120px);
}
.history-card {
  height: 100%;
}
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.history-list {
  max-height: calc(100vh - 240px);
  overflow: auto;
}
.history-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  &:hover {
    background: var(--el-fill-color-light);
  }
  &.active {
    border-color: var(--el-color-primary-light-5);
    background: var(--el-color-primary-light-9);
  }
}
.history-question {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.history-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ask-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kb-select {
  width: 360px;
}
.stream-area {
  min-height: 320px;
  max-height: calc(100vh - 380px);
  overflow: auto;
  padding: 4px 2px;
}
.qa-turn {
  margin-bottom: 18px;
}
.qa-question {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-weight: 600;
  margin-bottom: 10px;
}
.qa-answer {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.qa-answer-body {
  flex: 1;
  white-space: pre-wrap;
  line-height: 1.7;
}
.cursor {
  color: var(--el-color-primary);
}
.answer-error {
  color: var(--el-color-danger);
  margin-top: 6px;
}
.citations {
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color);
  padding-top: 8px;
}
.citations-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.citation-item {
  font-size: 13px;
  margin-bottom: 8px;
  white-space: normal;
}
.citation-title {
  margin-left: 6px;
  font-weight: 600;
}
.citation-heading {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
}
.citation-snippet {
  color: var(--el-text-color-regular);
  margin-top: 2px;
  padding-left: 34px;
}
.ask-bar {
  margin-top: 12px;
}
.ask-actions {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
