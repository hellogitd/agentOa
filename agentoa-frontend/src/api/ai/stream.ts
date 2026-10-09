import { globalHeaders } from '@/utils/request';

/** SSE 事件回调（docs/21 §4.3/§5.3：event: meta/delta/usage/done/error） */
export interface StreamHandlers {
  onMeta?: (payload: { conversationId?: string; messageId?: string; taskId?: string; runId?: string }) => void;
  onDelta?: (text: string) => void;
  onUsage?: (payload: { promptTokens: number; completionTokens: number; totalTokens: number }) => void;
  onDone?: (payload: any) => void;
  onError?: (payload: { code: string; msg: string }) => void;
  /** Agent 执行轨迹逐步回调（docs/21 §7.3 event: step） */
  onStep?: (payload: any) => void;
}

/**
 * 以 POST 发起 SSE 流式补全（EventSource 不支持 POST，改用 fetch 流式解析）。
 * 返回 AbortController，可中止本地接收（停止生成请另调 /messages/{id}/stop）。
 */
export function streamChatCompletion(body: Record<string, any>, handlers: StreamHandlers): AbortController {
  const controller = new AbortController();
  void fetchStream('/api/v1/ai/chat/completions', body, handlers, controller);
  return controller;
}

/** 知识问答（docs/21 M3）：SSE 流式回答，done 事件携带引用列表 */
export function streamQaAsk(body: Record<string, any>, handlers: StreamHandlers): AbortController {
  const controller = new AbortController();
  void fetchStream('/api/v1/ai/qa/ask', body, handlers, controller);
  return controller;
}

/** 重新生成（保留旧版本） */
export function streamRegenerate(messageId: string, handlers: StreamHandlers): AbortController {
  const controller = new AbortController();
  void fetchStream(`/api/v1/ai/chat/messages/${messageId}/regenerate`, {}, handlers, controller);
  return controller;
}

/** 业务助手生成（docs/21 §6.3 POST /copilot/{scene}，SSE） */
export function streamCopilotGenerate(scene: string, body: Record<string, any>, handlers: StreamHandlers): AbortController {
  const controller = new AbortController();
  void fetchStream(`/api/v1/ai/copilot/${scene}`, body, handlers, controller);
  return controller;
}

/** Agent 运行（docs/21 §7.3 POST /agents/{id}/run，SSE，含 step 轨迹事件） */
export function streamAgentRun(agentId: string, body: Record<string, any>, handlers: StreamHandlers): AbortController {
  const controller = new AbortController();
  void fetchStream(`/api/v1/ai/agents/${agentId}/run`, body, handlers, controller);
  return controller;
}

async function fetchStream(path: string, body: Record<string, any>, handlers: StreamHandlers, controller: AbortController) {
  const base = import.meta.env.VITE_APP_BASE_API ?? '';
  try {
    const response = await fetch(base + path, {
      method: 'POST',
      headers: { ...globalHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    if (!response.ok || !response.body) {
      let message = `流式请求失败（HTTP ${response.status}）`;
      try {
        const payload = await response.json();
        message = payload?.msg ?? message;
      } catch {
        // 保持默认信息
      }
      handlers.onError?.({ code: `HTTP_${response.status}`, msg: message });
      return;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        dispatchEvent(buffer.slice(0, boundary), handlers);
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf('\n\n');
      }
    }
    if (buffer.trim()) {
      dispatchEvent(buffer, handlers);
    }
  } catch (error: any) {
    if (controller.signal.aborted) {
      return;
    }
    handlers.onError?.({ code: 'AI_STREAM_FAILED', msg: error?.message ?? '流式连接中断' });
  }
}

function dispatchEvent(chunk: string, handlers: StreamHandlers) {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }
  if (!dataLines.length) {
    return;
  }
  let payload: any = null;
  try {
    payload = JSON.parse(dataLines.join('\n'));
  } catch {
    payload = dataLines.join('\n');
  }
  switch (event) {
    case 'meta':
      handlers.onMeta?.(payload);
      break;
    case 'step':
      handlers.onStep?.(payload);
      break;
    case 'delta':
      handlers.onDelta?.(payload?.text ?? '');
      break;
    case 'usage':
      handlers.onUsage?.(payload);
      break;
    case 'done':
      handlers.onDone?.(payload);
      break;
    case 'error':
      handlers.onError?.(payload ?? { code: 'AI_CHAT_FAILED', msg: 'AI 生成失败' });
      break;
    default:
      break;
  }
}
