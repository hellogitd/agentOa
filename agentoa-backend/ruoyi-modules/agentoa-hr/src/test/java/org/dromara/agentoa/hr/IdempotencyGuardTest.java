package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.domain.OaIdempotency;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.agentoa.hr.support.HrTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Idempotency-Key 语义（API 规范 1.7）：同键同请求重放首次结果，同键不同请求 409。
 */
class IdempotencyGuardTest {

    private static IdempotencyGuard guard;

    @BeforeAll
    static void boot() {
        HrTestEnvironment.bootstrap();
        guard = new IdempotencyGuard(HrTestEnvironment.idempotencies);
    }

    @BeforeEach
    void reset() {
        HrTestEnvironment.clearData();
    }

    @Test
    void replaySameKeySameBodyReturnsFirstResult() {
        String digest = IdempotencyGuard.digest("{\"name\":\"张三\"}");
        assertThat(guard.begin(1L, "key-1", "/api/v1/hr/employees", digest)).isNull();
        guard.complete(1L, "key-1", "42");

        assertThat(guard.begin(1L, "key-1", "/api/v1/hr/employees", digest)).isEqualTo("42");
        assertThat(HrTestEnvironment.countIdempotencies()).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyConflicts() {
        guard.begin(1L, "key-2", "/api/v1/hr/employees", IdempotencyGuard.digest("{\"a\":1}"));
        assertThatThrownBy(() -> guard.begin(1L, "key-2", "/api/v1/hr/employees", IdempotencyGuard.digest("{\"a\":2}")))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
    }

    @Test
    void keyScopeIsPerUser() {
        String digest = IdempotencyGuard.digest("body");
        guard.begin(1L, "key-3", "/api/v1/hr/employees", digest);
        assertThat(guard.begin(2L, "key-3", "/api/v1/hr/employees", digest)).isNull();
        assertThat(HrTestEnvironment.countIdempotencies()).isEqualTo(2);
    }

    @Test
    void expiredRecordAllowsReExecution() {
        String digest = IdempotencyGuard.digest("body");
        guard.begin(1L, "key-4", "/api/v1/hr/employees", digest);
        OaIdempotency record = HrTestEnvironment.idempotencies.selectByUserAndKey(1L, "key-4");
        record.setCreateTime(new java.util.Date(System.currentTimeMillis() - 25L * 60 * 60 * 1000));
        HrTestEnvironment.idempotencies.updateById(record);

        assertThat(guard.begin(1L, "key-4", "/api/v1/hr/employees", digest)).isNull();
        assertThat(HrTestEnvironment.countIdempotencies()).isEqualTo(1);
    }

    @Test
    void digestIsStable() {
        assertThat(IdempotencyGuard.digest("abc")).isEqualTo(IdempotencyGuard.digest("abc"));
        assertThat(IdempotencyGuard.digest("abc")).isNotEqualTo(IdempotencyGuard.digest("abd"));
    }
}
