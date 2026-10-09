// H5 移动端冒烟（docs/23 H5-H1-04）：登录 → 工作台 → 审批列表/详情 → 打卡页 → 公告 → AI 对话/知识问答（mock 上游）→ 我的，
// 断言 401 跳登录、storage 会话口径（token 存 storage 为移动端设计，退出即清；不落敏感内容）。
// AI 段（docs/21 §10.6-4 补强）：进程内 OpenAI 兼容 mock 上游（chat completions/embeddings），
// 后端经 host.docker.internal 访问（compose.smoke.yml extra_hosts），流式/停止/重生成/引用跳转全链路真实走后端 SSE。
// 依赖本地栈：docker compose（h5=18082、backend=18081），CAPTCHA_ENABLED=false（同 smoke.mjs 口径）。
// 用法：node scripts/h5-smoke.mjs   （H5_BASE_URL 可覆盖默认 http://localhost:18082）
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:http';
import assert from 'node:assert/strict';

const BASE = (process.env.H5_BASE_URL || 'http://localhost:18082').replace(/\/+$/, '');
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const MOCK_HOST = process.env.H5_MOCK_UPSTREAM_HOST || 'host.docker.internal';
let checks = 0;
const pass = (msg) => {
  checks += 1;
  console.log(`ok ${checks} ${msg}`);
};

async function loadPlaywright() {
  const local = new URL('../.cache/browser/node_modules/playwright/index.mjs', import.meta.url);
  if (existsSync(local)) return import(local.href);
  return import('playwright');
}

async function launchBrowser(chromium) {
  try {
    return await chromium.launch({ channel: 'msedge', headless: true });
  } catch (e) {
    return await chromium.launch({ headless: true });
  }
}

// OpenAI 兼容 mock 上游（docs/21 §10.6-4）：chat completions（流式/非流式）+ embeddings。
// 行为标记：MOCK_SLOW=慢速流（供停止生成），MOCK_ERROR=首次 500（供失败重生成，闩锁防重生成再失败）。
const MOCK_EMBEDDING = [0.11, 0.22, 0.33, 0.44, 0.55, 0.66, 0.77, 0.88];
let mockErrorArmed = true;
const mockTimers = new Set();
const later = (fn, ms) => {
  const t = setTimeout(() => {
    mockTimers.delete(t);
    fn();
  }, ms);
  mockTimers.add(t);
  return t;
};
const textOf = (content) => {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) return content.map((p) => (p && p.text) || '').join(' ');
  return '';
};
const mockUpstream = createServer((req, res) => {
  let raw = '';
  req.on('data', (chunk) => {
    raw += chunk;
  });
  req.on('end', () => {
    let body = {};
    try {
      body = JSON.parse(raw || '{}');
    } catch (e) {
      body = {};
    }
    if (req.url === '/v1/embeddings') {
      const input = Array.isArray(body.input) ? body.input : [body.input || ''];
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(
        JSON.stringify({
          object: 'list',
          model: String(body.model || 'h5-mock-embed'),
          data: input.map((_, i) => ({ object: 'embedding', index: i, embedding: MOCK_EMBEDDING })),
          usage: { prompt_tokens: 1, total_tokens: 1 }
        })
      );
      return;
    }
    if (req.url !== '/v1/chat/completions') {
      res.writeHead(404).end();
      return;
    }
    const prompt = (body.messages || []).map((m) => textOf(m.content)).join('\n');
    const userLine = (body.messages || []).filter((m) => m.role === 'user').map((m) => textOf(m.content)).pop() || '';
    if (prompt.includes('MOCK_ERROR') && mockErrorArmed) {
      mockErrorArmed = false;
      res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'mock upstream failure', type: 'server_error' } }));
      return;
    }
    const reply = 'MOCK 回复：' + userLine;
    const deltas = [reply.slice(0, 4), reply.slice(4, 10), reply.slice(10)];
    const base = { id: 'cmpl-h5smoke', object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model: String(body.model || 'h5-mock-chat') };
    const writeChunk = (obj) => res.write(`data: ${JSON.stringify(obj)}\n\n`);
    const finishStream = () => {
      if (res.writableEnded || res.destroyed) return;
      writeChunk({ ...base, choices: [{ index: 0, delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 8, completion_tokens: 8, total_tokens: 16 } });
      res.write('data: [DONE]\n\n');
      res.end();
    };
    if (!body.stream) {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(
        JSON.stringify({
          ...base,
          object: 'chat.completion',
          choices: [{ index: 0, message: { role: 'assistant', content: reply }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 8, completion_tokens: 8, total_tokens: 16 }
        })
      );
      return;
    }
    res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' });
    writeChunk({ ...base, choices: [{ index: 0, delta: { role: 'assistant', content: deltas[0] }, finish_reason: null }] });
    if (prompt.includes('MOCK_SLOW')) {
      // 慢速流：首字后 8s 再续，供「停止生成」断言（届时响应已被取消）
      later(() => {
        if (res.writableEnded || res.destroyed) return;
        for (const d of deltas.slice(1)) writeChunk({ ...base, choices: [{ index: 0, delta: { content: d }, finish_reason: null }] });
        finishStream();
      }, 8000);
      return;
    }
    let i = 1;
    const timer = setInterval(() => {
      if (res.writableEnded || res.destroyed) {
        clearInterval(timer);
        mockTimers.delete(timer);
        return;
      }
      if (i < deltas.length) {
        writeChunk({ ...base, choices: [{ index: 0, delta: { content: deltas[i] }, finish_reason: null }] });
        i += 1;
      } else {
        clearInterval(timer);
        mockTimers.delete(timer);
        finishStream();
      }
    }, 40);
    mockTimers.add(timer);
  });
});
await new Promise((resolve) => mockUpstream.listen(0, '0.0.0.0', resolve));
const mockPort = mockUpstream.address().port;

const { chromium, devices } = await loadPlaywright();
const browser = await launchBrowser(chromium);
const mobile =
  devices && devices['iPhone 13']
    ? devices['iPhone 13']
    : {
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        userAgent:
          'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'
      };
const context = await browser.newContext(mobile);
const page = await context.newPage();
const failures = [];
page.on('pageerror', (e) => failures.push(e.message));

const secrets = new URL('../deploy/secrets/', import.meta.url);
const usernameFile = new URL('admin-current-username', secrets);
const passwordFile = new URL('admin-current-password', secrets);
const username = existsSync(usernameFile) ? readFileSync(usernameFile, 'utf8').trim() : 'admin';
const password = readFileSync(existsSync(passwordFile) ? passwordFile : new URL('bootstrap-password', secrets), 'utf8').trim();

const tabbar = (text) => page.locator('uni-tabbar').getByText(new RegExp(text.split('').join('\\s*')));

async function login() {
  await page.goto(`${BASE}/#/pages/login/login`, { waitUntil: 'domcontentloaded' });
  await page.locator('input').first().waitFor({ timeout: 15000 });
  await page.locator('input').first().fill(username);
  await page.locator('input[type=password]').fill(password);
  await page
    .locator('uni-button, button')
    .filter({ hasText: /登\s*录/ })
    .first()
    .click();
  await page.locator('.wb__hello').waitFor({ timeout: 20000 });
  pass('登录进入工作台');
}

try {
  await login();

  // storage 会话口径（docs/23 §5）：token 存 storage 为设计决策；不落密码/密钥类敏感内容
  const storage = await page.evaluate(() => ({ ...localStorage }));
  assert.ok(storage.agentoa_token, '移动端会话 token 存 storage（设计口径）');
  assert.ok(storage.agentoa_user, '用户缓存存在');
  assert.ok(storage.agentoa_token_expires, '会话过期时间存在');
  const isSensitiveKey = (key) => {
    const k = String(key).toLowerCase();
    if (k === 'mustchangepassword') return false;
    return k === 'password' || k.endsWith('password') || k.includes('secret') || k.includes('apikey') || k.includes('accesstoken');
  };
  const userCache = JSON.parse(storage.agentoa_user || '{}');
  for (const key of Object.keys(userCache)) {
    assert.equal(isSensitiveKey(key), false, `用户缓存不得含敏感字段 ${key}`);
  }
  for (const key of Object.keys(storage)) {
    assert.equal(isSensitiveKey(key), false, `storage 不得含敏感键 ${key}`);
  }
  pass('storage 会话口径与敏感内容断言');

  // 审批列表 + 详情
  await tabbar('审批').click();
  await page.getByText('发起新申请').waitFor({ timeout: 15000 });
  await page
    .waitForFunction(() => document.querySelectorAll('.ap__card').length > 0 || document.body.innerText.includes('暂无'), null, {
      timeout: 15000
    })
    .catch(() => {});
  pass('审批列表渲染');
  const cards = page.locator('.ap__card');
  if ((await cards.count()) > 0) {
    await cards.first().click();
    await page.getByText('审批历史').waitFor({ timeout: 15000 });
    pass('审批详情渲染');
  } else {
    pass('审批列表为空（无待办可跳详情，跳过详情断言）');
  }

  // 打卡页（详情为非 tab 页，先回首页再点 tab）
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await tabbar('考勤').click();
  await page.locator('.at__punch-btn').waitFor({ timeout: 15000 });
  await page.getByText('今日汇总').waitFor({ timeout: 15000 });
  pass('打卡页渲染');

  // 公告
  await page.goto(`${BASE}/#/pages/notice/index`, { waitUntil: 'domcontentloaded' });
  await page.getByText(/暂无公告|查看详情/).first().waitFor({ timeout: 15000 });
  pass('公告列表渲染');

  // ---- AI 对话/知识问答（docs/21 §10.5 H6 + §10.6-4 补强）：mock 上游全链路 ----
  const api = async (path, { method = 'GET', body, token, status = 200 } = {}) => {
    const res = await fetch(BASE + path, {
      method,
      headers: { clientid: CLIENT_ID, 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: body ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    assert.equal(res.status, status, `${method} ${path}: HTTP ${res.status}, ${text.slice(0, 200)}`);
    return text ? JSON.parse(text) : {};
  };
  const adminToken = await page.evaluate(() => localStorage.getItem('agentoa_token'));
  const aiStamp = Date.now();
  // 别名带时间戳：本地重复运行不与历史模型同名（CI 每次全新库）
  const aliasA = 'H5MOCK A ' + aiStamp;
  const aliasB = 'H5MOCK B ' + aiStamp;
  const aiProvider = await api('/api/v1/ai/providers', {
    method: 'POST',
    token: adminToken,
    body: {
      name: 'H5SMOKE AI ' + aiStamp,
      providerType: 'custom',
      baseUrl: `http://${MOCK_HOST}:${mockPort}/v1`,
      apiKey: 'sk-h5smoke-abcdef123456',
      priority: 10,
      enabled: 1
    }
  });
  const aiModelA = await api('/api/v1/ai/models', {
    method: 'POST',
    token: adminToken,
    body: { providerId: aiProvider.data.id, modelKey: 'h5-mock-chat-a-' + aiStamp, alias: aliasA, capability: ['chat'], contextWindow: 4096, maxTokens: 512, enabled: 1, isDefault: 0 }
  });
  const aiModelB = await api('/api/v1/ai/models', {
    method: 'POST',
    token: adminToken,
    body: { providerId: aiProvider.data.id, modelKey: 'h5-mock-chat-b-' + aiStamp, alias: aliasB, capability: ['chat'], contextWindow: 4096, maxTokens: 512, enabled: 1, isDefault: 1 }
  });
  const aiEmbed = await api('/api/v1/ai/models', {
    method: 'POST',
    token: adminToken,
    body: { providerId: aiProvider.data.id, modelKey: 'h5-mock-embed-' + aiStamp, alias: 'H5MOCK 向量', capability: ['embedding'], contextWindow: 4096, maxTokens: 128, enabled: 1, isDefault: 0 }
  });
  const aiTemplates = await api('/api/v1/ai/chat/templates', { token: adminToken });
  assert.ok(aiTemplates.data.length > 0, '内置提示词模板可用（模型/模板切换用）');
  const aiTemplate = aiTemplates.data[0];
  const aiSpace = await api('/api/v1/knowledge/spaces', {
    method: 'POST',
    token: adminToken,
    body: { name: 'H5SMOKE Space ' + aiStamp, spaceType: '3', description: 'h5 smoke' }
  });
  const aiDoc = await api('/api/v1/knowledge/documents', {
    method: 'POST',
    token: adminToken,
    body: { spaceId: String(aiSpace.data.id), title: 'H5SMOKE 差旅报销制度 ' + aiStamp, content: '# 差旅报销\n差旅报销需在30天内提交，逾期需说明原因。', tags: 'h5smoke' }
  });
  // 清理历史冒烟知识域：跨次运行遗留知识域的向量模型指向已关闭的 mock 上游，
  // 无 kbIds 的问答会跨全部可见知识域检索（docs/21 AI-M3-05），会被残留数据拖垮
  const staleKbs = await api('/api/v1/ai/kb?pageNum=1&pageSize=200', { token: adminToken });
  for (const stale of staleKbs.data.records || []) {
    if (/SMOKE|H5SMOKE|DBG/.test(String(stale.name || ''))) {
      await api(`/api/v1/ai/kb/${stale.id}`, { method: 'DELETE', token: adminToken });
    }
  }
  const aiKb = await api('/api/v1/ai/kb', {
    method: 'POST',
    token: adminToken,
    body: { name: 'H5SMOKE KB ' + aiStamp, visibility: 'private', embeddingModelId: String(aiEmbed.data.id), status: 'active' }
  });
  const aiSource = await api(`/api/v1/ai/kb/${aiKb.data.id}/sources`, {
    method: 'POST',
    token: adminToken,
    body: { sourceType: 'document', docId: String(aiDoc.data.id) }
  });
  let aiIndexed = false;
  for (let i = 0; i < 40 && !aiIndexed; i += 1) {
    const srcList = await api(`/api/v1/ai/kb/${aiKb.data.id}/sources`, { token: adminToken });
    const row = (srcList.data.records || []).find((r) => String(r.id) === String(aiSource.data.id));
    if (row && row.indexStatus === 'ready') aiIndexed = true;
    else if (row && row.indexStatus === 'failed') assert.fail(`知识域索引失败: ${row.errorMsg}`);
    else await new Promise((r) => setTimeout(r, 500));
  }
  assert.ok(aiIndexed, '知识域索引完成（mock 向量化）');
  pass('AI mock 上游与知识域数据就绪');

  let lastChatRequest = null;
  page.on('request', (req) => {
    if (req.method() === 'POST' && req.url().includes('/api/v1/ai/chat/completions')) {
      try {
        lastChatRequest = JSON.parse(req.postData() || '{}');
      } catch (e) {
        lastChatRequest = null;
      }
    }
  });
  // uni-app H5 picker 在移动视口走滚轮（.uni-picker-select 默认隐藏），冒烟放开内置选项列表供点击
  await page.addStyleTag({
    content:
      '.uni-picker-container .uni-picker-select{display:block !important;max-height:300px;overflow:auto}.uni-picker-container .uni-picker-content{display:none !important}'
  });

  await page.goto(`${BASE}/#/pages/ai/chat`, { waitUntil: 'domcontentloaded' });
  await page.locator('.ai__input').waitFor({ timeout: 15000 });
  await page.getByText('AI 生成内容仅供参考').first().waitFor({ timeout: 15000 });
  await page.getByText(`模型：${aliasB}`).first().waitFor({ timeout: 15000 });
  pass('AI 对话页渲染与默认模型选择器');

  await page.locator('.ai__input input').fill('你好');
  await page.locator('.ai__send').click();
  await page.waitForFunction(() => {
    const el = document.querySelector('.ai__msg--ai .ai__content');
    return el && el.textContent && el.textContent.includes('MOCK 回复');
  }, null, { timeout: 20000 });
  assert.equal(lastChatRequest && String(lastChatRequest.modelId), String(aiModelB.data.id), '发送随消息下发默认模型（AI-M2-06）');
  pass('AI 流式对话产出（meta→delta→done）');

  await page.locator('.ai__tool-text').filter({ hasText: /^模型/ }).click();
  await page
    .locator('.uni-picker-toggle .uni-picker-select .uni-picker-item')
    .filter({ hasText: aliasA })
    .click();
  await page.getByText(`模型：${aliasA}`).first().waitFor({ timeout: 10000 });
  await page.locator('.ai__input input').fill('切换模型');
  await page.locator('.ai__send').click();
  await page.getByText('MOCK 回复：切换模型').first().waitFor({ timeout: 20000 });
  assert.equal(lastChatRequest && String(lastChatRequest.modelId), String(aiModelA.data.id), '切换后新消息用新模型');
  pass('AI 会话内模型切换');

  await page.locator('.ai__tool-text').filter({ hasText: /^模板/ }).click();
  await page
    .locator('.uni-picker-toggle .uni-picker-select .uni-picker-item')
    .filter({ hasText: aiTemplate.name })
    .click();
  await page.locator('.ai__input input').fill('套用模板');
  await page.locator('.ai__send').click();
  await page.getByText('MOCK 回复：套用模板').first().waitFor({ timeout: 20000 });
  assert.equal(lastChatRequest && String(lastChatRequest.promptTemplateId), String(aiTemplate.id), '切换后新消息带提示词模板');
  pass('AI 会话内提示词模板切换');

  await page.locator('.ai__input input').fill('MOCK_SLOW 慢速生成');
  await page.locator('.ai__send').click();
  await page.waitForFunction(() => {
    const el = document.querySelector('#m-streaming .ai__content');
    return el && el.textContent && el.textContent.includes('MOCK');
  }, null, { timeout: 15000 });
  await page.locator('.ai__stop').click();
  await page.getByText('已停止生成').first().waitFor({ timeout: 10000 });
  pass('AI 停止生成状态机（stopped）');

  await page.locator('.ai__input input').fill('MOCK_ERROR 触发失败');
  await page.locator('.ai__send').click();
  await page.getByText('LLM_UPSTREAM_ERROR').first().waitFor({ timeout: 20000 });
  pass('AI 失败消息级提示（错误码）');
  await page.locator('.ai__err-act').last().click();
  await page.getByText('MOCK 回复：MOCK_ERROR 触发失败').first().waitFor({ timeout: 20000 });
  assert.ok(await page.getByText('LLM_UPSTREAM_ERROR').first().isVisible(), '重新生成保留旧失败版本');
  pass('AI 单条重新生成（保留旧版本）');

  await page.goto(`${BASE}/#/pages/ai/qa`, { waitUntil: 'domcontentloaded' });
  await page.locator('.qa__input').waitFor({ timeout: 15000 });
  await page.getByText('AI 生成内容仅供参考').first().waitFor({ timeout: 15000 });
  pass('知识问答页渲染');
  await page.locator('.qa__input input').fill('差旅报销的规定');
  await page.locator('.qa__send').click();
  await page.waitForFunction(() => {
    const el = document.querySelector('.qa__a-text');
    return el && el.textContent && el.textContent.includes('MOCK');
  }, null, { timeout: 20000 });
  await page.locator('.qa__cite').first().waitFor({ timeout: 15000 });
  pass('知识问答流式回答与引用列表');
  await page.locator('.qa__cite').first().click();
  await page.waitForFunction(() => window.location.hash.includes('pages/knowledge/preview'), null, { timeout: 10000 });
  pass('知识问答引用跳转文档预览');
  await page.goto(`${BASE}/#/pages/ai/qa`, { waitUntil: 'domcontentloaded' });
  await page.locator('.qa__history').waitFor({ timeout: 15000 });
  pass('知识问答历史渲染');

  // 我的（非 tab 页无 tabbar，先回首页再点 tab）
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await tabbar('我的').click();
  await page.getByText('退出登录').waitFor({ timeout: 15000 });
  pass('我的页面渲染');

  // 退出登录清理全部会话键（docs/23 §5）
  await page.getByText('退出登录').click();
  const confirmBtn = page.locator('uni-modal .uni-modal__btn_primary');
  if ((await confirmBtn.count()) > 0) {
    await confirmBtn.first().click();
  } else {
    await page.locator('uni-button, button').filter({ hasText: /确\s*定/ }).last().click();
  }
  await page.locator('input[type=password]').waitFor({ timeout: 20000 });
  const afterLogout = await page.evaluate(() => ({ ...localStorage }));
  assert.equal(afterLogout.agentoa_token, undefined, '退出登录清 token');
  assert.equal(afterLogout.agentoa_user, undefined, '退出登录清用户缓存');
  pass('退出登录清理 storage 会话');

  // 401/无会话跳登录：清 token 后访问受保护页
  await login();
  await page.evaluate(() => {
    localStorage.removeItem('agentoa_token');
  });
  await page.goto(`${BASE}/#/pages/mine/profile`, { waitUntil: 'domcontentloaded' });
  await page.locator('input[type=password]').waitFor({ timeout: 20000 });
  pass('无会话访问受保护页跳登录');

  assert.equal(failures.length, 0, `页面 JS 错误: ${failures.join(' | ')}`);
  console.log(`PASS: ${checks} H5 mobile checks`);
} finally {
  for (const t of mockTimers) clearTimeout(t);
  mockTimers.clear();
  mockUpstream.closeAllConnections?.();
  mockUpstream.close();
  await browser.close();
}
