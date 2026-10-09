package org.dromara.agentoa.attendance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.attendance.domain.OaPunchRecord;
import org.dromara.agentoa.attendance.domain.OaShiftAssignment;
import org.dromara.agentoa.attendance.domain.bo.PunchBo;
import org.dromara.agentoa.attendance.domain.bo.ScheduleAssignmentBo;
import org.dromara.agentoa.attendance.domain.vo.PunchVo;
import org.dromara.agentoa.attendance.domain.vo.ScheduleAssignmentVo;
import org.dromara.agentoa.attendance.service.impl.ScheduleServiceImpl;
import org.dromara.agentoa.attendance.support.AttTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 集成测试：外勤打卡（AT-09 GPS+照片）与排班指派（AT-03 班次覆盖）。
 */
class AttendanceP1H2Test {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final Long USER = AttTestEnvironment.USER_EMPLOYEE;

    private ScheduleServiceImpl scheduleService;

    @BeforeAll
    static void boot() {
        AttTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AttTestEnvironment.clearData();
        AttTestEnvironment.logout();
        AttTestEnvironment.fixedNow = null;
        AttTestEnvironment.seedUser(USER, AttTestEnvironment.DEPT_A, "field-worker");
        AttTestEnvironment.seedShift(1L, "FIXED", LocalTime.of(9, 0), LocalTime.of(18, 0),
            LocalTime.of(12, 0), LocalTime.of(13, 0), 0, 0, 0, 120, 240);
        AttTestEnvironment.seedShift(2L, "NIGHT", LocalTime.of(21, 0), LocalTime.of(6, 0),
            LocalTime.of(0, 0), LocalTime.of(1, 0), 1, 0, 0, 120, 240);
        AttTestEnvironment.seedGroup(1L, "G1", 1L, "1,2,3,4,5");
        AttTestEnvironment.seedMember(1L, 1L, USER, LocalDate.of(2026, 1, 1), null);
        scheduleService = new ScheduleServiceImpl(AttTestEnvironment.shiftAssignments, AttTestEnvironment.shifts,
            AttTestEnvironment.identity);
        AttTestEnvironment.loginAs(USER, AttTestEnvironment.DEPT_A, Set.of());
    }

    private PunchBo fieldBo() {
        PunchBo bo = new PunchBo();
        bo.setPunchType(3);
        bo.setLng(new BigDecimal("116.397128"));
        bo.setLat(new BigDecimal("39.916527"));
        bo.setAddress("customer site");
        bo.setPhotoFileId(9001L);
        bo.setDevice("iPhone");
        return bo;
    }

    // ---------------------------------------------------------------- 外勤打卡

    @Test
    void fieldPunchRequiresGpsAndPhoto() {
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(22, 0));
        PunchBo noGps = fieldBo();
        noGps.setLng(null);
        assertThatThrownBy(() -> AttTestEnvironment.punchService.punch(noGps, "key-f1"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ATTENDANCE_LOCATION_INVALID");

        PunchBo noPhoto = fieldBo();
        noPhoto.setPhotoFileId(null);
        assertThatThrownBy(() -> AttTestEnvironment.punchService.punch(noPhoto, "key-f2"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("现场照片");
    }

    @Test
    void fieldPunchRecordsEvenOutsideWindow() {
        // 22:00 远离班次窗口，外勤打卡仍应记录（punch_type=3）
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(22, 0));
        PunchVo vo = AttTestEnvironment.punchService.punch(fieldBo(), "key-f3");
        assertThat(vo.getPunchType()).isEqualTo(3);
        OaPunchRecord record = AttTestEnvironment.punches.selectOne(new LambdaQueryWrapper<OaPunchRecord>()
            .eq(OaPunchRecord::getUserId, USER)
            .eq(OaPunchRecord::getPunchDate, DAY));
        assertThat(record.getPhotoFileId()).isEqualTo(9001L);
        assertThat(record.getLng()).isEqualByComparingTo("116.397128");
    }

    @Test
    void fieldPunchWorksWithoutSchedule() {
        AttTestEnvironment.members.deleteById(1L);
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(22, 0));
        PunchVo vo = AttTestEnvironment.punchService.punch(fieldBo(), "key-f4");
        assertThat(vo.getPunchType()).isEqualTo(3);
    }

    @Test
    void normalPunchStillRejectedWithoutSchedule() {
        AttTestEnvironment.members.deleteById(1L);
        AttTestEnvironment.fixedNow = LocalDateTime.of(DAY, LocalTime.of(9, 0));
        PunchBo bo = new PunchBo();
        bo.setPunchType(1);
        assertThatThrownBy(() -> AttTestEnvironment.punchService.punch(bo, "key-n1"))
            .isInstanceOf(ServiceException.class);
    }

    // ---------------------------------------------------------------- 排班指派

    @Test
    void assignmentOverridesGroupShift() {
        ScheduleAssignmentBo bo = new ScheduleAssignmentBo();
        bo.setUserId(USER);
        bo.setShiftId(2L);
        bo.setWorkDate(DAY);
        List<ScheduleAssignmentVo> created = scheduleService.assign(bo);
        assertThat(created).hasSize(1);

        var schedule = AttTestEnvironment.scheduleResolver.resolve(USER, DAY);
        assertThat(schedule.shift().getId()).isEqualTo(2L);

        // 未指派日期仍按组班次
        var other = AttTestEnvironment.scheduleResolver.resolve(USER, DAY.plusDays(1));
        assertThat(other.shift().getId()).isEqualTo(1L);
    }

    @Test
    void assignmentReplacesSameDayAndSupportsRange() {
        ScheduleAssignmentBo first = new ScheduleAssignmentBo();
        first.setUserId(USER);
        first.setShiftId(2L);
        first.setWorkDate(DAY);
        scheduleService.assign(first);

        ScheduleAssignmentBo second = new ScheduleAssignmentBo();
        second.setUserId(USER);
        second.setShiftId(1L);
        second.setWorkDate(DAY);
        scheduleService.assign(second);
        assertThat(AttTestEnvironment.shiftAssignments.selectCount(new LambdaQueryWrapper<OaShiftAssignment>()
            .eq(OaShiftAssignment::getUserId, USER))).isEqualTo(1);
        assertThat(AttTestEnvironment.scheduleResolver.resolve(USER, DAY).shift().getId()).isEqualTo(1L);

        ScheduleAssignmentBo range = new ScheduleAssignmentBo();
        range.setUserId(USER);
        range.setShiftId(2L);
        range.setDateFrom(DAY.plusDays(1));
        range.setDateTo(DAY.plusDays(3));
        assertThat(scheduleService.assign(range)).hasSize(3);
    }

    @Test
    void assignmentWithoutShiftRejected() {
        ScheduleAssignmentBo bo = new ScheduleAssignmentBo();
        bo.setUserId(USER);
        bo.setShiftId(999L);
        bo.setWorkDate(DAY);
        assertThatThrownBy(() -> scheduleService.assign(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("班次不存在");
    }
}
