-- AgentOA attendance P1: field punch evidence columns and shift assignments (AT-03/AT-09).
-- API contract: docs/05-api-spec.md section 5.

-- Field punch keeps GPS/photo as risk signals, never as trusted evidence (docs/03 3.3).
ALTER TABLE oa_punch_record ADD COLUMN photo_file_id BIGINT DEFAULT NULL COMMENT '现场照片sys_file ID';
ALTER TABLE oa_punch_record ADD COLUMN wifi_name VARCHAR(64) DEFAULT NULL COMMENT 'WiFi 名称(风险信号)';

-- Shift assignment overrides the group shift for a specific person and day (AT-03 排班管理).
CREATE TABLE oa_shift_assignment (
  id            BIGINT       NOT NULL                   COMMENT '排班ID',
  tenant_id     VARCHAR(20)  NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  user_id       BIGINT       NOT NULL                   COMMENT '账号ID',
  employee_id   BIGINT       DEFAULT NULL               COMMENT '员工档案ID',
  shift_id      BIGINT       NOT NULL                   COMMENT '班次ID',
  work_date     DATE         NOT NULL                   COMMENT '排班日期',
  source        TINYINT      NOT NULL DEFAULT 1         COMMENT '来源(1手工 2轮班)',
  create_dept   BIGINT       DEFAULT NULL               COMMENT '创建部门',
  create_by     BIGINT       DEFAULT NULL               COMMENT '创建者',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     BIGINT       DEFAULT NULL               COMMENT '更新者',
  update_time   DATETIME     DEFAULT NULL               COMMENT '更新时间',
  remark        VARCHAR(500) DEFAULT NULL               COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_shift_assignment (user_id, work_date),
  KEY idx_shift_assignment_date (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工排班指派';

-- Menus (ids 2815+ sit inside the attendance 2200 block but keep clear of 2222+ and 2800+).
INSERT INTO sys_menu VALUES(2815, '排班管理', 2200, 7, 'schedule', 'attendance/schedule/index', '', 1, 0, 'C', '0', '0', 'at:schedule:list', 'time', 100, 1, sysdate(), null, null, '按人按日指定班次');
INSERT INTO sys_menu VALUES(2816, '排班查询', 2815, 1, '', '', '', 1, 0, 'F', '0', '0', 'at:schedule:query', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2817, '排班新增', 2815, 2, '', '', '', 1, 0, 'F', '0', '0', 'at:schedule:add', '#', 100, 1, sysdate(), null, null, '');
INSERT INTO sys_menu VALUES(2818, '排班删除', 2815, 3, '', '', '', 1, 0, 'F', '0', '0', 'at:schedule:remove', '#', 100, 1, sysdate(), null, null, '');

INSERT INTO sys_role_menu VALUES(20, 2815);
INSERT INTO sys_role_menu VALUES(20, 2816);
INSERT INTO sys_role_menu VALUES(20, 2817);
INSERT INTO sys_role_menu VALUES(20, 2818);
INSERT INTO sys_role_menu VALUES(21, 2815);
INSERT INTO sys_role_menu VALUES(21, 2816);
