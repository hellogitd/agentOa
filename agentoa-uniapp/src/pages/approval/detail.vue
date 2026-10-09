<template>
  <view class="dt">
    <view v-if="loading" class="dt__state">加载中…</view>
    <oa-empty v-else-if="!detail" text="流程不存在或无权查看" />

    <block v-else>
      <view class="dt__hero">
        <view class="dt__hero-head">
          <text class="dt__title">{{ detail.title }}</text>
          <oa-tag :text="status.label" :tone="status.tone" />
        </view>
        <view class="dt__hero-meta">
          <text>{{ bizName }}</text>
          <text>{{ detail.processName }}</text>
          <text>发起人 {{ detail.initiatorName || '-' }}</text>
          <text>{{ fmtDate(detail.startTime) }}</text>
        </view>
      </view>

      <oa-card title="表单内容">
        <view v-if="formRows.length === 0" class="dt__empty">暂无表单数据</view>
        <view v-for="row in formRows" :key="row.key" class="dt__row">
          <text class="dt__row-label">{{ row.label }}</text>
          <text class="dt__row-value">{{ row.value }}</text>
        </view>
        <view v-for="(group, gi) in formLists" :key="'g' + gi" class="dt__group">
          <text class="dt__group-title">{{ group.label }} {{ gi + 1 }}</text>
          <view v-for="row in group.rows" :key="group.label + row.label" class="dt__row">
            <text class="dt__row-label">{{ row.label }}</text>
            <text class="dt__row-value">{{ row.value }}</text>
          </view>
        </view>
        <view v-if="attachments.length" class="dt__atts">
          <text class="dt__group-title">附件</text>
          <view v-for="(att, ai) in attachments" :key="'a' + ai" class="dt__att">
            <image
              v-if="attachmentPreviews[ai]"
              class="dt__att-thumb"
              :src="attachmentPreviews[ai]"
              mode="aspectFill"
              @click="viewAttachment(att, ai)"
            />
            <view class="dt__att-row">
              <text class="dt__att-label">{{ att.label }}</text>
              <text class="dt__att-act" @click="viewAttachment(att, ai)">查看</text>
              <text class="dt__att-act" @click="downloadAttachment(att)">下载</text>
            </view>
          </view>
        </view>
      </oa-card>

      <oa-card title="流程图">
        <view v-if="diagram" class="dt__diagram">
          <image class="dt__diagram-img" :src="diagram" mode="widthFix" />
        </view>
        <text v-else class="dt__empty">{{ diagramError || '流程图加载中…' }}</text>
      </oa-card>

      <oa-card title="审批历史">
        <view v-if="actions.length === 0" class="dt__empty">暂无操作记录</view>
        <view v-for="(a, i) in actions" :key="i" class="dt__action">
          <view class="dt__action-head">
            <text class="dt__action-op">{{ a.operatorName || a.operatorUserId }} · {{ actionName(a.action) }}</text>
            <text class="dt__action-time">{{ fmtDateTime(a.actionTime) }}</text>
          </view>
          <text v-if="a.taskName" class="dt__action-task">{{ a.taskName }}</text>
          <text v-if="a.comment" class="dt__action-comment">{{ a.comment }}</text>
          <text v-if="a.newAssigneeName" class="dt__action-task">转办至 {{ a.newAssigneeName }}</text>
        </view>
      </oa-card>

      <view class="dt__placeholder" />

      <view v-if="canAct" class="dt__bar">
        <button v-if="canRevoke" class="dt__bar-btn dt__bar-btn--ghost" @click="openAction('revoke')">撤销</button>
        <button v-if="canTransfer" class="dt__bar-btn dt__bar-btn--ghost" @click="openAction('transfer')">转办</button>
        <button v-if="canComplete" class="dt__bar-btn dt__bar-btn--danger" @click="openAction('reject')">拒绝</button>
        <button v-if="canComplete" class="dt__bar-btn dt__bar-btn--primary" @click="openAction('agree')">同意</button>
      </view>
    </block>

    <view v-if="actionType" class="dt__mask" @click="closeAction">
      <view class="dt__panel" @click.stop>
        <text class="dt__panel-title">{{ actionTitle }}</text>

        <view v-if="actionType === 'transfer'" class="dt__panel-field">
          <user-select-field
            label="目标办理人"
            required
            :value="targetUserId"
            placeholder="请选择目标办理人"
            @change="onTargetUserChange"
          />
        </view>

        <view class="dt__panel-field">
          <text class="dt__panel-label">{{ actionType === 'transfer' ? '转办原因' : '审批意见' }}</text>
          <textarea
            v-model="comment"
            class="dt__panel-textarea"
            :placeholder="actionType === 'revoke' ? '撤销原因（可选）' : '请填写意见（拒绝时必填）'"
            maxlength="500"
          />
        </view>

        <view class="dt__panel-btns">
          <button class="dt__panel-btn dt__panel-btn--ghost" @click="closeAction">取消</button>
          <button class="dt__panel-btn dt__panel-btn--primary" :disabled="submitting" @click="confirmAction">
            {{ submitting ? '提交中…' : '确定' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  getInstance,
  getInstanceHistory,
  completeTask,
  transferTask,
  revokeInstance,
  instanceDiagramUrl,
  instanceStatus,
  BUSINESS_TYPE_NAME
} from '@/api/workflow'
import { fetchImage, downloadAuthed } from '@/utils/request'
import { previewFlowImage, downloadFlowAttachment, previewInvoiceImage } from '@/api/file'
import { getUser } from '@/utils/auth'
import { back as navBack } from '@/utils/nav'
import { formatDate, formatDateTime } from '@/utils/format'
import OaCard from '@/components/oa-card.vue'
import OaTag from '@/components/oa-tag.vue'
import OaEmpty from '@/components/oa-empty.vue'
import UserSelectField from '@/components/user-select-field.vue'

const ACTION_NAME = {
  agree: '同意',
  reject: '拒绝',
  transfer: '转办',
  revoke: '撤销',
  start: '发起',
  submit: '提交',
  create: '创建'
}

export default {
  components: { OaCard, OaTag, OaEmpty, UserSelectField },
  data() {
    return {
      instanceId: '',
      taskId: '',
      loading: true,
      detail: null,
      actions: [],
      diagram: '',
      diagramError: '',
      actionType: '',
      comment: '',
      targetUserId: '',
      submitting: false,
      attachmentPreviews: {}
    }
  },
  computed: {
    status() {
      return instanceStatus(this.detail && this.detail.status)
    },
    bizName() {
      return BUSINESS_TYPE_NAME[this.detail && this.detail.businessType] || (this.detail && this.detail.businessType) || '-'
    },
    myUserId() {
      const u = getUser()
      return u && u.userId ? String(u.userId) : ''
    },
    myTasks() {
      return (this.detail && this.detail.currentTasks) || []
    },
    canComplete() {
      return this.myTasks.some((t) => String(t.assigneeId) === this.myUserId)
    },
    canTransfer() {
      return this.canComplete
    },
    canRevoke() {
      if (!this.detail) return false
      return (
        String(this.detail.initiatorUserId) === this.myUserId &&
        Number(this.detail.status) === 1 &&
        this.actions.length === 0
      )
    },
    canAct() {
      return this.canComplete || this.canRevoke
    },
    actionTitle() {
      return { agree: '同意审批', reject: '拒绝审批', transfer: '转办任务', revoke: '撤销流程' }[this.actionType] || ''
    },
    formRows() {
      return this.rendered.rows
    },
    formLists() {
      return this.rendered.lists
    },
    attachments() {
      return this.rendered.attachments || []
    },
    rendered() {
      return renderForm(this.detail)
    }
  },
  onLoad(options) {
    this.instanceId = (options && options.id) || ''
    this.taskId = (options && options.task) || ''
    this.fetch()
    this.fetchDiagram()
  },
  methods: {
    fmtDate: formatDate,
    fmtDateTime: formatDateTime,
    actionName(a) {
      return ACTION_NAME[a] || a
    },
    async fetch() {
      this.loading = true
      try {
        this.detail = await getInstance(this.instanceId)
        this.actions = (await getInstanceHistory(this.instanceId)) || []
        this.loadAttachmentPreviews()
      } catch (e) {
        this.detail = null
      } finally {
        this.loading = false
      }
    },
    loadAttachmentPreviews() {
      this.attachments.forEach((att, i) => {
        if (att.kind !== 'image' && att.kind !== 'invoice') return
        const loader = att.kind === 'invoice' ? previewInvoiceImage(att.invoiceId) : previewFlowImage(this.instanceId, att.fileId)
        loader
          .then((uri) => {
            this.attachmentPreviews = { ...this.attachmentPreviews, [i]: uri }
          })
          .catch(() => {})
      })
    },
    viewAttachment(att, ai) {
      const uri = this.attachmentPreviews[ai]
      if (uri) {
        uni.previewImage({ urls: [uri] })
        return
      }
      this.downloadAttachment(att)
    },
    downloadAttachment(att) {
      const p =
        att.kind === 'invoice'
          ? downloadAuthed(`/api/v1/finance/invoices/${att.invoiceId}/download`, att.label)
          : downloadFlowAttachment(this.instanceId, att.fileId, att.label)
      p.then((res) => {
        if (res && res.tempFilePath) {
          uni.openDocument({ filePath: res.tempFilePath, fail: () => {} })
        }
      }).catch(() => {})
    },
    async fetchDiagram() {
      try {
        this.diagram = await fetchImage(instanceDiagramUrl(this.instanceId))
      } catch (e) {
        this.diagramError = '流程图不可用'
      }
    },
    openAction(type) {
      this.actionType = type
      this.comment = ''
      this.targetUserId = ''
    },
    onTargetUserChange(v) {
      this.targetUserId = v == null ? '' : String(v)
    },
    closeAction() {
      if (this.submitting) return
      this.actionType = ''
    },
    async confirmAction() {
      if (this.submitting) return
      const type = this.actionType
      if (type === 'reject' && !this.comment.trim()) {
        uni.showToast({ title: '拒绝必须填写审批意见', icon: 'none' })
        return
      }
      if (type === 'transfer' && !this.targetUserId.trim()) {
        uni.showToast({ title: '请选择目标办理人', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        const lockVersion = this.detail ? this.detail.lockVersion : undefined
        if (type === 'agree' || type === 'reject') {
          const taskId = this.taskId || (this.myTasks[0] && this.myTasks[0].taskId)
          await completeTask(taskId, {
            action: type,
            comment: this.comment.trim() || undefined,
            lockVersion
          })
          uni.showToast({ title: type === 'agree' ? '已同意' : '已拒绝', icon: 'success' })
        } else if (type === 'transfer') {
          const taskId = this.taskId || (this.myTasks[0] && this.myTasks[0].taskId)
          await transferTask(taskId, {
            targetUserId: this.targetUserId.trim(),
            reason: this.comment.trim() || undefined
          })
          uni.showToast({ title: '已转办', icon: 'success' })
        } else if (type === 'revoke') {
          await revokeInstance(this.instanceId, lockVersion)
          uni.showToast({ title: '已撤销', icon: 'success' })
        }
        this.actionType = ''
        await this.fetch()
        setTimeout(() => navBack(), 500)
      } catch (e) {
        // 错误提示已在请求层统一处理
      } finally {
        this.submitting = false
      }
    }
  }
}

/** 按提交时固定的表单快照渲染（docs/12：实例固定表单版本）。 */
function renderForm(detail) {
  const rows = []
  const lists = []
  const attachments = []
  if (!detail) return { rows, lists, attachments }
  let schema = null
  let data = null
  try {
    schema = detail.formSchema ? JSON.parse(detail.formSchema) : null
  } catch (e) {
    schema = null
  }
  try {
    data = detail.formData ? JSON.parse(detail.formData) : null
  } catch (e) {
    data = null
  }
  if (!data) return { rows, lists, attachments }

  const fields = (schema && schema.fields) || []
  const labelOf = {}
  const typeOf = {}
  fields.forEach((f) => {
    labelOf[f.key] = f.label || f.key
    typeOf[f.key] = f.type
  })

  Object.keys(data).forEach((key) => {
    const value = data[key]
    if (value === null || value === undefined || value === '') return
    const label = labelOf[key] || key
    const type = typeOf[key]
    if (Array.isArray(value)) {
      const field = fields.find((f) => f.key === key)
      const itemFields = (field && field.itemFields) || []
      value.forEach((item) => {
        const sub = []
        itemFields.forEach((f) => {
          const v = item[f.key]
          if (v === null || v === undefined || v === '') return
          if (f.type === 'file' || f.type === 'image') {
            attachments.push({ fileId: String(v), label: `${label}·${f.label || f.key}`, kind: f.type })
            return
          }
          sub.push({ label: f.label || f.key, value: String(v) })
        })
        if (item && item.invoiceId) {
          attachments.push({ invoiceId: String(item.invoiceId), label: `${label}·发票`, kind: 'invoice' })
        }
        if (sub.length === 0) {
          Object.keys(item || {}).forEach((k) => {
            if (k === 'invoiceId') return
            sub.push({ label: k, value: String(item[k]) })
          })
        }
        if (sub.length) lists.push({ label, rows: sub })
      })
      return
    }
    if (type === 'file' || type === 'image') {
      attachments.push({ fileId: String(value), label, kind: type })
      return
    }
    rows.push({ label, value: String(value) })
  })
  return { rows, lists, attachments }
}
</script>

<style lang="scss" scoped>
.dt {
  padding-bottom: 40rpx;
}
.dt__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.dt__hero {
  margin: 20rpx 24rpx;
  padding: 28rpx;
  background: linear-gradient(150deg, $oa-primary 0%, #4b83f8 100%);
  border-radius: $oa-radius;
  color: #ffffff;
}
.dt__hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.dt__title {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  font-weight: 600;
  margin-right: 16rpx;
}
.dt__hero-meta {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
}
.dt__hero-meta text {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.86);
  margin-right: 24rpx;
}
.dt__row {
  display: flex;
  align-items: flex-start;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.dt__row:last-child {
  border-bottom: none;
}
.dt__row-label {
  width: 200rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: $oa-text-secondary;
}
.dt__row-value {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: $oa-text;
  text-align: right;
  word-break: break-all;
}
.dt__group {
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  background: #f7f9fc;
  border-radius: 12rpx;
}
.dt__group-title {
  display: block;
  font-size: 24rpx;
  color: $oa-primary;
  margin-bottom: 8rpx;
}
.dt__atts {
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  background: #f7f9fc;
  border-radius: 12rpx;
}
.dt__att {
  margin-top: 12rpx;
}
.dt__att-thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  background: #eef1f7;
}
.dt__att-row {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
}
.dt__att-label {
  flex: 1;
  font-size: 24rpx;
  color: $oa-text;
}
.dt__att-act {
  margin-left: 24rpx;
  font-size: 24rpx;
  color: $oa-primary;
}
.dt__empty {
  display: block;
  padding: 24rpx 0;
  color: $oa-text-muted;
  font-size: 24rpx;
}
.dt__diagram {
  text-align: center;
}
.dt__diagram-img {
  width: 100%;
}
.dt__action {
  padding: 18rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.dt__action:last-child {
  border-bottom: none;
}
.dt__action-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dt__action-op {
  font-size: 26rpx;
  color: $oa-text;
  font-weight: 600;
}
.dt__action-time {
  font-size: 22rpx;
  color: $oa-text-muted;
}
.dt__action-task {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: $oa-text-secondary;
}
.dt__action-comment {
  display: block;
  margin-top: 8rpx;
  padding: 12rpx 16rpx;
  background: #f7f9fc;
  border-radius: 10rpx;
  font-size: 24rpx;
  color: $oa-text;
}
.dt__placeholder {
  height: 160rpx;
}
.dt__bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: $oa-card;
  display: flex;
  box-shadow: 0 -4rpx 16rpx rgba(31, 36, 48, 0.06);
}
.dt__bar-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0 8rpx;
  font-size: 28rpx;
  border-radius: 40rpx;
  border: none;
}
.dt__bar-btn::after {
  border: none;
}
.dt__bar-btn--primary {
  background: $oa-primary;
  color: #ffffff;
}
.dt__bar-btn--danger {
  background: #fdeceb;
  color: $oa-danger;
}
.dt__bar-btn--ghost {
  background: #f0f2f7;
  color: $oa-text;
}
.dt__mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: flex-end;
  z-index: 99;
}
.dt__panel {
  width: 100%;
  background: $oa-card;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
}
.dt__panel-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-text;
}
.dt__panel-field {
  margin-top: 24rpx;
}
.dt__panel-label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 12rpx;
}
.dt__panel-input {
  height: 76rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.dt__panel-hint {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
}
.dt__panel-textarea {
  width: 100%;
  height: 180rpx;
  padding: 16rpx 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.dt__panel-btns {
  margin-top: 32rpx;
  display: flex;
}
.dt__panel-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  margin: 0 12rpx;
  border-radius: 42rpx;
  font-size: 28rpx;
  border: none;
}
.dt__panel-btn::after {
  border: none;
}
.dt__panel-btn--ghost {
  background: #f0f2f7;
  color: $oa-text;
}
.dt__panel-btn--primary {
  background: $oa-primary;
  color: #ffffff;
}
</style>
