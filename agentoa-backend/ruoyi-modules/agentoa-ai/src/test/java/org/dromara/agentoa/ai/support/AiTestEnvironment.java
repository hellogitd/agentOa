package org.dromara.agentoa.ai.support;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.dromara.agentoa.ai.domain.OaAiConversation;
import org.dromara.agentoa.ai.domain.OaAiCopilotConfig;
import org.dromara.agentoa.ai.domain.OaAiCopilotTask;
import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.OaAiKbChunk;
import org.dromara.agentoa.ai.domain.OaAiKbSource;
import org.dromara.agentoa.ai.domain.OaAiMessage;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiPromptTemplate;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.domain.OaAiQuota;
import org.dromara.agentoa.ai.domain.OaAiTool;
import org.dromara.agentoa.ai.domain.OaAiAgent;
import org.dromara.agentoa.ai.domain.OaAiAgentRun;
import org.dromara.agentoa.ai.domain.OaAiUsageLog;
import org.dromara.agentoa.ai.mapper.AiIdentityMapper;
import org.dromara.agentoa.ai.mapper.OaAiConversationMapper;
import org.dromara.agentoa.ai.mapper.OaAiCopilotConfigMapper;
import org.dromara.agentoa.ai.mapper.OaAiCopilotTaskMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbChunkMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbMapper;
import org.dromara.agentoa.ai.mapper.OaAiKbSourceMapper;
import org.dromara.agentoa.ai.mapper.OaAiMessageMapper;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.agentoa.ai.mapper.OaAiPromptTemplateMapper;
import org.dromara.agentoa.ai.mapper.OaAiProviderMapper;
import org.dromara.agentoa.ai.mapper.OaAiQuotaMapper;
import org.dromara.agentoa.ai.mapper.OaAiToolMapper;
import org.dromara.agentoa.ai.mapper.OaAiAgentMapper;
import org.dromara.agentoa.ai.mapper.OaAiAgentRunMapper;
import org.dromara.agentoa.ai.mapper.OaAiUsageLogMapper;
import org.dromara.agentoa.ai.service.IAiAgentService;
import org.dromara.agentoa.ai.service.IAiChatService;
import org.dromara.agentoa.ai.service.IAiCopilotService;
import org.dromara.agentoa.ai.service.IAiKbService;
import org.dromara.agentoa.ai.service.IAiModelService;
import org.dromara.agentoa.ai.service.IAiPromptTemplateService;
import org.dromara.agentoa.ai.service.IAiProviderService;
import org.dromara.agentoa.ai.service.IAiQaService;
import org.dromara.agentoa.ai.service.IAiQuotaService;
import org.dromara.agentoa.ai.service.IAiToolService;
import org.dromara.agentoa.ai.service.IAiUsageService;
import org.dromara.agentoa.ai.service.impl.AiAgentServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiChatServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiCopilotServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiKbServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiModelServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiPromptTemplateServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiProviderServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiQaServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiQuotaServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiToolServiceImpl;
import org.dromara.agentoa.ai.service.impl.AiUsageServiceImpl;
import org.dromara.agentoa.ai.service.support.AgentEngine;
import org.dromara.agentoa.ai.service.support.AgentToolExecutor;
import org.dromara.agentoa.ai.service.support.AiImageStore;
import org.dromara.agentoa.ai.service.support.AiKeyCipher;
import org.dromara.agentoa.ai.service.support.AiModelRouter;
import org.dromara.agentoa.ai.service.support.AiQuotaGuard;
import org.dromara.agentoa.ai.service.support.AiUsageRecorder;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.ai.service.support.EmbeddingTransport;
import org.dromara.agentoa.ai.service.support.FunctionToolRegistry;
import org.dromara.agentoa.ai.service.support.KbDocumentAccess;
import org.dromara.agentoa.ai.service.support.KbFileStore;
import org.dromara.agentoa.ai.service.support.KbIndexer;
import org.dromara.agentoa.ai.service.support.KbRetriever;
import org.dromara.agentoa.ai.service.support.KbVectorCache;
import org.dromara.agentoa.ai.service.support.KbVectors;
import org.dromara.agentoa.ai.service.support.LlmCallException;
import org.dromara.agentoa.ai.service.support.LlmGateway;
import org.dromara.agentoa.ai.service.support.McpToolGateway;
import org.dromara.agentoa.ai.service.support.ProviderHealth;
import org.dromara.agentoa.ai.service.support.ProviderRegistry;
import org.h2.jdbcx.JdbcConnectionPool;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * H2 + MyBatis-Plus 测试环境：真实 Mapper、真实服务实现、可编程传输桩（docs/21 §3.4/§4.4/§5.4）。
 */
public final class AiTestEnvironment {

    public static final Long USER_A = 100L;
    public static final Long USER_B = 200L;

    private static DataSource dataSource;
    private static SqlSessionTemplate sqlSession;
    private static JdbcTemplate jdbcTemplate;

    public static OaAiProviderMapper providers;
    public static OaAiModelMapper models;
    public static OaAiUsageLogMapper usages;
    public static OaAiQuotaMapper quotas;
    public static OaAiConversationMapper conversations;
    public static OaAiMessageMapper messages;
    public static OaAiPromptTemplateMapper templates;
    public static OaAiKbMapper kbs;
    public static OaAiKbSourceMapper kbSources;
    public static OaAiKbChunkMapper kbChunks;
    public static AiIdentityMapper identities;
    public static OaAiCopilotConfigMapper copilotConfigs;
    public static OaAiCopilotTaskMapper copilotTasks;
    public static OaAiToolMapper tools;
    public static OaAiAgentMapper agents;
    public static OaAiAgentRunMapper agentRuns;

    public static StubChatTransport transport;
    public static StubEmbeddingTransport embeddingTransport;
    public static FakeImageStore imageStore;
    public static FakeKbFileStore kbFileStore;
    public static FakeDocumentAccess documentAccess;
    public static ProviderRegistry registry;
    public static ProviderHealth health;
    public static AiModelRouter router;
    public static AiUsageRecorder usageRecorder;
    public static AiQuotaGuard quotaGuard;
    public static KbVectorCache vectorCache;
    public static KbIndexer indexer;
    public static KbRetriever retriever;
    public static LlmGateway gateway;

    public static IAiProviderService providerService;
    public static IAiModelService modelService;
    public static IAiQuotaService quotaService;
    public static IAiUsageService usageService;
    public static IAiPromptTemplateService promptService;
    public static IAiChatService chatService;
    public static IAiKbService kbService;
    public static IAiQaService qaService;
    public static IAiCopilotService copilotService;
    public static IAiToolService toolService;
    public static IAiAgentService agentService;
    public static FunctionToolRegistry functionToolRegistry;
    public static McpToolGateway mcpToolGateway;
    public static AgentToolExecutor toolExecutor;
    public static AgentEngine agentEngine;

    private AiTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        bootstrapSpringContext();
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:aitest-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        runSchema();
        jdbcTemplate = new JdbcTemplate(dataSource);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        globalConfig.getDbConfig().setIdType(IdType.ASSIGN_ID);
        factoryBean.setGlobalConfig(globalConfig);
        factoryBean.setConfiguration(new MybatisConfiguration());
        try {
            var factory = factoryBean.getObject();
            var configuration = factory.getConfiguration();
            for (Class<?> mapper : List.of(OaAiProviderMapper.class, OaAiModelMapper.class, OaAiUsageLogMapper.class,
                OaAiQuotaMapper.class, OaAiConversationMapper.class, OaAiMessageMapper.class,
                OaAiPromptTemplateMapper.class, OaAiKbMapper.class, OaAiKbSourceMapper.class,
                OaAiKbChunkMapper.class, AiIdentityMapper.class,
                OaAiCopilotConfigMapper.class, OaAiCopilotTaskMapper.class, OaAiToolMapper.class,
                OaAiAgentMapper.class, OaAiAgentRunMapper.class)) {
                configuration.addMapper(mapper);
            }
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        providers = sqlSession.getMapper(OaAiProviderMapper.class);
        models = sqlSession.getMapper(OaAiModelMapper.class);
        usages = sqlSession.getMapper(OaAiUsageLogMapper.class);
        quotas = sqlSession.getMapper(OaAiQuotaMapper.class);
        conversations = sqlSession.getMapper(OaAiConversationMapper.class);
        messages = sqlSession.getMapper(OaAiMessageMapper.class);
        templates = sqlSession.getMapper(OaAiPromptTemplateMapper.class);
        kbs = sqlSession.getMapper(OaAiKbMapper.class);
        kbSources = sqlSession.getMapper(OaAiKbSourceMapper.class);
        kbChunks = sqlSession.getMapper(OaAiKbChunkMapper.class);
        identities = sqlSession.getMapper(AiIdentityMapper.class);
        copilotConfigs = sqlSession.getMapper(OaAiCopilotConfigMapper.class);
        copilotTasks = sqlSession.getMapper(OaAiCopilotTaskMapper.class);
        tools = sqlSession.getMapper(OaAiToolMapper.class);
        agents = sqlSession.getMapper(OaAiAgentMapper.class);
        agentRuns = sqlSession.getMapper(OaAiAgentRunMapper.class);

        transport = new StubChatTransport();
        embeddingTransport = new StubEmbeddingTransport();
        imageStore = new FakeImageStore();
        kbFileStore = new FakeKbFileStore();
        documentAccess = new FakeDocumentAccess();
        registry = new ProviderRegistry(providers, models, null);
        health = new ProviderHealth();
        router = new AiModelRouter(models, registry, health);
        usageRecorder = new AiUsageRecorder(usages);
        quotaGuard = new AiQuotaGuard(quotas, usages, identities, 100_000L, 500);
        vectorCache = new KbVectorCache();
        gateway = new LlmGateway(router, transport, embeddingTransport, quotaGuard, usageRecorder, registry, health);
        indexer = new KbIndexer(kbs, kbSources, kbChunks, documentAccess, kbFileStore, gateway, vectorCache);
        retriever = new KbRetriever(kbChunks, kbSources, vectorCache, documentAccess, gateway);

        providerService = new AiProviderServiceImpl(providers, models, gateway);
        modelService = new AiModelServiceImpl(models, providers);
        quotaService = new AiQuotaServiceImpl(quotas);
        usageService = new AiUsageServiceImpl(usages);
        promptService = new AiPromptTemplateServiceImpl(templates, copilotConfigs);
        chatService = new AiChatServiceImpl(conversations, messages, promptService, imageStore,
            router, gateway);
        kbService = new AiKbServiceImpl(kbs, kbSources, kbChunks, identities, registry,
            documentAccess, kbFileStore, indexer, retriever);
        qaService = new AiQaServiceImpl(conversations, messages, kbService, retriever, gateway);
        functionToolRegistry = new FunctionToolRegistry(List.of());
        mcpToolGateway = new McpToolGateway();
        toolExecutor = new AgentToolExecutor(tools, functionToolRegistry, mcpToolGateway);
        agentEngine = new AgentEngine(gateway, toolExecutor);
        copilotService = new AiCopilotServiceImpl(copilotConfigs, copilotTasks, models, templates, gateway, List.of(), List.of());
        toolService = new AiToolServiceImpl(tools, functionToolRegistry, mcpToolGateway);
        agentService = new AiAgentServiceImpl(agents, agentRuns, tools, models, agentEngine);
    }

    /** 重建限额守卫（测试期收紧/放开限额） */
    public static void rebuildQuotaGuard(long fallbackTokenLimit, int fallbackRequestLimit) {
        quotaGuard = new AiQuotaGuard(quotas, usages, identities, fallbackTokenLimit, fallbackRequestLimit);
        gateway = new LlmGateway(router, transport, embeddingTransport, quotaGuard, usageRecorder, registry, health);
        providerService = new AiProviderServiceImpl(providers, models, gateway);
        chatService = new AiChatServiceImpl(conversations, messages, promptService, imageStore,
            router, gateway);
        indexer = new KbIndexer(kbs, kbSources, kbChunks, documentAccess, kbFileStore, gateway, vectorCache);
        retriever = new KbRetriever(kbChunks, kbSources, vectorCache, documentAccess, gateway);
        kbService = new AiKbServiceImpl(kbs, kbSources, kbChunks, identities, registry,
            documentAccess, kbFileStore, indexer, retriever);
        qaService = new AiQaServiceImpl(conversations, messages, kbService, retriever, gateway);
        agentEngine = new AgentEngine(gateway, toolExecutor);
        copilotService = new AiCopilotServiceImpl(copilotConfigs, copilotTasks, models, templates, gateway, List.of(), List.of());
        agentService = new AiAgentServiceImpl(agents, agentRuns, tools, models, agentEngine);
    }

    public static void clearData() {
        jdbcTemplate.execute("DELETE FROM oa_ai_provider");
        jdbcTemplate.execute("DELETE FROM oa_ai_model");
        jdbcTemplate.execute("DELETE FROM oa_ai_usage_log");
        jdbcTemplate.execute("DELETE FROM oa_ai_quota");
        jdbcTemplate.execute("DELETE FROM oa_ai_conversation");
        jdbcTemplate.execute("DELETE FROM oa_ai_message");
        jdbcTemplate.execute("DELETE FROM oa_ai_prompt_template");
        jdbcTemplate.execute("DELETE FROM oa_ai_kb");
        jdbcTemplate.execute("DELETE FROM oa_ai_kb_source");
        jdbcTemplate.execute("DELETE FROM oa_ai_kb_chunk");
        jdbcTemplate.execute("DELETE FROM oa_ai_copilot_config");
        jdbcTemplate.execute("DELETE FROM oa_ai_copilot_task");
        jdbcTemplate.execute("DELETE FROM oa_ai_tool");
        jdbcTemplate.execute("DELETE FROM oa_ai_agent");
        jdbcTemplate.execute("DELETE FROM oa_ai_agent_run");
        jdbcTemplate.execute("DELETE FROM sys_user_role");
        transport.reset();
        embeddingTransport.reset();
        documentAccess.reset();
        kbFileStore.reset();
        vectorCache.invalidateAll();
    }

    public static void grantRole(Long userId, Long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?, ?)", userId, roleId);
    }

    public static void insertUsage(Long userId, int totalTokens) {
        insertUsage(userId, totalTokens, null);
    }

    /** 造一条用量记录（createTime 显式指定用于周期限额边界断言） */
    public static void insertUsage(Long userId, int totalTokens, Date createTime) {
        OaAiUsageLog log = new OaAiUsageLog();
        log.setUserId(userId);
        log.setUsername("u" + userId);
        log.setModelKey("test-model");
        log.setBizType("chat");
        log.setPromptTokens(totalTokens);
        log.setCompletionTokens(0);
        log.setTotalTokens(totalTokens);
        log.setStatus(OaAiUsageLog.STATUS_SUCCESS);
        log.setCreateTime(createTime);
        usages.insert(log);
    }

    /** 造一个可用渠道 + 模型（默认能力 chat） */
    public static OaAiModel seedProviderAndModel(String name, String modelKey, String capability) {
        OaAiProvider provider = new OaAiProvider();
        provider.setName(name);
        provider.setProviderType("openai");
        provider.setBaseUrl("http://localhost:" + (19000 + providers.selectCount(null)));
        provider.setApiKeyCipher(AiKeyCipher.encrypt("sk-test-" + name));
        provider.setApiKeyHint(AiKeyCipher.hint("sk-test-" + name));
        provider.setPriority(100);
        provider.setEnabled(1);
        providers.insert(provider);

        OaAiModel model = new OaAiModel();
        model.setProviderId(provider.getId());
        model.setModelKey(modelKey);
        model.setCapability(capability);
        model.setContextWindow(4096);
        model.setMaxTokens(512);
        model.setEnabled(1);
        model.setIsDefault(0);
        models.insert(model);
        return model;
    }

    /** 造一个知识域（createBy 需显式指定，用于对象权限断言） */
    public static OaAiModel seedDefaultModel() {
        OaAiModel model = seedProviderAndModel("default-provider", "gpt-default", "[\"chat\"]");
        model.setIsDefault(1);
        models.updateById(model);
        return model;
    }

    public static OaAiKb seedKb(String name, String visibility, Long embeddingModelId, Long createBy) {
        OaAiKb kb = new OaAiKb();
        kb.setName(name);
        kb.setVisibility(visibility);
        kb.setEmbeddingModelId(embeddingModelId);
        kb.setStatus(OaAiKb.STATUS_ACTIVE);
        kb.setCreateBy(createBy);
        kbs.insert(kb);
        return kb;
    }

    // ---------------------------------------------------------------- stub transports

    /** 可编程传输桩：记录调用并按配置返回/抛出 */
    public static final class StubChatTransport implements ChatTransport {

        public final List<Call> calls = new CopyOnWriteArrayList<>();
        public Function<Call, Result> completeHandler = call -> new Result("ok", 3, 5, 8);
        public Function<Call, Throwable> streamErrorHandler;
        public List<String> streamDeltas = List.of("你好", "，世界");
        public long streamDelayMs = 0L;

        public void reset() {
            calls.clear();
            completeHandler = call -> new Result("ok", 3, 5, 8);
            streamErrorHandler = null;
            streamDeltas = List.of("你好", "，世界");
            streamDelayMs = 0L;
        }

        @Override
        public Result complete(Call call) {
            calls.add(call);
            if (completeHandler == null) {
                throw new LlmCallException(LlmCallException.Category.UPSTREAM, "stub failure");
            }
            try {
                Result result = completeHandler.apply(call);
                if (result == null) {
                    throw new LlmCallException(LlmCallException.Category.UPSTREAM, "stub failure");
                }
                return result;
            } catch (LlmCallException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            }
        }

        @Override
        public Handle stream(Call call, Listener listener) {
            calls.add(call);
            AtomicInteger cancelled = new AtomicInteger(0);
            Thread worker = new Thread(() -> {
                if (streamErrorHandler != null) {
                    listener.onError(streamErrorHandler.apply(call));
                    return;
                }
                StringBuilder buffer = new StringBuilder();
                for (String delta : streamDeltas) {
                    if (cancelled.get() == 1) {
                        return;
                    }
                    buffer.append(delta);
                    listener.onDelta(delta);
                    if (streamDelayMs > 0) {
                        try {
                            Thread.sleep(streamDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                if (cancelled.get() == 0) {
                    listener.onComplete(new Result(buffer.toString(), 3, buffer.length(), buffer.length() + 3));
                }
            }, "stub-llm-stream");
            worker.setDaemon(true);
            worker.start();
            return () -> cancelled.set(1);
        }
    }

    /** 可编程向量化桩：文本 → 向量（默认确定性哈希向量） */
    public static final class StubEmbeddingTransport implements EmbeddingTransport {

        public final List<Call> calls = new CopyOnWriteArrayList<>();
        public Function<String, float[]> vectorOf = StubEmbeddingTransport::hashVector;
        public Function<List<String>, Throwable> errorHandler;

        public void reset() {
            calls.clear();
            vectorOf = StubEmbeddingTransport::hashVector;
            errorHandler = null;
        }

        @Override
        public Result embed(Call call) {
            calls.add(call);
            if (errorHandler != null) {
                Throwable error = errorHandler.apply(call.texts());
                if (error != null) {
                    if (error instanceof LlmCallException callException) {
                        throw callException;
                    }
                    throw new LlmCallException(LlmCallException.Category.UPSTREAM, error.getMessage(), error);
                }
            }
            List<float[]> vectors = new ArrayList<>();
            for (String text : call.texts()) {
                vectors.add(vectorOf.apply(text));
            }
            return new Result(vectors, Math.max(1, call.texts().size()));
        }

        /** 确定性 4 维向量（同文本同向量） */
        public static float[] hashVector(String text) {
            float[] vector = new float[] {0.1f, 0.1f, 0.1f, 0.1f};
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    vector[i % 4] += (text.charAt(i) % 7) * 0.01f;
                }
            }
            return vector;
        }
    }

    /** 内存图片存储（隔离/校验口径与 S3 实现一致） */
    public static final class FakeImageStore implements AiImageStore {

        private final Map<Long, LoadedImage> store = new ConcurrentHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger(0);

        @Override
        public StoredImage upload(String rawName, byte[] bytes, Long ownerUserId) {
            long id = 900000L + sequence.incrementAndGet();
            store.put(id, new LoadedImage("image/png", bytes));
            return new StoredImage(id, rawName, "image/png", bytes.length);
        }

        @Override
        public LoadedImage read(long fileId, Long ownerUserId) {
            LoadedImage loaded = store.get(fileId);
            if (loaded == null) {
                throw new org.dromara.common.core.exception.ServiceException("AI_ATTACHMENT_NOT_FOUND 图片不存在", 404);
            }
            return loaded;
        }
    }

    /** 内存知识文件存储（可编程内容，重索引测试用） */
    public static final class FakeKbFileStore implements KbFileStore {

        private final Map<Long, byte[]> store = new ConcurrentHashMap<>();
        private final Map<Long, String> names = new ConcurrentHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger(0);

        public void reset() {
            store.clear();
            names.clear();
        }

        /** 直接改写已存文件内容（模拟文档更新触发增量重索引） */
        public void overwrite(long fileId, byte[] bytes) {
            store.put(fileId, bytes);
        }

        @Override
        public StoredFile upload(String rawName, byte[] bytes, Long ownerUserId) {
            AiDocumentParserHolder.verify(rawName, bytes);
            long id = 800000L + sequence.incrementAndGet();
            store.put(id, bytes);
            names.put(id, rawName);
            return new StoredFile(id, rawName, "text/plain", bytes.length);
        }

        @Override
        public byte[] read(long fileId) {
            byte[] bytes = store.get(fileId);
            if (bytes == null) {
                throw new org.dromara.common.core.exception.ServiceException("AI_KB_FILE_NOT_FOUND 文件不存在", 404);
            }
            return bytes;
        }

        @Override
        public void delete(long fileId) {
            store.remove(fileId);
            names.remove(fileId);
        }
    }

    /** 桥接 AiDocumentParser 校验（保持三重校验口径一致） */
    private static final class AiDocumentParserHolder {
        static void verify(String name, byte[] bytes) {
            org.dromara.agentoa.ai.service.support.AiDocumentParser.verifiedType(name, bytes);
        }
    }

    /** 可编程知识文档访问桩 */
    public static final class FakeDocumentAccess implements KbDocumentAccess {

        private final Map<Long, DocumentContent> docs = new ConcurrentHashMap<>();
        private final Map<Long, Set<Long>> visibility = new ConcurrentHashMap<>();

        public void reset() {
            docs.clear();
            visibility.clear();
        }

        public void put(long docId, String title, String text, Set<Long> viewerIds) {
            docs.put(docId, new DocumentContent(docId, title, text, new Date()));
            visibility.put(docId, viewerIds);
        }

        public void updateText(long docId, String text) {
            DocumentContent content = docs.get(docId);
            if (content != null) {
                docs.put(docId, new DocumentContent(content.docId(), content.title(), text, new Date()));
            }
        }

        public void remove(long docId) {
            docs.remove(docId);
            visibility.remove(docId);
        }

        @Override
        public Set<Long> visibleDocumentIds(Long userId) {
            Set<Long> result = new java.util.LinkedHashSet<>();
            for (Map.Entry<Long, Set<Long>> entry : visibility.entrySet()) {
                if (entry.getValue().contains(userId)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }

        @Override
        public DocumentContent load(long docId) {
            return docs.get(docId);
        }

        @Override
        public boolean canView(Long userId, long docId) {
            Set<Long> viewers = visibility.get(docId);
            return viewers != null && viewers.contains(userId);
        }
    }

    /** 轻量 Spring 上下文：供 JsonUtils 等静态工具取 Bean（测试专用） */
    private static void bootstrapSpringContext() {
        org.springframework.context.support.GenericApplicationContext context =
            new org.springframework.context.support.GenericApplicationContext();
        context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,
            () -> new com.fasterxml.jackson.databind.ObjectMapper());
        context.refresh();
        new org.dromara.common.core.utils.SpringUtils().setApplicationContext(context);
    }

    private static void runSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("ai-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append('\n');
                }
            }
            for (String part : sql.toString().split(";")) {
                if (!part.isBlank()) {
                    statement.execute(part);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create test schema", e);
        }
    }
}
