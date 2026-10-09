import { get, unwrap } from '@/utils/request'

/** 付款登记查询（只读，需 fn:payment:list，docs/23 H5-H3-03） */
export function listPayments(params) {
  return unwrap(get('/api/v1/finance/payments', params))
}

export function getPayment(id) {
  return unwrap(get(`/api/v1/finance/payments/${id}`))
}
