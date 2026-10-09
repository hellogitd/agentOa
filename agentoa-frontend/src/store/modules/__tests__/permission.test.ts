import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

const h = vi.hoisted(() => ({
  hasPermiOr: vi.fn((perms: string[]) => perms.includes('ai:chat:use')),
  hasRoleOr: vi.fn((roles: string[]) => roles.includes('admin')),
  getRouters: vi.fn(() => Promise.resolve({ code: 200, data: [] }))
}));

vi.mock('@/router', () => ({
  default: { addRoute: vi.fn(), currentRoute: { value: { fullPath: '/' } }, replace: vi.fn() },
  constantRoutes: [{ path: '/login', name: 'Login', component: {} }],
  dynamicRoutes: []
}));
vi.mock('@/store', () => ({ default: {} }));
vi.mock('@/api/menu', () => ({ getRouters: h.getRouters }));
vi.mock('@/plugins/auth', () => ({
  default: {
    hasPermiOr: h.hasPermiOr,
    hasRoleOr: h.hasRoleOr,
    hasPermi: (p: string) => h.hasPermiOr([p]),
    hasRole: (r: string) => h.hasRoleOr([r])
  }
}));
vi.mock('@/layout/index.vue', () => ({ default: { name: 'Layout' } }));
vi.mock('@/components/ParentView/index.vue', () => ({ default: { name: 'ParentView' } }));
vi.mock('@/layout/components/InnerLink/index.vue', () => ({ default: { name: 'InnerLink' } }));

describe('store/modules/permission filterDynamicRoutes', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('按 permissions 过滤', async () => {
    const { filterDynamicRoutes } = await import('@/store/modules/permission');
    const routes = [
      { path: '/a', permissions: ['ai:chat:use'] },
      { path: '/c', permissions: ['x:y:z'] }
    ] as any;
    const result = filterDynamicRoutes(routes);
    expect(result).toHaveLength(1);
    expect(result[0].path).toBe('/a');
    expect(h.hasPermiOr).toHaveBeenCalledWith(['ai:chat:use']);
  });

  it('按 roles 过滤', async () => {
    const { filterDynamicRoutes } = await import('@/store/modules/permission');
    const routes = [
      { path: '/b', roles: ['admin'] },
      { path: '/e', roles: ['user'] }
    ] as any;
    const result = filterDynamicRoutes(routes);
    expect(result).toHaveLength(1);
    expect(result[0].path).toBe('/b');
    expect(h.hasRoleOr).toHaveBeenCalledWith(['admin']);
  });

  it('无 permissions/roles 的路由被丢弃', async () => {
    const { filterDynamicRoutes } = await import('@/store/modules/permission');
    expect(filterDynamicRoutes([{ path: '/d' }] as any)).toHaveLength(0);
    expect(filterDynamicRoutes([])).toEqual([]);
  });
});

describe('store/modules/permission loadView', () => {
  it('命中视图返回带 name 的异步组件工厂', async () => {
    const { loadView } = await import('@/store/modules/permission');
    const view = loadView('ai/chat/index', 'AiChat');
    expect(typeof view).toBe('function');
  });

  it('业务与系统视图均可映射', async () => {
    const { loadView } = await import('@/store/modules/permission');
    expect(typeof loadView('ai/kb/index', 'AiKb')).toBe('function');
    expect(typeof loadView('system/dict/index', 'Dict')).toBe('function');
    expect(typeof loadView('hr/employee/index', 'HrEmployee')).toBe('function');
  });

  it('悬空菜单组件解析为空', async () => {
    const { loadView } = await import('@/store/modules/permission');
    expect(loadView('system/dict/data', 'DictData')).toBeUndefined();
  });
});

describe('store/modules/permission 状态存取', () => {
  it('setRoutes 拼接 constantRoutes', async () => {
    setActivePinia(createPinia());
    const { usePermissionStore } = await import('@/store/modules/permission');
    const store = usePermissionStore();
    store.setRoutes([{ path: '/x' }] as any);
    expect(store.getRoutes().map((r: any) => r.path)).toEqual(['/login', '/x']);
  });

  it('setSidebarRouters 写入侧边栏路由', async () => {
    setActivePinia(createPinia());
    const { usePermissionStore } = await import('@/store/modules/permission');
    const store = usePermissionStore();
    store.setSidebarRouters([{ path: '/y' }] as any);
    expect(store.getSidebarRoutes().map((r: any) => r.path)).toEqual(['/y']);
    expect(store.getTopbarRoutes()).toEqual([]);
    expect(store.getDefaultRoutes()).toEqual([]);
  });
});
