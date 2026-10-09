-- AgentOA module 3: attendance groups, shifts, punches, leave balances and reports.
-- API contract: docs/05-api-spec.md section 5. Business ids are assigned snowflake values.
-- Naming follows V4: docs/04 at_* drafts are unified to the oa_ prefix.

-- ---------------------------------------------------------------- catalog tables

CREATE TABLE oa_shift (
  id                 BIGINT       NOT NULL                   COMMENT '班次ID',
  tenant_id          VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  shift_code         VARCHAR(64)  NOT NULL                   COMMENT '班次编码',
  shift_name         VARCHAR(64)  NOT NULL                   COMMENT '班次名称',
  work_start_time    TIME         NOT NULL                   COMMENT '上班时间',
  work_end_time      TIME         NOT NULL                   COMMENT '下班时间',
  rest_start_time    TIME         DEFAULT NULL               COMMENT '休息开始',
  rest_end_time      TIME         DEFAULT NULL               COMMENT '休息结束',
  is_cross_day      TINYINT      NOT NULL DEFAULT 0          COMMENT '是否跨夜班（0否 1是）',
  flexible_minutes   INT          NOT NULL DEFAULT 0          COMMENT '弹性分钟（晚到顺延下班）',
  grace_minutes      INT          NOT NULL DEFAULT 0          COMMENT '迟到早退宽限分钟',
  punch_window_start INT          NOT NULL DEFAULT 120        COMMENT '班次开始前允许打卡分钟',
  punch_window_end   INT          NOT NULL DEFAULT 240        COMMENT '班次结束后允许打卡分钟',
  status             CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '状态（0正常 1停用）',
  create_dept        BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by          BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time        DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark             VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shift_code (shift_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次';

CREATE TABLE oa_attendance_group (
  id              BIGINT       NOT NULL                   COMMENT '考勤组ID',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  group_code      VARCHAR(64)  NOT NULL                   COMMENT '考勤组编码',
  group_name      VARCHAR(64)  NOT NULL                   COMMENT '考勤组名称',
  shift_id        BIGINT       NOT NULL                   COMMENT '班次ID',
  work_days       VARCHAR(32)  NOT NULL DEFAULT '1,2,3,4,5' COMMENT '工作日（周一=1）',
  effective_date  DATE         NOT NULL                   COMMENT '规则生效日期',
  scope_snapshot  JSON         DEFAULT NULL               COMMENT '规则配置快照',
  status          CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '状态（0正常 1停用）',
  create_dept     BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by       BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time     DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark          VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_code (group_code),
  KEY idx_group_shift (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤组';

CREATE TABLE oa_attendance_member (
  id            BIGINT      NOT NULL                   COMMENT '成员ID',
  tenant_id     VARCHAR(20) NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  group_id      BIGINT      NOT NULL                   COMMENT '考勤组ID',
  user_id       BIGINT      NOT NULL                   COMMENT '成员账号ID',
  employee_id   BIGINT      DEFAULT NULL               COMMENT '员工档案ID',
  valid_from    DATE        NOT NULL                   COMMENT '生效日期',
  valid_to      DATE        DEFAULT NULL               COMMENT '失效日期（空表示长期）',
  create_by     BIGINT      DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_user_from (user_id, valid_from),
  KEY idx_member_group (group_id, user_id),
  KEY idx_member_user (user_id, valid_from, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤组成员生效区间';

CREATE TABLE oa_calendar (
  id            BIGINT       NOT NULL                   COMMENT '日历ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  work_date     DATE         NOT NULL                   COMMENT '日历日期',
  day_type      CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '日期类型（0工作日 1节假日 2调休上班）',
  description   VARCHAR(128) DEFAULT NULL               COMMENT '说明',
  rule_version  INT          NOT NULL DEFAULT 1          COMMENT '规则版本',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_calendar_date (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织工作日历与调休';

CREATE TABLE oa_holiday (
  id            BIGINT       NOT NULL                   COMMENT '节假日ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  holiday_date  DATE         NOT NULL                   COMMENT '日期',
  holiday_name  VARCHAR(64)  NOT NULL                   COMMENT '名称',
  holiday_type  VARCHAR(16)  NOT NULL DEFAULT 'HOLIDAY' COMMENT '类型（HOLIDAY节假日 MAKEUP调休上班）',
  year          INT          NOT NULL                   COMMENT '年度',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_holiday_date (holiday_date),
  KEY idx_holiday_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节假日登记';

-- ---------------------------------------------------------------- punch and daily summary

CREATE TABLE oa_punch_record (
  id                    BIGINT        NOT NULL                   COMMENT '打卡记录ID',
  tenant_id             VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id               BIGINT        NOT NULL                   COMMENT '打卡账号ID',
  employee_id           BIGINT        DEFAULT NULL               COMMENT '员工档案ID',
  punch_date            DATE          NOT NULL                   COMMENT '归属考勤日期',
  punch_time            DATETIME      NOT NULL                   COMMENT '打卡时间（服务器接收时间）',
  punch_type            TINYINT       NOT NULL                   COMMENT '类型（1上班 2下班 3外勤 4补卡）',
  correction_request_id BIGINT        DEFAULT NULL               COMMENT '补卡申请ID（仅审批通过后写入）',
  is_late               TINYINT       NOT NULL DEFAULT 0          COMMENT '是否迟到',
  late_minutes          INT           NOT NULL DEFAULT 0          COMMENT '迟到分钟',
  is_early              TINYINT       NOT NULL DEFAULT 0          COMMENT '是否早退',
  early_minutes         INT           NOT NULL DEFAULT 0          COMMENT '早退分钟',
  lng                   DECIMAL(10,6) DEFAULT NULL               COMMENT '经度',
  lat                   DECIMAL(10,6) DEFAULT NULL               COMMENT '纬度',
  accuracy_meters       DECIMAL(10,2) DEFAULT NULL               COMMENT '客户端定位精度（非可信证明）',
  address               VARCHAR(255)  DEFAULT NULL               COMMENT '详细地址',
  device                VARCHAR(64)   DEFAULT NULL               COMMENT '设备',
  ip                    VARCHAR(50)   DEFAULT NULL               COMMENT 'IP',
  source                TINYINT       NOT NULL DEFAULT 1          COMMENT '来源（1PC 2App 3小程序）',
  create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_punch_correction (correction_request_id),
  KEY idx_punch_user_date (user_id, punch_date),
  KEY idx_punch_date (punch_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡记录（保留全部原始记录）';

CREATE TABLE oa_attendance_day (
  id                BIGINT       NOT NULL                   COMMENT '日报ID',
  tenant_id         VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id           BIGINT       NOT NULL                   COMMENT '账号ID',
  employee_id       BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  dept_id           BIGINT       DEFAULT NULL               COMMENT '部门ID',
  attendance_date   DATE         NOT NULL                   COMMENT '考勤日期',
  group_id          BIGINT       DEFAULT NULL               COMMENT '考勤组快照',
  shift_id          BIGINT       DEFAULT NULL               COMMENT '班次快照',
  rule_version      INT          NOT NULL DEFAULT 1          COMMENT '计算规则版本',
  first_punch_time  DATETIME     DEFAULT NULL               COMMENT '首次有效打卡',
  last_punch_time   DATETIME     DEFAULT NULL               COMMENT '末次有效打卡',
  work_status       TINYINT      NOT NULL DEFAULT 0          COMMENT '主状态（0待结算 1出勤 2休息 3缺勤 4请假）',
  missing_punch     TINYINT      NOT NULL DEFAULT 0          COMMENT '是否缺卡',
  scheduled_minutes INT          NOT NULL DEFAULT 0          COMMENT '计划工作分钟',
  worked_minutes    INT          NOT NULL DEFAULT 0          COMMENT '实际出勤分钟',
  late_minutes      INT          NOT NULL DEFAULT 0          COMMENT '迟到分钟',
  early_minutes     INT          NOT NULL DEFAULT 0          COMMENT '早退分钟',
  leave_minutes     INT          NOT NULL DEFAULT 0          COMMENT '请假分钟',
  overtime_minutes  INT          NOT NULL DEFAULT 0          COMMENT '加班分钟',
  is_abnormal       TINYINT      NOT NULL DEFAULT 0          COMMENT '是否异常',
  abnormal_reason   VARCHAR(255) DEFAULT NULL               COMMENT '异常原因',
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time       DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_day_user_date (user_id, attendance_date),
  KEY idx_day_date (attendance_date),
  KEY idx_day_abnormal (is_abnormal),
  KEY idx_day_dept (dept_id, attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤日报（使用班次/考勤组快照，不随规则修改重算）';

-- ---------------------------------------------------------------- leave balance ledger

CREATE TABLE oa_leave_type (
  id             BIGINT       NOT NULL                   COMMENT '假种ID',
  tenant_id      VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  type_code      VARCHAR(32)  NOT NULL                   COMMENT '假种编码',
  type_name      VARCHAR(64)  NOT NULL                   COMMENT '假种名称',
  quota_limited  TINYINT      NOT NULL DEFAULT 1          COMMENT '是否计额度（1是 0否）',
  default_minutes INT         NOT NULL DEFAULT 0          COMMENT '年度默认额度（分钟，0表示由HR发放）',
  status         CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '状态（0正常 1停用）',
  create_dept    BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by      BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by      BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_leave_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期类型与额度策略';

CREATE TABLE oa_leave_balance (
  id             BIGINT       NOT NULL                   COMMENT '余额ID',
  tenant_id      VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id        BIGINT       NOT NULL                   COMMENT '账号ID',
  employee_id    BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  year           INT          NOT NULL                   COMMENT '年度',
  leave_type     VARCHAR(32)  NOT NULL                   COMMENT '假种编码',
  total_minutes  INT          NOT NULL DEFAULT 0          COMMENT '总额度（分钟）',
  frozen_minutes INT          NOT NULL DEFAULT 0          COMMENT '审批中冻结（分钟）',
  used_minutes   INT          NOT NULL DEFAULT 0          COMMENT '已用（分钟）',
  lock_version   INT          NOT NULL DEFAULT 0          COMMENT '乐观锁',
  expire_date    DATE         DEFAULT NULL               COMMENT '年度额度到期日期',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_balance_user_year_type (user_id, year, leave_type),
  KEY idx_balance_type (leave_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期额度（分钟账本余额）';

CREATE TABLE oa_leave_ledger (
  id             BIGINT       NOT NULL                   COMMENT '流水ID',
  tenant_id      VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  balance_id     BIGINT       NOT NULL                   COMMENT '余额ID',
  event_key      VARCHAR(128) NOT NULL                   COMMENT '业务事件ID（幂等去重）',
  business_type  VARCHAR(32)  NOT NULL                   COMMENT '业务类型（grant/leave）',
  business_id    BIGINT       NOT NULL                   COMMENT '业务ID',
  submission_no  INT          NOT NULL DEFAULT 0          COMMENT '提交序号',
  action         VARCHAR(16)  NOT NULL                   COMMENT '动作（GRANT/FREEZE/SETTLE/RELEASE/ADJUST）',
  total_delta    INT          NOT NULL DEFAULT 0          COMMENT '总额度变化（分钟）',
  frozen_delta   INT          NOT NULL DEFAULT 0          COMMENT '冻结变化（分钟）',
  used_delta     INT          NOT NULL DEFAULT 0          COMMENT '已用变化（分钟）',
  leave_minutes  INT          NOT NULL DEFAULT 0          COMMENT '事件涉及请假分钟',
  operator_id    BIGINT       NOT NULL                   COMMENT '操作者账号ID',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_event (balance_id, event_key),
  KEY idx_ledger_business (business_type, business_id, submission_no),
  KEY idx_ledger_balance (balance_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变额度流水';

-- ---------------------------------------------------------------- approved overtime / correction effects

CREATE TABLE oa_overtime (
  id                  BIGINT       NOT NULL                   COMMENT '加班记录ID',
  tenant_id           VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id             BIGINT       NOT NULL                   COMMENT '账号ID',
  employee_id         BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  overtime_request_id BIGINT       NOT NULL                   COMMENT '加班申请ID',
  overtime_date       DATE         NOT NULL                   COMMENT '加班日期',
  start_time          DATETIME     DEFAULT NULL               COMMENT '开始时间',
  end_time            DATETIME     DEFAULT NULL               COMMENT '结束时间',
  duration_minutes    INT          NOT NULL DEFAULT 0          COMMENT '核定加班分钟',
  overtime_type       VARCHAR(32)  DEFAULT NULL               COMMENT '加班类型（weekday/weekend/holiday）',
  status              TINYINT      NOT NULL DEFAULT 3          COMMENT '状态（3已通过 7已撤销）',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time         DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_overtime_request (overtime_request_id),
  KEY idx_overtime_user_date (user_id, overtime_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加班核定记录';

CREATE TABLE oa_correction (
  id                   BIGINT       NOT NULL                   COMMENT '补卡记录ID',
  tenant_id            VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id              BIGINT       NOT NULL                   COMMENT '账号ID',
  employee_id          BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  correction_request_id BIGINT      NOT NULL                   COMMENT '补卡申请ID',
  attendance_date      DATE         NOT NULL                   COMMENT '补卡日期',
  punch_type           TINYINT      NOT NULL                   COMMENT '时段（1上班 2下班）',
  corrected_time       DATETIME     NOT NULL                   COMMENT '补卡时间',
  status               TINYINT      NOT NULL DEFAULT 3          COMMENT '状态（3已通过 7已撤销）',
  create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time          DATETIME     DEFAULT NULL               COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_correction_request (correction_request_id),
  KEY idx_correction_user_date (user_id, attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补卡生效记录';

-- ---------------------------------------------------------------- dictionaries

INSERT INTO sys_dict_type VALUES(50, '000000', '打卡类型', 'at_punch_type', 100, 1, sysdate(), null, null, '打卡记录类型');
INSERT INTO sys_dict_data VALUES(500, '000000', 1, '上班', '1', 'at_punch_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(501, '000000', 2, '下班', '2', 'at_punch_type', '', 'success', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(502, '000000', 3, '外勤', '3', 'at_punch_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, 'P1');
INSERT INTO sys_dict_data VALUES(503, '000000', 4, '补卡', '4', 'at_punch_type', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(51, '000000', '日历日期类型', 'at_day_type', 100, 1, sysdate(), null, null, '工作日历日期类型');
INSERT INTO sys_dict_data VALUES(510, '000000', 1, '工作日', '0', 'at_day_type', '', 'primary', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(511, '000000', 2, '节假日', '1', 'at_day_type', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(512, '000000', 3, '调休上班', '2', 'at_day_type', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(52, '000000', '考勤日状态', 'at_work_status', 100, 1, sysdate(), null, null, '考勤日报主状态');
INSERT INTO sys_dict_data VALUES(520, '000000', 1, '待结算', '0', 'at_work_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(521, '000000', 2, '出勤', '1', 'at_work_status', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(522, '000000', 3, '休息', '2', 'at_work_status', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(523, '000000', 4, '缺勤', '3', 'at_work_status', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(524, '000000', 5, '请假', '4', 'at_work_status', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_type VALUES(53, '000000', '额度动作', 'at_leave_action', 100, 1, sysdate(), null, null, '额度账本动作');
INSERT INTO sys_dict_data VALUES(530, '000000', 1, '发放', 'GRANT', 'at_leave_action', '', 'success', 'Y', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(531, '000000', 2, '冻结', 'FREEZE', 'at_leave_action', '', 'warning', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(532, '000000', 3, '结算', 'SETTLE', 'at_leave_action', '', 'primary', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(533, '000000', 4, '释放', 'RELEASE', 'at_leave_action', '', 'info', 'N', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(534, '000000', 5, '调整', 'ADJUST', 'at_leave_action', '', 'danger', 'N', 100, 1, sysdate(), null, null, '');

-- ---------------------------------------------------------------- leave types (aligned with dict at_leave_type)

INSERT INTO oa_leave_type VALUES(1, '000000', 'annual', '年假', 1, 0, '0', null, null, sysdate(), null, null, '额度由 HR 确认政策后发放');
INSERT INTO oa_leave_type VALUES(2, '000000', 'compensatory', '调休', 1, 0, '0', null, null, sysdate(), null, null, '按 HR 确认的加班折算比例发放');
INSERT INTO oa_leave_type VALUES(3, '000000', 'sick', '病假', 0, 0, '0', null, null, sysdate(), null, null, '不计额度，仅统计时长');
INSERT INTO oa_leave_type VALUES(4, '000000', 'personal', '事假', 0, 0, '0', null, null, sysdate(), null, null, '不计额度，仅统计时长');
INSERT INTO oa_leave_type VALUES(5, '000000', 'marriage', '婚假', 0, 0, '0', null, null, sysdate(), null, null, '不计额度，仅统计时长');
INSERT INTO oa_leave_type VALUES(6, '000000', 'maternity', '产假', 0, 0, '0', null, null, sysdate(), null, null, '不计额度，仅统计时长');
INSERT INTO oa_leave_type VALUES(7, '000000', 'paternity', '陪产假', 0, 0, '0', null, null, sysdate(), null, null, '不计额度，仅统计时长');
INSERT INTO oa_leave_type VALUES(8, '000000', 'bereavement', '丧假', 0, 0, '0', null, null, sysdate(), null, null, '不计额度，仅统计时长');

-- ---------------------------------------------------------------- seed shifts (docs/04 12.1)

INSERT INTO oa_shift VALUES(1, '000000', 'FIXED', '固定班', '09:00:00', '18:00:00', '12:00:00', '13:00:00', 0, 0, 0, 120, 240, '0', null, null, sysdate(), null, null, '固定 09:00-18:00，午休 12:00-13:00');
INSERT INTO oa_shift VALUES(2, '000000', 'FLEXIBLE', '弹性班', '09:00:00', '18:00:00', '12:00:00', '13:00:00', 0, 30, 0, 120, 240, '0', null, null, sysdate(), null, null, '弹性 09:00±30min-18:00±30min');
INSERT INTO oa_shift VALUES(3, '000000', 'NIGHT', '跨夜班', '22:00:00', '06:00:00', null, null, 1, 0, 0, 120, 240, '0', null, null, sysdate(), null, null, '跨夜 22:00-次日 06:00，归属班次开始日期');

-- ---------------------------------------------------------------- menus and buttons (permission strings follow docs/05 section 5 scope rules)

INSERT INTO sys_menu VALUES(2200, '考勤管理', 0, 8, 'attendance', null, '', 1, 0, 'M', '0', '0', '', 'time', 100, 1, sysdate(), null, null, '考勤打卡目录');
INSERT INTO sys_menu VALUES(2201, '今日打卡', 2200, 1, 'punch', 'attendance/punch/index', '', 1, 0, 'C', '0', '0', '', 'form', 100, 1, sysdate(), null, null, '打卡与本人打卡记录');
INSERT INTO sys_menu VALUES(2202, '我的余额', 2200, 2, 'balance', 'attendance/balance/index', '', 1, 0, 'C', '0', '0', '', 'money', 100, 1, sysdate(), null, null, '假期余额与额度账本');
INSERT INTO sys_menu VALUES(2203, '考勤组', 2200, 3, 'group', 'attendance/group/index', '', 1, 0, 'C', '0', '0', 'at:group:list', 'peoples', 100, 1, sysdate(), null, null, '考勤组与成员管理');
INSERT INTO sys_menu VALUES(2204, '班次管理', 2200, 4, 'shift', 'attendance/shift/index', '', 1, 0, 'C', '0', '0', 'at:shift:list', 'time-range', 100, 1, sysdate(), null, null, '班次规则管理');
INSERT INTO sys_menu VALUES(2205, '工作日历', 2200, 5, 'calendar', 'attendance/calendar/index', '', 1, 0, 'C', '0', '0', 'at:calendar:list', 'date', 100, 1, sysdate(), null, null, '工作日历与节假日');
INSERT INTO sys_menu VALUES(2206, '考勤报表', 2200, 6, 'report', 'attendance/report/index', '', 1, 0, 'C', '0', '0', 'at:report:list', 'chart', 100, 1, sysdate(), null, null, '日报、月报与导出');
INSERT INTO sys_menu VALUES(2210, '考勤组查询', 2203, 1, '', '', '', 1, 0, 'F', '0', '0', 'at:group:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2211, '考勤组新增', 2203, 2, '', '', '', 1, 0, 'F', '0', '0', 'at:group:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2212, '考勤组修改', 2203, 3, '', '', '', 1, 0, 'F', '0', '0', 'at:group:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2213, '考勤组删除', 2203, 4, '', '', '', 1, 0, 'F', '0', '0', 'at:group:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2214, '班次查询', 2204, 1, '', '', '', 1, 0, 'F', '0', '0', 'at:shift:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2215, '班次新增', 2204, 2, '', '', '', 1, 0, 'F', '0', '0', 'at:shift:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2216, '班次修改', 2204, 3, '', '', '', 1, 0, 'F', '0', '0', 'at:shift:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2217, '班次删除', 2204, 4, '', '', '', 1, 0, 'F', '0', '0', 'at:shift:remove', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2218, '日历维护', 2205, 1, '', '', '', 1, 0, 'F', '0', '0', 'at:calendar:edit', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2219, '额度发放', 2202, 1, '', '', '', 1, 0, 'F', '0', '0', 'at:leave:grant', '#', 100, 1, sysdate(), null, null, '发放/调整假期额度');
INSERT INTO sys_menu VALUES(2220, '他人余额查询', 2202, 2, '', '', '', 1, 0, 'F', '0', '0', 'at:leave:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2221, '报表导出', 2206, 1, '', '', '', 1, 0, 'F', '0', '0', 'at:report:export', '#', 100, 1, sysdate(), null, null, '');

-- Role-menu matrix: HR maintains rules, managers read department reports, employees use self-service.
INSERT INTO sys_role_menu VALUES(20, 2200);
INSERT INTO sys_role_menu VALUES(20, 2201);
INSERT INTO sys_role_menu VALUES(20, 2202);
INSERT INTO sys_role_menu VALUES(20, 2203);
INSERT INTO sys_role_menu VALUES(20, 2204);
INSERT INTO sys_role_menu VALUES(20, 2205);
INSERT INTO sys_role_menu VALUES(20, 2206);
INSERT INTO sys_role_menu VALUES(20, 2210);
INSERT INTO sys_role_menu VALUES(20, 2211);
INSERT INTO sys_role_menu VALUES(20, 2212);
INSERT INTO sys_role_menu VALUES(20, 2213);
INSERT INTO sys_role_menu VALUES(20, 2214);
INSERT INTO sys_role_menu VALUES(20, 2215);
INSERT INTO sys_role_menu VALUES(20, 2216);
INSERT INTO sys_role_menu VALUES(20, 2217);
INSERT INTO sys_role_menu VALUES(20, 2218);
INSERT INTO sys_role_menu VALUES(20, 2219);
INSERT INTO sys_role_menu VALUES(20, 2220);
INSERT INTO sys_role_menu VALUES(20, 2221);
INSERT INTO sys_role_menu VALUES(21, 2200);
INSERT INTO sys_role_menu VALUES(21, 2201);
INSERT INTO sys_role_menu VALUES(21, 2202);
INSERT INTO sys_role_menu VALUES(21, 2206);
INSERT INTO sys_role_menu VALUES(21, 2220);
INSERT INTO sys_role_menu VALUES(21, 2221);
INSERT INTO sys_role_menu VALUES(22, 2200);
INSERT INTO sys_role_menu VALUES(22, 2201);
INSERT INTO sys_role_menu VALUES(22, 2202);

