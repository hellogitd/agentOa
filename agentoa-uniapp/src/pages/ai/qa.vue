<template>
  <view class="qa">
    <scroll-view class="qa__main" scroll-y :scroll-into-view="scrollToId">
      <view v-if="!answer && !streaming && history.length === 0" class="qa__state">
        输入问题开始知识问答；回答附引用片段，点击引用可跳转原文（仅可见范围内命中）。
      </view>

      <view v-if="question" class="qa__q">
        <text class="qa__q-text">{{ question }}</text>
      </view>

      <view v-if="answer || streaming" class="qa__a">
        <text class="qa__a-label">AI 回答</text>
        <text class="qa__a-text" user-select>{{ answer || streamText || '…' }}</text>
        <text v-if="streaming" class="qa__stop" @click="stopStream">停止生成</text>
        <view v-if="citations.length" class="qa__cites">
          <text class="qa__cites-label">引用来源</text>
          <view v-for="c in citations" :key="c.index" class="qa__cite" @click="openCitation(c)">
            <text class="qa__cite-title">[{{ c.index }}] {{ c.title || '未命名' }}{{ c.heading ? ' · ' + c.heading : '' }}</text>
            <text class="qa__cite-snippet">{{ c.snippet || '' }}</text>
          </view>
        </view>
      </view>

      <view v-if="history.length" class="qa__history">
        <text class="qa__history-title">历史问答</text>
        <view v-for="h in history" :key="h.messageId" class="qa__h">
          <text class="qa__h-q">{{ h.question }}</text>
          <text class="qa__h-a">{{ h.answer }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="qa__bar">
      <input
        class="qa__input"
        v-model="input"
        placeholder="输入问题…"
        confirm-type="send"
        :disabled="streaming"
        @confirm="ask"
      />
      <button class="qa__send" :disabled="streaming || !input.trim()" @click="ask">
        {{ streaming ? '生成中' : '提问' }}
      </button>
    </view>
    <text class="qa__notice">AI 生成内容仅供参考</text>
  </view>
</template>

<script>
import { askQa, qaHistory } from '@/api/ai'
import { go as navGo } from '@/utils/nav'

export default {
  data() {
    return {
      input: '',
      question: '',
      answer: '',
      streamText: '',
      citations: [],
      streaming: false,
      activeStream: null,
      history: [],
      scrollToId: ''
    }
  },
  onShow() {
    this.loadHistory()
  },
  onUnload() {
    if (this.activeStream) this.activeStream.abort()
  },
  methods: {
    async loadHistory() {
      try {
        const page = await qaHistory({ pageNum: 1, pageSize: 20 })
        this.history = (page && page.records) || []
      } catch (e) {
        this.history = []
      }
    },
    scrollToEnd() {
      this.scrollToId = ''
      this.$nextTick(() => {
        this.scrollToId = 'qa-stream-end'
      })
    },
    openCitation(c) {
      if (c && c.docId) {
        navGo('/pages/knowledge/preview?id=' + c.docId)
      } else if (c && c.link) {
        navGo(String(c.link))
      }
    },
    ask() {
      if (this.streaming) return
      const query = this.input.trim()
      if (!query) return
      this.input = ''
      this.question = query
      this.answer = ''
      this.streamText = ''
      this.citations = []
      this.streaming = true
      this.scrollToEnd()
      const stream = askQa({ query }, (ev) => this.onStreamEvent(ev))
      this.activeStream = stream
      this.awaitStream(stream)
    },
    onStreamEvent(ev) {
      if (ev.event === 'delta') {
        this.streamText += (ev.data && ev.data.text) || ''
      } else if (ev.event === 'done') {
        const result = ev.data || {}
        this.answer = result.content || this.streamText
        this.citations = result.citations || []
        this.streamText = ''
        this.streaming = false
        this.loadHistory()
        this.scrollToEnd()
      } else if (ev.event === 'error') {
        this.answer = (ev.data && ev.data.msg) || '知识问答失败'
        this.streamText = ''
        this.streaming = false
      }
    },
    async awaitStream(stream) {
      try {
        await stream.done
      } catch (e) {
        this.answer = (e && e.msg) || '网络异常，回答中断'
      }
      if (this.streaming) {
        if (this.streamText) this.answer = this.streamText
        this.streamText = ''
        this.streaming = false
      }
      this.activeStream = null
    },
    stopStream() {
      if (this.activeStream) this.activeStream.abort()
    }
  }
}
</script>

<style lang="scss" scoped>
.qa {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f6f8;
}
.qa__main {
  flex: 1;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
}
.qa__state {
  padding: 60rpx 20rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 24rpx;
}
.qa__q {
  margin: 12rpx 0;
  padding: 18rpx 22rpx;
  background: $oa-primary-light;
  border-radius: 16rpx;
}
.qa__q-text {
  font-size: 27rpx;
  color: $oa-text;
}
.qa__a {
  margin: 12rpx 0;
  padding: 18rpx 22rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.qa__a-label {
  display: block;
  font-size: 20rpx;
  color: $oa-text-secondary;
  margin-bottom: 6rpx;
}
.qa__a-text {
  display: block;
  font-size: 27rpx;
  color: $oa-text;
  white-space: pre-wrap;
  word-break: break-all;
}
.qa__stop {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $oa-danger;
}
.qa__cites {
  margin-top: 16rpx;
  border-top: 1rpx solid $oa-border;
  padding-top: 12rpx;
}
.qa__cites-label {
  display: block;
  font-size: 22rpx;
  color: $oa-text-secondary;
  margin-bottom: 8rpx;
}
.qa__cite {
  margin: 8rpx 0;
  padding: 12rpx 16rpx;
  background: #f7f9fc;
  border-radius: 10rpx;
}
.qa__cite-title {
  display: block;
  font-size: 24rpx;
  color: $oa-primary;
}
.qa__cite-snippet {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: $oa-text-secondary;
}
.qa__history {
  margin-top: 24rpx;
}
.qa__history-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: $oa-text;
  margin-bottom: 8rpx;
}
.qa__h {
  margin: 12rpx 0;
  padding: 16rpx 20rpx;
  background: #ffffff;
  border-radius: 12rpx;
}
.qa__h-q {
  display: block;
  font-size: 25rpx;
  font-weight: 600;
  color: $oa-text;
}
.qa__h-a {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $oa-text-secondary;
}
.qa__bar {
  display: flex;
  align-items: center;
  padding: 12rpx 24rpx;
  background: #ffffff;
  border-top: 1rpx solid $oa-border;
}
.qa__input {
  flex: 1;
  height: 72rpx;
  margin-right: 12rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.qa__send {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
  background: $oa-primary;
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 12rpx;
  border: none;
}
.qa__send::after {
  border: none;
}
.qa__send[disabled] {
  background: #a9c1f5;
  color: #ffffff;
}
.qa__notice {
  display: block;
  padding: 6rpx 0 calc(6rpx + env(safe-area-inset-bottom));
  text-align: center;
  background: #ffffff;
  font-size: 20rpx;
  color: $oa-text-muted;
}
</style>
