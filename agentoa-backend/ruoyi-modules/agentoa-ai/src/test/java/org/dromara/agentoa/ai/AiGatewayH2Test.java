package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiProvider;
import org.dromara.agentoa.ai.domain.bo.AiModelBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiProviderTestBo;
import org.dromara.agentoa.ai.domain.enums.AiProviderError;
import org.dromara.agentoa.ai.domain.vo.AiProviderTestVo;
import org.dromara.agentoa.ai.domain.vo.AiProviderVo;
import org.dromara.agentoa.ai.service.support.AiKeyCipher;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.ai.service.support.LlmCallException;
import org.dromara.agentoa.ai.service.support.LlmGateway;
import org.dromara.agentoa.ai.support.AiTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M1 网关契约（docs/21 §3.4）：Key 解析优先级、测试连接错误分类、路由降级、
 * 限额拦截、用量日志异步落库、删除被引用渠道 409、Key 不泄露。
 */
class AiGatewayH2Test {

    @BeforeAll
    static void boot() {
        AiTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AiTestEnvironment.usageRecorder.flush();
        AiTestEnvironment.clearData();
        AiTestEnvironment.rebuildQuotaGuard(100_000L, 500);
        System.clearProperty("AGENTOA_TEST_SECRET");
    }

    private LlmGateway.CompletionRequest request() {
        return new LlmGateway.CompletionRequest(AiTestEnvironment.USER_A, "u100", "chat", 1L, null,
            null, null, null, List.of(ChatTransport.Turn.user("你好")));
    }

    /** 小预留输出的请求（调用前限额预算 = 输入 + 16） */
    private LlmGateway.CompletionRequest smallRequest() {
        return new LlmGateway.CompletionRequest(AiTestEnvironment.USER_A, "u100", "chat", 1L, null,
            null, null, 16, List.of(ChatTransport.Turn.user("你好")));
    }

    // ---------------------------------------------------------------- Key 解析

    @Test
    void secretRefTakesPrecedenceOverCipher() {
        System.setProperty("AGENTOA_TEST_SECRET", "sk-from-secret-ref");
        OaAiProvider provider = new OaAiProvider();
        provider.setName("ref渠道");
        provider.setProviderType("openai");
        provider.setBaseUrl("http://localhost:19001");
        provider.setApiKeyCipher(AiKeyCipher.encrypt("sk-from-cipher"));
        provider.setSecretRef("AGENTOA_TEST_SECRET");
        provider.setPriority(10);
        provider.setEnabled(1);
        AiTestEnvironment.providers.insert(provider);
        OaAiModel model = new OaAiModel();
        model.setProviderId(provider.getId());
        model.setModelKey("gpt-test");
        model.setCapability("[\"chat\"]");
        model.setContextWindow(4096);
        model.setMaxTokens(64);
        model.setEnabled(1);
        model.setIsDefault(1);
        AiTestEnvironment.models.insert(model);

        AiTestEnvironment.gateway.chat(request());

        assertThat(AiTestEnvironment.transport.calls).hasSize(1);
        assertThat(AiTestEnvironment.transport.calls.get(0).apiKey()).isEqualTo("sk-from-secret-ref");
    }

    @Test
    void missingKeyMakesProviderUnusable() {
        OaAiProvider provider = new OaAiProvider();
        provider.setName("无密钥");
        provider.setProviderType("openai");
        provider.setBaseUrl("http://localhost:19002");
        provider.setPriority(10);
        provider.setEnabled(1);
        AiTestEnvironment.providers.insert(provider);
        OaAiModel model = new OaAiModel();
        model.setProviderId(provider.getId());
        model.setModelKey("gpt-test");
        model.setCapability("[\"chat\"]");
        model.setEnabled(1);
        model.setIsDefault(1);
        AiTestEnvironment.models.insert(model);

        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_PROVIDER_KEY_MISSING");
    }

    // ---------------------------------------------------------------- 测试连接

    @Test
    void probeClassifiesUpstreamErrors() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("probe渠道", "gpt-test", "[\"chat\"]");
        AiProviderTestBo bo = new AiProviderTestBo();
        bo.setModelKey("gpt-test");

        AiTestEnvironment.transport.completeHandler = call -> {
            throw new LlmCallException(LlmCallException.Category.AUTH, "bad key");
        };
        AiProviderTestVo auth = AiTestEnvironment.providerService.test(model.getProviderId(), bo);
        assertThat(auth.isOk()).isFalse();
        assertThat(auth.getErrorCategory()).isEqualTo(AiProviderError.AUTH_FAILED.name());

        AiTestEnvironment.transport.completeHandler = call -> {
            throw new LlmCallException(LlmCallException.Category.BALANCE, "insufficient_quota");
        };
        assertThat(AiTestEnvironment.providerService.test(model.getProviderId(), bo).getErrorCategory())
            .isEqualTo(AiProviderError.BALANCE.name());

        AiTestEnvironment.transport.completeHandler = call -> {
            throw new LlmCallException(LlmCallException.Category.TIMEOUT, "read timeout");
        };
        assertThat(AiTestEnvironment.providerService.test(model.getProviderId(), bo).getErrorCategory())
            .isEqualTo(AiProviderError.TIMEOUT.name());

        AiTestEnvironment.transport.completeHandler = call -> {
            throw new LlmCallException(LlmCallException.Category.MODEL_NOT_FOUND, "no model");
        };
        assertThat(AiTestEnvironment.providerService.test(model.getProviderId(), bo).getErrorCategory())
            .isEqualTo(AiProviderError.MODEL_NOT_FOUND.name());

        AiTestEnvironment.transport.completeHandler = call -> new ChatTransport.Result("pong", 1, 1, 2);
        AiProviderTestVo ok = AiTestEnvironment.providerService.test(model.getProviderId(), bo);
        assertThat(ok.isOk()).isTrue();
        assertThat(ok.getLatencyMs()).isNotNull();
    }

    @Test
    void probeDoesNotWriteUsageLog() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("probe渠道2", "gpt-test", "[\"chat\"]");
        AiTestEnvironment.transport.completeHandler = call -> new ChatTransport.Result("pong", 1, 1, 2);
        AiTestEnvironment.providerService.test(model.getProviderId(), new AiProviderTestBo());
        AiTestEnvironment.usageRecorder.flush();
        assertThat(AiTestEnvironment.usages.selectCount(null)).isZero();
    }

    // ---------------------------------------------------------------- 路由与降级

    @Test
    void routerFallsBackToSecondaryProvider() {
        OaAiModel primary = AiTestEnvironment.seedProviderAndModel("主渠道", "gpt-shared", "[\"chat\"]");
        OaAiProvider primaryProvider = AiTestEnvironment.providers.selectById(primary.getProviderId());
        primaryProvider.setPriority(10);
        AiTestEnvironment.providers.updateById(primaryProvider);

        OaAiModel backup = AiTestEnvironment.seedProviderAndModel("备用渠道", "gpt-shared", "[\"chat\"]");
        OaAiProvider backupProvider = AiTestEnvironment.providers.selectById(backup.getProviderId());
        backupProvider.setPriority(20);
        AiTestEnvironment.providers.updateById(backupProvider);

        OaAiModel anchor = AiTestEnvironment.models.selectById(primary.getId());
        anchor.setIsDefault(1);
        AiTestEnvironment.models.updateById(anchor);

        List<ChatTransport.Call> calls = AiTestEnvironment.transport.calls;
        AiTestEnvironment.transport.completeHandler = call -> {
            if (calls.size() == 1) {
                throw new LlmCallException(LlmCallException.Category.UPSTREAM, "boom");
            }
            return new ChatTransport.Result("fallback-ok", 2, 2, 4);
        };

        LlmGateway.CompletionResult result = AiTestEnvironment.gateway.chat(request());
        assertThat(result.text()).isEqualTo("fallback-ok");
        assertThat(AiTestEnvironment.transport.calls).hasSize(2);
        assertThat(result.providerId()).isEqualTo(backup.getProviderId());
    }

    @Test
    void allCandidatesFailingReturnsUpstreamError() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("坏渠道", "gpt-bad", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);
        AiTestEnvironment.transport.completeHandler = call -> {
            throw new LlmCallException(LlmCallException.Category.UPSTREAM, "boom");
        };

        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("LLM_UPSTREAM_ERROR");
    }

    // ---------------------------------------------------------------- 限额

    @Test
    void userTokenQuotaBlocksCall() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("限额渠道", "gpt-quota", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);

        org.dromara.agentoa.ai.domain.OaAiQuota quota = new org.dromara.agentoa.ai.domain.OaAiQuota();
        quota.setScopeType("user");
        quota.setScopeId(AiTestEnvironment.USER_A);
        quota.setScopeName("u100");
        quota.setPeriodType("day");
        quota.setTokenLimit(10L);
        quota.setRequestLimit(100);
        quota.setEnabled(1);
        AiTestEnvironment.quotas.insert(quota);
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_A, 12);

        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_QUOTA_EXCEEDED");

        AiTestEnvironment.usageRecorder.flush();
        assertThat(AiTestEnvironment.transport.calls).isEmpty();
    }

    @Test
    void requestCountQuotaBlocksCall() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("次数渠道", "gpt-count", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);

        org.dromara.agentoa.ai.domain.OaAiQuota quota = new org.dromara.agentoa.ai.domain.OaAiQuota();
        quota.setScopeType("user");
        quota.setScopeId(AiTestEnvironment.USER_A);
        quota.setPeriodType("day");
        quota.setTokenLimit(1_000_000L);
        quota.setRequestLimit(1);
        quota.setEnabled(1);
        AiTestEnvironment.quotas.insert(quota);
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_A, 1);

        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_QUOTA_EXCEEDED");
    }

    @Test
    void roleQuotaAppliesToMembers() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("角色渠道", "gpt-role", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);
        AiTestEnvironment.grantRole(AiTestEnvironment.USER_A, 55L);
        AiTestEnvironment.grantRole(AiTestEnvironment.USER_B, 55L);

        org.dromara.agentoa.ai.domain.OaAiQuota quota = new org.dromara.agentoa.ai.domain.OaAiQuota();
        quota.setScopeType("role");
        quota.setScopeId(55L);
        quota.setScopeName("测试角色");
        quota.setPeriodType("month");
        quota.setTokenLimit(20L);
        quota.setEnabled(1);
        AiTestEnvironment.quotas.insert(quota);
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_B, 25);

        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_QUOTA_EXCEEDED");
    }

    @Test
    void fallbackLimitAppliesWithoutExplicitQuota() {
        AiTestEnvironment.rebuildQuotaGuard(10L, 500);
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("兜底渠道", "gpt-fallback", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_A, 5);

        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_QUOTA_EXCEEDED");
    }

    @Test
    void monthlyQuotaIgnoresPreviousMonthUsage() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("月限额渠道", "gpt-month", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);

        org.dromara.agentoa.ai.domain.OaAiQuota quota = new org.dromara.agentoa.ai.domain.OaAiQuota();
        quota.setScopeType("user");
        quota.setScopeId(AiTestEnvironment.USER_A);
        quota.setPeriodType("month");
        quota.setTokenLimit(200L);
        quota.setRequestLimit(100);
        quota.setEnabled(1);
        AiTestEnvironment.quotas.insert(quota);

        java.util.Date lastMonth = java.util.Date.from(java.time.LocalDate.now().minusMonths(1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_A, 5000, lastMonth);
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_A, 50, new java.util.Date());

        // 上月 5000 token 不计入月周期；本月 50 + 预算 18 < 200 → 放行
        AiTestEnvironment.gateway.chat(smallRequest());

        // 本月累计 203 后再请求即超限
        AiTestEnvironment.insertUsage(AiTestEnvironment.USER_A, 145, new java.util.Date());
        assertThatThrownBy(() -> AiTestEnvironment.gateway.chat(smallRequest()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_QUOTA_EXCEEDED");
    }

    @Test
    void midStreamQuotaExhaustionInterruptsStream() throws Exception {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("流式限额渠道", "gpt-stream", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);

        org.dromara.agentoa.ai.domain.OaAiQuota quota = new org.dromara.agentoa.ai.domain.OaAiQuota();
        quota.setScopeType("user");
        quota.setScopeId(AiTestEnvironment.USER_A);
        quota.setPeriodType("day");
        quota.setTokenLimit(350L);
        quota.setRequestLimit(100);
        quota.setEnabled(1);
        AiTestEnvironment.quotas.insert(quota);
        // 单个大 delta：首个 delta 后即触发中途复检（调用前预算 18 ≤ 350 放行）
        AiTestEnvironment.transport.streamDeltas = List.of("一".repeat(1000), "二".repeat(1000));

        List<String> deltas = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicReference<ServiceException> error = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        AiTestEnvironment.gateway.stream(smallRequest(), new LlmGateway.StreamListener() {
            @Override
            public void onDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete(LlmGateway.CompletionResult result) {
                finished.countDown();
            }

            @Override
            public void onError(ServiceException e) {
                error.set(e);
                finished.countDown();
            }
        });
        assertThat(finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThat(error.get()).isNotNull();
        assertThat(error.get().getMessage()).contains("AI_QUOTA_EXCEEDED").contains("流式生成已中断");
        assertThat(error.get().getCode()).isEqualTo(429);
        assertThat(deltas).hasSize(1);
        // 上游已中断，第二个 delta 不会到达
        AiTestEnvironment.usageRecorder.flush();
        var rows = AiTestEnvironment.usages.selectList(null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("failed");
        assertThat(rows.get(0).getErrorCode()).isEqualTo("AI_QUOTA_EXCEEDED");
        assertThat(rows.get(0).getCompletionTokens()).isEqualTo(1000);
    }

    // ---------------------------------------------------------------- 用量日志与安全

    @Test
    void usageLogIsWrittenAsynchronously() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("日志渠道", "gpt-log", "[\"chat\"]");
        model.setIsDefault(1);
        AiTestEnvironment.models.updateById(model);

        AiTestEnvironment.gateway.chat(request());
        AiTestEnvironment.usageRecorder.flush();

        var rows = AiTestEnvironment.usages.selectList(null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("success");
        assertThat(rows.get(0).getBizType()).isEqualTo("chat");
        assertThat(rows.get(0).getUserId()).isEqualTo(AiTestEnvironment.USER_A);
        assertThat(rows.get(0).getTotalTokens()).isEqualTo(8);
    }

    @Test
    void providerViewNeverExposesKeyMaterial() {
        OaAiProvider provider = new OaAiProvider();
        provider.setName("脱敏渠道");
        provider.setProviderType("openai");
        provider.setBaseUrl("http://localhost:19111");
        provider.setApiKeyCipher(AiKeyCipher.encrypt("sk-abcdef123456"));
        provider.setApiKeyHint(AiKeyCipher.hint("sk-abcdef123456"));
        provider.setPriority(10);
        provider.setEnabled(1);
        AiTestEnvironment.providers.insert(provider);

        AiProviderVo vo = AiTestEnvironment.providerService.get(provider.getId());
        assertThat(vo.getApiKeyHint()).isEqualTo("sk-***3456");
        assertThat(vo.getHasApiKey()).isTrue();
        // 视图对象没有任何密钥字段（编译期保证），断言字段清单防止回退
        assertThat(java.util.Arrays.stream(AiProviderVo.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName))
            .doesNotContain("apiKey", "apiKeyCipher", "plaintext");
    }

    @Test
    void deletingReferencedProviderReturns409() {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("被引用渠道", "gpt-ref", "[\"chat\"]");
        assertThatThrownBy(() -> AiTestEnvironment.providerService.delete(model.getProviderId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_PROVIDER_IN_USE");

        AiTestEnvironment.models.deleteById(model.getId());
        AiTestEnvironment.providerService.delete(model.getProviderId());
        assertThat(AiTestEnvironment.providers.selectById(model.getProviderId())).isNull();
    }

    @Test
    void modelPageWithoutKeyFilterReturnsAll() {
        AiTestEnvironment.seedProviderAndModel("无过滤渠道", "gpt-any", "[\"chat\"]");

        var page = AiTestEnvironment.modelService.page(new AiModelBo(), new AiPageQuery());
        assertThat(page.getRecords()).extracting(model -> model.getModelKey()).containsExactly("gpt-any");
    }
}
