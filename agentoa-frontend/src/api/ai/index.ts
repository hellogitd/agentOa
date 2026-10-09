import { AxiosPromise } from 'axios';
import request from '@/utils/request';

/** /api/v1 统一响应封装（docs/05 1.3） */
export interface ApiEnvelope<T = any> {
  code: number;
  msg: string;
  data: T;
  timestamp: number;
  requestId: string;
}

/** 分页响应（docs/05 1.4） */
export interface PageVo<T = any> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

// ------------------------------------------------------------- 模型渠道（M1）

export interface ProviderVO {
  id: string;
  name: string;
  providerType: string;
  baseUrl: string;
  apiKeyHint?: string;
  hasApiKey: boolean;
  secretRef?: string;
  priority: number;
  enabled: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ProviderForm {
  id?: string;
  name: string;
  providerType: string;
  baseUrl: string;
  apiKey?: string;
  secretRef?: string;
  priority?: number;
  enabled?: number;
  remark?: string;
}

export interface ProviderTestVO {
  ok: boolean;
  latencyMs?: number;
  modelKey?: string;
  errorCategory?: string;
  message?: string;
}

// ------------------------------------------------------------- 模型（M1）

export interface ModelVO {
  id: string;
  providerId: string;
  providerName?: string;
  modelKey: string;
  alias?: string;
  capability: string[];
  contextWindow: number;
  defaultTemperature?: number | string;
  maxTokens?: number;
  enabled: number;
  isDefault: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ModelForm {
  id?: string;
  providerId: string;
  modelKey: string;
  alias?: string;
  capability: string[];
  contextWindow: number;
  defaultTemperature?: number;
  maxTokens?: number;
  enabled?: number;
  isDefault?: number;
  remark?: string;
}

// ------------------------------------------------------------- 用量与配额（M1）

export interface UsageLogVO {
  id: string;
  userId: string;
  username?: string;
  providerId?: string;
  modelKey?: string;
  bizType: string;
  conversationId?: string;
  taskId?: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  latencyMs?: number;
  status: string;
  errorCode?: string;
  errorMsg?: string;
  createTime?: string;
}

export interface UsageStatVO {
  requests: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  byDay: { day: string; requests: number; promptTokens: number; completionTokens: number; totalTokens: number }[];
  byModel: { modelKey: string; requests: number; promptTokens: number; completionTokens: number; totalTokens: number }[];
}

export interface QuotaVO {
  id: string;
  scopeType: string;
  scopeId: string;
  scopeName?: string;
  periodType: string;
  tokenLimit?: number;
  requestLimit?: number;
  enabled: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface QuotaForm {
  id?: string;
  scopeType: string;
  scopeId: string;
  scopeName?: string;
  periodType: string;
  tokenLimit?: number | null;
  requestLimit?: number | null;
  enabled?: number;
  remark?: string;
}

// ------------------------------------------------------------- 对话（M2）

export interface ConversationVO {
  id: string;
  userId: string;
  title?: string;
  modelId?: string;
  promptTemplateId?: string;
  status: string;
  lastMessageTime?: string;
  createTime?: string;
}

export interface MessageVO {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  attachments?: string[];
  modelId?: string;
  promptTokens: number;
  completionTokens: number;
  status: 'streaming' | 'done' | 'stopped' | 'error';
  errorCode?: string;
  createTime?: string;
}

export interface PromptTemplateVO {
  id: string;
  code: string;
  name: string;
  category?: string;
  content: string;
  enabled: number;
  isBuiltin: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PromptTemplateForm {
  id?: string;
  code: string;
  name: string;
  category?: string;
  content: string;
  enabled?: number;
  remark?: string;
}

export interface ChatRequest {
  conversationId?: string;
  modelId?: string;
  promptTemplateId?: string;
  content: string;
  attachmentIds?: string[];
}

// ------------------------------------------------------------- 知识域与问答（M3）

export interface KbVO {
  id: string;
  name: string;
  description?: string;
  visibility: 'private' | 'members' | 'all';
  memberUserIds?: string[];
  embeddingModelId?: string;
  embeddingModelName?: string;
  status: 'active' | 'disabled';
  owner?: boolean;
  sourceCount?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface KbForm {
  id?: string;
  name: string;
  description?: string;
  visibility: string;
  memberUserIds?: string[];
  embeddingModelId?: string;
  status?: string;
  remark?: string;
}

export interface KbMemberVO {
  userId: string;
  userName?: string;
  nickName?: string;
}

export interface KbSourceVO {
  id: string;
  kbId: string;
  sourceType: 'document' | 'file';
  docId?: string;
  fileId?: string;
  title?: string;
  chunkCount: number;
  indexStatus: 'pending' | 'indexing' | 'ready' | 'failed';
  errorMsg?: string;
  indexedAt?: string;
  createTime?: string;
  updateTime?: string;
}

export interface KbChunkVO {
  id: string;
  kbId: string;
  sourceId: string;
  seq: number;
  heading?: string;
  content: string;
  tokenCount: number;
}

export interface KbSearchHitVO {
  chunkId: string;
  sourceId: string;
  kbId: string;
  sourceType: string;
  docId?: string;
  fileId?: string;
  title?: string;
  heading?: string;
  snippet: string;
  score: number;
  vectorScore?: number;
  link?: string;
}

export interface QaCitationVO {
  index: number;
  kbId: string;
  sourceId: string;
  sourceType: string;
  docId?: string;
  fileId?: string;
  title?: string;
  heading?: string;
  snippet: string;
  score?: number;
  link?: string;
}

export interface QaHistoryVO {
  conversationId: string;
  messageId: string;
  question: string;
  answer: string;
  citations?: QaCitationVO[];
  createTime?: string;
}

export interface QaRequest {
  kbIds?: string[];
  query: string;
  conversationId?: string;
  modelId?: string;
}

// ------------------------------------------------------------- API

export function listProviders(query: any): AxiosPromise<ApiEnvelope<PageVo<ProviderVO>>> {
  return request({ url: '/api/v1/ai/providers', method: 'get', params: query });
}

export function getProvider(id: string): AxiosPromise<ApiEnvelope<ProviderVO>> {
  return request({ url: `/api/v1/ai/providers/${id}`, method: 'get' });
}

export function addProvider(data: ProviderForm): AxiosPromise<ApiEnvelope<ProviderVO>> {
  return request({ url: '/api/v1/ai/providers', method: 'post', data });
}

export function updateProvider(id: string, data: ProviderForm): AxiosPromise<ApiEnvelope<ProviderVO>> {
  return request({ url: `/api/v1/ai/providers/${id}`, method: 'put', data });
}

export function updateProviderStatus(id: string, enabled: number) {
  return request({ url: `/api/v1/ai/providers/${id}/status`, method: 'put', data: { enabled } });
}

export function deleteProvider(id: string) {
  return request({ url: `/api/v1/ai/providers/${id}`, method: 'delete' });
}

export function testProvider(id: string, data?: { prompt?: string; modelKey?: string }): AxiosPromise<ApiEnvelope<ProviderTestVO>> {
  return request({ url: `/api/v1/ai/providers/${id}/test`, method: 'post', data: data ?? {} });
}

export function listModels(query: any): AxiosPromise<ApiEnvelope<PageVo<ModelVO>>> {
  return request({ url: '/api/v1/ai/models', method: 'get', params: query });
}

export function listEnabledModels(): AxiosPromise<ApiEnvelope<ModelVO[]>> {
  return request({ url: '/api/v1/ai/models/enabled', method: 'get' });
}

export function addModel(data: ModelForm): AxiosPromise<ApiEnvelope<ModelVO>> {
  return request({ url: '/api/v1/ai/models', method: 'post', data });
}

export function updateModel(id: string, data: ModelForm): AxiosPromise<ApiEnvelope<ModelVO>> {
  return request({ url: `/api/v1/ai/models/${id}`, method: 'put', data });
}

export function deleteModel(id: string) {
  return request({ url: `/api/v1/ai/models/${id}`, method: 'delete' });
}

export function usageStats(query: any): AxiosPromise<ApiEnvelope<UsageStatVO>> {
  return request({ url: '/api/v1/ai/usage/stats', method: 'get', params: query });
}

export function usageLogs(query: any): AxiosPromise<ApiEnvelope<PageVo<UsageLogVO>>> {
  return request({ url: '/api/v1/ai/usage/logs', method: 'get', params: query });
}

export function listQuotas(query: any): AxiosPromise<ApiEnvelope<PageVo<QuotaVO>>> {
  return request({ url: '/api/v1/ai/quotas', method: 'get', params: query });
}

export function addQuota(data: QuotaForm): AxiosPromise<ApiEnvelope<QuotaVO>> {
  return request({ url: '/api/v1/ai/quotas', method: 'post', data });
}

export function updateQuota(id: string, data: QuotaForm): AxiosPromise<ApiEnvelope<QuotaVO>> {
  return request({ url: `/api/v1/ai/quotas/${id}`, method: 'put', data });
}

export function deleteQuota(id: string) {
  return request({ url: `/api/v1/ai/quotas/${id}`, method: 'delete' });
}

export function listConversations(query: any): AxiosPromise<ApiEnvelope<PageVo<ConversationVO>>> {
  return request({ url: '/api/v1/ai/chat/conversations', method: 'get', params: query });
}

export function createConversation(data?: { title?: string; modelId?: string; promptTemplateId?: string }) {
  return request({ url: '/api/v1/ai/chat/conversations', method: 'post', data: data ?? {} });
}

export function updateConversation(id: string, data: { title?: string; modelId?: string; promptTemplateId?: string }) {
  return request({ url: `/api/v1/ai/chat/conversations/${id}`, method: 'put', data });
}

export function deleteConversation(id: string) {
  return request({ url: `/api/v1/ai/chat/conversations/${id}`, method: 'delete' });
}

export function listMessages(conversationId: string, query: any): AxiosPromise<ApiEnvelope<PageVo<MessageVO>>> {
  return request({ url: `/api/v1/ai/chat/conversations/${conversationId}/messages`, method: 'get', params: query });
}

export function stopMessage(id: string) {
  return request({ url: `/api/v1/ai/chat/messages/${id}/stop`, method: 'post' });
}

export function listChatTemplates(): AxiosPromise<ApiEnvelope<PromptTemplateVO[]>> {
  return request({ url: '/api/v1/ai/chat/templates', method: 'get' });
}

export function listPromptTemplates(query: any): AxiosPromise<ApiEnvelope<PageVo<PromptTemplateVO>>> {
  return request({ url: '/api/v1/ai/prompt-templates', method: 'get', params: query });
}

export function addPromptTemplate(data: PromptTemplateForm) {
  return request({ url: '/api/v1/ai/prompt-templates', method: 'post', data });
}

export function updatePromptTemplate(id: string, data: PromptTemplateForm) {
  return request({ url: `/api/v1/ai/prompt-templates/${id}`, method: 'put', data });
}

export function deletePromptTemplate(id: string) {
  return request({ url: `/api/v1/ai/prompt-templates/${id}`, method: 'delete' });
}

export function uploadChatAttachment(
  file: File
): AxiosPromise<ApiEnvelope<{ fileId: string; fileName: string; contentType: string; sizeBytes: number }>> {
  const form = new FormData();
  form.append('file', file);
  return request({
    url: '/api/v1/ai/chat/attachments',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

// ------------------------------------------------------------- 知识域（M3）

export function listKb(query: any): AxiosPromise<ApiEnvelope<PageVo<KbVO>>> {
  return request({ url: '/api/v1/ai/kb', method: 'get', params: query });
}

export function getKb(id: string): AxiosPromise<ApiEnvelope<KbVO>> {
  return request({ url: `/api/v1/ai/kb/${id}`, method: 'get' });
}

export function addKb(data: KbForm): AxiosPromise<ApiEnvelope<KbVO>> {
  return request({ url: '/api/v1/ai/kb', method: 'post', data });
}

export function updateKb(id: string, data: KbForm): AxiosPromise<ApiEnvelope<KbVO>> {
  return request({ url: `/api/v1/ai/kb/${id}`, method: 'put', data });
}

export function deleteKb(id: string) {
  return request({ url: `/api/v1/ai/kb/${id}`, method: 'delete' });
}

export function listKbMembers(id: string): AxiosPromise<ApiEnvelope<KbMemberVO[]>> {
  return request({ url: `/api/v1/ai/kb/${id}/members`, method: 'get' });
}

export function addKbMembers(id: string, userIds: string[]) {
  return request({ url: `/api/v1/ai/kb/${id}/members`, method: 'post', data: { userIds } });
}

export function deleteKbMember(id: string, userId: string) {
  return request({ url: `/api/v1/ai/kb/${id}/members/${userId}`, method: 'delete' });
}

export function listKbSources(id: string, query: any): AxiosPromise<ApiEnvelope<PageVo<KbSourceVO>>> {
  return request({ url: `/api/v1/ai/kb/${id}/sources`, method: 'get', params: query });
}

export function addKbDocumentSource(id: string, docId: string): AxiosPromise<ApiEnvelope<KbSourceVO>> {
  return request({ url: `/api/v1/ai/kb/${id}/sources`, method: 'post', data: { sourceType: 'document', docId } });
}

export function addKbFileSource(id: string, file: File): AxiosPromise<ApiEnvelope<KbSourceVO>> {
  const form = new FormData();
  form.append('file', file);
  return request({
    url: `/api/v1/ai/kb/${id}/sources`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }
  });
}

export function deleteKbSource(id: string, sourceId: string) {
  return request({ url: `/api/v1/ai/kb/${id}/sources/${sourceId}`, method: 'delete' });
}

export function reindexKbSource(id: string, sourceId: string): AxiosPromise<ApiEnvelope<KbSourceVO>> {
  return request({ url: `/api/v1/ai/kb/${id}/sources/${sourceId}/reindex`, method: 'post' });
}

export function kbSearchTest(id: string, data: { query: string; topK?: number }): AxiosPromise<ApiEnvelope<KbSearchHitVO[]>> {
  return request({ url: `/api/v1/ai/kb/${id}/search-test`, method: 'post', data });
}

export function listKbChunks(id: string, query: any): AxiosPromise<ApiEnvelope<PageVo<KbChunkVO>>> {
  return request({ url: `/api/v1/ai/kb/${id}/chunks`, method: 'get', params: query });
}

// ------------------------------------------------------------- 知识问答（M3）

export function qaHistory(query: any): AxiosPromise<ApiEnvelope<PageVo<QaHistoryVO>>> {
  return request({ url: '/api/v1/ai/qa/history', method: 'get', params: query });
}

// ------------------------------------------------------------- 业务助手（M4）

export interface CopilotSceneVO {
  code: string;
  name: string;
  description?: string;
  enabled: number;
  modelId?: string;
  modelName?: string;
  promptTemplateId?: string;
  promptTemplateName?: string;
  bizType?: string;
  configurable?: boolean;
}

export interface CopilotSceneForm {
  enabled?: number;
  modelId?: string;
  promptTemplateId?: string;
  remark?: string;
}

export interface CopilotResultVO {
  taskId?: string;
  scene?: string;
  status?: string;
  content?: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  sources?: string[];
}

export interface CopilotTaskVO {
  id: string;
  sceneCode: string;
  sceneName?: string;
  bizType?: string;
  bizId?: string;
  userId?: string;
  status: string;
  errorMsg?: string;
  promptTokens?: number;
  totalTokens?: number;
  finishTime?: string;
  createTime?: string;
  output?: string;
}

export function listCopilotScenes(): AxiosPromise<ApiEnvelope<CopilotSceneVO[]>> {
  return request({ url: '/api/v1/ai/copilot/scenes', method: 'get' });
}

export function updateCopilotScene(code: string, data: CopilotSceneForm): AxiosPromise<ApiEnvelope<CopilotSceneVO>> {
  return request({ url: `/api/v1/ai/copilot/scenes/${code}`, method: 'put', data });
}

export function listCopilotTasks(query: any): AxiosPromise<ApiEnvelope<PageVo<CopilotTaskVO>>> {
  return request({ url: '/api/v1/ai/copilot/tasks', method: 'get', params: query });
}

export function getCopilotTask(id: string): AxiosPromise<ApiEnvelope<CopilotTaskVO>> {
  return request({ url: `/api/v1/ai/copilot/tasks/${id}`, method: 'get' });
}

export function retryCopilotTask(id: string): AxiosPromise<ApiEnvelope<CopilotTaskVO>> {
  return request({ url: `/api/v1/ai/copilot/tasks/${id}/retry`, method: 'post' });
}

export function deleteCopilotTask(id: string) {
  return request({ url: `/api/v1/ai/copilot/tasks/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 工具与 MCP（M5）

export interface ToolVO {
  id: string;
  code: string;
  name: string;
  type: string;
  description?: string;
  schemaJson?: string;
  endpoint?: string;
  hasAuthHeader?: boolean;
  writeFlag: number;
  enabled: number;
  isBuiltin: number;
  remark?: string;
}

export interface ToolForm {
  id?: string;
  code: string;
  name: string;
  type: string;
  description?: string;
  schemaJson?: string;
  configJson?: string;
  writeFlag?: number;
  enabled?: number;
  remark?: string;
}

export interface ToolTestVO {
  ok: boolean;
  type?: string;
  toolCount?: number;
  latencyMs?: number;
  message?: string;
}

export interface ToolDiscoveryVO {
  name: string;
  description?: string;
  parametersJsonSchema?: string;
}

export function listTools(query: any): AxiosPromise<ApiEnvelope<PageVo<ToolVO>>> {
  return request({ url: '/api/v1/ai/tools', method: 'get', params: query });
}

export function getTool(id: string): AxiosPromise<ApiEnvelope<ToolVO>> {
  return request({ url: `/api/v1/ai/tools/${id}`, method: 'get' });
}

export function addTool(data: ToolForm): AxiosPromise<ApiEnvelope<ToolVO>> {
  return request({ url: '/api/v1/ai/tools', method: 'post', data });
}

export function updateTool(id: string, data: ToolForm): AxiosPromise<ApiEnvelope<ToolVO>> {
  return request({ url: `/api/v1/ai/tools/${id}`, method: 'put', data });
}

export function deleteTool(id: string) {
  return request({ url: `/api/v1/ai/tools/${id}`, method: 'delete' });
}

export function testTool(id: string): AxiosPromise<ApiEnvelope<ToolTestVO>> {
  return request({ url: `/api/v1/ai/tools/${id}/test`, method: 'post', data: {} });
}

export function discoverTools(mcpServerId: string): AxiosPromise<ApiEnvelope<ToolDiscoveryVO[]>> {
  return request({ url: `/api/v1/ai/tools/discover/${mcpServerId}`, method: 'get' });
}

/** 把 MCP server 上发现的工具纳入工具清单（docs/21 AI-M5-02） */
export function importDiscoveredTools(mcpServerId: string): AxiosPromise<ApiEnvelope<ToolVO[]>> {
  return request({ url: `/api/v1/ai/tools/discover/${mcpServerId}/import`, method: 'post', data: {} });
}

// ------------------------------------------------------------- Agent（M5）

export interface AgentVO {
  id: string;
  code: string;
  name: string;
  systemPrompt: string;
  modelId?: string;
  modelName?: string;
  toolCodes?: string[];
  maxSteps: number;
  timeoutSec: number;
  enabled: number;
  isBuiltin: number;
  remark?: string;
}

export interface AgentForm {
  id?: string;
  code: string;
  name: string;
  systemPrompt: string;
  modelId?: string;
  toolCodes?: string[];
  maxSteps?: number;
  timeoutSec?: number;
  enabled?: number;
  remark?: string;
}

export interface AgentTraceStepVO {
  step: number;
  type: string;
  tool?: string;
  arguments?: string;
  result?: string;
  writeTool?: boolean;
  durationMs?: number;
}

export interface AgentRunVO {
  id: string;
  agentId: string;
  agentName?: string;
  userId?: string;
  conversationId?: string;
  input?: string;
  output?: string;
  status: string;
  errorMsg?: string;
  totalTokens?: number;
  durationMs?: number;
  trace?: AgentTraceStepVO[];
  createTime?: string;
}

export function listAgents(query: any): AxiosPromise<ApiEnvelope<PageVo<AgentVO>>> {
  return request({ url: '/api/v1/ai/agents', method: 'get', params: query });
}

export function getAgent(id: string): AxiosPromise<ApiEnvelope<AgentVO>> {
  return request({ url: `/api/v1/ai/agents/${id}`, method: 'get' });
}

export function addAgent(data: AgentForm): AxiosPromise<ApiEnvelope<AgentVO>> {
  return request({ url: '/api/v1/ai/agents', method: 'post', data });
}

export function updateAgent(id: string, data: AgentForm): AxiosPromise<ApiEnvelope<AgentVO>> {
  return request({ url: `/api/v1/ai/agents/${id}`, method: 'put', data });
}

export function deleteAgent(id: string) {
  return request({ url: `/api/v1/ai/agents/${id}`, method: 'delete' });
}

export function listAgentRuns(query: any): AxiosPromise<ApiEnvelope<PageVo<AgentRunVO>>> {
  return request({ url: '/api/v1/ai/agents/runs', method: 'get', params: query });
}

export function getAgentRun(runId: string): AxiosPromise<ApiEnvelope<AgentRunVO>> {
  return request({ url: `/api/v1/ai/agents/runs/${runId}`, method: 'get' });
}
