<template>
  <view class="usf">
    <text class="usf__label">{{ label }}<text v-if="required" class="usf__req">*</text></text>
    <view class="usf__box" :class="{ 'usf__box--empty': !displayText }" @click="open">
      {{ displayText || placeholder }}
    </view>

    <view v-if="visible" class="usf__mask" @click="close">
      <view class="usf__panel" @click.stop>
        <text class="usf__panel-title">{{ panelTitle }}</text>

        <input
          v-model="keyword"
          class="usf__search"
          confirm-type="search"
          placeholder="搜索姓名 / 账号"
          @input="onSearchInput"
          @confirm="reload"
        />

        <scroll-view scroll-y class="usf__list">
          <view v-for="u in list" :key="u.userId" class="usf__item" @click="toggle(u)">
            <view class="usf__item-main">
              <text class="usf__item-name">{{ u.name }}</text>
              <text class="usf__item-dept">{{ u.deptName || '未分配部门' }}</text>
            </view>
            <view class="usf__item-check" :class="{ 'usf__item-check--on': isSelected(u) }">
              <text v-if="isSelected(u)">✓</text>
            </view>
          </view>

          <view v-if="loading" class="usf__empty">加载中…</view>
          <view v-else-if="!list.length" class="usf__empty">无匹配人员</view>
          <view v-if="hasMore && !loading" class="usf__more" @click="loadMore">加载更多</view>
        </scroll-view>

        <view v-if="selected.length" class="usf__selected">
          <view v-for="u in selected" :key="u.userId" class="usf__tag" @click="removeOne(u)">
            <text>{{ u.name }}</text>
            <text class="usf__tag-close">✕</text>
          </view>
        </view>

        <view class="usf__btns">
          <button class="usf__btn usf__btn--ghost" @click="close">取消</button>
          <button class="usf__btn usf__btn--primary" @click="confirm">确定</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { listAssignableUsers } from '@/api/workflow'

const PAGE_SIZE = 20
let searchTimer = null

export default {
  name: 'UserSelectField',
  props: {
    label: { type: String, default: '' },
    /** 单选：用户 ID 字符串/数字；多选：用户 ID 数组 */
    value: { type: [String, Number, Array], default: '' },
    multiple: { type: Boolean, default: false },
    required: { type: Boolean, default: false },
    placeholder: { type: String, default: '请选择办理人' }
  },
  data() {
    return {
      visible: false,
      keyword: '',
      loading: false,
      list: [],
      pageNum: 1,
      pages: 1,
      /** 内部选择集 [{userId, name, deptName}] */
      selected: [],
      /** userId -> 显示名（回显缓存） */
      nameMap: {}
    }
  },
  computed: {
    displayText() {
      const ids = this.idsOf(this.value)
      if (!ids.length) return ''
      return ids.map((id) => this.nameMap[id] || id).join('、')
    },
    panelTitle() {
      return this.multiple ? '选择办理人（可多选）' : '选择办理人'
    },
    hasMore() {
      return this.pageNum < this.pages
    }
  },
  watch: {
    value: {
      immediate: true,
      handler() {
        this.syncFromValue()
      }
    }
  },
  methods: {
    idsOf(v) {
      if (v === '' || v === null || v === undefined) return []
      if (Array.isArray(v)) return v.map((x) => String(x)).filter(Boolean)
      return String(v)
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
    },
    async syncFromValue() {
      const ids = this.idsOf(this.value)
      if (!ids.length) {
        this.selected = []
        return
      }
      this.selected = ids.map((id) => ({ userId: id, name: this.nameMap[id] || id, deptName: '' }))
      const unknown = ids.filter((id) => !this.nameMap[id])
      if (!unknown.length) return
      try {
        const res = await listAssignableUsers({ pageNum: 1, pageSize: 100 })
        const rows = (res && res.records) || []
        rows.forEach((r) => {
          const id = String(r.userId)
          if (r.name) this.nameMap = { ...this.nameMap, [id]: r.name }
        })
        this.selected = this.selected.map((u) => ({ ...u, name: this.nameMap[u.userId] || u.userId }))
      } catch (e) {
        // 检索失败时保留 ID 展示
      }
    },
    isSelected(u) {
      return this.selected.some((s) => String(s.userId) === String(u.userId))
    },
    toggle(u) {
      if (this.isSelected(u)) {
        this.removeOne(u)
        return
      }
      if (this.multiple) {
        this.selected = [...this.selected, { userId: String(u.userId), name: u.name, deptName: u.deptName }]
      } else {
        this.selected = [{ userId: String(u.userId), name: u.name, deptName: u.deptName }]
      }
      this.nameMap = { ...this.nameMap, [String(u.userId)]: u.name }
    },
    removeOne(u) {
      this.selected = this.selected.filter((s) => String(s.userId) !== String(u.userId))
    },
    open() {
      this.visible = true
      this.keyword = ''
      this.reload()
    },
    close() {
      if (this.loading) return
      this.visible = false
    },
    onSearchInput() {
      clearTimeout(searchTimer)
      searchTimer = setTimeout(() => this.reload(), 300)
    },
    async reload() {
      this.pageNum = 1
      this.list = []
      await this.fetch()
    },
    async loadMore() {
      if (this.loading || this.pageNum >= this.pages) return
      this.pageNum += 1
      await this.fetch()
    },
    async fetch() {
      this.loading = true
      try {
        const res = await listAssignableUsers({
          keyword: this.keyword.trim(),
          pageNum: this.pageNum,
          pageSize: PAGE_SIZE
        })
        const rows = (res && res.records) || []
        this.pages = (res && res.pages) || 1
        const map = { ...this.nameMap }
        rows.forEach((r) => {
          if (r.name) map[String(r.userId)] = r.name
        })
        this.nameMap = map
        this.list = this.pageNum === 1 ? rows : [...this.list, ...rows]
      } catch (e) {
        this.list = []
      } finally {
        this.loading = false
      }
    },
    confirm() {
      const ids = this.selected.map((s) => String(s.userId))
      if (this.multiple) {
        this.$emit('change', ids)
      } else {
        this.$emit('change', ids[0] === undefined ? '' : ids[0])
      }
      this.visible = false
    }
  }
}
</script>

<style lang="scss" scoped>
.usf {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.usf:last-child {
  border-bottom: none;
}
.usf__label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 12rpx;
}
.usf__req {
  color: $oa-danger;
  margin-left: 6rpx;
}
.usf__box {
  min-height: 76rpx;
  line-height: 76rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: $oa-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usf__box--empty {
  color: $oa-text-muted;
}
.usf__mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  z-index: 999;
}
.usf__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 78vh;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx;
  display: flex;
  flex-direction: column;
}
.usf__panel-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-text;
  margin-bottom: 20rpx;
}
.usf__search {
  height: 72rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
  margin-bottom: 16rpx;
}
.usf__list {
  flex: 1;
  min-height: 320rpx;
  max-height: 48vh;
}
.usf__item {
  display: flex;
  align-items: center;
  padding: 22rpx 8rpx;
  border-bottom: 1rpx solid $oa-border;
}
.usf__item-main {
  flex: 1;
  min-width: 0;
}
.usf__item-name {
  display: block;
  font-size: 30rpx;
  color: $oa-text;
}
.usf__item-dept {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-top: 4rpx;
}
.usf__item-check {
  width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
  border-radius: 50%;
  border: 2rpx solid $oa-border;
  color: #fff;
  font-size: 26rpx;
}
.usf__item-check--on {
  background: $oa-primary;
  border-color: $oa-primary;
}
.usf__empty {
  padding: 60rpx 0;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.usf__more {
  padding: 26rpx 0;
  text-align: center;
  color: $oa-primary;
  font-size: 26rpx;
}
.usf__selected {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 16rpx 0;
}
.usf__tag {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 18rpx;
  background: $oa-primary-light;
  color: $oa-primary;
  border-radius: 999rpx;
  font-size: 24rpx;
}
.usf__tag-close {
  font-size: 22rpx;
}
.usf__btns {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}
.usf__btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.usf__btn--ghost {
  background: #f5f7fb;
  color: $oa-text;
}
.usf__btn--primary {
  background: $oa-primary;
  color: #fff;
}
</style>
