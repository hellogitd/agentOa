<template>
  <view class="pv">
    <view v-if="loading" class="pv__state">加载中…</view>
    <oa-empty v-else-if="!doc" text="文档不可见或不存在" hint="确认权限后重试" />

    <block v-else>
      <view class="pv__head">
        <text class="pv__title">{{ doc.title }}</text>
        <text class="pv__meta">{{ doc.spaceName || '' }} · {{ doc.lastEditByName || '-' }} · {{ fmtDate(doc.lastEditTime || doc.updateTime) }}</text>
      </view>

      <view class="pv__body">
        <block v-for="(block, bi) in blocks" :key="bi">
          <text v-if="block.type === 'h1'" class="pv__h1">{{ block.text }}</text>
          <text v-else-if="block.type === 'h2'" class="pv__h2">{{ block.text }}</text>
          <text v-else-if="block.type === 'h3'" class="pv__h3">{{ block.text }}</text>
          <view v-else-if="block.type === 'quote'" class="pv__quote">
            <text>{{ block.text }}</text>
          </view>
          <view v-else-if="block.type === 'code'" class="pv__code">
            <text>{{ block.text }}</text>
          </view>
          <view v-else-if="block.type === 'li'" class="pv__li">
            <text class="pv__li-mark">{{ block.mark }}</text>
            <text class="pv__li-text">{{ block.text }}</text>
          </view>
          <text v-else-if="block.type === 'hr'" class="pv__hr">————————————</text>
          <view v-else-if="block.type === 'p'" class="pv__p">
            <text v-for="(seg, si) in block.segments" :key="si" :class="{ 'pv__bold': seg.bold }">{{ seg.text }}</text>
          </view>
        </block>
        <text v-if="blocks.length === 0" class="pv__empty-text">（文档暂无正文）</text>
      </view>

      <view class="pv__foot">
        <text class="pv__foot-btn" @click="toggleFav">{{ favorited ? '★ 已收藏' : '☆ 收藏' }}</text>
      </view>
    </block>
  </view>
</template>

<script>
import { getDocument, listFavorites, favorite, unfavorite } from '@/api/knowledge'
import { formatDate } from '@/utils/format'
import OaEmpty from '@/components/oa-empty.vue'

/** 极简 Markdown 块渲染（只读预览，纯文本渲染无 XSS 面） */
/** @returns {Record<string, any>[]} */
function parseBlocks(content) {
  const lines = String(content || '').split(/\r?\n/)
  const blocks = /** @type {Record<string, any>[]} */ ([])
  let inCode = false
  let codeLines = []
  lines.forEach((raw) => {
    const line = raw.replace(/\s+$/, '')
    if (/^```/.test(line)) {
      if (inCode) {
        blocks.push({ type: 'code', text: codeLines.join('\n') })
        codeLines = []
      }
      inCode = !inCode
      return
    }
    if (inCode) {
      codeLines.push(raw)
      return
    }
    if (!line.trim()) return
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      blocks.push({ type: 'hr' })
      return
    }
    const h = line.match(/^(#{1,3})\s+(.*)$/)
    if (h) {
      blocks.push({ type: 'h' + h[1].length, text: h[2] })
      return
    }
    const quote = line.match(/^>\s?(.*)$/)
    if (quote) {
      blocks.push({ type: 'quote', text: quote[1] })
      return
    }
    const li = line.match(/^\s*([-*+]|\d+[.)])\s+(.*)$/)
    if (li) {
      blocks.push({ type: 'li', mark: /^\d/.test(li[1]) ? li[1].replace(/[.)]$/, '') + '.' : '·', text: li[2] })
      return
    }
    blocks.push({ type: 'p', segments: inline(line) })
  })
  if (inCode && codeLines.length) blocks.push({ type: 'code', text: codeLines.join('\n') })
  return blocks
}

function inline(text) {
  const segments = []
  String(text)
    .split(/(\*\*[^*]+\*\*)/)
    .forEach((part) => {
      if (!part) return
      if (/^\*\*[^*]+\*\*$/.test(part)) {
        segments.push({ text: part.slice(2, -2), bold: true })
      } else {
        segments.push({ text: part.replace(/[*`]/g, ''), bold: false })
      }
    })
  return segments.length ? segments : [{ text, bold: false }]
}

export default {
  components: { OaEmpty },
  data() {
    return {
      docId: '',
      doc: null,
      loading: true,
      favorited: false
    }
  },
  computed: {
    blocks() {
      return parseBlocks(this.doc && this.doc.content)
    }
  },
  onLoad(options) {
    this.docId = (options && options.id) || ''
  },
  onShow() {
    this.fetch()
  },
  methods: {
    fmtDate: formatDate,
    async fetch() {
      this.loading = true
      try {
        this.doc = await getDocument(this.docId)
        const favs = (await listFavorites().catch(() => [])) || []
        this.favorited = favs.map(String).includes(String(this.docId))
      } catch (e) {
        this.doc = null
      } finally {
        this.loading = false
      }
    },
    async toggleFav() {
      try {
        if (this.favorited) {
          await unfavorite(this.docId)
          this.favorited = false
        } else {
          await favorite(this.docId)
          this.favorited = true
        }
      } catch (e) {
        // 错误信封由请求层提示
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.pv {
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}
.pv__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.pv__head {
  padding: 28rpx 24rpx 16rpx;
}
.pv__title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: $oa-text;
}
.pv__meta {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $oa-text-muted;
}
.pv__body {
  margin: 0 24rpx;
  padding: 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.pv__h1 {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: $oa-text;
  margin: 16rpx 0 8rpx;
}
.pv__h2 {
  display: block;
  font-size: 29rpx;
  font-weight: 600;
  color: $oa-text;
  margin: 14rpx 0 8rpx;
}
.pv__h3 {
  display: block;
  font-size: 27rpx;
  font-weight: 600;
  color: $oa-text;
  margin: 12rpx 0 6rpx;
}
.pv__p {
  display: block;
  margin: 8rpx 0;
}
.pv__bold {
  font-weight: 600;
  color: $oa-text;
}
.pv__quote {
  margin: 8rpx 0;
  padding: 8rpx 16rpx;
  border-left: 6rpx solid $oa-primary;
  background: $oa-primary-light;
}
.pv__code {
  margin: 8rpx 0;
  padding: 12rpx 16rpx;
  background: #f5f7fb;
  border-radius: 8rpx;
  font-family: monospace;
}
.pv__li {
  display: flex;
  margin: 6rpx 0;
}
.pv__li-mark {
  width: 40rpx;
  color: $oa-text-secondary;
}
.pv__li-text {
  flex: 1;
}
.pv__hr {
  display: block;
  color: $oa-border;
  margin: 12rpx 0;
}
.pv__empty-text {
  color: $oa-text-muted;
  font-size: 24rpx;
}
.pv__foot {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #ffffff;
  border-top: 1rpx solid $oa-border;
}
.pv__foot-btn {
  display: block;
  text-align: center;
  height: 76rpx;
  line-height: 76rpx;
  background: $oa-primary-light;
  color: $oa-primary;
  border-radius: 38rpx;
  font-size: 26rpx;
}
</style>
