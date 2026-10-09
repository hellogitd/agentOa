<template>
  <view class="ai">
    <view class="ai__head">
      <text class="ai__head-title" @click="showSessions = true">{{ currentTitle || '新会话' }} ▾</text>
      <text class="ai__head-new" @click="newSession">新会话</text>
    </view>

    <view class="ai__tools">
      <picker
        v-if="models.length"
        class="ai__tool"
        mode="selector"
        :range="modelLabels"
        :value="modelIndex"
        @change="onModelChange"
      >
        <text class="ai__tool-text" aria-label="切换模型">模型：{{ modelLabel }} ▾</text>
      </picker>
      <picker
        v-if="templates.length"
        class="ai__tool"
        mode="selector"
        :range="templateLabels"
        :value="templateIndex"
        @change="onTemplateChange"
      >
        <text class="ai__tool-text" aria-label="切换提示词模板">模板：{{ templateLabel }} ▾</text>
      </picker>
    </view>

    <scroll-view class="ai__msgs" scroll-y :scroll-into-view="scrollToId">
      <view v-if="loadingHistory" class="ai__state">会话加载中…</view>
      <view v-else-if="messages.length === 0 && !streaming" class="ai__state">
        向 AI 助手提问，支持公文写作、邮件润色、翻译等；AI 生成内容仅供参考。
      </view>

      <view
        v-for="m in messages"
        :key="m.id"
        :id="'m-' + m.id"
        class="ai__msg"
        :class="m.role === 'user' ? 'ai__msg--user' : 'ai__msg--ai'"
      >
        <text class="ai__role">{{ m.role === 'user' ? '我' : 'AI' }}</text>
        <text class="ai__content" user-select>{{ m.content }}</text>
        <view v-if="m.status === 'error'" class="ai__err">
          <text class="ai__err-text">{{ m.errorCode || 'AI_ERROR' }}</text>
          <text v-if="m.regenerable" class="ai__err-act" @click="regenerateMsg(m)">重新生成</text>
        </view>
        <view v-if="m.status === 'stopped'" class="ai__err">
          <text class="ai__err-text">已停止生成</text>
          <text v-if="m.regenerable" class="ai__err-act" @click="regenerateMsg(m)">重新生成</text>
        </view>
      </view>

      <view v-if="streaming" id="m-streaming" class="ai__msg ai__msg--ai">
        <text class="ai__role">AI</text>
        <text class="ai__content" user-select>{{ streamText || '…' }}</text>
        <text class="ai__stop" @click="stopStream">停止生成</text>
      </view>
    </scroll-view>

    <view class="ai__bar">
      <view v-if="attachments.length" class="ai__atts">
        <view v-for="(att, i) in attachments" :key="att.fileId" class="ai__att">
          <image class="ai__att-thumb" :src="att.preview" mode="aspectFill" />
          <text class="ai__att-del" @click="removeAttachment(i)">×</text>
        </view>
      </view>
      <view class="ai__bar-row">
        <text
          class="ai__pick"
          aria-label="上传图片（最多 5 张，单张不超过 10 MiB）"
          @click="pickImages"
        >＋图</text>
        <input
          class="ai__input"
          v-model="input"
          placeholder="输入消息…"
          confirm-type="send"
          :disabled="streaming"
          @confirm="send"
        />
        <button class="ai__send" :disabled="streaming || (!input.trim() && !attachments.length)" @click="send">
          {{ streaming ? '生成中' : '发送' }}
        </button>
      </view>
      <text class="ai__notice">AI 生成内容仅供参考</text>
    </view>

    <view v-if="showSessions" class="ai__mask" @click="showSessions = false">
      <view class="ai__panel" @click.stop>
        <text class="ai__panel-title">历史会话</text>
        <scroll-view class="ai__panel-list" scroll-y>
          <view
            v-for="s in sessions"
            :key="s.id"
            class="ai__session"
            :class="{ 'ai__session--active': String(s.id) === String(conversationId) }"
          >
            <text class="ai__session-title" @click="selectSession(s)">{{ s.title || '未命名会话' }}</text>
            <text class="ai__session-del" @click="removeSession(s)">删除</text>
          </view>
          <view v-if="sessions.length === 0" class="ai__state">暂无历史会话</view>
        </scroll-view>
        <button class="ai__panel-close" @click="showSessions = false">关闭</button>
      </view>
    </view>
  </view>
</template>

<script>
import {
  listConversations,
  deleteConversation,
  listMessages,
  chatCompletions,
  regenerate,
  stopMessage,
  listModels,
  listChatTemplates,
  updateConversation
} from '@/api/ai'
import { uploadAiAttachment } from '@/api/file'
import { formatDateTime } from '@/utils/format'

const IMAGE_RE = /\.(jpe?g|png|webp)$/i
const MAX_IMAGES = 5
const MAX_IMAGE_BYTES = 10 * 1024 * 1024

export default {
  data() {
    return {
      sessions: [],
      conversationId: '',
      messages: [],
      input: '',
      streaming: false,
      streamText: '',
      activeStream: null,
      pendingMessageId: '',
      attachments: [],
      showSessions: false,
      loadingHistory: false,
      scrollToId: '',
      models: [],
      templates: [],
      selectedModelId: '',
      selectedTemplateId: '',
      stopRequested: false
    }
  },
  computed: {
    currentTitle() {
      const s = this.sessions.find((x) => String(x.id) === String(this.conversationId))
      return (s && s.title) || ''
    },
    modelLabels() {
      return this.models.map((m) => m.alias || m.modelKey)
    },
    modelIndex() {
      return this.models.findIndex((m) => String(m.id) === String(this.selectedModelId))
    },
    modelLabel() {
      const m = this.models[this.modelIndex]
      return (m && (m.alias || m.modelKey)) || '默认'
    },
    templateLabels() {
      return this.templates.map((t) => t.name)
    },
    templateIndex() {
      return this.templates.findIndex((t) => String(t.id) === String(this.selectedTemplateId))
    },
    templateLabel() {
      const t = this.templates[this.templateIndex]
      return (t && t.name) || '无'
    }
  },
  onShow() {
    this.refreshSessions()
    this.loadModels()
    this.loadTemplates()
  },
  onUnload() {
    if (this.activeStream) this.activeStream.abort()
  },
  methods: {
    fmtDateTime: formatDateTime,
    async refreshSessions() {
      try {
        const page = await listConversations({ pageNum: 1, pageSize: 50 })
        this.sessions = (page && page.records) || []
      } catch (e) {
        this.sessions = []
      }
    },
    async loadModels() {
      try {
        const list = await listModels()
        this.models = list || []
        if (!this.selectedModelId && this.models.length) {
          const fallback = this.models.find((m) => Number(m.isDefault) === 1) || this.models[0]
          this.selectedModelId = String(fallback.id)
        }
      } catch (e) {
        this.models = []
      }
    },
    async loadTemplates() {
      try {
        this.templates = (await listChatTemplates()) || []
      } catch (e) {
        this.templates = []
      }
    },
    /** AI-M2-06：切换模型只影响新消息，历史不重算 */
    onModelChange(e) {
      const m = this.models[Number(e.detail.value)]
      if (!m) return
      this.selectedModelId = String(m.id)
      this.persistSelection()
    },
    onTemplateChange(e) {
      const t = this.templates[Number(e.detail.value)]
      if (!t) return
      this.selectedTemplateId = String(t.id)
      this.persistSelection()
    },
    /** 会话内切换即持久化绑定（新建会话在下一次发送时带上绑定） */
    persistSelection() {
      if (!this.conversationId) return
      updateConversation(this.conversationId, {
        modelId: this.selectedModelId || undefined,
        promptTemplateId: this.selectedTemplateId || undefined
      }).catch(() => {})
    },
    newSession() {
      this.conversationId = ''
      this.messages = []
      this.showSessions = false
    },
    async selectSession(s) {
      this.showSessions = false
      this.conversationId = String(s.id)
      // 绑定模型已删除/停用时保留当前选择，下一次发送自愈绑定（同 PC selectConversation）
      if (s.modelId && this.models.some((m) => String(m.id) === String(s.modelId))) {
        this.selectedModelId = String(s.modelId)
      }
      this.selectedTemplateId = s.promptTemplateId ? String(s.promptTemplateId) : ''
      this.loadingHistory = true
      try {
        const page = await listMessages(s.id, { pageNum: 1, pageSize: 100 })
        this.messages = ((page && page.records) || []).map((m) => ({
          id: m.id,
          role: m.role,
          content: m.content || '',
          status: m.status,
          errorCode: m.errorCode
        }))
      } catch (e) {
        this.messages = []
      } finally {
        this.loadingHistory = false
      }
    },
    removeSession(s) {
      uni.showModal({
        title: '删除会话',
        content: '确定删除该会话？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await deleteConversation(s.id)
            if (String(s.id) === String(this.conversationId)) this.newSession()
            this.refreshSessions()
          } catch (e) {
            // 错误信封由请求层提示
          }
        }
      })
    },
    async pickImages() {
      const room = MAX_IMAGES - this.attachments.length
      if (room <= 0) {
        uni.showToast({ title: `最多 ${MAX_IMAGES} 张图片`, icon: 'none' })
        return
      }
      const picked = await new Promise((resolve) => {
        uni.chooseImage({
          count: room,
          sizeType: ['compressed'],
          sourceType: ['camera', 'album'],
          success: (res) => {
            const paths = [].concat(res.tempFilePaths || [])
            resolve(paths.map((p, i) => ({ path: p, size: (res.tempFiles && res.tempFiles[i] && res.tempFiles[i].size) || 0 })))
          },
          fail: () => resolve([])
        })
      })
      if (!picked.length) return
      for (const item of picked) {
        if (this.attachments.length >= MAX_IMAGES) break
        if (!IMAGE_RE.test(item.path)) {
          uni.showToast({ title: '仅支持 jpg/png/webp 图片', icon: 'none' })
          continue
        }
        if (item.size && item.size > MAX_IMAGE_BYTES) {
          uni.showToast({ title: '单张图片不超过 10 MiB', icon: 'none' })
          continue
        }
        uni.showLoading({ title: '上传中…' })
        try {
          const file = await uploadAiAttachment(item.path)
          if (file && file.fileId) {
            this.attachments = this.attachments.concat([{ fileId: String(file.fileId), preview: item.path }])
          }
        } catch (e) {
          // 超限/格式错误信封由请求层提示
        } finally {
          uni.hideLoading()
        }
      }
    },
    removeAttachment(i) {
      this.attachments = this.attachments.filter((_, idx) => idx !== i)
    },
    scrollToEnd() {
      this.scrollToId = ''
      this.$nextTick(() => {
        this.scrollToId = this.streaming ? 'm-streaming' : this.messages.length ? 'm-' + this.messages[this.messages.length - 1].id : ''
      })
    },
    async send() {
      if (this.streaming) return
      const content = this.input.trim()
      const attachmentIds = this.attachments.map((a) => a.fileId)
      if (!content && !attachmentIds.length) return
      this.input = ''
      this.messages = this.messages.concat([
        { id: 'u' + Date.now(), role: 'user', content: content || '[图片]', status: 'done' }
      ])
      this.attachments = []
      this.streamText = ''
      this.streaming = true
      this.scrollToId = ''
      this.scrollToEnd()
      let conversationId = this.conversationId
      const stream = chatCompletions(
        {
          conversationId: conversationId || undefined,
          modelId: this.selectedModelId || undefined,
          promptTemplateId: this.selectedTemplateId || undefined,
          content: content || undefined,
          attachmentIds: attachmentIds.length ? attachmentIds : undefined
        },
        (ev) => this.onStreamEvent(ev)
      )
      this.activeStream = stream
      conversationId = await this.awaitStream(stream, conversationId, 'AI_CHAT_FAILED')
    },
    async regenerateMsg(m) {
      if (this.streaming) return
      this.streamText = ''
      this.streaming = true
      this.scrollToEnd()
      const stream = regenerate(m.id, (ev) => this.onStreamEvent(ev))
      this.activeStream = stream
      await this.awaitStream(stream, this.conversationId, 'AI_CHAT_FAILED')
    },
    onStreamEvent(ev) {
      if (ev.event === 'meta') {
        if (ev.data && ev.data.conversationId) this.conversationId = String(ev.data.conversationId)
        if (ev.data && ev.data.messageId) this.pendingMessageId = String(ev.data.messageId)
      } else if (ev.event === 'delta') {
        this.streamText += (ev.data && ev.data.text) || ''
      } else if (ev.event === 'done') {
        const result = ev.data || {}
        this.messages = this.messages.concat([
          {
            id: result.messageId || 'a' + Date.now(),
            role: 'assistant',
            content: result.content || this.streamText,
            status: result.status || 'done'
          }
        ])
        this.streamText = ''
        this.streaming = false
        this.pendingMessageId = ''
        this.refreshSessions()
        this.scrollToEnd()
      } else if (ev.event === 'error') {
        const code = (ev.data && ev.data.code) || 'AI_CHAT_FAILED'
        const msg = (ev.data && ev.data.msg) || 'AI 生成失败'
        // 失败消息保留服务端消息 ID，供单条重新生成使用
        this.messages = this.messages.concat([
          {
            id: this.pendingMessageId || 'e' + Date.now(),
            role: 'assistant',
            content: msg,
            status: 'error',
            errorCode: code,
            regenerable: Boolean(this.pendingMessageId)
          }
        ])
        this.streamText = ''
        this.streaming = false
        this.pendingMessageId = ''
        this.scrollToEnd()
      }
    },
    async awaitStream(stream, conversationId, fallbackCode) {
      try {
        await stream.done
      } catch (e) {
        const serverId = this.pendingMessageId
        if (this.stopRequested) {
          // 用户停止生成：保留已产出文本，状态 stopped（服务端已同步置 stopped）
          this.messages = this.messages.concat([
            {
              id: serverId || 'a' + Date.now(),
              role: 'assistant',
              content: this.streamText || '（已停止生成）',
              status: 'stopped',
              regenerable: Boolean(serverId)
            }
          ])
        } else {
          this.messages = this.messages.concat([
            {
              id: serverId || 'e' + Date.now(),
              role: 'assistant',
              content: (e && e.msg) || '网络异常，生成中断',
              status: 'error',
              errorCode: (e && e.code) || fallbackCode,
              regenerable: Boolean(serverId)
            }
          ])
        }
        this.streamText = ''
      }
      if (this.streaming) {
        if (this.streamText) {
          this.messages = this.messages.concat([
            {
              id: this.pendingMessageId || 'a' + Date.now(),
              role: 'assistant',
              content: this.streamText,
              status: 'stopped',
              regenerable: Boolean(this.pendingMessageId)
            }
          ])
        }
        this.streamText = ''
        this.streaming = false
      }
      this.stopRequested = false
      this.pendingMessageId = ''
      this.activeStream = null
      return conversationId
    },
    async stopStream() {
      if (!this.streaming) return
      this.stopRequested = true
      if (this.activeStream) this.activeStream.abort()
      if (this.pendingMessageId) {
        stopMessage(this.pendingMessageId).catch(() => {})
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.ai {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f6f8;
}
.ai__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: #ffffff;
  border-bottom: 1rpx solid $oa-border;
}
.ai__head-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.ai__head-new {
  font-size: 24rpx;
  color: $oa-primary;
}
.ai__tools {
  display: flex;
  padding: 10rpx 24rpx;
  background: #ffffff;
  border-bottom: 1rpx solid $oa-border;
}
.ai__tool {
  flex: 1;
  min-width: 0;
}
.ai__tool + .ai__tool {
  margin-left: 12rpx;
}
.ai__tool-text {
  display: block;
  padding: 8rpx 16rpx;
  background: #f0f2f7;
  border-radius: 12rpx;
  font-size: 22rpx;
  color: $oa-text-secondary;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.ai__msgs {
  flex: 1;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
}
.ai__state {
  padding: 60rpx 20rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 24rpx;
}
.ai__msg {
  margin: 12rpx 0;
  padding: 18rpx 22rpx;
  border-radius: 16rpx;
  background: #ffffff;
}
.ai__msg--user {
  background: $oa-primary-light;
}
.ai__role {
  display: block;
  font-size: 20rpx;
  color: $oa-text-secondary;
  margin-bottom: 6rpx;
}
.ai__content {
  display: block;
  font-size: 27rpx;
  color: $oa-text;
  white-space: pre-wrap;
  word-break: break-all;
}
.ai__stop {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $oa-danger;
}
.ai__err {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
}
.ai__err-text {
  flex: 1;
  font-size: 20rpx;
  color: $oa-danger;
}
.ai__err-act {
  font-size: 22rpx;
  color: $oa-primary;
}
.ai__bar {
  padding: 12rpx 24rpx calc(12rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1rpx solid $oa-border;
}
.ai__atts {
  display: flex;
  margin-bottom: 10rpx;
}
.ai__att {
  position: relative;
  margin-right: 12rpx;
}
.ai__att-thumb {
  width: 96rpx;
  height: 96rpx;
  border-radius: 10rpx;
}
.ai__att-del {
  position: absolute;
  top: -10rpx;
  right: -10rpx;
  width: 34rpx;
  height: 34rpx;
  line-height: 34rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  border-radius: 50%;
  font-size: 22rpx;
}
.ai__bar-row {
  display: flex;
  align-items: center;
}
.ai__pick {
  padding: 12rpx 18rpx;
  background: #f0f2f7;
  border-radius: 12rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.ai__input {
  flex: 1;
  height: 72rpx;
  margin: 0 12rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.ai__send {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
  background: $oa-primary;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 12rpx;
  border: none;
}
.ai__send::after {
  border: none;
}
.ai__send[disabled] {
  background: #a9c1f5;
  color: #ffffff;
}
.ai__notice {
  display: block;
  margin-top: 8rpx;
  text-align: center;
  font-size: 20rpx;
  color: $oa-text-muted;
}
.ai__mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 20;
}
.ai__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 70vh;
  background: #ffffff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 24rpx;
}
.ai__panel-title {
  display: block;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-text;
}
.ai__panel-list {
  max-height: 52vh;
  margin-top: 12rpx;
}
.ai__session {
  display: flex;
  align-items: center;
  padding: 20rpx 8rpx;
  border-bottom: 1rpx solid $oa-border;
}
.ai__session--active .ai__session-title {
  color: $oa-primary;
}
.ai__session-title {
  flex: 1;
  font-size: 26rpx;
  color: $oa-text;
}
.ai__session-del {
  font-size: 22rpx;
  color: $oa-danger;
}
.ai__panel-close {
  margin-top: 16rpx;
  height: 76rpx;
  line-height: 76rpx;
  background: #f0f2f7;
  color: $oa-text-secondary;
  font-size: 26rpx;
  border-radius: 38rpx;
  border: none;
}
.ai__panel-close::after {
  border: none;
}
</style>
