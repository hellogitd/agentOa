<template>
  <view class="apply-form">
    <view v-if="loading" class="apply-form__loading">表单加载中…</view>

    <view v-else-if="loadError" class="apply-form__loading">
      <text>{{ loadError }}</text>
      <button class="apply-form__back" @click="goBack">返回</button>
    </view>

    <block v-else>
      <view class="apply-form__panel">
        <text class="apply-form__panel-title">{{ current.name }}</text>
        <text class="apply-form__panel-desc">{{ current.desc || '表单结构取自流程模板发布的表单快照' }}</text>

        <view v-if="!schema" class="apply-form__loading">表单模板不可用</view>

        <block v-else>
          <template v-for="field in schema.fields" :key="field.key">
            <!-- 明细列表 -->
            <view v-if="field.type === 'list'" class="apply-form__details">
              <view class="apply-form__details-head">
                <text class="apply-form__details-title">{{ field.label }}</text>
                <text class="apply-form__details-add" @click="addRow(field)">＋ 添加{{ field.label }}</text>
              </view>
              <view v-if="rowsOf(field).length === 0" class="apply-form__details-empty">
                请至少添加一条{{ field.label }}
              </view>
              <view v-for="(row, ri) in rowsOf(field)" :key="field.key + ri" class="apply-form__detail">
                <view class="apply-form__detail-head">
                  <text class="apply-form__detail-index">{{ field.label }} {{ ri + 1 }}</text>
                  <text class="apply-form__detail-del" @click="removeRow(field, ri)">删除</text>
                </view>
                <template v-for="sub in field.itemFields" :key="field.key + ri + sub.key">
                  <datetime-picker
                    v-if="sub.type === 'date' || sub.type === 'datetime'"
                    :label="sub.label"
                    :date-only="sub.type === 'date'"
                    :required="!!sub.required"
                    :value="row[sub.key]"
                    @change="(v) => setRow(field, ri, sub.key, v)"
                  />
                  <select-field
                    v-else-if="sub.type === 'select'"
                    :label="sub.label"
                    :options="sub.options || []"
                    :required="!!sub.required"
                    :value="row[sub.key]"
                    @change="(v) => setRow(field, ri, sub.key, v)"
                  />
                  <attachment-field
                    v-else-if="sub.type === 'file' || sub.type === 'image'"
                    :label="sub.label"
                    :required="!!sub.required"
                    :image-only="sub.type === 'image'"
                    :value="row[sub.key]"
                    @change="(v) => setRow(field, ri, sub.key, v)"
                  />
                  <text-field
                    v-else
                    :label="sub.label"
                    :required="!!sub.required"
                    :value="row[sub.key]"
                    :type="sub.type === 'number' ? 'number' : 'text'"
                    :placeholder="'请输入' + sub.label"
                    @change="(v) => setRow(field, ri, sub.key, v)"
                  />
                </template>
                <view v-if="field.key === 'details'" class="apply-form__invoice">
                  <template v-if="row.invoiceId">
                    <text class="apply-form__invoice-ok">发票已附</text>
                    <text class="apply-form__invoice-act" @click="viewInvoice(row)">查看</text>
                    <text class="apply-form__invoice-act" @click="removeInvoice(field, ri)">移除</text>
                  </template>
                  <text v-else class="apply-form__invoice-act" @click="attachInvoice(field, ri)">上传发票图片</text>
                </view>
              </view>
              <view v-if="field.key === 'details'" class="apply-form__total">
                <text class="apply-form__total-label">合计金额</text>
                <text class="apply-form__total-value">{{ totalAmount }} 元</text>
              </view>
            </view>

            <!-- 只读计算项 -->
            <view v-else-if="field.readonly" class="apply-form__row">
              <text class="apply-form__row-label">{{ field.label }}</text>
              <text class="apply-form__row-value">{{ readonlyValue(field) }}</text>
            </view>

            <!-- 日期 / 时间 -->
            <datetime-picker
              v-else-if="field.type === 'date' || field.type === 'datetime'"
              :label="field.label"
              :date-only="field.type === 'date'"
              :required="!!field.required"
              :value="form[field.key]"
              @change="(v) => set(field.key, v)"
            />

            <!-- 下拉 -->
            <select-field
              v-else-if="field.type === 'select'"
              :label="field.label"
              :options="field.options || []"
              :required="!!field.required"
              :value="form[field.key]"
              :placeholder="'请选择' + field.label"
              @change="(v) => set(field.key, v)"
            />

            <!-- 附件 / 图片 -->
            <attachment-field
              v-else-if="field.type === 'file' || field.type === 'image'"
              :label="field.label"
              :required="!!field.required"
              :image-only="field.type === 'image'"
              :value="form[field.key]"
              @change="(v) => set(field.key, v)"
            />

            <!-- 文本 -->
            <text-field
              v-else
              :label="field.label"
              :required="!!field.required"
              :value="form[field.key]"
              :type="field.type === 'number' ? 'number' : 'text'"
              :textarea="field.type === 'textarea'"
              :placeholder="'请输入' + field.label"
              @change="(v) => set(field.key, v)"
            />
          </template>

          <user-select-field
            v-for="node in selectableNodes"
            :key="node.nodeId"
            :label="node.name || '选择审批人'"
            required
            :multiple="!!node.multiple"
            :value="selections[node.nodeId]"
            :placeholder="node.multiple ? '请选择审批人（可多选）' : '请选择审批人'"
            @change="(v) => setSelection(node.nodeId, v)"
          />

          <text-field label="备注" :value="form.__remark" placeholder="可选" @change="(v) => set('__remark', v)" />
        </block>
      </view>

      <view class="apply-form__placeholder" />
      <view class="apply-form__bar">
        <button class="apply-form__bar-btn apply-form__bar-btn--ghost" :disabled="submitting" @click="saveDraft">保存草稿</button>
        <button class="apply-form__bar-btn apply-form__bar-btn--primary" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交审批' }}
        </button>
      </view>
    </block>
  </view>
</template>

<script>
import { getLaunchableDefinition, getForm, businessApiFor, createGenericRequest, launchGenericRequest } from '@/api/workflow'
import { createInvoice, previewInvoiceImage, uploadPrivateFile } from '@/api/file'
import { chooseImageFile } from '@/utils/picker'
import { formatMoney } from '@/utils/format'
import { back as navBack, redirectTo as navRedirect } from '@/utils/nav'
import SelectField from '@/components/select-field.vue'
import TextField from '@/components/text-field.vue'
import DatetimePicker from '@/components/datetime-picker.vue'
import UserSelectField from '@/components/user-select-field.vue'
import AttachmentField from '@/components/attachment-field.vue'

const AMOUNT_KEYS = ['amount', 'totalAmount', 'regularSalary']
const AMOUNT_RE = /^\d{1,10}(\.\d{1,2})?$/

/** 业务承接单直接承载的字段；其余字段按 docs/12 表单快照写入 formData。 */
const BO_FIELDS = {
  leave: ['leaveType', 'startTime', 'endTime', 'reason'],
  overtime: ['overtimeDate', 'overtimeType', 'startTime', 'endTime', 'reason'],
  correction: ['attendanceDate', 'punchType', 'correctedTime', 'reason'],
  reimburse: ['reimburseType', 'totalAmount', 'payMethod', 'details', 'reason'],
  regularize: ['effectiveDate'],
  offboard: ['effectiveDate']
}

export default {
  components: { SelectField, TextField, DatetimePicker, UserSelectField, AttachmentField },
  data() {
    return {
      definitionId: null,
      current: null,
      schema: null,
      loading: true,
      loadError: '',
      form: /** @type {Record<string, any>} */ ({ __remark: '' }),
      /** 发起人自选审批节点（selectableNodes） */
      selectableNodes: [],
      /** nodeId -> userId | userId[] */
      selections: {},
      submitting: false
    }
  },
  computed: {
    totalAmount() {
      const field = this.schema && (this.schema.fields || []).find((f) => f.key === 'details')
      const rows = field ? this.form.details || [] : []
      const sum = rows.reduce((acc, d) => {
        const n = Number(d.amount)
        return acc + (isNaN(n) ? 0 : n)
      }, 0)
      return formatMoney(sum)
    }
  },
  onLoad(query) {
    this.definitionId = (query && (query.id || query.definitionId)) || null
    this.load()
  },
  methods: {
    async load() {
      if (!this.definitionId) {
        this.loading = false
        this.loadError = '缺少流程参数，请从发起申请页进入'
        return
      }
      this.loading = true
      this.loadError = ''
      try {
        const def = await getLaunchableDefinition(this.definitionId)
        this.current = {
          id: def.id,
          type: def.businessType,
          name: def.processName,
          desc: def.remark,
          formKey: def.formKey,
          definition: def
        }
        if (def.processName) {
          try {
            uni.setNavigationBarTitle({ title: def.processName })
          } catch (e) {
            // ignore
          }
        }
        let form = def.form
        if (!form || !form.schema) form = await getForm(def.formKey)
        this.schema = form && form.schema ? JSON.parse(form.schema) : null
        this.form = /** @type {Record<string, any>} */ ({ __remark: '' })
        ;(this.schema ? this.schema.fields : []).forEach((f) => {
          if (f.type === 'list') this.form[f.key] = []
        })
        this.selectableNodes = def.selectableNodes || []
        this.selections = {}
      } catch (e) {
        this.current = null
        this.schema = null
        this.loadError = (e && e.message) || '流程不可用，请返回重试'
      } finally {
        this.loading = false
      }
    },
    goBack() {
      navBack()
    },
    /** @returns {Record<string, any>[]} */
    rowsOf(field) {
      const rows = this.form[field.key]
      return Array.isArray(rows) ? rows : []
    },
    readonlyValue(field) {
      if (field.key === 'totalAmount') return this.totalAmount + ' 元'
      const v = this.form[field.key]
      return v === undefined || v === null || v === '' ? '提交后由服务端计算' : String(v)
    },
    set(key, value) {
      this.form[key] = value
    },
    setSelection(nodeId, value) {
      this.selections = { ...this.selections, [nodeId]: value }
    },
    addRow(field) {
      const blank = {}
      ;(field.itemFields || []).forEach((f) => {
        blank[f.key] = ''
      })
      const rows = this.form[field.key] ? this.form[field.key].slice() : []
      rows.push(blank)
      this.form[field.key] = rows
    },
    removeRow(field, index) {
      const rows = this.rowsOf(field).slice()
      rows.splice(index, 1)
      this.form[field.key] = rows
    },
    setRow(field, index, key, value) {
      const rows = this.rowsOf(field).map((row, i) => (i === index ? { ...row, [key]: value } : row))
      this.form[field.key] = rows
    },
    async attachInvoice(field, ri) {
      const row = this.rowsOf(field)[ri] || {}
      const invoiceNo = row.invoiceNo != null ? String(row.invoiceNo).trim() : ''
      const amount = Number(row.amount)
      if (!invoiceNo) {
        uni.showToast({ title: '请先填写发票号码', icon: 'none' })
        return
      }
      if (!amount || isNaN(amount)) {
        uni.showToast({ title: '请先填写金额', icon: 'none' })
        return
      }
      const paths = await chooseImageFile(1)
      const filePath = paths[0]
      if (!filePath) return
      uni.showLoading({ title: '发票上传中…' })
      try {
        const file = await uploadPrivateFile(filePath)
        const invoice = await createInvoice({
          invoiceType: '电子发票',
          invoiceNo,
          invoiceDate: row.occurDate ? String(row.occurDate).slice(0, 10) : undefined,
          amount: amount.toFixed(2),
          fileId: file.fileId
        })
        this.setRow(field, ri, 'invoiceId', invoice && invoice.id ? String(invoice.id) : '')
        uni.showToast({ title: '发票已附', icon: 'success' })
      } catch (e) {
        // 占用/去重 409 等错误信封由请求层提示（FINANCE_INVOICE_*）
      } finally {
        uni.hideLoading()
      }
    },
    viewInvoice(row) {
      const id = row.invoiceId
      if (!id) return
      previewInvoiceImage(String(id))
        .then((uri) => uni.previewImage({ urls: [uri] }))
        .catch(() => {})
    },
    removeInvoice(field, ri) {
      this.setRow(field, ri, 'invoiceId', '')
    },
    validate() {
      const fields = (this.schema && this.schema.fields) || []
      for (let i = 0; i < fields.length; i++) {
        const f = fields[i]
        if (f.readonly) continue
        const value = this.form[f.key]
        if (f.type === 'list') {
          const rows = value || []
          if (f.required && rows.length === 0) {
            uni.showToast({ title: `请至少添加一条${f.label}`, icon: 'none' })
            return false
          }
          for (let r = 0; r < rows.length; r++) {
            for (let s = 0; s < (f.itemFields || []).length; s++) {
              const sub = f.itemFields[s]
              const v = rows[r][sub.key]
              if (sub.required && (v === '' || v === undefined || v === null)) {
                uni.showToast({ title: `${f.label} ${r + 1}：请填写${sub.label}`, icon: 'none' })
                return false
              }
              if (AMOUNT_KEYS.indexOf(sub.key) >= 0 && v && !AMOUNT_RE.test(String(v).trim())) {
                uni.showToast({ title: `${f.label} ${r + 1}：${sub.label}格式非法`, icon: 'none' })
                return false
              }
            }
          }
          continue
        }
        if (f.required && (value === '' || value === undefined || value === null)) {
          uni.showToast({ title: `请填写${f.label}`, icon: 'none' })
          return false
        }
        if (AMOUNT_KEYS.indexOf(f.key) >= 0 && value && !AMOUNT_RE.test(String(value).trim())) {
          uni.showToast({ title: `${f.label}格式非法`, icon: 'none' })
          return false
        }
      }
      if (this.current.type === 'leave') {
        const s = this.form.startTime
        const e = this.form.endTime
        if (s && e && new Date(String(e).replace(' ', 'T')) <= new Date(String(s).replace(' ', 'T'))) {
          uni.showToast({ title: '结束时间必须晚于开始时间', icon: 'none' })
          return false
        }
      }
      for (let i = 0; i < this.selectableNodes.length; i++) {
        const node = this.selectableNodes[i]
        const picked = this.selections[node.nodeId]
        const ids = Array.isArray(picked) ? picked.filter(Boolean) : picked ? [picked] : []
        if (!ids.length) {
          uni.showToast({ title: `请选择${node.name || '审批人'}`, icon: 'none' })
          return false
        }
        if (!node.multiple && ids.length !== 1) {
          uni.showToast({ title: `${node.name || '审批人'}只能选择 1 人`, icon: 'none' })
          return false
        }
      }
      return true
    },
    buildPayload() {
      const type = this.current.type
      const fields = (this.schema && this.schema.fields) || []
      const boKeys = BO_FIELDS[type] || []
      const payload = {}
      const extra = {}
      fields.forEach((f) => {
        if (f.readonly) return
        let value = this.form[f.key]
        if (f.type === 'list') {
          value = (value || []).map((row) => {
            const out = {}
            ;(f.itemFields || []).forEach((sub) => {
              const v = row[sub.key]
              if (v === '' || v === undefined || v === null) return
              out[sub.key] = sub.type === 'number' ? Number(v) : v
            })
            return out
          })
        }
        if (value === '' || value === undefined || value === null) return
        if (boKeys.indexOf(f.key) >= 0) payload[f.key] = value
        else extra[f.key] = value
      })
      if (type === 'overtime' && !payload.overtimeType) payload.overtimeType = undefined
      if (type === 'reimburse') {
        if (!payload.totalAmount) payload.totalAmount = this.totalAmount
        payload.currency = 'CNY'
      }
      if (type === 'regularize' || type === 'offboard') {
        payload.requestType = type === 'offboard' ? 'OFFBOARD' : 'REGULARIZE'
        // LifecycleRequestBo 只接收 effectiveDate + formData，其余字段按表单快照写入 formData
        if (Object.keys(extra).length) payload.formData = JSON.stringify(extra)
      }
      if (this.form.__remark) payload.remark = this.form.__remark
      const selections = this.buildSelections()
      if (Object.keys(selections).length) payload.assigneeSelections = selections
      return payload
    },
    /** 发起人自选审批人：nodeId -> userId（单人）| userId[]（会签/或签） */
    buildSelections() {
      const out = {}
      this.selectableNodes.forEach((node) => {
        const picked = this.selections[node.nodeId]
        const ids = (Array.isArray(picked) ? picked : picked ? [picked] : []).filter(Boolean).map(String)
        if (!ids.length) return
        out[node.nodeId] = node.multiple ? ids : ids[0]
      })
      return out
    },
    apiFor() {
      return businessApiFor(this.current.type)
    },
    /** 纯 OA 表单/自定义流程：字段差异收敛到 formData，走通用申请通道 */
    buildGenericPayload() {
      const fields = (this.schema && this.schema.fields) || []
      const data = {}
      fields.forEach((f) => {
        if (f.readonly) return
        let value = this.form[f.key]
        if (f.type === 'list') {
          value = (value || []).map((row) => {
            const out = {}
            ;(f.itemFields || []).forEach((sub) => {
              const v = row[sub.key]
              if (v === '' || v === undefined || v === null) return
              out[sub.key] = sub.type === 'number' ? Number(v) : v
            })
            return out
          })
        } else if (f.type === 'number' && value !== '' && value !== undefined && value !== null) {
          value = Number(value)
        }
        if (value === '' || value === undefined || value === null) return
        data[f.key] = value
      })
      const payload = {
        definitionId: this.current.id,
        title: this.current.name,
        formData: JSON.stringify(data)
      }
      if (this.form.__remark) payload.remark = this.form.__remark
      const selections = this.buildSelections()
      if (Object.keys(selections).length) payload.assigneeSelections = selections
      return payload
    },
    async saveDraft() {
      if (this.submitting) return
      if (!this.schema || !this.validate()) return
      this.submitting = true
      try {
        const api = this.apiFor()
        if (api) await api.create(this.buildPayload())
        else await createGenericRequest(this.buildGenericPayload())
        uni.showToast({ title: '草稿已保存', icon: 'success' })
        setTimeout(() => navBack(), 500)
      } catch (e) {
        // 统一错误提示
      } finally {
        this.submitting = false
      }
    },
    async submit() {
      if (this.submitting) return
      if (!this.schema || !this.validate()) return
      this.submitting = true
      try {
        const api = this.apiFor()
        let instanceId = null
        if (api) {
          const created = await api.create(this.buildPayload())
          const started = await api.submit(created.id, created.lockVersion, this.buildSelections())
          instanceId = (started && started.instanceId) || created.id
        } else {
          const launched = await launchGenericRequest(this.buildGenericPayload())
          instanceId = launched && launched.flowInstanceId
        }
        uni.showToast({ title: '已提交审批', icon: 'success' })
        setTimeout(() => {
          navRedirect('/pages/approval/detail?id=' + instanceId)
        }, 500)
      } catch (e) {
        // 统一错误提示
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.apply-form {
  padding-bottom: 40rpx;
}
.apply-form__panel {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.apply-form__panel-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-text;
}
.apply-form__panel-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: $oa-text-muted;
}
.apply-form__loading {
  padding: 60rpx 32rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 25rpx;
  line-height: 1.8;
}
.apply-form__back {
  margin: 32rpx auto 0;
  width: 320rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
  border-radius: 40rpx;
  border: none;
  background: $oa-primary;
  color: #ffffff;
}
.apply-form__back::after {
  border: none;
}
.apply-form__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.apply-form__row-label {
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.apply-form__row-value {
  font-size: 27rpx;
  color: $oa-text;
}
.apply-form__details {
  margin-top: 24rpx;
}
.apply-form__details-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.apply-form__details-title {
  font-size: 26rpx;
  font-weight: 600;
  color: $oa-text;
}
.apply-form__details-add {
  font-size: 24rpx;
  color: $oa-primary;
}
.apply-form__details-empty {
  padding: 24rpx 0;
  font-size: 23rpx;
  color: $oa-text-muted;
}
.apply-form__detail {
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  background: #f7f9fc;
  border-radius: 12rpx;
}
.apply-form__detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.apply-form__detail-index {
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.apply-form__detail-del {
  font-size: 24rpx;
  color: $oa-danger;
}
.apply-form__invoice {
  margin-top: 12rpx;
  display: flex;
  align-items: center;
}
.apply-form__invoice-ok {
  font-size: 22rpx;
  color: $oa-success;
}
.apply-form__invoice-act {
  margin-left: 24rpx;
  font-size: 22rpx;
  color: $oa-primary;
}
.apply-form__invoice-act:first-child {
  margin-left: 0;
}
.apply-form__total {
  margin-top: 20rpx;
  padding: 20rpx;
  background: $oa-primary-light;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.apply-form__total-label {
  font-size: 26rpx;
  color: $oa-text-secondary;
}
.apply-form__total-value {
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-primary;
}
.apply-form__placeholder {
  height: 160rpx;
}
.apply-form__bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: $oa-card;
  display: flex;
  box-shadow: 0 -4rpx 16rpx rgba(31, 36, 48, 0.06);
}
.apply-form__bar-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0 8rpx;
  font-size: 28rpx;
  border-radius: 40rpx;
  border: none;
}
.apply-form__bar-btn::after {
  border: none;
}
.apply-form__bar-btn--ghost {
  background: #f0f2f7;
  color: $oa-text;
}
.apply-form__bar-btn--primary {
  background: $oa-primary;
  color: #ffffff;
}
</style>
