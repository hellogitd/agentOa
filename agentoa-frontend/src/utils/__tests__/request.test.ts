import { describe, it, expect, vi, beforeEach } from 'vitest';

const h = vi.hoisted(() => ({
  elMessage: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), warning: vi.fn() }),
  elMessageBoxConfirm: vi.fn(() => new Promise<void>(() => {})),
  elNotificationError: vi.fn(),
  elLoadingService: vi.fn(() => ({ close: vi.fn() })),
  getToken: vi.fn(() => 'unit-test-token'),
  getLanguage: vi.fn(() => 'zh_CN'),
  routerReplace: vi.fn(),
  logout: vi.fn(() => Promise.resolve())
}));

vi.mock('element-plus', () => ({
  ElMessage: h.elMessage,
  ElMessageBox: { confirm: h.elMessageBoxConfirm },
  ElNotification: { error: h.elNotificationError },
  ElLoading: { service: h.elLoadingService }
}));
vi.mock('@/utils/auth', () => ({ getToken: h.getToken }));
vi.mock('@/lang', () => ({ getLanguage: h.getLanguage }));
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ logout: h.logout }) }));
vi.mock('@/router', () => ({
  default: {
    replace: h.routerReplace,
    currentRoute: { value: { fullPath: '/index' } }
  }
}));
vi.mock('@/utils/crypto', () => ({
  encryptBase64: (v: string) => v,
  encryptWithAes: (v: string) => v,
  generateAesKey: () => 'aes-key',
  decryptWithAes: (v: string) => v,
  decryptBase64: (v: string) => v
}));
vi.mock('@/utils/jsencrypt', () => ({ encrypt: (v: string) => v, decrypt: (v: string) => v }));
vi.mock('file-saver', () => ({ default: { saveAs: vi.fn() } }));

/** 构造 axios adapter：直接回灌假响应以驱动响应拦截器 */
const respond = (data: unknown) => async (config: any) => ({
  data,
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
  request: { responseType: config.responseType }
});

const failWith = (error: Error) => async () => {
  throw error;
};

let service: typeof import('@/utils/request').default;
let isRelogin: { show: boolean };

beforeEach(async () => {
  vi.clearAllMocks();
  vi.resetModules();
  const mod = await import('@/utils/request');
  service = mod.default;
  isRelogin = mod.isRelogin;
});

describe('utils/request 成功分支', () => {
  it('code=200 解析 envelope 并返回 data 载荷', async () => {
    const envelope = { code: 200, msg: 'ok', data: { id: 1 }, timestamp: 1, requestId: 'r1' };
    const result: any = await service.get('/t', { adapter: respond(envelope) });
    expect(result).toEqual(envelope);
    expect(result.data.id).toBe(1);
  });

  it('无 code 字段默认按成功处理', async () => {
    const envelope = { msg: 'ok', data: 'x' };
    const result: any = await service.get('/t', { adapter: respond(envelope) });
    expect(result.data).toBe('x');
  });

  it('blob 响应直接透传二进制体', async () => {
    const raw = { code: 200, msg: 'blob' };
    const result: any = await service.post('/t', {}, { responseType: 'blob', adapter: respond(raw) });
    expect(result).toEqual(raw);
  });

  it('get 参数序列化进 url', async () => {
    let captured: any;
    await service.get('/t', {
      params: { a: 1, b: 'x y' },
      adapter: async (config: any) => {
        captured = config;
        return respond({ code: 200, data: null })(config);
      }
    });
    expect(captured.url).toBe('/t?a=1&b=x%20y');
    expect(captured.params).toEqual({});
  });

  it('请求头携带 Bearer token 与 Content-Language', async () => {
    let captured: any;
    await service.get('/t', {
      adapter: async (config: any) => {
        captured = config;
        return respond({ code: 200, data: null })(config);
      }
    });
    expect(captured.headers['Authorization']).toBe('Bearer unit-test-token');
    expect(captured.headers['Content-Language']).toBe('zh_CN');
  });
});

describe('utils/request 业务码分支', () => {
  it('401 触发重新登录弹框并拒绝', async () => {
    const promise = service.get('/t', { adapter: respond({ code: 401, msg: '认证失败' }) });
    await expect(promise).rejects.toBe('无效的会话，或者会话已过期，请重新登录。');
    expect(h.elMessageBoxConfirm).toHaveBeenCalled();
    expect(isRelogin.show).toBe(true);
  });

  it('500 提示错误并以 Error 拒绝', async () => {
    const promise = service.get('/t', { adapter: respond({ code: 500, msg: '服务器异常' }) });
    await expect(promise).rejects.toThrow('服务器异常');
    expect(h.elMessage).toHaveBeenCalledWith(expect.objectContaining({ message: '服务器异常', type: 'error' }));
  });

  it('601 提示警告并以 Error 拒绝', async () => {
    const promise = service.get('/t', { adapter: respond({ code: 601, msg: '数据警告' }) });
    await expect(promise).rejects.toThrow('数据警告');
    expect(h.elMessage).toHaveBeenCalledWith(expect.objectContaining({ message: '数据警告', type: 'warning' }));
  });

  it('其它业务码走通知栏并拒绝 error', async () => {
    const promise = service.get('/t', { adapter: respond({ code: 403, msg: '自定义无权限' }) });
    await expect(promise).rejects.toBe('error');
    expect(h.elNotificationError).toHaveBeenCalledWith({ title: '当前操作没有权限' });
  });
});

describe('utils/request 传输层错误分支', () => {
  it('Network Error 映射为后端接口连接异常', async () => {
    const promise = service.get('/t', { adapter: failWith(new Error('Network Error')) });
    await expect(promise).rejects.toThrow('Network Error');
    expect(h.elMessage).toHaveBeenCalledWith(expect.objectContaining({ message: '后端接口连接异常' }));
  });

  it('超时映射为系统接口请求超时', async () => {
    const promise = service.get('/t', { adapter: failWith(new Error('timeout of 50000ms exceeded')) });
    await expect(promise).rejects.toThrow('timeout');
    expect(h.elMessage).toHaveBeenCalledWith(expect.objectContaining({ message: '系统接口请求超时' }));
  });

  it('HTTP 状态码异常带码提示', async () => {
    const promise = service.get('/t', { adapter: failWith(new Error('Request failed with status code 502')) });
    await expect(promise).rejects.toThrow('502');
    expect(h.elMessage).toHaveBeenCalledWith(expect.objectContaining({ message: '系统接口502异常' }));
  });
});

describe('utils/request 重复提交拦截', () => {
  it('500ms 内相同 POST 被拒绝', async () => {
    const adapter = respond({ code: 200, data: null });
    await service.post('/t', { a: 1 }, { adapter });
    const dup = service.post('/t', { a: 1 }, { adapter });
    await expect(dup).rejects.toThrow('数据正在处理，请勿重复提交');
  });

  it('不同请求体不误伤', async () => {
    const adapter = respond({ code: 200, data: null });
    await service.post('/t', { a: 1 }, { adapter });
    await expect(service.post('/t', { a: 2 }, { adapter })).resolves.toEqual({ code: 200, data: null });
  });

  it('repeatSubmit=false 时关闭防重', async () => {
    const adapter = respond({ code: 200, data: null });
    const config = { adapter, headers: { repeatSubmit: false } };
    await service.post('/t', { a: 1 }, config);
    await expect(service.post('/t', { a: 1 }, config)).resolves.toEqual({ code: 200, data: null });
  });
});
