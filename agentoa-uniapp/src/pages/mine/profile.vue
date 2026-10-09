<template>
  <view class="pf">
    <view v-if="loading" class="pf__state">加载中…</view>

    <block v-else>
      <view class="pf__card">
        <text class="pf__card-title">账号信息</text>
        <oa-cell label="用户 ID" :value="account.userId" />
        <oa-cell label="登录账号" :value="account.username" />
        <oa-cell label="姓名" :value="account.nickname" />
        <oa-cell label="部门" :value="account.deptName" />
        <oa-cell label="岗位" :value="account.postName" />
        <oa-cell label="角色">
          <text class="pf__roles">{{ roleText }}</text>
        </oa-cell>
      </view>

      <view class="pf__card" v-if="employee">
        <view class="pf__card-head">
          <text class="pf__card-title">人事档案</text>
          <text v-if="!editing" class="pf__edit" @click="startEdit">编辑联系方式</text>
        </view>
        <oa-cell label="工号" :value="employee.employeeNo" />
        <oa-cell label="姓名" :value="employee.name" />
        <oa-cell label="性别" :value="genderText" />
        <oa-cell label="状态">
          <oa-tag :text="status.label" :tone="status.tone" />
        </oa-cell>
        <oa-cell label="部门" :value="employee.deptName" />
        <oa-cell label="岗位" :value="employee.postName" />
        <oa-cell label="职级" :value="employee.positionLevel" />
        <oa-cell label="直属主管" :value="employee.directLeaderName" />
        <oa-cell label="入职日期" :value="employee.entryDate" />
        <oa-cell label="转正日期" :value="employee.regularDate || employee.probationEndDate" />
        <oa-cell v-if="employee.leaveDate" label="离职日期" :value="employee.leaveDate" />
        <oa-cell v-if="employee.phone" label="手机" :value="employee.phone" />
        <oa-cell v-if="employee.email" label="邮箱" :value="employee.email" />

        <view v-if="editing" class="pf__editor">
          <text-field label="手机" :value="editPhone" placeholder="可选" type="number" @change="(v) => (editPhone = v)" />
          <text-field label="邮箱" :value="editEmail" placeholder="可选" @change="(v) => (editEmail = v)" />
          <view class="pf__editor-btns">
            <button class="pf__editor-btn pf__editor-btn--ghost" :disabled="saving" @click="editing = false">取消</button>
            <button class="pf__editor-btn pf__editor-btn--primary" :disabled="saving" @click="saveEdit">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </view>
          <text class="pf__editor-hint">仅手机、邮箱可自助修改，其他字段由 HR 维护</text>
        </view>
      </view>

      <view class="pf__card" v-else>
        <text class="pf__card-title">人事档案</text>
        <text class="pf__empty">未查询到员工档案。若刚入职，请联系 HR 建立档案。</text>
      </view>

      <view class="pf__card">
        <text class="pf__card-title">安全</text>
        <view class="pf__link" @click="goPassword">
          <text class="pf__link-name">修改密码</text>
          <text class="pf__link-arrow">›</text>
        </view>
      </view>
    </block>
  </view>
</template>

<script>
import { listEmployees, employeeStatus, updateSelfProfile } from '@/api/hr'
import { useUserStore } from '@/store/user'
import { go as navGo } from '@/utils/nav'
import OaCell from '@/components/oa-cell.vue'
import OaTag from '@/components/oa-tag.vue'
import TextField from '@/components/text-field.vue'

const GENDER = { 0: '男', 1: '女', M: '男', F: '女' }

export default {
  components: { OaCell, OaTag, TextField },
  data() {
    return {
      loading: true,
      account: {},
      employee: null,
      editing: false,
      editPhone: '',
      editEmail: '',
      saving: false
    }
  },
  computed: {
    roleText() {
      return (this.account.roles || []).join('、') || '-'
    },
    genderText() {
      return GENDER[this.employee && this.employee.gender] || '-'
    },
    status() {
      return employeeStatus(this.employee && this.employee.status)
    }
  },
  onShow() {
    this.fetch()
  },
  methods: {
    goPassword() {
      navGo('/pages/mine/password')
    },
    startEdit() {
      this.editPhone = (this.employee && this.employee.phone) || ''
      this.editEmail = (this.employee && this.employee.email) || ''
      this.editing = true
    },
    async saveEdit() {
      if (this.saving) return
      this.saving = true
      try {
        await updateSelfProfile({
          phone: this.editPhone ? String(this.editPhone).trim() : undefined,
          email: this.editEmail ? String(this.editEmail).trim() : undefined
        })
        uni.showToast({ title: '已保存', icon: 'success' })
        this.editing = false
        this.fetch()
      } catch (e) {
        // 校验/权限错误信封由请求层提示
      } finally {
        this.saving = false
      }
    },
    async fetch() {
      this.loading = true
      try {
        this.account = (await useUserStore().loadProfile(true)) || {}
      } catch (e) {
        this.account = {}
      }
      try {
        const page = await listEmployees({
          userId: this.account.userId,
          pageNum: 1,
          pageSize: 1
        })
        this.employee = (page && page.records && page.records[0]) || null
      } catch (e) {
        this.employee = null
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.pf {
  padding-bottom: 40rpx;
}
.pf__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.pf__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.pf__card-title {
  display: block;
  margin-bottom: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.pf__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pf__edit {
  font-size: 24rpx;
  color: $oa-primary;
}
.pf__editor {
  margin-top: 8rpx;
}
.pf__editor-btns {
  margin-top: 16rpx;
  display: flex;
}
.pf__editor-btn {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 38rpx;
  font-size: 26rpx;
}
.pf__editor-btn--ghost {
  background: #f0f2f7;
  color: $oa-text-secondary;
  margin-right: 16rpx;
}
.pf__editor-btn--primary {
  background: $oa-primary;
  color: #ffffff;
}
.pf__editor-btn::after {
  border: none;
}
.pf__editor-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 20rpx;
  color: $oa-text-muted;
}
.pf__roles {
  font-size: 25rpx;
  color: $oa-text;
}
.pf__empty {
  display: block;
  padding: 20rpx 0;
  font-size: 24rpx;
  color: $oa-text-muted;
}
.pf__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
}
.pf__link-name {
  font-size: 27rpx;
  color: $oa-text;
}
.pf__link-arrow {
  color: $oa-text-muted;
  font-size: 32rpx;
}
</style>
