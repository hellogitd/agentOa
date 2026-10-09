# k6 压测脚本（docs/19 模块 9 · G2）

## 场景

| 脚本 | 场景 | 目标 |
|------|------|------|
| `mixed.js` | 登录/列表/提交混合 | 200 VU × 30 min |

## 性能目标（docs/02 NFR）

| 指标 | 目标 |
|------|------|
| p95 登录 | < 1s |
| p95 列表 | < 500ms |
| p95 提交 | < 2s |
| 错误率 | < 1% |
| WS 连接 | ≥ 500 |

## 运行

```bash
# 安装 k6
# Windows: choco install k6 / scoop install k6
# Linux: sudo apt-get install k6

# 运行混合场景
k6 run --vus 200 --duration 30m -e BASE_URL=https://your-domain scripts/loadtest/mixed.js

# 输出 JSON 报告
k6 run --out json=results.json scripts/loadtest/mixed.js
```

## 记录

压测完成后记录：
- p50/p95/p99 延迟
- 错误率
- CPU/内存/MySQL/Redis 曲线
- 瓶颈结论与达标判定
