let seq = 0

/** 生成幂等键（docs/05 1.2：UUID，最长 64 字符）。 */
export function newIdempotencyKey() {
  seq = (seq + 1) % 100000
  const rand = () => Math.random().toString(16).slice(2, 10)
  return `${Date.now().toString(16)}-${rand()}-${rand()}-${seq.toString(16)}`
}

/** 生成请求追踪 ID（docs/05 1.2 X-Request-Id）。 */
export function newRequestId() {
  return newIdempotencyKey()
}
