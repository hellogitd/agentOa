/**
 * 上传附件预检（docs/21 AI-M2-04）。
 * 与服务端 `AiAttachmentRules` 同口径的前置拦截：张数、大小、扩展名；
 * 内容魔数与扩展名一致性由服务端三重校验兜底，前端不重复读字节。
 */

export const CHAT_IMAGE_MAX_COUNT = 5;
export const CHAT_IMAGE_MAX_BYTES = 10 * 1024 * 1024;

const CHAT_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'];

export interface UploadCheckResult {
  ok: boolean;
  reason: string;
}

function extensionOf(name: string): string {
  const lower = (name ?? '').toLowerCase();
  const dot = lower.lastIndexOf('.');
  return dot < 0 ? '' : lower.slice(dot + 1);
}

/** AI 对话图片预检：已传数量 + 扩展名 + 大小 */
export function checkChatImage(file: { name?: string; size?: number }, existingCount = 0): UploadCheckResult {
  if (existingCount >= CHAT_IMAGE_MAX_COUNT) {
    return { ok: false, reason: `最多上传 ${CHAT_IMAGE_MAX_COUNT} 张图片` };
  }
  if (!CHAT_IMAGE_EXTENSIONS.includes(extensionOf(file?.name ?? ''))) {
    return { ok: false, reason: '仅支持 jpg/jpeg/png/webp 格式图片' };
  }
  const size = file?.size ?? 0;
  if (size <= 0) {
    return { ok: false, reason: '图片内容为空' };
  }
  if (size > CHAT_IMAGE_MAX_BYTES) {
    return { ok: false, reason: '单张图片不能超过 10 MiB' };
  }
  return { ok: true, reason: '' };
}

/** 编辑器图片 MIME 白名单（与聊天附件口径一致，不含 svg 防内嵌脚本） */
const EDITOR_IMAGE_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];

/**
 * 富文本编辑器图片预检（docs/22 F5-03）：MIME + 大小。
 * 插入/粘贴统一走 sys_file 上传通道，禁 data: 内联；魔数与扩展名一致性由服务端校验兜底。
 */
export function checkEditorImage(file: { name?: string; size?: number; type?: string }, maxMB = 5): UploadCheckResult {
  const type = (file?.type ?? '').toLowerCase();
  if (!EDITOR_IMAGE_TYPES.includes(type)) {
    return { ok: false, reason: '图片格式错误，仅支持 jpg/jpeg/png/webp' };
  }
  const size = file?.size ?? 0;
  if (size <= 0) {
    return { ok: false, reason: '图片内容为空' };
  }
  if (maxMB > 0 && size / 1024 / 1024 >= maxMB) {
    return { ok: false, reason: `上传文件大小不能超过 ${maxMB} MB` };
  }
  return { ok: true, reason: '' };
}
