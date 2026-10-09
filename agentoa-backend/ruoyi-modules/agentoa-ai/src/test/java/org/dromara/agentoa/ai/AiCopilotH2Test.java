package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiCopilotTask;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.bo.AiCopilotRunBo;
import org.dromara.agentoa.ai.domain.bo.AiCopilotSceneBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiCopilotResultVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotSceneVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotTaskVo;
import org.dromara.agentoa.ai.service.IAiCopilotService;
import org.dromara.agentoa.ai.service.support.CopilotTaskRecovery;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.ai.support.AiTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M4 业务助手契约（docs/21 §6.4）：场景 bootstrap、启停 403、空输入 400、
 * 生成落任务、任务仅本人可见（越权 404）、异步重试、任务删除。
 * AI 生成内容仅供参考，业务数据只取只读摘要。
 */
class AiCopilotH2Test {

    @BeforeAll
    static void boot() {
        AiTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AiTestEnvironment.usageRecorder.flush();
        AiTestEnvironment.clearData();
        AiTestEnvironment.rebuildQuotaGuard(100_000L, 500);
        AiTestEnvironment.seedDefaultModel();
    }

    // ---------------------------------------------------------------- scenes

    @Test
    void scenesBootstrapAllFive() {
        List<AiCopilotSceneVo> scenes = AiTestEnvironment.copilotService.scenes(AiTestEnvironment.USER_A);
        assertThat(scenes).hasSize(5);
        assertThat(scenes).extracting(AiCopilotSceneVo::getCode)
            .containsExactly("approve-summary", "report-insight", "notice-draft", "form-suggest", "minutes");
        assertThat(scenes).allSatisfy(scene -> assertThat(scene.getEnabled()).isEqualTo(1));
    }

    @Test
    void disabledSceneRejectsRun() {
        AiCopilotSceneBo bo = new AiCopilotSceneBo();
        bo.setEnabled(0);
        AiTestEnvironment.copilotService.updateScene("notice-draft", bo, AiTestEnvironment.USER_A);

        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("notice-draft");
        run.setContent("要点：系统升级");
        assertThatThrownBy(() -> AiTestEnvironment.copilotService.run(
            run, AiTestEnvironment.USER_A, "u100", new CollectingListener()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_DISABLED");
    }

    @Test
    void emptyInputRejected() {
        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("minutes");
        assertThatThrownBy(() -> AiTestEnvironment.copilotService.run(
            run, AiTestEnvironment.USER_A, "u100", new CollectingListener()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_INPUT_EMPTY");
    }

    @Test
    void unknownSceneRejected() {
        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("nope");
        assertThatThrownBy(() -> AiTestEnvironment.copilotService.run(
            run, AiTestEnvironment.USER_A, "u100", new CollectingListener()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_SCENE_UNKNOWN");
    }

    // ---------------------------------------------------------------- run + tasks

    @Test
    void runStreamsAndPersistsTask() throws Exception {
        OaAiModel model = AiTestEnvironment.seedProviderAndModel("copilot-provider", "gpt-copilot", "[\"chat\"]");
        AiCopilotSceneBo config = new AiCopilotSceneBo();
        config.setModelId(model.getId());
        AiTestEnvironment.copilotService.updateScene("report-insight", config, AiTestEnvironment.USER_A);

        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("report-insight");
        run.setInstruction("解读本月费用");
        run.setContent("本月费用 12 万，环比 +8%");
        CollectingListener listener = new CollectingListener();
        IAiCopilotService.RunHandle handle =
            AiTestEnvironment.copilotService.run(run, AiTestEnvironment.USER_A, "u100", listener);
        assertThat(listener.await()).isTrue();

        assertThat(handle.taskId()).isNotNull();
        assertThat(listener.result).isNotNull();
        assertThat(listener.result.getScene()).isEqualTo("report-insight");
        assertThat(listener.deltas).isNotEmpty();

        AiCopilotTaskVo task = AiTestEnvironment.copilotService.task(handle.taskId(), AiTestEnvironment.USER_A);
        assertThat(task.getStatus()).isEqualTo(OaAiCopilotTask.STATUS_DONE);
        assertThat(task.getOutput()).isNotBlank();
        assertThat(task.getTotalTokens()).isGreaterThan(0);
    }

    @Test
    void taskVisibleOnlyToOwner() throws Exception {
        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("form-suggest");
        run.setContent("字段：请假类型");
        CollectingListener listener = new CollectingListener();
        IAiCopilotService.RunHandle handle =
            AiTestEnvironment.copilotService.run(run, AiTestEnvironment.USER_A, "u100", listener);
        listener.await();

        assertThatThrownBy(() -> AiTestEnvironment.copilotService.task(handle.taskId(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_TASK_NOT_FOUND");

        PageVo<AiCopilotTaskVo> mine =
            AiTestEnvironment.copilotService.tasks(new AiPageQuery(), AiTestEnvironment.USER_A);
        assertThat(mine.getRecords()).hasSize(1);
        PageVo<AiCopilotTaskVo> others =
            AiTestEnvironment.copilotService.tasks(new AiPageQuery(), AiTestEnvironment.USER_B);
        assertThat(others.getRecords()).isEmpty();
    }

    @Test
    void retryRunsAsynchronously() throws Exception {
        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("approve-summary");
        run.setContent("审批：出差申请");
        CollectingListener listener = new CollectingListener();
        IAiCopilotService.RunHandle handle =
            AiTestEnvironment.copilotService.run(run, AiTestEnvironment.USER_A, "u100", listener);
        listener.await();

        AiTestEnvironment.copilotService.retry(handle.taskId(), AiTestEnvironment.USER_A, "u100");
        waitUntil(() -> {
            AiCopilotTaskVo task = AiTestEnvironment.copilotService.task(handle.taskId(), AiTestEnvironment.USER_A);
            return OaAiCopilotTask.STATUS_DONE.equals(task.getStatus())
                || OaAiCopilotTask.STATUS_FAILED.equals(task.getStatus());
        }, 5000L);
        AiCopilotTaskVo task = AiTestEnvironment.copilotService.task(handle.taskId(), AiTestEnvironment.USER_A);
        assertThat(task.getStatus()).isEqualTo(OaAiCopilotTask.STATUS_DONE);
    }

    @Test
    void orphanInFlightTasksAreRecoveredOnStartup() {
        OaAiCopilotTask running = insertTask(OaAiCopilotTask.STATUS_RUNNING);
        OaAiCopilotTask pending = insertTask(OaAiCopilotTask.STATUS_PENDING);
        OaAiCopilotTask done = insertTask(OaAiCopilotTask.STATUS_DONE);

        int recovered = new CopilotTaskRecovery(AiTestEnvironment.copilotTasks).recoverOrphans();
        assertThat(recovered).isEqualTo(2);

        OaAiCopilotTask recoveredRunning = AiTestEnvironment.copilotTasks.selectById(running.getId());
        assertThat(recoveredRunning.getStatus()).isEqualTo(OaAiCopilotTask.STATUS_FAILED);
        assertThat(recoveredRunning.getErrorMsg()).isEqualTo(CopilotTaskRecovery.RECOVERED_MSG);
        assertThat(recoveredRunning.getFinishTime()).isNotNull();
        assertThat(AiTestEnvironment.copilotTasks.selectById(pending.getId()).getStatus())
            .isEqualTo(OaAiCopilotTask.STATUS_FAILED);
        assertThat(AiTestEnvironment.copilotTasks.selectById(done.getId()).getStatus())
            .isEqualTo(OaAiCopilotTask.STATUS_DONE);

        AiCopilotTaskVo retried = AiTestEnvironment.copilotService.retry(
            running.getId(), AiTestEnvironment.USER_A, "u100");
        assertThat(retried.getStatus()).isEqualTo(OaAiCopilotTask.STATUS_PENDING);
    }

    @Test
    void retryClaimsOnlyTerminalTasks() {
        OaAiCopilotTask running = insertTask(OaAiCopilotTask.STATUS_RUNNING);
        assertThatThrownBy(() -> AiTestEnvironment.copilotService.retry(
            running.getId(), AiTestEnvironment.USER_A, "u100"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_TASK_CONFLICT");

        OaAiCopilotTask pending = insertTask(OaAiCopilotTask.STATUS_PENDING);
        assertThatThrownBy(() -> AiTestEnvironment.copilotService.retry(
            pending.getId(), AiTestEnvironment.USER_A, "u100"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_TASK_CONFLICT");
    }

    private static OaAiCopilotTask insertTask(String status) {
        OaAiCopilotTask task = new OaAiCopilotTask();
        task.setSceneCode("approve-summary");
        task.setBizType("flow_instance");
        task.setUserId(AiTestEnvironment.USER_A);
        task.setInputRef("【业务摘要】出差申请单据摘要");
        task.setOutput("");
        task.setStatus(status);
        task.setPromptTokens(0);
        task.setTotalTokens(0);
        AiTestEnvironment.copilotTasks.insert(task);
        return task;
    }

    @Test
    void deleteTaskIsOwnerScoped() throws Exception {
        AiCopilotRunBo run = new AiCopilotRunBo();
        run.setScene("minutes");
        run.setContent("会议记录");
        CollectingListener listener = new CollectingListener();
        IAiCopilotService.RunHandle handle =
            AiTestEnvironment.copilotService.run(run, AiTestEnvironment.USER_A, "u100", listener);
        listener.await();

        assertThatThrownBy(() -> AiTestEnvironment.copilotService.deleteTask(handle.taskId(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_TASK_NOT_FOUND");

        AiTestEnvironment.copilotService.deleteTask(handle.taskId(), AiTestEnvironment.USER_A);
        assertThatThrownBy(() -> AiTestEnvironment.copilotService.task(handle.taskId(), AiTestEnvironment.USER_A))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_COPILOT_TASK_NOT_FOUND");
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ---------------------------------------------------------------- listener

    private static final class CollectingListener implements IAiCopilotService.StreamListener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final StringBuilder deltas = new StringBuilder();
        private volatile AiCopilotResultVo result;
        private volatile ServiceException error;

        @Override
        public void onDelta(String delta) {
            deltas.append(delta);
        }

        @Override
        public void onComplete(AiCopilotResultVo value) {
            result = value;
            latch.countDown();
        }

        @Override
        public void onError(ServiceException value) {
            error = value;
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS) && error == null;
        }
    }
}
