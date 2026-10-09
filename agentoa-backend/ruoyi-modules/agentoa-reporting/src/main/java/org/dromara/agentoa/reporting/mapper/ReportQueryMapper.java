package org.dromara.agentoa.reporting.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.agentoa.reporting.domain.vo.ReportBalanceVo;
import org.dromara.agentoa.reporting.domain.vo.DayValueVo;
import org.dromara.agentoa.reporting.domain.vo.LabelValueVo;
import org.dromara.agentoa.reporting.domain.vo.MonthValueVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表只读查询（docs/18 步骤 3）：不复制事实表，直接按登记口径读 V4–V10 业务表与 Flowable 运行时表。
 * 所有查询先应用部门范围（deptId 非空即限定该部门），区间统一为 [startDate, endDateExclusive)。
 */
public interface ReportQueryMapper {

    // ---------------------------------------------------------------- 人事（oa_employee）

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_employee e WHERE e.status IN ('PROBATION','ACTIVE','LEAVE_PENDING') "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "</script>")
    Long countActiveEmployees(@Param("deptId") Long deptId);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_employee e WHERE e.entry_date IS NOT NULL "
        + "AND e.entry_date &gt;= #{start} AND e.entry_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "</script>")
    Long countNewHires(@Param("deptId") Long deptId,
                       @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_employee e WHERE e.status = 'LEFT' "
        + "AND e.leave_date IS NOT NULL AND e.leave_date &gt;= #{start} AND e.leave_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "</script>")
    Long countLeavers(@Param("deptId") Long deptId,
                      @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_employee e WHERE e.status = 'PROBATION' "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "</script>")
    Long countProbation(@Param("deptId") Long deptId);

    @Select("<script>"
        + "SELECT COALESCE(d.dept_name, '未分配') AS label, COUNT(*) AS `value` "
        + "FROM oa_employee e LEFT JOIN sys_dept d ON e.dept_id = d.dept_id "
        + "WHERE e.status IN ('PROBATION','ACTIVE','LEAVE_PENDING') "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "GROUP BY e.dept_id, d.dept_name ORDER BY `value` DESC, label"
        + "</script>")
    List<LabelValueVo> deptDistribution(@Param("deptId") Long deptId);

    @Select("<script>"
        + "SELECT COALESCE(e.position_level, '未设置') AS label, COUNT(*) AS `value` "
        + "FROM oa_employee e WHERE e.status IN ('PROBATION','ACTIVE','LEAVE_PENDING') "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "GROUP BY e.position_level ORDER BY `value` DESC, label"
        + "</script>")
    List<LabelValueVo> levelDistribution(@Param("deptId") Long deptId);

    @Select("<script>"
        + "SELECT CASE WHEN e.entry_date IS NULL THEN '未设置' "
        + "WHEN YEAR(CURRENT_DATE) - YEAR(e.entry_date) &lt; 1 THEN '1年以内' "
        + "WHEN YEAR(CURRENT_DATE) - YEAR(e.entry_date) &lt; 3 THEN '1-3年' "
        + "WHEN YEAR(CURRENT_DATE) - YEAR(e.entry_date) &lt; 5 THEN '3-5年' ELSE '5年以上' END AS label, "
        + "COUNT(*) AS `value` FROM oa_employee e "
        + "WHERE e.status IN ('PROBATION','ACTIVE','LEAVE_PENDING') "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "GROUP BY label ORDER BY label"
        + "</script>")
    List<LabelValueVo> tenureDistribution(@Param("deptId") Long deptId);

    @Select("<script>"
        + "SELECT YEAR(t.d) AS y, MONTH(t.d) AS m, SUM(t.isEntry) AS `value`, SUM(t.isLeave) AS `value2` FROM ( "
        + "  SELECT e.entry_date AS d, 1 AS isEntry, 0 AS isLeave FROM oa_employee e "
        + "  WHERE e.entry_date &gt;= #{start} AND e.entry_date &lt; #{endExclusive} "
        + "  <if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "  UNION ALL "
        + "  SELECT e.leave_date AS d, 0 AS isEntry, 1 AS isLeave FROM oa_employee e "
        + "  WHERE e.status = 'LEFT' AND e.leave_date &gt;= #{start} AND e.leave_date &lt; #{endExclusive} "
        + "  <if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + ") t GROUP BY YEAR(t.d), MONTH(t.d) ORDER BY y, m"
        + "</script>")
    List<MonthValueVo> hireTrend(@Param("deptId") Long deptId,
                                 @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    // ---------------------------------------------------------------- 考勤（oa_attendance_day）

    @Select("<script>"
        + "SELECT COALESCE(SUM(a.worked_minutes), 0) AS `value`, "
        + "COALESCE(SUM(a.scheduled_minutes - a.leave_minutes), 0) AS `value2` "
        + "FROM oa_attendance_day a WHERE a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "</script>")
    Map<String, Object> attendanceRate(@Param("deptId") Long deptId,
                                       @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_attendance_day a "
        + "WHERE a.late_minutes &gt; 0 AND a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "</script>")
    Long countLateDays(@Param("deptId") Long deptId,
                       @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(SUM(a.overtime_minutes), 0) FROM oa_attendance_day a "
        + "WHERE a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "</script>")
    Long sumOvertimeMinutes(@Param("deptId") Long deptId,
                            @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(SUM(a.leave_minutes), 0) FROM oa_attendance_day a "
        + "WHERE a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "</script>")
    Long sumLeaveMinutes(@Param("deptId") Long deptId,
                         @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT a.attendance_date AS d, SUM(a.worked_minutes) AS `value`, "
        + "SUM(a.scheduled_minutes - a.leave_minutes) AS `value2` "
        + "FROM oa_attendance_day a "
        + "WHERE a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "GROUP BY a.attendance_date ORDER BY a.attendance_date"
        + "</script>")
    List<DayValueVo> attendanceRateTrend(@Param("deptId") Long deptId,
                                         @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(d.dept_name, '未分配') AS label, SUM(a.late_minutes) AS `value` "
        + "FROM oa_attendance_day a LEFT JOIN sys_dept d ON a.dept_id = d.dept_id "
        + "WHERE a.late_minutes &gt; 0 AND a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "GROUP BY a.dept_id, d.dept_name ORDER BY `value` DESC, label LIMIT #{limit}"
        + "</script>")
    List<LabelValueVo> lateTopByDept(@Param("deptId") Long deptId,
                                     @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                     @Param("limit") int limit);

    @Select("<script>"
        + "SELECT u.nick_name AS label, SUM(a.overtime_minutes) AS `value` "
        + "FROM oa_attendance_day a JOIN sys_user u ON a.user_id = u.user_id "
        + "WHERE a.overtime_minutes &gt; 0 AND a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "GROUP BY a.user_id, u.nick_name ORDER BY `value` DESC, label LIMIT #{limit}"
        + "</script>")
    List<LabelValueVo> overtimeTopByUser(@Param("deptId") Long deptId,
                                         @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                         @Param("limit") int limit);

    @Select("<script>"
        + "SELECT r.leave_type AS label, SUM(r.duration_minutes) AS `value` "
        + "FROM oa_leave_request r JOIN sys_user u ON r.user_id = u.user_id "
        + "WHERE r.status = 3 AND r.start_time &gt;= #{start} AND r.start_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "GROUP BY r.leave_type ORDER BY `value` DESC, label"
        + "</script>")
    List<LabelValueVo> leaveTypeDistribution(@Param("deptId") Long deptId,
                                             @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT a.id AS id, CONCAT(COALESCE(u.nick_name, CAST(a.user_id AS CHAR)), ' ', "
        + "CAST(a.attendance_date AS CHAR)) AS label, "
        + "COALESCE(a.abnormal_reason, '异常考勤') AS detail, CAST(a.attendance_date AS CHAR) AS time "
        + "FROM oa_attendance_day a LEFT JOIN sys_user u ON a.user_id = u.user_id "
        + "WHERE a.is_abnormal = 1 AND a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "ORDER BY a.attendance_date DESC LIMIT #{limit}"
        + "</script>")
    List<Map<String, Object>> attendanceExceptions(@Param("deptId") Long deptId,
                                                   @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                                   @Param("limit") int limit);

    // ---------------------------------------------------------------- 财务（报销/发票/付款）

    @Select("<script>"
        + "SELECT COALESCE(SUM(r.total_amount), 0) FROM oa_reimburse_request r "
        + "JOIN oa_flow_instance f ON r.flow_instance_id = f.id "
        + "JOIN sys_user u ON r.user_id = u.user_id "
        + "WHERE r.status &lt;&gt; 1 AND f.start_time &gt;= #{start} AND f.start_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "</script>")
    BigDecimal sumAppliedAmount(@Param("deptId") Long deptId,
                                @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(SUM(r.total_amount), 0) FROM oa_reimburse_request r "
        + "JOIN oa_flow_instance f ON r.flow_instance_id = f.id "
        + "JOIN sys_user u ON r.user_id = u.user_id "
        + "WHERE f.status = 2 AND f.end_time &gt;= #{start} AND f.end_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "</script>")
    BigDecimal sumApprovedAmount(@Param("deptId") Long deptId,
                                 @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(SUM(p.amount), 0) FROM oa_payment_record p "
        + "JOIN oa_reimburse_request r ON p.reimburse_id = r.id "
        + "JOIN sys_user u ON r.user_id = u.user_id "
        + "WHERE p.pay_status = 2 AND p.pay_date &gt;= #{start} AND p.pay_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "</script>")
    BigDecimal sumPaidAmount(@Param("deptId") Long deptId,
                             @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(SUM(r.total_amount), 0) FROM oa_reimburse_request r "
        + "JOIN sys_user u ON r.user_id = u.user_id WHERE r.status = 2 "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "</script>")
    BigDecimal sumPendingAmount(@Param("deptId") Long deptId);

    @Select("<script>"
        + "SELECT YEAR(t.d) AS y, MONTH(t.d) AS m, SUM(t.applied) AS `value`, SUM(t.approved) AS `value2` FROM ( "
        + "  SELECT f.start_time AS d, r.total_amount AS applied, 0 AS approved FROM oa_reimburse_request r "
        + "  JOIN oa_flow_instance f ON r.flow_instance_id = f.id "
        + "  JOIN sys_user u ON r.user_id = u.user_id "
        + "  WHERE r.status &lt;&gt; 1 AND f.start_time &gt;= #{start} AND f.start_time &lt; #{endExclusive} "
        + "  <if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "  UNION ALL "
        + "  SELECT f.end_time AS d, 0 AS applied, r.total_amount AS approved FROM oa_reimburse_request r "
        + "  JOIN oa_flow_instance f ON r.flow_instance_id = f.id "
        + "  JOIN sys_user u ON r.user_id = u.user_id "
        + "  WHERE f.status = 2 AND f.end_time &gt;= #{start} AND f.end_time &lt; #{endExclusive} "
        + "  <if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + ") t GROUP BY YEAR(t.d), MONTH(t.d) ORDER BY y, m"
        + "</script>")
    List<MonthValueVo> financeTrend(@Param("deptId") Long deptId,
                                    @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(t.name, i.expense_type, '未分类') AS label, SUM(i.amount) AS `value` "
        + "FROM oa_expense_item i JOIN oa_reimburse_request c ON i.reimburse_id = c.id "
        + "JOIN sys_user u ON c.user_id = u.user_id "
        + "LEFT JOIN oa_expense_type t ON i.expense_type = t.code "
        + "WHERE c.status IN (3, 5, 6) AND i.occur_date &gt;= #{start} AND i.occur_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "GROUP BY t.name, i.expense_type ORDER BY `value` DESC, label"
        + "</script>")
    List<LabelValueVo> expenseTypeDistribution(@Param("deptId") Long deptId,
                                               @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT COALESCE(d.dept_name, '未分配') AS label, SUM(i.amount) AS `value` "
        + "FROM oa_expense_item i JOIN oa_reimburse_request c ON i.reimburse_id = c.id "
        + "JOIN sys_user u ON c.user_id = u.user_id "
        + "LEFT JOIN sys_dept d ON u.dept_id = d.dept_id "
        + "WHERE c.status IN (3, 5, 6) AND i.occur_date &gt;= #{start} AND i.occur_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "GROUP BY u.dept_id, d.dept_name ORDER BY `value` DESC, label LIMIT #{limit}"
        + "</script>")
    List<LabelValueVo> expenseTopByDept(@Param("deptId") Long deptId,
                                        @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                        @Param("limit") int limit);

    // ---------------------------------------------------------------- 流程（oa_flow_instance + Flowable）

    /** 与待办列表同一口径：运行时任务办理人为本人或候选人为本人。 */
    @Select("SELECT COUNT(*) FROM ACT_RU_TASK t WHERE t.ASSIGNEE_ = #{userId} OR EXISTS "
        + "(SELECT 1 FROM ACT_RU_IDENTITYLINK l WHERE l.TASK_ID_ = t.ID_ AND l.TYPE_ = 'candidate' "
        + "AND l.USER_ID_ = #{userId})")
    Long countMyTodo(@Param("userId") String userId);

    @Select("SELECT t.ID_ AS id, i.id AS instance_id, i.title AS title, i.business_type AS business_type, "
        + "i.status AS status, t.CREATE_TIME_ AS created_at "
        + "FROM ACT_RU_TASK t LEFT JOIN oa_flow_instance i ON i.flowable_proc_inst_id = t.PROC_INST_ID_ "
        + "WHERE t.ASSIGNEE_ = #{userId} OR EXISTS "
        + "(SELECT 1 FROM ACT_RU_IDENTITYLINK l WHERE l.TASK_ID_ = t.ID_ AND l.TYPE_ = 'candidate' "
        + "AND l.USER_ID_ = #{userId}) ORDER BY t.CREATE_TIME_ DESC LIMIT #{limit}")
    List<Map<String, Object>> myTodoRecent(@Param("userId") String userId, @Param("limit") int limit);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_flow_instance i WHERE i.initiator_user_id = #{userId} "
        + "<if test='status != null'> AND i.status = #{status} </if>"
        + "</script>")
    Long countMyInitiated(@Param("userId") Long userId, @Param("status") Integer status);

    @Select("SELECT i.id AS id, i.title AS title, i.business_type AS business_type, i.status AS status, "
        + "i.start_time AS created_at FROM oa_flow_instance i WHERE i.initiator_user_id = #{userId} "
        + "ORDER BY i.start_time DESC LIMIT #{limit}")
    List<Map<String, Object>> myInitiatedRecent(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ACT_HI_TASKINST h WHERE h.ASSIGNEE_ = #{userId} "
        + "AND h.END_TIME_ IS NOT NULL AND h.END_TIME_ >= #{start} AND h.END_TIME_ < #{endExclusive}")
    Long countHandled(@Param("userId") String userId,
                      @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_flow_instance i WHERE i.status = 1 AND i.start_time &lt; #{timeoutStart} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "</script>")
    Long countTimeout(@Param("deptId") Long deptId, @Param("timeoutStart") LocalDateTime timeoutStart);

    /** 平均处理时长（小时）：仅已通过实例；拒绝/撤销/终止单列不进平均（docs/02 13.4）。 */
    @Select("<script>"
        + "SELECT AVG(i.duration) / 3600000 FROM oa_flow_instance i "
        + "WHERE i.status = 2 AND i.duration IS NOT NULL "
        + "AND i.end_time &gt;= #{start} AND i.end_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "</script>")
    BigDecimal avgDurationHours(@Param("deptId") Long deptId,
                                @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT i.status AS label, COUNT(*) AS `value` FROM oa_flow_instance i "
        + "WHERE i.end_time &gt;= #{start} AND i.end_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "GROUP BY i.status ORDER BY i.status"
        + "</script>")
    List<LabelValueVo> statusCounts(@Param("deptId") Long deptId,
                                    @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT i.start_time AS d, COUNT(*) AS `value` FROM oa_flow_instance i "
        + "WHERE i.start_time &gt;= #{start} AND i.start_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "GROUP BY i.start_time ORDER BY i.start_time"
        + "</script>")
    List<DayValueVo> startTrend(@Param("deptId") Long deptId,
                                @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT i.business_type AS label, COUNT(*) AS `value` FROM oa_flow_instance i "
        + "WHERE i.start_time &gt;= #{start} AND i.start_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "GROUP BY i.business_type ORDER BY `value` DESC, label"
        + "</script>")
    List<LabelValueVo> byType(@Param("deptId") Long deptId,
                              @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);

    @Select("<script>"
        + "SELECT i.business_type AS label, AVG(i.duration) / 3600000 AS `value` FROM oa_flow_instance i "
        + "WHERE i.status = 2 AND i.duration IS NOT NULL "
        + "AND i.end_time &gt;= #{start} AND i.end_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "GROUP BY i.business_type ORDER BY `value` DESC, label LIMIT #{limit}"
        + "</script>")
    List<LabelValueVo> topSlow(@Param("deptId") Long deptId,
                               @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive,
                               @Param("limit") int limit);

    @Select("<script>"
        + "SELECT i.id AS id, i.title AS label, CONCAT('发起人 ', COALESCE(i.initiator_name, ''), ' 已超时 ', "
        + "CAST(#{timeoutHours} AS CHAR), ' 小时') AS detail, CAST(i.start_time AS CHAR) AS time "
        + "FROM oa_flow_instance i WHERE i.status = 1 AND i.start_time &lt; #{timeoutStart} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "ORDER BY i.start_time LIMIT #{limit}"
        + "</script>")
    List<Map<String, Object>> timeoutList(@Param("deptId") Long deptId,
                                          @Param("timeoutStart") LocalDateTime timeoutStart,
                                          @Param("timeoutHours") int timeoutHours,
                                          @Param("limit") int limit);

    // ---------------------------------------------------------------- 工作台（个人视角）

    @Select("SELECT e.id AS id, e.title AS title, e.start_time AS start_time, e.end_time AS end_time "
        + "FROM oa_calendar_event e WHERE e.del_flag = 0 AND e.status = 1 "
        + "AND e.end_time >= #{dayStart} AND e.start_time < #{dayEndExclusive} "
        + "AND (e.organizer_id = #{userId} OR EXISTS (SELECT 1 FROM oa_calendar_attendee a "
        + "WHERE a.event_id = e.id AND a.user_id = #{userId})) ORDER BY e.start_time LIMIT #{limit}")
    List<Map<String, Object>> todayEvents(@Param("userId") Long userId,
                                          @Param("dayStart") LocalDateTime dayStart,
                                          @Param("dayEndExclusive") LocalDateTime dayEndExclusive,
                                          @Param("limit") int limit);

    @Select("SELECT n.id AS id, n.title AS title, n.publish_time AS publish_time "
        + "FROM oa_announcement n JOIN oa_announcement_audience aud ON aud.notice_id = n.id "
        + "AND aud.user_id = #{userId} WHERE n.status = 2 "
        + "ORDER BY n.is_top DESC, n.publish_time DESC LIMIT #{limit}")
    List<Map<String, Object>> noticeRecent(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT MIN(CASE WHEN p.punch_type = 1 THEN p.punch_time END) AS punch_in, "
        + "MAX(CASE WHEN p.punch_type = 2 THEN p.punch_time END) AS punch_out, COUNT(*) AS punches "
        + "FROM oa_punch_record p WHERE p.user_id = #{userId} AND p.punch_date = #{day}")
    Map<String, Object> punchOfDay(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Select("SELECT b.leave_type AS leaveType, b.total_minutes AS totalMinutes, "
        + "b.frozen_minutes AS frozenMinutes, b.used_minutes AS usedMinutes "
        + "FROM oa_leave_balance b WHERE b.user_id = #{userId} AND b.`year` = #{year} "
        + "ORDER BY b.leave_type")
    List<ReportBalanceVo> leaveBalances(@Param("userId") Long userId, @Param("year") int year);

    // ---------------------------------------------------------------- 明细（钻取与导出，同一口径）

    @Select("<script>"
        + "SELECT e.employee_no AS employee_no, e.name AS name, COALESCE(d.dept_name, '') AS dept_name, "
        + "COALESCE(e.position_level, '') AS position_level, e.status AS status, "
        + "CAST(e.entry_date AS CHAR) AS entry_date, CAST(e.leave_date AS CHAR) AS leave_date, "
        + "e.phone AS phone, e.id_card_no AS id_card_no, e.email AS email "
        + "FROM oa_employee e LEFT JOIN sys_dept d ON e.dept_id = d.dept_id "
        + "WHERE 1 = 1 "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "<if test='start != null'> AND e.entry_date IS NOT NULL AND e.entry_date &gt;= #{start} "
        + "AND e.entry_date &lt; #{endExclusive} </if>"
        + "ORDER BY e.employee_no LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<Map<String, Object>> detailHr(@Param("deptId") Long deptId,
                                       @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                       @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_employee e WHERE 1 = 1 "
        + "<if test='deptId != null'> AND e.dept_id = #{deptId} </if>"
        + "<if test='start != null'> AND e.entry_date IS NOT NULL AND e.entry_date &gt;= #{start} "
        + "AND e.entry_date &lt; #{endExclusive} </if>"
        + "</script>")
    long countDetailHr(@Param("deptId") Long deptId,
                       @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT CAST(a.attendance_date AS CHAR) AS attendance_date, COALESCE(u.nick_name, '') AS user_name, "
        + "COALESCE(d.dept_name, '') AS dept_name, a.work_status AS work_status, "
        + "CAST(a.first_punch_time AS CHAR) AS first_punch_time, CAST(a.last_punch_time AS CHAR) AS last_punch_time, "
        + "a.scheduled_minutes AS scheduled_minutes, a.worked_minutes AS worked_minutes, "
        + "a.late_minutes AS late_minutes, a.early_minutes AS early_minutes, "
        + "a.leave_minutes AS leave_minutes, a.overtime_minutes AS overtime_minutes, "
        + "a.is_abnormal AS is_abnormal "
        + "FROM oa_attendance_day a LEFT JOIN sys_user u ON a.user_id = u.user_id "
        + "LEFT JOIN sys_dept d ON a.dept_id = d.dept_id "
        + "WHERE a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "ORDER BY a.attendance_date, a.user_id LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<Map<String, Object>> detailAttendance(@Param("deptId") Long deptId,
                                               @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                               @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_attendance_day a "
        + "WHERE a.attendance_date &gt;= #{start} AND a.attendance_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND a.dept_id = #{deptId} </if>"
        + "</script>")
    long countDetailAttendance(@Param("deptId") Long deptId,
                               @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT c.reimburse_no AS reimburse_no, COALESCE(u.nick_name, '') AS user_name, "
        + "COALESCE(d.dept_name, '') AS dept_name, COALESCE(i.expense_type, '') AS expense_type, "
        + "CAST(i.occur_date AS CHAR) AS occur_date, i.amount AS amount, c.status AS status "
        + "FROM oa_expense_item i JOIN oa_reimburse_request c ON i.reimburse_id = c.id "
        + "JOIN sys_user u ON c.user_id = u.user_id "
        + "LEFT JOIN sys_dept d ON u.dept_id = d.dept_id "
        + "WHERE c.status IN (3, 5, 6) AND i.occur_date &gt;= #{start} AND i.occur_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "ORDER BY i.occur_date, c.reimburse_no LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<Map<String, Object>> detailFinance(@Param("deptId") Long deptId,
                                            @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive,
                                            @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_expense_item i JOIN oa_reimburse_request c ON i.reimburse_id = c.id "
        + "JOIN sys_user u ON c.user_id = u.user_id "
        + "WHERE c.status IN (3, 5, 6) AND i.occur_date &gt;= #{start} AND i.occur_date &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "</script>")
    long countDetailFinance(@Param("deptId") Long deptId,
                            @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Select("<script>"
        + "SELECT i.business_key AS business_key, i.title AS title, i.business_type AS business_type, "
        + "COALESCE(i.initiator_name, '') AS initiator_name, COALESCE(d.dept_name, '') AS dept_name, "
        + "i.status AS status, CAST(i.start_time AS CHAR) AS start_time, CAST(i.end_time AS CHAR) AS end_time, "
        + "CASE WHEN i.duration IS NULL THEN NULL ELSE i.duration / 3600000 END AS duration_hours "
        + "FROM oa_flow_instance i LEFT JOIN sys_dept d ON i.initiator_dept_id = d.dept_id "
        + "WHERE i.start_time &gt;= #{start} AND i.start_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "ORDER BY i.start_time DESC LIMIT #{limit} OFFSET #{offset}"
        + "</script>")
    List<Map<String, Object>> detailFlow(@Param("deptId") Long deptId,
                                         @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive,
                                         @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>"
        + "SELECT COUNT(*) FROM oa_flow_instance i "
        + "WHERE i.start_time &gt;= #{start} AND i.start_time &lt; #{endExclusive} "
        + "<if test='deptId != null'> AND i.initiator_dept_id = #{deptId} </if>"
        + "</script>")
    long countDetailFlow(@Param("deptId") Long deptId,
                         @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive);
}
