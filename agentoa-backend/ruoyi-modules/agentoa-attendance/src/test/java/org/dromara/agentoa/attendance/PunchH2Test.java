package org.dromara.agentoa.attendance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.attendance.domain.OaAttendanceDay;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.domain.bo.PunchBo;
import org.dromara.agentoa.attendance.domain.vo.PunchTodayVo;
import org.dromara.agentoa.attendance.domain.vo.PunchVo;
import org.dromara.agentoa.attendance.support.AttTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打卡命令：服务器时间、记录窗口、迟到早退计算、幂等重放与日报重算（docs/13）。
 */
class PunchH2Test {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final Long USER = AttTestEnvironment.USER_EMPLOYEE;

    @BeforeAll
    static void boot() {
        AttTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AttTestEnvironment.clearData();
        AttTestEnvironment.logout();
        AttTestEnvironment.seedUser(USER, AttTestEnvironment.DEPT_A, "张三");
        AttTestEnvironment.seedShift(1L, "FIXED", LocalTime.of(9, 0), LocalTime.of(18, 0),
            LocalTime.of(12, 0), LocalTime.of(13, 0), 0, 0, 0, 120, 240);
        AttTestEnvironment.seedGroup(1L, "G1", 1L, "1,2,3,4,5");
        AttTestEnvironment.seedMember(1L, 1L, USER, LocalDate.of(2026, 1, 1), null);
        AttTestEnvironment.loginAs(USER, AttTestEnvironment.DEPT_A, Set.of());
    }

    private PunchBo punchBo(int type) {
        PunchBo bo = new PunchBo();
        bo.setPunchType(type);
        bo.setDevice("Chrome");
        bo.setAddress("北京市东城区xxx");
        return bo;
    }

    @Test
    void latePunchIsRecordedNotDiscarded() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 5));
        PunchVo vo = AttTestEnvironment.punchService.punch(punchBo(1), "key-late");
        assertThat(vo.getIsLate()).isTrue();
        assertThat(vo.getLateMinutes()).isEqualTo(5);
        assertThat(vo.getPunchType()).isEqualTo(1);

        OaAttendanceDay day = AttTestEnvironment.days.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, USER)
            .eq(OaAttendanceDay::getAttendanceDate, DAY));
        assertThat(day.getWorkStatus()).isEqualTo(1);
        assertThat(day.getLateMinutes()).isEqualTo(5);
        assertThat(day.getIsAbnormal()).isEqualTo(1);
        assertThat(day.getAbnormalReason()).contains("迟到5分钟");
    }

    @Test
    void punchesOutsideRecordWindowAreRejected() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(6, 0));
        assertThatThrownBy(() -> AttTestEnvironment.punchService.punch(punchBo(1), "key-window"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ATTENDANCE_WINDOW_INVALID");
    }

    @Test
    void punchWithoutScheduleIsRejected() {
        AttTestEnvironment.members.deleteById(1L);
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 5));
        assertThatThrownBy(() -> AttTestEnvironment.punchService.punch(punchBo(1), "key-noschedule"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未配置考勤组");
    }

    @Test
    void sameIdempotencyKeyReplaysFirstResult() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 1));
        PunchVo first = AttTestEnvironment.punchService.punch(punchBo(1), "key-replay");
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 30));
        PunchVo replay = AttTestEnvironment.punchService.punch(punchBo(1), "key-replay");
        assertThat(replay.getPunchTime()).isEqualTo(first.getPunchTime());
        assertThat(AttTestEnvironment.punches.selectCount(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getUserId, USER))).isEqualTo(1);
    }

    @Test
    void recordsExposeLocationAndPhoto() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 1));
        PunchBo bo = punchBo(1);
        bo.setLng(new java.math.BigDecimal("116.4074"));
        bo.setLat(new java.math.BigDecimal("39.9042"));
        bo.setAccuracyMeters(new java.math.BigDecimal("12.5"));
        bo.setPhotoFileId(345678901234567890L);
        AttTestEnvironment.punchService.punch(bo, "key-geo");
        org.dromara.agentoa.hr.domain.vo.PageVo<org.dromara.agentoa.attendance.domain.vo.PunchRecordVo> page =
            AttTestEnvironment.punchService.selectRecords(USER, DAY, DAY, new org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery());
        org.dromara.agentoa.attendance.domain.vo.PunchRecordVo record = page.getRecords().get(0);
        assertThat(record.getLng()).isEqualByComparingTo("116.4074");
        assertThat(record.getLat()).isEqualByComparingTo("39.9042");
        assertThat(record.getAccuracyMeters()).isEqualByComparingTo("12.5");
        assertThat(record.getPhotoFileId()).isEqualTo(345678901234567890L);
    }

    @Test
    void repeatedPunchesKeepRawRecordsAndComputeEffectiveRange() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 2));
        AttTestEnvironment.punchService.punch(punchBo(1), "key-a");
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(12, 0));
        AttTestEnvironment.punchService.punch(punchBo(2), "key-b");
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(18, 3));
        AttTestEnvironment.punchService.punch(punchBo(2), "key-c");

        assertThat(AttTestEnvironment.punches.selectCount(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getUserId, USER))).isEqualTo(3);

        OaAttendanceDay day = AttTestEnvironment.days.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, USER)
            .eq(OaAttendanceDay::getAttendanceDate, DAY));
        assertThat(day.getFirstPunchTime()).isEqualTo(LocalDateTime.of(DAY, LocalTime.of(9, 2)));
        assertThat(day.getLastPunchTime()).isEqualTo(LocalDateTime.of(DAY, LocalTime.of(18, 3)));
        assertThat(day.getLateMinutes()).isEqualTo(2);
        assertThat(day.getWorkedMinutes()).isEqualTo(478);
    }

    @Test
    void missingPunchIsFlaggedWithoutDroppingRecords() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 0));
        AttTestEnvironment.punchService.punch(punchBo(1), "key-in");
        OaAttendanceDay day = AttTestEnvironment.days.selectOne(new LambdaQueryWrapper<OaAttendanceDay>()
            .eq(OaAttendanceDay::getUserId, USER)
            .eq(OaAttendanceDay::getAttendanceDate, DAY));
        assertThat(day.getMissingPunch()).isEqualTo(1);
        assertThat(day.getAbnormalReason()).contains("缺下班卡");
    }

    @Test
    void todayStatusExposesPunchInAndOut() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 0));
        AttTestEnvironment.punchService.punch(punchBo(1), "key-t1");
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(18, 0));
        AttTestEnvironment.punchService.punch(punchBo(2), "key-t2");
        PunchTodayVo today = AttTestEnvironment.punchService.today();
        assertThat(today.getTodayPunched()).isTrue();
        assertThat(today.getPunchInTime()).isEqualTo(LocalDateTime.of(DAY, LocalTime.of(9, 0)));
        assertThat(today.getPunchOutTime()).isEqualTo(LocalDateTime.of(DAY, LocalTime.of(18, 0)));
        assertThat(today.getAttendanceDate()).isEqualTo(DAY.toString());
    }

    @Test
    void crossNightPunchBelongsToShiftStartDate() {
        AttTestEnvironment.clearData();
        AttTestEnvironment.seedUser(USER, AttTestEnvironment.DEPT_A, "张三");
        AttTestEnvironment.seedShift(2L, "NIGHT", LocalTime.of(22, 0), LocalTime.of(6, 0),
            null, null, 1, 0, 0, 120, 240);
        AttTestEnvironment.seedGroup(2L, "G2", 2L, "1,2,3,4,5,6,7");
        AttTestEnvironment.seedMember(2L, 2L, USER, LocalDate.of(2026, 1, 1), null);
        AttTestEnvironment.loginAs(USER, AttTestEnvironment.DEPT_A, Set.of());

        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY.plusDays(1), LocalTime.of(1, 30));
        PunchVo vo = AttTestEnvironment.punchService.punch(punchBo(2), "key-night");
        assertThat(vo.getPunchTime()).isEqualTo(LocalDateTime.of(DAY.plusDays(1), LocalTime.of(1, 30)));

        OaPunchRecord record = AttTestEnvironment.punches.selectOne(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getUserId, USER));
        assertThat(record.getPunchDate()).isEqualTo(DAY);
    }
}
