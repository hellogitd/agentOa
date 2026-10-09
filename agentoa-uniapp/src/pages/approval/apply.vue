<template>
  <view class="apply">
    <view class="apply__section">
      <text class="apply__section-title">快捷入口</text>
      <view class="apply__types">
        <view
          v-for="t in APPLY_TYPES"
          :key="t.type"
          class="apply__type"
          @click="selectQuick(t)"
        >
          <view class="apply__type-icon">{{ t.icon }}</view>
          <text class="apply__type-name">{{ t.name }}</text>
        </view>
      </view>
    </view>

    <view class="apply__section">
      <text class="apply__section-title">全部流程</text>
      <view v-if="loadingCatalog" class="apply__loading">流程目录加载中…</view>
      <view v-else-if="!groups.length" class="apply__loading">暂无可发起的流程，请联系管理员启用流程模板</view>
      <view v-for="g in groups" :key="g.category.id" class="apply__group">
        <text class="apply__group-title">{{ g.category.name }}</text>
        <view class="apply__group-list">
          <view v-for="d in g.definitions" :key="d.id" class="apply__def" @click="openForm(d)">
            <view class="apply__def-icon">{{ iconOf(d) }}</view>
            <view class="apply__def-info">
              <text class="apply__def-name">{{ d.processName }}</text>
              <text class="apply__def-desc">{{ d.remark || '发起申请' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="apply__tip">
      <text>目录与表单结构取自流程模板发布的表单快照，后台启用/停用/发布流程或调整分类后这里即时同步；点击流程进入填写页发起申请。</text>
    </view>
  </view>
</template>

<script>
import { APPLY_TYPES, listLaunchable } from '@/api/workflow'
import { go as navGo } from '@/utils/nav'

export default {
  data() {
    return {
      APPLY_TYPES,
      loadingCatalog: false,
      categories: [],
      definitions: []
    }
  },
  computed: {
    /** 分类 + 已发布流程定义（取自 /api/v1/wf/launchable，与后台管理端同步） */
    groups() {
      const byCategory = {}
      ;(this.definitions || []).forEach((d) => {
        const key = String(d.categoryId)
        if (!byCategory[key]) byCategory[key] = []
        byCategory[key].push(d)
      })
      return (this.categories || [])
        .map((c) => ({ category: c, definitions: byCategory[String(c.id)] || [] }))
        .filter((g) => g.definitions.length > 0)
    }
  },
  onLoad() {
    this.loadCatalog()
  },
  methods: {
    async loadCatalog() {
      this.loadingCatalog = true
      try {
        const data = await listLaunchable()
        this.categories = (data && data.categories) || []
        this.definitions = (data && data.definitions) || []
      } catch (e) {
        this.categories = []
        this.definitions = []
      } finally {
        this.loadingCatalog = false
      }
    },
    iconOf(d) {
      const name = (d && d.processName) || ''
      return name ? name.slice(0, 1) : '审'
    },
    findDefForQuick(t) {
      const defs = this.definitions || []
      return defs.find((d) => d.processKey === t.type) || defs.find((d) => d.businessType === t.type) || null
    },
    selectQuick(t) {
      const def = this.findDefForQuick(t)
      if (!def) {
        uni.showToast({ title: `「${t.name}」未启用，请联系管理员`, icon: 'none' })
        return
      }
      this.openForm(def)
    },
    openForm(d) {
      navGo('/pages/approval/apply-form?id=' + d.id)
    }
  }
}
</script>

<style lang="scss" scoped>
.apply {
  padding-bottom: 40rpx;
}
.apply__types {
  display: flex;
  flex-wrap: wrap;
  padding: 24rpx 16rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.apply__type {
  width: 33.33%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 0;
  box-sizing: border-box;
}
.apply__type-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: #eef2fb;
  color: $oa-primary;
  font-size: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.apply__type-name {
  margin-top: 10rpx;
  font-size: 25rpx;
  color: $oa-text-secondary;
}
.apply__section {
  margin: 20rpx 24rpx 0;
}
.apply__section-title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: $oa-text;
}
.apply__group {
  margin-top: 16rpx;
  padding: 20rpx 24rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.apply__group-title {
  display: block;
  font-size: 25rpx;
  font-weight: 600;
  color: $oa-text-secondary;
}
.apply__group-list {
  margin-top: 8rpx;
}
.apply__def {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.apply__def:last-child {
  border-bottom: none;
}
.apply__def-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  background: #eef2fb;
  color: $oa-primary;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.apply__def-info {
  margin-left: 20rpx;
  min-width: 0;
}
.apply__def-name {
  display: block;
  font-size: 28rpx;
  color: $oa-text;
}
.apply__def-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: $oa-text-muted;
}
.apply__tip {
  margin: 24rpx;
  padding: 24rpx 32rpx;
  background: $oa-card;
  border-radius: $oa-radius;
  color: $oa-text-muted;
  font-size: 23rpx;
  line-height: 1.8;
}
.apply__loading {
  padding: 40rpx 0;
  text-align: center;
  color: $oa-text-muted;
  font-size: 25rpx;
}
</style>
