-- AgentOA module 8: reporting metric dictionary, versioned metric definitions and export task records.
-- API contract: docs/05-api-spec.md section 10. Read-only over the V4-V10 fact tables (docs/18 step 1):
-- no fact table copies, no report writes into business tables.

-- ---------------------------------------------------------------- metric dictionary and versions

CREATE TABLE oa_report_metric (
  id              BIGINT       NOT NULL                   COMMENT '指标ID',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  metric_code     VARCHAR(64)  NOT NULL                   COMMENT '指标编码',
  metric_name     VARCHAR(64)  NOT NULL                   COMMENT '指标名称',
  category        VARCHAR(32)  NOT NULL                   COMMENT '分类（hr/attendance/finance/workflow）',
  unit            VARCHAR(32)  DEFAULT NULL               COMMENT '单位（count/minutes/days/hours/amount/ratio）',
  description     VARCHAR(500) DEFAULT NULL               COMMENT '口径摘要',
  current_version INT          NOT NULL DEFAULT 1         COMMENT '当前口径版本',
  status          TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1启用 0停用）',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_metric_code (metric_code),
  KEY idx_report_metric_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表指标字典';

CREATE TABLE oa_report_metric_version (
  id             BIGINT       NOT NULL                   COMMENT '口径版本ID',
  metric_code    VARCHAR(64)  NOT NULL                   COMMENT '指标编码',
  version        INT          NOT NULL                   COMMENT '口径版本号',
  definition     TEXT         NOT NULL                   COMMENT '口径定义（时间范围/时区/状态/去重/组织过滤/数据更新时间）',
  status         TINYINT      NOT NULL DEFAULT 1         COMMENT '状态（1生效 2历史）',
  effective_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  created_by     BIGINT       DEFAULT NULL               COMMENT '登记人',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_metric_version (metric_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表口径版本（口径变更即升版本）';

-- ---------------------------------------------------------------- export task records

CREATE TABLE oa_report_export (
  id              BIGINT        NOT NULL                   COMMENT '导出任务ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  export_no       VARCHAR(32)   NOT NULL                   COMMENT '导出单号',
  report_type     VARCHAR(32)   NOT NULL                   COMMENT '报表类型（hr/attendance/finance/flow）',
  metric_version  INT           NOT NULL DEFAULT 1         COMMENT '口径版本快照',
  format          VARCHAR(8)    NOT NULL DEFAULT 'CSV'     COMMENT '格式（CSV/XLSX）',
  filters         VARCHAR(2000) DEFAULT NULL               COMMENT '过滤条件（JSON）',
  status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/RUNNING/SUCCESS/FAILED）',
  row_count       INT           DEFAULT NULL               COMMENT '导出行数',
  file_id         BIGINT        DEFAULT NULL               COMMENT '私有文件ID（sys_file）',
  error_message   VARCHAR(500)  DEFAULT NULL               COMMENT '失败原因',
  idempotency_key VARCHAR(64)   DEFAULT NULL               COMMENT '幂等键',
  requested_by    BIGINT        NOT NULL                   COMMENT '操作者账号ID',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  finish_time     DATETIME      DEFAULT NULL               COMMENT '完成时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_export_no (export_no),
  UNIQUE KEY uk_report_export_idem (requested_by, idempotency_key),
  KEY idx_report_export_user (requested_by, create_time),
  KEY idx_report_export_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表导出任务记录（操作者/过滤条件/口径版本）';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(100, '000000', '指标分类', 'rp_metric_category', 100, 1, sysdate(), null, null, '报表指标字典分类');
INSERT INTO sys_dict_data VALUES(1000, '000000', 1, '人事', 'hr', 'rp_metric_category', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1001, '000000', 2, '考勤', 'attendance', 'rp_metric_category', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1002, '000000', 3, '财务', 'finance', 'rp_metric_category', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1003, '000000', 4, '流程', 'workflow', 'rp_metric_category', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(101, '000000', '报表类型', 'rp_report_type', 100, 1, sysdate(), null, null, '看板与导出类型');
INSERT INTO sys_dict_data VALUES(1010, '000000', 1, '人事看板', 'hr', 'rp_report_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1011, '000000', 2, '考勤看板', 'attendance', 'rp_report_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1012, '000000', 3, '财务看板', 'finance', 'rp_report_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1013, '000000', 4, '流程看板', 'flow', 'rp_report_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(102, '000000', '导出格式', 'rp_export_format', 100, 1, sysdate(), null, null, '报表导出格式');
INSERT INTO sys_dict_data VALUES(1020, '000000', 1, 'CSV', 'CSV', 'rp_export_format', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1021, '000000', 2, 'Excel', 'XLSX', 'rp_export_format', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(103, '000000', '导出任务状态', 'rp_export_status', 100, 1, sysdate(), null, null, '报表导出任务状态');
INSERT INTO sys_dict_data VALUES(1030, '000000', 1, '待处理', 'PENDING', 'rp_export_status', '', 'info', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1031, '000000', 2, '处理中', 'RUNNING', 'rp_export_status', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1032, '000000', 3, '已完成', 'SUCCESS', 'rp_export_status', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(1033, '000000', 4, '失败', 'FAILED', 'rp_export_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- menus and buttons (docs/05 section 10)

INSERT INTO sys_menu VALUES(2700, '报表看板', 0, 13, 'report', null, '', 1, 0, 'M', '0', '0', '', 'chart', 100, 1, sysdate(), null, null, '人事、考勤、财务、流程四类看板与导出');
INSERT INTO sys_menu VALUES(2701, '人事看板', 2700, 1, 'hr', 'report/hr/index', '', 1, 0, 'C', '0', '0', '', 'peoples', 100, 1, sysdate(), null, null, '员工与组织概览');
INSERT INTO sys_menu VALUES(2702, '考勤看板', 2700, 2, 'attendance', 'report/attendance/index', '', 1, 0, 'C', '0', '0', '', 'date', 100, 1, sysdate(), null, null, '出勤率与异常');
INSERT INTO sys_menu VALUES(2703, '财务看板', 2700, 3, 'finance', 'report/finance/index', '', 1, 0, 'C', '0', '0', '', 'money', 100, 1, sysdate(), null, null, '申请/批准/实付分列');
INSERT INTO sys_menu VALUES(2704, '流程看板', 2700, 4, 'flow', 'report/flow/index', '', 1, 0, 'C', '0', '0', '', 'form', 100, 1, sysdate(), null, null, '待办、时长与超时');
INSERT INTO sys_menu VALUES(2705, '导出记录', 2700, 5, 'exports', 'report/exports/index', '', 1, 0, 'C', '0', '0', '', 'download', 100, 1, sysdate(), null, null, '导出任务、过滤条件与口径版本');
INSERT INTO sys_menu VALUES(2710, '报表查看', 2701, 1, '', '', '', 1, 0, 'F', '0', '0', 'rp:report:list', '#', 100, 1, sysdate(), null, null, '四类看板只读查询');
INSERT INTO sys_menu VALUES(2711, '报表导出', 2705, 1, '', '', '', 1, 0, 'F', '0', '0', 'rp:report:export', '#', 100, 1, sysdate(), null, null, '导出与导出记录全量查看');

-- Role-menu matrix: HR/部门经理/财务/总监维护报表；普通员工与出纳只用工作台自助卡片。
INSERT INTO sys_role_menu VALUES(20, 2700);
INSERT INTO sys_role_menu VALUES(20, 2701);
INSERT INTO sys_role_menu VALUES(20, 2702);
INSERT INTO sys_role_menu VALUES(20, 2703);
INSERT INTO sys_role_menu VALUES(20, 2704);
INSERT INTO sys_role_menu VALUES(20, 2705);
INSERT INTO sys_role_menu VALUES(20, 2710);
INSERT INTO sys_role_menu VALUES(20, 2711);
INSERT INTO sys_role_menu VALUES(21, 2700);
INSERT INTO sys_role_menu VALUES(21, 2701);
INSERT INTO sys_role_menu VALUES(21, 2702);
INSERT INTO sys_role_menu VALUES(21, 2703);
INSERT INTO sys_role_menu VALUES(21, 2704);
INSERT INTO sys_role_menu VALUES(21, 2705);
INSERT INTO sys_role_menu VALUES(21, 2710);
INSERT INTO sys_role_menu VALUES(21, 2711);
INSERT INTO sys_role_menu VALUES(23, 2700);
INSERT INTO sys_role_menu VALUES(23, 2701);
INSERT INTO sys_role_menu VALUES(23, 2702);
INSERT INTO sys_role_menu VALUES(23, 2703);
INSERT INTO sys_role_menu VALUES(23, 2704);
INSERT INTO sys_role_menu VALUES(23, 2705);
INSERT INTO sys_role_menu VALUES(23, 2710);
INSERT INTO sys_role_menu VALUES(23, 2711);
INSERT INTO sys_role_menu VALUES(25, 2700);
INSERT INTO sys_role_menu VALUES(25, 2701);
INSERT INTO sys_role_menu VALUES(25, 2702);
INSERT INTO sys_role_menu VALUES(25, 2703);
INSERT INTO sys_role_menu VALUES(25, 2704);
INSERT INTO sys_role_menu VALUES(25, 2705);
INSERT INTO sys_role_menu VALUES(25, 2710);
INSERT INTO sys_role_menu VALUES(25, 2711);

-- ---------------------------------------------------------------- metric dictionary seeds (docs/02 13.4 口径)

INSERT INTO oa_report_metric VALUES(1, '000000', 'hr.active_count', '在职总人数', 'hr', 'count', '状态为试用期/正式/待离职的员工数', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(1, 'hr.active_count', 1, '{"timeField":"oa_employee.status","timezone":"Asia/Shanghai","statusFilter":"status IN (PROBATION,ACTIVE,LEAVE_PENDING)","dedup":"oa_employee.id","orgFilter":"oa_employee.dept_id","dataSource":"oa_employee","dataUpdatedAt":"oa_employee.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(2, '000000', 'hr.new_hire_month', '本月新入职', 'hr', 'count', '入职日期落在统计自然月内的员工数', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(2, 'hr.new_hire_month', 1, '{"timeField":"oa_employee.entry_date","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"entry_date IS NOT NULL","dedup":"oa_employee.id","orgFilter":"oa_employee.dept_id","dataSource":"oa_employee","dataUpdatedAt":"oa_employee.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(3, '000000', 'hr.leave_month', '本月离职', 'hr', 'count', '离职日期落在统计自然月内且状态为已离职', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(3, 'hr.leave_month', 1, '{"timeField":"oa_employee.leave_date","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"status = LEFT","dedup":"oa_employee.id","orgFilter":"oa_employee.dept_id","dataSource":"oa_employee","dataUpdatedAt":"oa_employee.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(4, '000000', 'hr.probation_count', '试用期人数', 'hr', 'count', '状态为试用期的员工数', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(4, 'hr.probation_count', 1, '{"timeField":"oa_employee.status","timezone":"Asia/Shanghai","statusFilter":"status = PROBATION","dedup":"oa_employee.id","orgFilter":"oa_employee.dept_id","dataSource":"oa_employee","dataUpdatedAt":"oa_employee.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(5, '000000', 'att.rate_today', '今日出勤率', 'attendance', 'ratio', '实际出勤分钟/应出勤分钟，应出勤扣除已批准请假与非工作时间，分母为零返回空', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(5, 'att.rate_today', 1, '{"timeField":"oa_attendance_day.attendance_date","timezone":"Asia/Shanghai","interval":"自然日闭区间","statusFilter":"work_status IN (1,3,4)，work_status=2 休息不计","dedup":"oa_attendance_day.user_id+attendance_date","orgFilter":"oa_attendance_day.dept_id","dataSource":"oa_attendance_day","dataUpdatedAt":"oa_attendance_day.update_time","formula":"SUM(worked_minutes)/SUM(scheduled_minutes-leave_minutes)"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(6, '000000', 'att.late_week', '本周迟到次数', 'attendance', 'count', '本周日报迟到分钟大于零的人次', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(6, 'att.late_week', 1, '{"timeField":"oa_attendance_day.attendance_date","timezone":"Asia/Shanghai","interval":"自然周闭区间","statusFilter":"late_minutes > 0","dedup":"oa_attendance_day.user_id+attendance_date","orgFilter":"oa_attendance_day.dept_id","dataSource":"oa_attendance_day","dataUpdatedAt":"oa_attendance_day.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(7, '000000', 'att.overtime_month', '本月加班时长', 'attendance', 'minutes', '本月日报加班分钟合计', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(7, 'att.overtime_month', 1, '{"timeField":"oa_attendance_day.attendance_date","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"overtime_minutes > 0","dedup":"oa_attendance_day.user_id+attendance_date","orgFilter":"oa_attendance_day.dept_id","dataSource":"oa_attendance_day","dataUpdatedAt":"oa_attendance_day.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(8, '000000', 'att.leave_days_month', '本月请假天数', 'attendance', 'days', '本月已批准请假分钟合计折算天（480 分钟/天）', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(8, 'att.leave_days_month', 1, '{"timeField":"oa_attendance_day.attendance_date","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"leave_minutes > 0","dedup":"oa_attendance_day.user_id+attendance_date","orgFilter":"oa_attendance_day.dept_id","dataSource":"oa_attendance_day","dataUpdatedAt":"oa_attendance_day.update_time","formula":"SUM(leave_minutes)/480"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(9, '000000', 'fn.applied_month', '本月申请额', 'finance', 'amount', '按提交时间（流程发起）统计的报销总额，每单只计最新一次提交', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(9, 'fn.applied_month', 1, '{"timeField":"oa_flow_instance.start_time","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"oa_reimburse_request.status <> 草稿","dedup":"oa_reimburse_request.id（取最新 submission_no）","orgFilter":"sys_user.dept_id","dataSource":"oa_reimburse_request+oa_flow_instance","dataUpdatedAt":"oa_reimburse_request.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(10, '000000', 'fn.approved_month', '本月批准额', 'finance', 'amount', '按批准时间（流程结束且已通过）统计的报销总额', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(10, 'fn.approved_month', 1, '{"timeField":"oa_flow_instance.end_time","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"oa_flow_instance.status = 2 已通过","dedup":"oa_reimburse_request.id","orgFilter":"sys_user.dept_id","dataSource":"oa_reimburse_request+oa_flow_instance","dataUpdatedAt":"oa_flow_instance.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(11, '000000', 'fn.paid_month', '本月实付额', 'finance', 'amount', '按付款登记日期统计的已付金额', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(11, 'fn.paid_month', 1, '{"timeField":"oa_payment_record.pay_date","timezone":"Asia/Shanghai","interval":"自然月闭区间","statusFilter":"pay_status = 2 已付","dedup":"oa_payment_record.reimburse_id（一单一次付款）","orgFilter":"sys_user.dept_id","dataSource":"oa_payment_record","dataUpdatedAt":"oa_payment_record.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(12, '000000', 'fn.pending_amount', '在途待审批金额', 'finance', 'amount', '状态为审批中的报销总额', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(12, 'fn.pending_amount', 1, '{"timeField":"oa_reimburse_request.create_time","timezone":"Asia/Shanghai","statusFilter":"status = 2 审批中","dedup":"oa_reimburse_request.id","orgFilter":"sys_user.dept_id","dataSource":"oa_reimburse_request","dataUpdatedAt":"oa_reimburse_request.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(13, '000000', 'flow.todo_count', '我的待办数', 'workflow', 'count', '当前办理人为本人的运行时任务数（与待办列表同一口径）', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(13, 'flow.todo_count', 1, '{"timeField":"ACT_RU_TASK.CREATE_TIME_","timezone":"Asia/Shanghai","statusFilter":"运行时任务未结束","dedup":"ACT_RU_TASK.ID_","orgFilter":"不适用（个人视角）","dataSource":"ACT_RU_TASK+ACT_RU_IDENTITYLINK","dataUpdatedAt":"ACT_RU_TASK.REV_"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(14, '000000', 'flow.handled_week', '本周处理数', 'workflow', 'count', '本周内由本人办结的任务数', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(14, 'flow.handled_week', 1, '{"timeField":"ACT_HI_TASKINST.END_TIME_","timezone":"Asia/Shanghai","interval":"自然周闭区间","statusFilter":"END_TIME_ 非空且办理人为本人","dedup":"ACT_HI_TASKINST.ID_","orgFilter":"不适用（个人视角）","dataSource":"ACT_HI_TASKINST","dataUpdatedAt":"ACT_HI_TASKINST.LAST_UPDATED_TIME_"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(15, '000000', 'flow.timeout_count', '超时未处理数', 'workflow', 'count', '发起后超过 24 小时仍未办结的在途实例数（拒绝/撤销/终止除外）', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(15, 'flow.timeout_count', 1, '{"timeField":"oa_flow_instance.start_time","timezone":"Asia/Shanghai","thresholdHours":24,"statusFilter":"status = 1 审批中","dedup":"oa_flow_instance.id","orgFilter":"oa_flow_instance.initiator_dept_id","dataSource":"oa_flow_instance","dataUpdatedAt":"oa_flow_instance.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(16, '000000', 'flow.avg_duration_hours', '平均处理时长', 'workflow', 'hours', '区间内已结束实例的结束减开始平均小时数，排除在途实例，拒绝/撤销/终止单列', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(16, 'flow.avg_duration_hours', 1, '{"timeField":"oa_flow_instance.end_time","timezone":"Asia/Shanghai","interval":"闭区间","statusFilter":"status IN (2 已通过)，拒绝(3)/撤销(4)/终止(6)单列不计平均","dedup":"oa_flow_instance.id","orgFilter":"oa_flow_instance.initiator_dept_id","dataSource":"oa_flow_instance","dataUpdatedAt":"oa_flow_instance.update_time","formula":"AVG(duration)/3600000"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(17, '000000', 'hr.dept_distribution', '部门人数分布', 'hr', 'count', '按在职状态统计的部门人数', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(17, 'hr.dept_distribution', 1, '{"timeField":"oa_employee.status","timezone":"Asia/Shanghai","statusFilter":"status IN (PROBATION,ACTIVE,LEAVE_PENDING)","dedup":"oa_employee.id","orgFilter":"oa_employee.dept_id","dataSource":"oa_employee+sys_dept","dataUpdatedAt":"oa_employee.update_time"}', 1, sysdate(), 1, sysdate());
INSERT INTO oa_report_metric VALUES(18, '000000', 'flow.by_type', '流程类型分布', 'workflow', 'count', '按业务类型统计区间内发起的流程数（拒绝/撤销/终止仍计入发起量）', 1, 1, sysdate(), null);
INSERT INTO oa_report_metric_version VALUES(18, 'flow.by_type', 1, '{"timeField":"oa_flow_instance.start_time","timezone":"Asia/Shanghai","interval":"闭区间","statusFilter":"全部状态","dedup":"oa_flow_instance.id","orgFilter":"oa_flow_instance.initiator_dept_id","dataSource":"oa_flow_instance","dataUpdatedAt":"oa_flow_instance.update_time"}', 1, sysdate(), 1, sysdate());
