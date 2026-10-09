import { describe, it, expect } from 'vitest';
import { checkChatImage, checkEditorImage, CHAT_IMAGE_MAX_BYTES, CHAT_IMAGE_MAX_COUNT } from '@/utils/upload';

const file = (name: string, size: number) => ({ name, size });

describe('utils/upload checkChatImage', () => {
  it('通过合法的 jpg/png/webp 图片', () => {
    expect(checkChatImage(file('a.jpg', 1024)).ok).toBe(true);
    expect(checkChatImage(file('a.jpeg', 1024)).ok).toBe(true);
    expect(checkChatImage(file('a.png', 1024)).ok).toBe(true);
    expect(checkChatImage(file('a.webp', 1024)).ok).toBe(true);
  });

  it('扩展名大小写不敏感', () => {
    expect(checkChatImage(file('A.JPG', 1024)).ok).toBe(true);
    expect(checkChatImage(file('A.WebP', 1024)).ok).toBe(true);
  });

  it('拒绝不支持的扩展名', () => {
    const result = checkChatImage(file('a.gif', 1024));
    expect(result.ok).toBe(false);
    expect(result.reason).toContain('jpg/jpeg/png/webp');
    expect(checkChatImage(file('a.svg', 1024)).ok).toBe(false);
    expect(checkChatImage(file('a.exe', 1024)).ok).toBe(false);
    expect(checkChatImage(file('noext', 1024)).ok).toBe(false);
  });

  it('拒绝空内容与超过 10 MiB 的图片', () => {
    expect(checkChatImage(file('a.png', 0)).ok).toBe(false);
    expect(checkChatImage(file('a.png', CHAT_IMAGE_MAX_BYTES)).ok).toBe(true);
    expect(checkChatImage(file('a.png', CHAT_IMAGE_MAX_BYTES + 1)).ok).toBe(false);
  });

  it('拒绝超过 5 张', () => {
    expect(checkChatImage(file('a.png', 1024), CHAT_IMAGE_MAX_COUNT - 1).ok).toBe(true);
    const result = checkChatImage(file('a.png', 1024), CHAT_IMAGE_MAX_COUNT);
    expect(result.ok).toBe(false);
    expect(result.reason).toContain('5');
  });

  it('空文件对象不抛错', () => {
    expect(checkChatImage({}).ok).toBe(false);
    expect(checkChatImage({ name: undefined, size: undefined } as any).ok).toBe(false);
    expect(checkChatImage(null as any).ok).toBe(false);
  });
});

describe('utils/upload checkEditorImage', () => {
  const typed = (type: string, size: number) => ({ name: 'a', size, type });

  it('通过 jpg/jpeg/png/webp MIME', () => {
    expect(checkEditorImage(typed('image/jpeg', 1024)).ok).toBe(true);
    expect(checkEditorImage(typed('image/jpg', 1024)).ok).toBe(true);
    expect(checkEditorImage(typed('image/png', 1024)).ok).toBe(true);
    expect(checkEditorImage(typed('image/webp', 1024)).ok).toBe(true);
    expect(checkEditorImage(typed('IMAGE/PNG', 1024)).ok).toBe(true);
  });

  it('拒绝 svg 与未知 MIME（与聊天附件口径一致）', () => {
    expect(checkEditorImage(typed('image/svg+xml', 1024)).ok).toBe(false);
    expect(checkEditorImage(typed('image/gif', 1024)).ok).toBe(false);
    expect(checkEditorImage(typed('text/html', 1024)).ok).toBe(false);
    expect(checkEditorImage(typed('', 1024)).ok).toBe(false);
  });

  it('拒绝空内容与超过上限的大小', () => {
    expect(checkEditorImage(typed('image/png', 0)).ok).toBe(false);
    expect(checkEditorImage(typed('image/png', 5 * 1024 * 1024 - 1)).ok).toBe(true);
    expect(checkEditorImage(typed('image/png', 5 * 1024 * 1024)).ok).toBe(false);
    expect(checkEditorImage(typed('image/png', 1024 * 1024), 1).ok).toBe(false);
    expect(checkEditorImage(typed('image/png', 1024), 0).ok).toBe(true);
  });
});
