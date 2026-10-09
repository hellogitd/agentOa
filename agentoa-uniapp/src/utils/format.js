/** 时间、金额、状态等展示口径（docs/05 1.6）。
 *
 * 服务端存在两种时间序列化：
 *  - `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss`（LocalDate / LocalDateTime，无时区后缀）
 *  - `java.util.Date#toString()`（如 `Sun Oct 04 07:33:38 UTC 2026`）
 * 以及 RFC 3339（`2026-10-04T09:00:00+08:00`）。
 * 展示层不做时区换算（与 PC 端一致），只做格式规整，避免本地时区把无后缀时间错位。
 */

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const MONTHS = { Jan: 1, Feb: 2, Mar: 3, Apr: 4, May: 5, Jun: 6, Jul: 7, Aug: 8, Sep: 9, Oct: 10, Nov: 11, Dec: 12 }

const RE_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/
const RE_DATE = /^(\d{4})-(\d{2})-(\d{2})$/
/** Sun Oct 04 07:33:38 UTC 2026 / Sun Oct 04 07:33:38 GMT+0800 2026 */
const RE_JAVA_DATE = /^[A-Za-z]{3} ([A-Za-z]{3}) (\d{1,2}) (\d{2}):(\d{2}):(\d{2}) [A-Z+\-0-9() ]*?(\d{4})$/

export function pad2(n) {
  return (Number(n) || 0) < 10 ? '0' + n : String(n)
}

/** 把任意服务端时间串规整为 `yyyy-MM-dd HH:mm:ss` 或 `yyyy-MM-dd`，不做时区换算。 */
export function normalizeDate(value) {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'number') {
    const d = new Date(value)
    return isNaN(d.getTime()) ? '' : `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  }
  const raw = String(value).trim()
  let m = RE_DATE_TIME.exec(raw)
  if (m) {
    return `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}:${m[6] || '00'}`
  }
  if (RE_DATE.test(raw)) return raw
  m = RE_JAVA_DATE.exec(raw)
  if (m) {
    const month = MONTHS[m[1]]
    if (month) {
      return `${m[6]}-${pad2(month)}-${pad2(m[2])} ${m[3]}:${m[4]}:${m[5]}`
    }
  }
  const d = new Date(raw)
  if (!isNaN(d.getTime())) {
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  }
  return ''
}

export function today() {
  return formatDate(new Date())
}

export function formatDate(value) {
  const n = normalizeDate(value)
  return n ? n.slice(0, 10) : value ? String(value).slice(0, 10) : ''
}

export function formatDateTime(value) {
  const n = normalizeDate(value)
  return n ? n.slice(0, 16) : ''
}

export function formatTime(value) {
  const n = normalizeDate(value)
  return n && n.length > 10 ? n.slice(11, 16) : ''
}

/** 仅用于本地选择器取值的 Date 对象（比较两个本地串的先后足够）。 */
export function parseDate(value) {
  const n = normalizeDate(value)
  if (!n) return null
  const d = new Date(n.replace(' ', 'T'))
  return isNaN(d.getTime()) ? null : d
}

export function weekday(value) {
  const d = value instanceof Date ? value : parseDate(value)
  return d ? '周' + WEEKDAYS[d.getDay()] : ''
}

/** 整数分钟 -> "x 小时 y 分钟" / "y 分钟"（docs/05 1.6 时长用整数分钟）。 */
export function formatMinutes(minutes) {
  const m = Number(minutes) || 0
  if (m === 0) return '0 分钟'
  const sign = m < 0 ? '-' : ''
  const abs = Math.abs(m)
  const h = Math.floor(abs / 60)
  const rest = abs % 60
  if (h && rest) return `${sign}${h} 小时 ${rest} 分钟`
  if (h) return `${sign}${h} 小时`
  return `${sign}${rest} 分钟`
}

/** 两位小数金额字符串 -> 展示串（docs/05 1.6）。 */
export function formatMoney(amount) {
  if (amount === null || amount === undefined || amount === '') return '--'
  const n = Number(amount)
  if (isNaN(n)) return String(amount)
  return n.toFixed(2)
}

/** 日期时间选择器值（yyyy-MM-ddTHH:mm）与后端 yyyy-MM-dd HH:mm:ss 互转。 */
export function toPickerDateTime(value) {
  const n = normalizeDate(value)
  return n ? n.replace(' ', 'T').slice(0, 16) : ''
}

export function fromPickerDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ') + ':00'
}

export function toPickerDate(value) {
  return formatDate(value)
}

export default {
  pad2,
  normalizeDate,
  today,
  formatDate,
  formatDateTime,
  formatTime,
  parseDate,
  weekday,
  formatMinutes,
  formatMoney,
  toPickerDateTime,
  fromPickerDateTime,
  toPickerDate
}
