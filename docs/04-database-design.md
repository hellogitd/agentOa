# 04 · 数据库设计文档

> 版本：v1.1（DDL 设计草案，尚未在 MySQL 执行）
> 更新日期：2026-10-02
> 实施状态（2026-10-02）：M0 基础工程已落地，实际版本、命令、接口与验证见 [09 · 底座使用与验收](09-foundation.md)。本文尚未标注实现的业务能力仍为设计。
> 数据库：MySQL 8.x（utf8mb4 / InnoDB，具体受维护版本在 M0 锁定）
> 字符集：utf8mb4_0900_ai_ci
> 命名规范：小写下划线（snake_case），表前缀按模块区分

---

## 1. 全局约定

### 1.1 通用字段

以下为字段约定，实际是否包含以各表 DDL 为准。首版单组织部署，不实现多租户；底座 tenant_id 如存在应由服务器固定，禁止接受客户端租户切换。本文同时保留 P1 表草案，迁移按阶段交付，不代表所有表都在 P0 启用。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | 自研表主键示例使用自增；底座 ID 策略在 M0 对齐，不计划无收益的二次更换 |
| `create_by` | VARCHAR(64) | 创建人 |
| `create_time` | DATETIME | 创建时间 |
| `update_by` | VARCHAR(64) | 更新人 |
| `update_time` | DATETIME | 更新时间 |
| `del_flag` | TINYINT | 逻辑删除（0 正常，1 删除）|
| `remark` | VARCHAR(500) | 备注 |
| `lock_version` | INT | 乐观锁版本号；发布版本/文档版本单独定义 |

时间列保存 UTC，业务日期按 Asia/Shanghai 归日；金额使用 DECIMAL，API ID 和金额以字符串传输。用户 ID 与员工 ID 明确区分，直属上级为员工 ID，办理人/申请人为用户 ID。底座主键类型、字段名与逻辑删除值须在 M0 按真实 schema 对齐。

业务编码默认删除后不复用；需要复用时须单独定义活跃唯一键，不能简单加 del_flag 组成唯一键导致第二次删除冲突。账本、流程历史、付款和 outbox 不采用可任意删除的通用 CRUD。

### 1.2 表前缀

| 前缀 | 模块 |
|---|---|
| `sys_` | 系统管理（继承 RuoYi-Vue-Plus）|
| `hr_` | 组织人事 |
| `wf_` | 流程工作流 |
| `at_` | 考勤 |
| `fn_` | 财务报销 |
| `nc_` | 公告通知 |
| `kb_` | 知识库 |
| `cl_` | 日程/会议/任务 |
| `rp_` | 报表（视图/汇总表）|

### 1.3 索引规范

- 主键：`PRIMARY KEY (id)`
- 引用完整性：底座/Flowable 关联先按其约定；自研表是否使用外键在迁移评审中确定。无外键时必须实现父记录存在性、受限删除、事务和孤儿数据检查，不能只以性能为由省略
- 高频查询字段：必须建索引
- 唯一约束：手机号/身份证的 HMAC、发票身份、工号、账号关联等业务唯一键；邮箱是否唯一由组织政策确定
- 复合索引：遵循最左前缀原则

---

## 2. 系统管理（继承 RuoYi-Vue-Plus）

> 复用底座的 `sys_user`、`sys_role`、`sys_menu`、`sys_dept`、`sys_post`、`sys_dict_*`、`sys_config`、`sys_log_*` 等表，此处仅列出扩展。

### 2.1 sys_user（用户表，扩展字段）

不直接 ALTER 未知版本的底座表。先读取 sys_user/sys_dept/sys_post 的实际 schema，复用已有头像、登录时间、密码锁定等字段。员工与账号关系由 hr_employee.user_id 唯一维护，避免反向 employee_id 造成双写不一致。微信/钉钉身份绑定为后续独立关系表，需包含平台与应用标识，不能只保存一个 openid。

---

## 3. 组织人事（HR）

### 3.1 hr_employee（员工档案表）

```sql
CREATE TABLE hr_employee (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  employee_no         VARCHAR(32) NOT NULL COMMENT '工号',
  user_id             BIGINT UNSIGNED COMMENT '关联用户ID',
  name                VARCHAR(64) NOT NULL COMMENT '姓名',
  gender              TINYINT DEFAULT 0 COMMENT '性别(0未知 1男 2女)',
  birth_date          DATE COMMENT '出生日期',
  id_card_no          VARCHAR(255) COMMENT '身份证号(AES加密)',
  phone               VARCHAR(255) COMMENT '手机号(AES加密)',
  phone_hash          CHAR(64) CHARACTER SET ascii COLLATE ascii_bin COMMENT '规范化手机号HMAC精确索引',
  id_card_hash        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin COMMENT '规范化身份证HMAC唯一索引',
  key_version         VARCHAR(32) COMMENT '敏感字段加密密钥版本',
  email               VARCHAR(128) COMMENT '邮箱',
  address             VARCHAR(255) COMMENT '住址',
  emergency_contact   VARCHAR(64) COMMENT '紧急联系人',
  emergency_phone     VARCHAR(255) COMMENT '紧急联系电话(加密)',
  avatar              VARCHAR(500) COMMENT '头像URL',
  nation              VARCHAR(32) COMMENT '民族',
  political_status    VARCHAR(32) COMMENT '政治面貌',
  marital_status      VARCHAR(32) COMMENT '婚姻状况',
  education           VARCHAR(32) COMMENT '最高学历',
  school              VARCHAR(128) COMMENT '毕业院校',
  major               VARCHAR(128) COMMENT '专业',
  graduation_date     DATE COMMENT '毕业日期',
  dept_id             BIGINT UNSIGNED NOT NULL COMMENT '部门ID',
  post_id             BIGINT UNSIGNED COMMENT '岗位ID',
  position_level      VARCHAR(32) COMMENT '职级(如 P6/M2)',
  direct_leader_id    BIGINT UNSIGNED COMMENT '直属上级ID',
  employee_type       TINYINT DEFAULT 1 COMMENT '员工类型(1正式 2实习 3外包 4兼职)',
  entry_date          DATE COMMENT '入职日期',
  regular_date        DATE COMMENT '转正日期',
  contract_start_date DATE COMMENT '合同开始日期',
  contract_end_date   DATE COMMENT '合同结束日期',
  contract_type       VARCHAR(32) COMMENT '合同类型',
  status              TINYINT DEFAULT 1 COMMENT '状态(0待入职 1试用期 2正式 3离职中 4已离职)',
  leave_date          DATE COMMENT '离职日期',
  leave_reason        VARCHAR(255) COMMENT '离职原因',
  work_years          DECIMAL(4,1) COMMENT '司龄(年)',
  base_salary         VARCHAR(512) COMMENT '基本工资密文(含nonce/tag，P1)',
  bank_account        VARCHAR(255) COMMENT '银行卡号(加密)',
  bank_name           VARCHAR(128) COMMENT '开户行',
  create_by           VARCHAR(64) COMMENT '创建人',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(64) COMMENT '更新人',
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag            TINYINT DEFAULT 0 COMMENT '逻辑删除',
  remark              VARCHAR(500) COMMENT '备注',
  lock_version        INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (id),
  UNIQUE KEY uk_employee_no (employee_no),
  UNIQUE KEY uk_phone_hash (phone_hash),
  UNIQUE KEY uk_id_card_hash (id_card_hash),
  UNIQUE KEY uk_employee_user (user_id),
  KEY idx_dept_id (dept_id),
  KEY idx_direct_leader (direct_leader_id),
  KEY idx_status (status),
  KEY idx_entry_date (entry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工档案表';
```

### 3.2 hr_employee_education（教育经历）

```sql
CREATE TABLE hr_employee_education (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id     BIGINT UNSIGNED NOT NULL COMMENT '员工ID',
  school          VARCHAR(128) COMMENT '学校',
  major           VARCHAR(128) COMMENT '专业',
  education       VARCHAR(32) COMMENT '学历',
  degree          VARCHAR(32) COMMENT '学位',
  start_date      DATE COMMENT '开始日期',
  end_date        DATE COMMENT '结束日期',
  is_full_time    TINYINT DEFAULT 1 COMMENT '是否全日制',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教育经历';
```

### 3.3 hr_employee_work（工作经历）

```sql
CREATE TABLE hr_employee_work (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id     BIGINT UNSIGNED NOT NULL,
  company         VARCHAR(128) COMMENT '公司',
  position        VARCHAR(64) COMMENT '职位',
  start_date      DATE COMMENT '开始日期',
  end_date        DATE COMMENT '结束日期',
  leave_reason    VARCHAR(255) COMMENT '离职原因',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作经历';
```

### 3.4 hr_employee_change（异动历史）

```sql
CREATE TABLE hr_employee_change (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  source_request_id BIGINT UNSIGNED COMMENT '生命周期申请ID，一次生效',
  employee_id     BIGINT UNSIGNED NOT NULL COMMENT '员工ID',
  change_type     TINYINT NOT NULL COMMENT '异动类型(1入职 2转正 3调岗 4调薪 5晋升 6降级 7离职)',
  effective_date  DATE COMMENT '生效日期',
  old_dept_id     BIGINT UNSIGNED COMMENT '原部门',
  new_dept_id     BIGINT UNSIGNED COMMENT '新部门',
  old_post_id     BIGINT UNSIGNED COMMENT '原岗位',
  new_post_id     BIGINT UNSIGNED COMMENT '新岗位',
  old_salary      VARCHAR(512) COMMENT '原薪资密文(含密钥版本)',
  new_salary      VARCHAR(512) COMMENT '新薪资密文(含密钥版本)',
  reason          VARCHAR(255) COMMENT '原因',
  flow_instance_id BIGINT UNSIGNED COMMENT '关联流程实例ID',
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_employee_id (employee_id),
  KEY idx_change_type (change_type),
  UNIQUE KEY uk_change_source_request (source_request_id),
  KEY idx_effective_date (effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工异动历史';
```

### 3.5 hr_contract（合同表）

```sql
CREATE TABLE hr_contract (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id     BIGINT UNSIGNED NOT NULL,
  contract_no     VARCHAR(64) COMMENT '合同编号',
  contract_type   VARCHAR(32) COMMENT '合同类型(固定期限/无固定期限/以完成一定工作任务)',
  start_date      DATE NOT NULL,
  end_date        DATE,
  sign_date       DATE COMMENT '签订日期',
  renew_count     INT DEFAULT 0 COMMENT '续签次数',
  status          TINYINT DEFAULT 1 COMMENT '状态(1生效 2到期 3终止)',
  file_id         BIGINT UNSIGNED COMMENT '合同扫描件sys_file ID',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_employee_id (employee_id),
  KEY idx_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同表';
```

---

## 4. 流程工作流（WF）

> Flowable 自带表（ACT_*）此处不列出，仅列自定义扩展表。

### 4.1 wf_category（流程分类）

```sql
CREATE TABLE wf_category (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '分类名称',
  code            VARCHAR(32) NOT NULL COMMENT '分类编码',
  sort            INT DEFAULT 0 COMMENT '排序',
  icon            VARCHAR(64) COMMENT '图标',
  status          TINYINT DEFAULT 1 COMMENT '状态(1启用 0停用)',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程分类';
```

### 4.2 wf_process_definition（流程定义扩展表）

```sql
CREATE TABLE wf_process_definition (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  process_key         VARCHAR(64) NOT NULL COMMENT '流程Key',
  process_name        VARCHAR(64) NOT NULL COMMENT '流程名称',
  category_id         BIGINT UNSIGNED COMMENT '分类ID',
  flowable_proc_def_id VARCHAR(64) COMMENT 'Flowable 流程定义ID',
  flowable_proc_def_key VARCHAR(64) COMMENT 'Flowable 流程定义Key',
  version             INT NOT NULL COMMENT '发布版本号，不可变',
  form_id             BIGINT UNSIGNED NOT NULL COMMENT '绑定确切表单版本ID',
  form_key            VARCHAR(64) COMMENT '关联表单Key',
  form_schema         JSON COMMENT '表单 Schema(冗余)',
  icon                VARCHAR(64) COMMENT '图标',
  sort                INT DEFAULT 0,
  status              TINYINT DEFAULT 1 COMMENT '状态(1启用 0停用)',
  is_default          TINYINT DEFAULT 0 COMMENT '是否默认流程',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  remark              VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_key_version (process_key, version),
  KEY idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义扩展表';
```

### 4.3 wf_form（表单定义）

```sql
CREATE TABLE wf_form (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  form_key        VARCHAR(64) NOT NULL COMMENT '表单Key',
  form_name       VARCHAR(64) NOT NULL COMMENT '表单名称',
  form_schema     JSON NOT NULL COMMENT '表单 Schema(JSON)',
  version         INT NOT NULL DEFAULT 1 COMMENT '发布版本号',
  status          TINYINT DEFAULT 1 COMMENT '状态(1启用 0停用)',
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64),
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  remark          VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_key_version (form_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单定义';
```

### 4.4 wf_instance（流程实例扩展表）

```sql
CREATE TABLE wf_instance (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  definition_id       BIGINT UNSIGNED NOT NULL COMMENT '确切发布定义ID',
  form_id             BIGINT UNSIGNED NOT NULL COMMENT '确切表单版本ID',
  form_schema_snapshot JSON NOT NULL COMMENT '提交时表单结构快照',
  submission_no       INT NOT NULL DEFAULT 1 COMMENT '同一业务单据提交序号',
  lock_version        INT NOT NULL DEFAULT 0,
  process_key         VARCHAR(64) NOT NULL COMMENT '流程Key',
  process_name        VARCHAR(64) COMMENT '流程名称',
  flowable_proc_inst_id VARCHAR(64) COMMENT 'Flowable 流程实例ID',
  business_key        VARCHAR(64) COMMENT '业务Key(如请假单号)',
  business_type       VARCHAR(32) NOT NULL COMMENT '业务类型(leave/reimburse/...)',
  business_id         BIGINT UNSIGNED NOT NULL COMMENT '业务表ID',
  title               VARCHAR(255) COMMENT '流程标题(如"张三的请假申请")',
  form_key            VARCHAR(64) COMMENT '表单Key',
  form_data           JSON COMMENT '表单数据(JSON)',
  initiator_id        BIGINT UNSIGNED NOT NULL COMMENT '发起人ID',
  initiator_name      VARCHAR(64) COMMENT '发起人姓名',
  initiator_dept_id   BIGINT UNSIGNED COMMENT '发起人部门',
  status              TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1审批中 2已通过 3已拒绝 4已撤销 5已挂起 6已终止)',
  priority            TINYINT DEFAULT 0 COMMENT '优先级(0普通 1紧急 2特急)',
  start_time          DATETIME COMMENT '发起时间',
  end_time            DATETIME COMMENT '结束时间',
  duration            BIGINT COMMENT '耗时(毫秒)',
  current_task_name   VARCHAR(64) COMMENT '当前节点名称',
  current_assignees   VARCHAR(255) COMMENT '当前办理人(逗号分隔)',
  is_timeout          TINYINT DEFAULT 0 COMMENT '是否超时',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flowable_proc_inst_id (flowable_proc_inst_id),
  UNIQUE KEY uk_business_submission (business_type, business_id, submission_no),
  KEY idx_process_key (process_key),
  KEY idx_initiator (initiator_id),
  KEY idx_status (status),
  KEY idx_start_time (start_time),
  KEY idx_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例扩展表';
```

### 4.5 wf_task（任务扩展表）

仅为引擎任务查询投影，审批授权与流转必须重新读取引擎。候选人字段为展示快照，不能据此判断权限。转办/加签是操作历史，转办不把在途任务标为已结束。

```sql
CREATE TABLE wf_task (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lock_version        INT NOT NULL DEFAULT 0 COMMENT '接口并发提示；执行状态仍由引擎核验',
  instance_id         BIGINT UNSIGNED NOT NULL COMMENT '流程实例ID',
  flowable_task_id    VARCHAR(64) NOT NULL COMMENT 'Flowable TaskID',
  task_name           VARCHAR(64) NOT NULL COMMENT '任务名称',
  task_def_key        VARCHAR(64) COMMENT '任务定义Key',
  assignee_id         BIGINT UNSIGNED COMMENT '办理人ID',
  assignee_name       VARCHAR(64) COMMENT '办理人姓名',
  candidate_users     VARCHAR(255) COMMENT '候选人(逗号分隔)',
  candidate_groups    VARCHAR(255) COMMENT '候选角色(逗号分隔)',
  is_multiple         TINYINT DEFAULT 0 COMMENT '是否多实例(会签)',
  is_countersign      TINYINT DEFAULT 0 COMMENT '是否会签(0或签 1会签)',
  status              TINYINT DEFAULT 1 COMMENT '状态(1待处理 2已同意 3已拒绝 4已取消 5已挂起)',
  comment             TEXT COMMENT '审批意见',
  due_time            DATETIME COMMENT '到期时间',
  handle_time         DATETIME COMMENT '处理时间',
  duration            BIGINT COMMENT '停留时长(毫秒)',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flowable_task_id (flowable_task_id),
  KEY idx_instance_id (instance_id),
  KEY idx_assignee (assignee_id),
  KEY idx_status (status),
  KEY idx_due_time (due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务扩展表';
```

### 4.6 wf_task_history（任务历史）

```sql
CREATE TABLE wf_task_history (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instance_id         BIGINT UNSIGNED NOT NULL,
  flowable_task_id    VARCHAR(64) COMMENT 'Flowable TaskID',
  task_name           VARCHAR(64),
  task_def_key        VARCHAR(64),
  assignee_id         BIGINT UNSIGNED,
  assignee_name       VARCHAR(64),
  action              VARCHAR(32) COMMENT '动作(agree/reject/return/transfer/addsign/suspend)',
  comment             TEXT COMMENT '审批意见',
  variables           JSON COMMENT '流程变量',
  handle_time         DATETIME,
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_instance_id (instance_id),
  KEY idx_assignee (assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务历史';
```

### 4.7 wf_delegate（委托代理）

```sql
CREATE TABLE wf_delegate (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_id        BIGINT UNSIGNED NOT NULL COMMENT '委托人ID',
  delegate_id     BIGINT UNSIGNED NOT NULL COMMENT '受托人ID',
  start_date      DATE NOT NULL COMMENT '开始日期',
  end_date        DATE NOT NULL COMMENT '结束日期',
  process_keys    VARCHAR(255) COMMENT '适用流程(逗号分隔,空=全部)',
  status          TINYINT DEFAULT 1 COMMENT '状态(1启用 0停用)',
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_owner (owner_id),
  KEY idx_delegate (delegate_id),
  KEY idx_date (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委托代理';
```

### 4.8 流程通知

统一使用 nc_message；不建立 wf_message。流程事务写 sys_outbox，消费时按事件 ID 和接收人唯一写入站内信。见第 14 节。

---

## 5. 考勤（AT）

### 5.1 at_group（考勤组）

```sql
CREATE TABLE at_group (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '考勤组名称',
  scope_snapshot  JSON COMMENT '规则配置快照；实际成员按at_group_member查询',
  shift_id        BIGINT UNSIGNED NOT NULL COMMENT '班次ID',
  work_days       VARCHAR(32) DEFAULT '1,2,3,4,5' COMMENT '工作日(周一=1)',
  effective_date  DATE COMMENT '生效日期',
  status          TINYINT DEFAULT 1,
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤组';
```

### 5.2 at_shift（班次）

```sql
CREATE TABLE at_shift (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name                VARCHAR(64) NOT NULL COMMENT '班次名称',
  work_start_time     TIME NOT NULL COMMENT '上班时间',
  work_end_time       TIME NOT NULL COMMENT '下班时间',
  is_flexible         TINYINT DEFAULT 0 COMMENT '是否弹性',
  flexible_minutes    INT DEFAULT 0 COMMENT '弹性分钟',
  rest_start_time     TIME COMMENT '休息开始',
  rest_end_time       TIME COMMENT '休息结束',
  is_cross_day        TINYINT DEFAULT 0 COMMENT '是否跨夜',
  punch_window_start  INT DEFAULT 120 COMMENT '班次开始前允许记录分钟',
  punch_window_end    INT DEFAULT 240 COMMENT '班次结束后允许记录分钟',
  grace_minutes       INT NOT NULL DEFAULT 0 COMMENT '迟到早退宽限分钟，与记录窗口独立',
  work_hours          DECIMAL(4,2) COMMENT '工作时长(小时)',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次';
```

### 5.3 at_punch_record（打卡记录）

```sql
CREATE TABLE at_punch_record (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  correction_request_id BIGINT UNSIGNED COMMENT '仅审批补卡填入，防止重复生成',
  user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  punch_date      DATE NOT NULL COMMENT '打卡日期',
  punch_time      DATETIME NOT NULL COMMENT '打卡时间',
  punch_type      TINYINT NOT NULL COMMENT '类型(1上班 2下班 3外勤 4补卡)',
  is_late         TINYINT DEFAULT 0 COMMENT '是否迟到',
  late_minutes    INT DEFAULT 0 COMMENT '迟到分钟',
  is_early        TINYINT DEFAULT 0 COMMENT '是否早退',
  early_minutes   INT DEFAULT 0 COMMENT '早退分钟',
  lng             DECIMAL(10,6) COMMENT '经度',
  lat             DECIMAL(10,6) COMMENT '纬度',
  accuracy_meters  DECIMAL(10,2) COMMENT '客户端定位精度(非可信证明)',
  address         VARCHAR(255) COMMENT '详细地址',
  device          VARCHAR(64) COMMENT '设备(如 iPhone/Chrome)',
  ip              VARCHAR(50) COMMENT 'IP',
  wifi_name       VARCHAR(64) COMMENT 'WiFi 名称',
  photo_file_id   BIGINT UNSIGNED COMMENT '现场照片sys_file ID(P1)',
  source          TINYINT DEFAULT 1 COMMENT '来源(1PC 2App 3小程序)',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_date (user_id, punch_date),
  UNIQUE KEY uk_punch_correction (correction_request_id),
  KEY idx_punch_date (punch_date),
  KEY idx_punch_type (punch_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡记录';
```

### 5.4 at_leave_balance（假期额度）

```sql
CREATE TABLE at_leave_balance (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  year            INT NOT NULL COMMENT '年度',
  leave_type      VARCHAR(32) NOT NULL COMMENT '假期类型(annual/personal/sick/marriage/maternity/...)',
  total_minutes   INT NOT NULL DEFAULT 0 COMMENT '总额度(分钟)',
  frozen_minutes  INT NOT NULL DEFAULT 0 COMMENT '审批中冻结(分钟)',
  used_minutes    INT NOT NULL DEFAULT 0 COMMENT '已用(分钟)',
  lock_version    INT NOT NULL DEFAULT 0,
  expire_date     DATE COMMENT '年度额度统一到期日期；滚动批次为P1',
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_year_type (user_id, year, leave_type),
  CHECK (total_minutes >= 0 AND frozen_minutes >= 0 AND used_minutes >= 0),
  CHECK (total_minutes >= frozen_minutes + used_minutes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期额度';
```

### 5.5 at_attendance_daily（考勤日报）

```sql
CREATE TABLE at_attendance_daily (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id             BIGINT UNSIGNED NOT NULL,
  attendance_date     DATE NOT NULL COMMENT '考勤日期',
  first_punch_time    DATETIME COMMENT '首次打卡',
  last_punch_time     DATETIME COMMENT '末次打卡',
  work_status         TINYINT DEFAULT 0 COMMENT '主状态(0待结算 1出勤 2休息 3缺勤 4请假)',
  rule_version        INT NOT NULL DEFAULT 1 COMMENT '计算规则版本',
  missing_punch       TINYINT NOT NULL DEFAULT 0,
  scheduled_minutes   INT NOT NULL DEFAULT 0 COMMENT '计划工作分钟',
  worked_minutes      INT NOT NULL DEFAULT 0 COMMENT '实际出勤分钟',
  late_minutes        INT DEFAULT 0,
  early_minutes       INT DEFAULT 0,
  work_hours          DECIMAL(4,2) COMMENT '工作时长',
  overtime_hours      DECIMAL(4,2) COMMENT '加班时长',
  leave_type          VARCHAR(32) COMMENT '请假类型',
  leave_minutes       INT NOT NULL DEFAULT 0 COMMENT '请假分钟',
  is_abnormal         TINYINT DEFAULT 0 COMMENT '是否异常',
  abnormal_reason     VARCHAR(255) COMMENT '异常原因',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_date (user_id, attendance_date),
  KEY idx_date (attendance_date),
  KEY idx_abnormal (is_abnormal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤日报';
```

### 5.6 at_overtime（加班）

```sql
CREATE TABLE at_overtime (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id             BIGINT UNSIGNED NOT NULL,
  overtime_date       DATE NOT NULL,
  start_time          DATETIME,
  end_time            DATETIME,
  duration_minutes    INT NOT NULL COMMENT '核定加班分钟，小时为展示值',
  overtime_type       VARCHAR(32) COMMENT '加班类型(工作日/周末/节假日)',
  reason              VARCHAR(255),
  status              TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1草稿 2审批中 3已通过 4已拒绝 7已撤销)',
  lock_version        INT NOT NULL DEFAULT 0,
  flow_instance_id    BIGINT UNSIGNED COMMENT '流程实例ID',
  submission_no      INT NOT NULL DEFAULT 0,
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_date (user_id, overtime_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加班记录';
```

### 5.7 at_leave_batch / at_leave_batch_allocation（P1 批次二，V18__attendance_leave_batch）

AT-06 调休/年假批次滚动过期（交付包 E）：`oa_leave_balance` 汇总行不变（`uk_balance_user_year_type` 不动），批次为子账本，不变式 SUM(批次剩余) == 余额可用；消耗 FIFO 先过期先扣（expire_date ASC），冻结过期保护至结算，释放回原批次（过期批次释放归零并回收总额 + ADJUST 流水）。

```sql
at_leave_batch（假期额度批次）
  id              BIGINT PK
  user_id         BIGINT
  year            INT                    -- 归属额度年度（与 oa_leave_balance 对齐）
  leave_type      VARCHAR(32)            -- 批次管理假种：compensatory/annual
  batch_no        VARCHAR(64)            -- 批次号（展示）
  grant_minutes   INT                    -- 发放（回收时条件缩减）
  frozen_minutes  INT
  used_minutes    INT
  expired_minutes INT
  valid_from      DATE
  expire_date     DATE                   -- 当日有效，次日起过期
  event_key       VARCHAR(128) UNIQUE    -- 创建事件（GRANT:{token} 幂等）
  status          TINYINT                -- 1有效 2已过期 3已用尽
  KEY idx_batch_user_type (user_id, leave_type, expire_date)
  KEY idx_batch_due (status, expire_date)

at_leave_batch_allocation（批次消耗分配）
  id               BIGINT PK
  batch_id         BIGINT
  ledger_event_key VARCHAR(128)          -- 关联 oa_leave_ledger.event_key
  minutes          INT
  action           VARCHAR(16)           -- FREEZE/SETTLE/RELEASE/EXPIRE
  KEY idx_alloc_batch (batch_id)
  KEY idx_alloc_event (ledger_event_key)
```

发放建批次默认有效期：调休 +3 个月、年假当年 12-31（`BalanceGrantBo.validFrom/expireDate` 可调，截止日必须晚于起始日）。过期扫描写 EXPIRE 流水（`event_key=EXPIRE:{batchId}` 幂等，`total_delta` 负数 ADJUST）；历史余额迁移为单批次（valid_from=当年 1/1，expire_date=原值/当年 12-31）。`oa_leave_ledger` 仍是唯一流水源，批次仅经 allocation 关联。

---

## 6. 财务报销（FN）

### 6.1 fn_expense_type（费用类型）

```sql
CREATE TABLE fn_expense_type (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '类型名称',
  code            VARCHAR(32) NOT NULL COMMENT '类型编码',
  parent_id       BIGINT UNSIGNED DEFAULT 0 COMMENT '父类型',
  sort            INT DEFAULT 0,
  budget_control  TINYINT DEFAULT 0 COMMENT '是否受预算控制',
  status          TINYINT DEFAULT 1,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用类型';
```

### 6.2 fn_reimburse（报销单）

```sql
CREATE TABLE fn_reimburse (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reimburse_no        VARCHAR(32) NOT NULL COMMENT '报销单号',
  applicant_id        BIGINT UNSIGNED NOT NULL COMMENT '申请人ID',
  applicant_name      VARCHAR(64),
  dept_id             BIGINT UNSIGNED COMMENT '部门',
  reimburse_type      VARCHAR(32) COMMENT '报销类型',
  total_amount        DECIMAL(12,2) NOT NULL COMMENT '报销总额',
  currency            VARCHAR(8) DEFAULT 'CNY',
  pay_method          VARCHAR(32) COMMENT '支付方式(现金/转账/冲账)',
  budget_id           BIGINT UNSIGNED COMMENT '关联预算ID',
  status              TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1草稿 2审批中 3已通过 4已拒绝 5待付款 6已付款 7已撤销)',
  lock_version        INT NOT NULL DEFAULT 0,
  flow_instance_id    BIGINT UNSIGNED COMMENT '流程实例ID',
  submit_time         DATETIME COMMENT '提交时间',
  approve_time        DATETIME COMMENT '审批通过时间',
  pay_time            DATETIME COMMENT '付款时间',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  remark              VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE KEY uk_reimburse_no (reimburse_no),
  KEY idx_applicant (applicant_id),
  KEY idx_dept (dept_id),
  KEY idx_status (status),
  KEY idx_submit_time (submit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销单';
```

### 6.3 fn_reimburse_detail（报销明细）

```sql
CREATE TABLE fn_reimburse_detail (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reimburse_id    BIGINT UNSIGNED NOT NULL COMMENT '报销单ID',
  expense_type_id BIGINT UNSIGNED COMMENT '费用类型ID',
  occur_date      DATE COMMENT '发生日期',
  amount          DECIMAL(12,2) NOT NULL COMMENT '金额',
  invoice_id      BIGINT UNSIGNED COMMENT '关联fn_invoice，提交时必填',
  description     VARCHAR(255) COMMENT '说明',
  sort            INT DEFAULT 0,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reimburse_id (reimburse_id),
  UNIQUE KEY uk_reimburse_invoice (reimburse_id, invoice_id),
  CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报销明细';
```

### 6.4 fn_budget（预算）

```sql
CREATE TABLE fn_budget (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  budget_code         VARCHAR(32) NOT NULL COMMENT '预算编号',
  budget_name         VARCHAR(64) COMMENT '预算名称',
  budget_type         TINYINT NOT NULL COMMENT '类型(1部门 2项目)',
  owner_id            BIGINT UNSIGNED NOT NULL COMMENT '归属ID(部门ID/项目ID)',
  year                INT NOT NULL COMMENT '年度',
  quarter             INT COMMENT '季度(1-4,空=年度)',
  total_amount        DECIMAL(14,2) NOT NULL COMMENT '预算总额',
  used_amount         DECIMAL(14,2) DEFAULT 0 COMMENT '已用金额',
  frozen_amount       DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '冻结金额；可用=总额-已用-冻结',
  lock_version        INT NOT NULL DEFAULT 0,
  warn_threshold      INT DEFAULT 80 COMMENT '预警阈值(%)',
  status              TINYINT DEFAULT 1 COMMENT '状态(1执行 2关闭)',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_budget_code (budget_code),
  KEY idx_owner (owner_id),
  KEY idx_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算';
```

### 6.5 fn_payment（付款）

```sql
CREATE TABLE fn_payment (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  payment_no          VARCHAR(32) NOT NULL COMMENT '付款单号',
  reimburse_id        BIGINT UNSIGNED NOT NULL COMMENT '关联报销单',
  payee_name          VARCHAR(64) COMMENT '收款人',
  payee_bank          VARCHAR(128) COMMENT '开户行',
  payee_account       VARCHAR(255) COMMENT '收款账号(加密)',
  amount              DECIMAL(12,2) NOT NULL COMMENT '付款金额',
  pay_date            DATE COMMENT '付款日期',
  pay_status          TINYINT DEFAULT 1 COMMENT '状态(1待付 2已付 3失败)',
  payment_method      VARCHAR(32) COMMENT '付款方式(转账/现金)',
  voucher_no          VARCHAR(64) COMMENT '凭证号',
  operator_id         BIGINT UNSIGNED NOT NULL COMMENT '出纳用户ID',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_reimburse (reimburse_id),
  KEY idx_pay_status (pay_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款';
```

---

## 7. 公告通知（NC）

### 7.1 nc_notice（公告）

```sql
CREATE TABLE nc_notice (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title               VARCHAR(255) NOT NULL COMMENT '标题',
  content             LONGTEXT NOT NULL COMMENT '内容(富文本)',
  notice_type         VARCHAR(32) COMMENT '公告类型(公司/部门/人事/行政)',
  publisher_id        BIGINT UNSIGNED NOT NULL COMMENT '发布人',
  publish_time        DATETIME COMMENT '发布时间',
  effective_start     DATETIME COMMENT '生效开始',
  effective_end       DATETIME COMMENT '生效结束',
  scope_type          TINYINT DEFAULT 1 COMMENT '范围(1全员 2部门 3角色 4指定人)',
  scope_values        VARCHAR(2000) COMMENT '范围值(部门/角色/用户ID)',
  is_top              TINYINT DEFAULT 0 COMMENT '是否置顶',
  is_popup            TINYINT DEFAULT 0 COMMENT '是否弹窗',
  status              TINYINT DEFAULT 1 COMMENT '状态(1草稿 2已发布 3已撤回)',
  read_count          INT DEFAULT 0 COMMENT '已读人数',
  attachments         JSON COMMENT '附件列表',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_publish_time (publish_time),
  KEY idx_publisher (publisher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';
```

### 7.2 nc_notice_read（公告已读）

```sql
CREATE TABLE nc_notice_read (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  notice_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  read_time       DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notice_user (notice_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读记录';
```

### 7.3 nc_message（统一站内信）

```sql
CREATE TABLE nc_message (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id        VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT 'outbox事件ID',
  receiver_id     BIGINT UNSIGNED NOT NULL COMMENT '接收人',
  msg_type        VARCHAR(32) NOT NULL COMMENT '类型(TODO/NOTICE/MENTION/SYSTEM)',
  title           VARCHAR(255) NOT NULL,
  content         TEXT,
  biz_type        VARCHAR(32) COMMENT '业务类型',
  biz_id          BIGINT UNSIGNED COMMENT '业务ID',
  is_read         TINYINT DEFAULT 0,
  read_time       DATETIME,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_receiver_read (receiver_id, is_read),
  UNIQUE KEY uk_message_event_receiver (event_id, receiver_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信';
```

### 7.4 oa_notice_template / oa_scheduled_push / oa_scheduled_push_run（P1 批次二，V17__notice_p1）

NC-04 通知模板与定时推送（交付包 C）：模板为受控 `{var}` 占位符（`vars_json` 为声明白名单，禁表达式）；定时推送任务用 DB 租约调度（`status=1 AND next_run_time<=now` + `FOR UPDATE SKIP LOCKED`），执行记录 `event_key` 唯一去重。

```sql
oa_notice_template（通知模板）
  id            BIGINT PK
  template_code VARCHAR(64) UNIQUE      -- 模板编码
  name          VARCHAR(128)
  title_tpl     VARCHAR(255)            -- {var} 占位符标题
  content_tpl   TEXT                    -- {var} 占位符内容
  msg_type      VARCHAR(32)             -- TODO/NOTICE/SYSTEM
  vars_json     VARCHAR(2000)           -- 变量声明 JSON 数组
  status        TINYINT                 -- 1启用 2停用

oa_scheduled_push（定时推送任务）
  id              BIGINT PK
  name            VARCHAR(128)
  push_type       TINYINT               -- 1定时公告 2模板推送
  announcement_id BIGINT NULL           -- 类型1：草稿公告，到点调 publish
  template_id     BIGINT NULL           -- 类型2
  scope_type      TINYINT               -- 类型2受众（1全员 2部门 3角色 4指定人）
  scope_values    VARCHAR(2000)
  vars_json       VARCHAR(2000)         -- 类型2模板变量值 JSON 对象
  schedule_type   TINYINT               -- 1单次 2周期cron子集
  run_at          DATETIME NULL         -- 单次执行时间
  cron_expr       VARCHAR(64) NULL      -- 5字段受限子集
  next_run_time   DATETIME NULL         -- 下次执行（NULL=无待执行）
  last_run_time   DATETIME NULL
  run_count       INT DEFAULT 0         -- 累计成功次数
  last_error      VARCHAR(500)
  status          TINYINT               -- 1启用 2暂停
  KEY idx_scheduled_push_due (status, next_run_time)

oa_scheduled_push_run（执行记录）
  id             BIGINT PK
  push_id        BIGINT
  event_key      VARCHAR(64) UNIQUE     -- SCHED-{pushId}-{slot} 幂等键
  slot_time      DATETIME               -- 计划执行时间
  status         TINYINT                -- 1成功 2失败
  receiver_count INT                    -- 本次投递人数
  error          VARCHAR(500)
  KEY idx_push_run_push (push_id, slot_time)
```

内置模板种子：`onboarding_welcome`（入职欢迎）、`birthday_greeting`（生日祝福）、`attendance_anomaly`（考勤异常提醒）。菜单 2420–2429（`nt:template:*`/`nt:schedule:*`，HR 角色 20 授权）；字典 `nc_push_type`/`nc_schedule_type`/`nc_push_status`。

---

## 8. 知识库（KB）

### 8.1 kb_space（知识空间）

```sql
CREATE TABLE kb_space (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL COMMENT '空间名称',
  icon            VARCHAR(64) COMMENT '图标',
  description     VARCHAR(500) COMMENT '简介',
  space_type      TINYINT DEFAULT 1 COMMENT '类型(1公开 2私密 3团队)',
  owner_id        BIGINT UNSIGNED NOT NULL COMMENT '管理员',
  sort            INT DEFAULT 0,
  status          TINYINT DEFAULT 1,
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间';
```

### 8.2 kb_folder（目录/文件夹）

```sql
CREATE TABLE kb_folder (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  space_id        BIGINT UNSIGNED NOT NULL,
  parent_id       BIGINT UNSIGNED DEFAULT 0 COMMENT '父目录',
  name            VARCHAR(128) NOT NULL,
  folder_type     TINYINT DEFAULT 1 COMMENT '类型(1文件夹 2文档)',
  sort            INT DEFAULT 0,
  path            VARCHAR(500) COMMENT '路径(如 /1/2/3/)',
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_space (space_id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库目录';
```

### 8.3 kb_document（文档）

```sql
CREATE TABLE kb_document (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  deleted_at          DATETIME COMMENT '回收站起算时间',
  deleted_by          BIGINT UNSIGNED,
  space_id            BIGINT UNSIGNED NOT NULL,
  folder_id           BIGINT UNSIGNED COMMENT '所属目录',
  title               VARCHAR(255) NOT NULL COMMENT '标题',
  content             LONGTEXT COMMENT '内容(Markdown/HTML)',
  content_text        LONGTEXT COMMENT '纯文本(搜索用)',
  doc_type            VARCHAR(32) DEFAULT 'markdown' COMMENT '类型(markdown/rich)',
  version             INT DEFAULT 1 COMMENT '当前版本',
  tags                VARCHAR(255) COMMENT '标签(逗号分隔)',
  is_top              TINYINT DEFAULT 0,
  view_count          INT DEFAULT 0,
  like_count          INT DEFAULT 0,
  status              TINYINT DEFAULT 1 COMMENT '状态(1正常 2删除到回收站)',
  last_edit_by        BIGINT UNSIGNED,
  last_edit_time      DATETIME,
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_space (space_id),
  KEY idx_folder (folder_id),
  KEY idx_title (title),
  FULLTEXT KEY ft_content (title, content_text) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';
```

### 8.4 kb_document_version（文档版本）

```sql
CREATE TABLE kb_document_version (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  document_id     BIGINT UNSIGNED NOT NULL,
  version         INT NOT NULL,
  title           VARCHAR(255),
  content         LONGTEXT,
  change_summary  VARCHAR(255) COMMENT '变更摘要',
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_version (document_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本历史';
```

### 8.5 kb_file（文件柜）

```sql
CREATE TABLE kb_file (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  deleted_at      DATETIME COMMENT '回收站起算时间',
  deleted_by      BIGINT UNSIGNED,
  space_id        BIGINT UNSIGNED NOT NULL,
  folder_id       BIGINT UNSIGNED COMMENT '所属文件夹',
  file_name       VARCHAR(255) NOT NULL,
  file_ext        VARCHAR(32) COMMENT '扩展名',
  file_size       BIGINT COMMENT '大小(字节)',
  file_type       VARCHAR(32) COMMENT 'MIME 类型',
  file_id         BIGINT UNSIGNED NOT NULL COMMENT 'sys_file元数据ID，桶始终私有',
  download_count  INT DEFAULT 0,
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_space (space_id),
  KEY idx_folder (folder_id),
  KEY idx_file_name (file_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文件';
```

> 实现口径（V9，docs/16）：知识库实际表名为 `oa_knowledge_space`、`oa_knowledge_member`、`oa_document`、`oa_document_version`、`oa_document_revision`、`oa_document_acl`、`oa_document_file`，沿用 V4 起的 `oa_` 前缀；本节 `kb_*` 为设计草案。差异：目录并入 `oa_document.parent_id`（文档树，无 `kb_folder`）；个人草稿独立为 `oa_document_revision`（document_id+user_id 唯一，记录 base_version）；文档级授权独立为 `oa_document_acl`（USER/ROLE 主体）；`kb_file` 落为 `oa_document_file`（关联 `sys_file`，含下载审计与回收站字段）；`kb_document.status` 的"2删除到回收站"改由 `deleted_at/deleted_by` 表达，`status` 仅表示 1草稿 2已发布 3已归档。

---

## 9. 日程/会议/任务（CL）

### 9.1 cl_event（日程/会议）

```sql
CREATE TABLE cl_event (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lock_version        INT NOT NULL DEFAULT 0,
  title               VARCHAR(255) NOT NULL,
  description         TEXT,
  event_type          TINYINT DEFAULT 1 COMMENT '类型(1日程 2会议 3任务)',
  start_time          DATETIME NOT NULL,
  end_time            DATETIME NOT NULL,
  is_all_day          TINYINT DEFAULT 0,
  location            VARCHAR(255) COMMENT '地点',
  organizer_id        BIGINT UNSIGNED NOT NULL COMMENT '组织者',
  room_id             BIGINT UNSIGNED COMMENT '会议室ID',
  remind_minutes      INT DEFAULT 15 COMMENT '提醒时间(分钟)',
  repeat_rule         VARCHAR(64) COMMENT '重复规则(RRULE)',
  status              TINYINT DEFAULT 1 COMMENT '状态(1正常 2已取消)',
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_organizer (organizer_id),
  KEY idx_start_time (start_time),
  KEY idx_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程/会议';
```

### 9.2 cl_meeting_room（会议室）

```sql
CREATE TABLE cl_meeting_room (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name                VARCHAR(64) NOT NULL,
  location            VARCHAR(255) COMMENT '位置',
  capacity            INT COMMENT '容纳人数',
  equipment           VARCHAR(255) COMMENT '设备(投影/视频/白板)',
  status              TINYINT DEFAULT 1 COMMENT '状态(1可用 2维护中)',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室';
```

### 9.3 cl_room_booking（会议室预约）

```sql
CREATE TABLE cl_room_booking (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lock_version        INT NOT NULL DEFAULT 0,
  room_id             BIGINT UNSIGNED NOT NULL,
  event_id            BIGINT UNSIGNED NOT NULL COMMENT '关联日程',
  title               VARCHAR(255) NOT NULL,
  booker_id           BIGINT UNSIGNED NOT NULL COMMENT '预订人',
  start_time          DATETIME NOT NULL,
  end_time            DATETIME NOT NULL,
  checkin_time        DATETIME COMMENT '签到时间',
  status              TINYINT DEFAULT 1 COMMENT '状态(1预订 2已签到 3已取消 4已释放)',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_room_time (room_id, start_time, end_time),
  UNIQUE KEY uk_booking_event (event_id),
  KEY idx_booker (booker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室预约';
```

### 9.4 cl_task（任务）

```sql
CREATE TABLE cl_task (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  lock_version        INT NOT NULL DEFAULT 0,
  title               VARCHAR(255) NOT NULL,
  description         TEXT,
  parent_id           BIGINT UNSIGNED DEFAULT 0 COMMENT '父任务',
  project_id          BIGINT UNSIGNED COMMENT '项目ID',
  assigner_id         BIGINT UNSIGNED NOT NULL COMMENT '指派人',
  assignee_id         BIGINT UNSIGNED NOT NULL COMMENT '负责人',
  collaborator_ids    VARCHAR(500) COMMENT '协作人',
  priority            TINYINT DEFAULT 2 COMMENT '优先级(1P0 2P1 3P2 4P3)',
  status              TINYINT DEFAULT 1 COMMENT '状态(1待办 2进行中 3已完成 4已取消)',
  start_date          DATE,
  due_date            DATE COMMENT '截止日期',
  completed_time      DATETIME,
  progress            INT DEFAULT 0 COMMENT '进度(%)',
  tags                VARCHAR(255),
  attachments         JSON,
  create_by           VARCHAR(64),
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by           VARCHAR(64),
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_assignee (assignee_id),
  KEY idx_status (status),
  KEY idx_due_date (due_date),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务';
```

### 9.5 cl_task_comment（任务评论）

```sql
CREATE TABLE cl_task_comment (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id         BIGINT UNSIGNED NOT NULL,
  content         TEXT NOT NULL,
  mention_ids     VARCHAR(500) COMMENT '被@人',
  create_by       VARCHAR(64),
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  del_flag        TINYINT DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务评论';
```

---

## 10. 报表（RP）

> 报表以 SQL 视图 + 应用层缓存为主，仅在此列出必要的汇总表。

### 10.1 rp_flow_statistic（流程统计日汇总）

```sql
CREATE TABLE rp_flow_statistic (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stat_date           DATE NOT NULL,
  process_key         VARCHAR(64) NOT NULL,
  dept_id             BIGINT UNSIGNED NOT NULL COMMENT '实际部门；0表示未分配，不混存合计行',
  start_count         INT DEFAULT 0 COMMENT '发起数',
  finish_count        INT DEFAULT 0 COMMENT '完成数',
  reject_count        INT DEFAULT 0 COMMENT '拒绝数',
  avg_duration        BIGINT COMMENT '平均耗时(毫秒)',
  max_duration        BIGINT,
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_date_key_dept (stat_date, process_key, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程统计';
```

### 10.2 rp_attendance_statistic（考勤月汇总）

```sql
CREATE TABLE rp_attendance_statistic (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  year_month          VARCHAR(7) NOT NULL COMMENT '月份(yyyy-MM)',
  user_id             BIGINT UNSIGNED NOT NULL,
  dept_id             BIGINT UNSIGNED,
  attendance_days     INT COMMENT '出勤天数',
  late_count          INT COMMENT '迟到次数',
  early_count         INT COMMENT '早退次数',
  absent_count        INT COMMENT '旷工次数',
  leave_minutes       INT NOT NULL DEFAULT 0 COMMENT '请假分钟',
  overtime_hours      DECIMAL(6,2) COMMENT '加班时长',
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_month_user (year_month, user_id),
  KEY idx_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤月统计';
```

---

## 11. 视图示例

### 11.1 v_flow_pending（待办视图）

```sql
CREATE OR REPLACE VIEW v_flow_pending AS
SELECT
  t.id AS task_id,
  t.instance_id,
  i.process_name,
  i.title,
  i.initiator_id,
  i.initiator_name,
  t.task_name,
  t.assignee_id,
  t.due_time,
  i.priority,
  i.create_time AS start_time
FROM wf_task t
JOIN wf_instance i ON t.instance_id = i.id
WHERE t.status = 1
  AND i.del_flag = 0;
```

### 11.2 v_employee_summary（员工汇总视图）

底座字段名为待核实映射，以下采用常见 dept_id/dept_name、post_id/post_name；必须与选定 commit 的 schema 对齐后才可执行。视图不提供数据权限，也不向列表返回敏感密文。

```sql
CREATE OR REPLACE VIEW v_employee_summary AS
SELECT
  e.id,
  e.employee_no,
  e.name,
  e.email,
  d.dept_name AS dept_name,
  p.post_name AS post_name,
  e.position_level,
  e.employee_type,
  e.entry_date,
  e.status
FROM hr_employee e
LEFT JOIN sys_dept d ON e.dept_id = d.dept_id
LEFT JOIN sys_post p ON e.post_id = p.post_id
WHERE e.del_flag = 0;
```

---

## 12. 数据迁移与初始化

### 12.1 初始化数据

| 表 | 初始化内容 |
|---|---|
| `sys_user` | 生产管理员一次性初始化；演示账号仅用于隔离演示环境 |
| `sys_dept` | 总部 + 技术部 / 产品部 / 市场部 / 财务部 / 行政人事部 |
| `sys_post` | 总监 / 经理 / 主管 / 专员 / 实习生 |
| `sys_dict_*` | 性别、学历、状态、流程类型、请假类型等 |
| `wf_category` | 人事流程 / 财务流程 / 行政流程 / 其他 |
| `wf_form` | P0：请假、加班、补卡、报销、转正、离职；其余模板为 P1 |
| `fn_expense_type` | 差旅、招待、办公、交通、其他 |
| `at_shift` | 固定班、弹性班、跨夜班 |
| `cl_meeting_room` | 大会议室 A、小会议室 B、洽谈室 C |

### 12.2 数据字典

| 字典类型 | 值 |
|---|---|
| `sys_user_sex` | 男/女/未知 |
| `sys_normal_disable` | 启用/停用 |
| `hr_employee_status` | 待入职/试用期/正式/离职中/已离职 |
| `hr_employee_type` | 正式/实习/外包/兼职 |
| `wf_instance_status` | 1审批中/2已通过/3已拒绝/4已撤销/5已挂起(P1)/6已终止；超时另存标志 |
| `wf_task_action` | 同意/拒绝/退回/转办/加签/挂起 |
| `at_punch_type` | 上班/下班/外勤/补卡 |
| `at_leave_type` | 年假/调休/病假/事假/婚假/产假/陪产假/丧假 |
| `fn_reimburse_status` | 1草稿/2审批中/3已通过/4已拒绝/5待付款/6已付款/7已撤销 |
| `cl_task_status` | 待办/进行中/已完成/已取消 |
| `cl_task_priority` | P0/P1/P2/P3 |

---

## 13. 归档与容量评估

| 表 | 分表维度 | 策略 |
|---|---|---|
| `wf_instance` | 年 | `wf_instance_2026`, `wf_instance_2027` |
| `wf_task_history` | 年 | 同上 |
| `at_punch_record` | 月 | `at_punch_record_202610` |
| `nc_message` | 月 | 老数据归档 |

上表仅为容量超过实测阈值后的候选拆分，不承诺二期实施。先评估索引、归档与留存策略；流程引擎表不能按业务表方案直接拆分。分表前重新验证跨期查询、唯一键、事务与恢复。

---

## 14. P0 闭环补充模型

本节补齐原稿缺少的核心实体。下列 DDL 仍需在 M0 选定的 MySQL 与底座 schema 上执行迁移验证；无外键示例不豁免引用完整性校验。

### 14.1 请假单与额度流水

```sql
CREATE TABLE at_leave_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  leave_type VARCHAR(32) NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  duration_minutes INT NOT NULL,
  reason VARCHAR(500),
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1草稿 2审批中 3已通过 4已拒绝 7已撤销',
  flow_instance_id BIGINT UNSIGNED,
  submission_no INT NOT NULL DEFAULT 0,
  lock_version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_leave_user_time (user_id, start_time, end_time),
  CHECK (end_time > start_time AND duration_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假业务单据';

CREATE TABLE at_leave_ledger (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  balance_id BIGINT UNSIGNED NOT NULL,
  event_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  business_type VARCHAR(32) NOT NULL,
  business_id BIGINT UNSIGNED NOT NULL,
  submission_no INT NOT NULL DEFAULT 0,
  action VARCHAR(16) NOT NULL COMMENT 'GRANT/FREEZE/SETTLE/RELEASE/ADJUST',
  total_delta INT NOT NULL DEFAULT 0,
  frozen_delta INT NOT NULL DEFAULT 0,
  used_delta INT NOT NULL DEFAULT 0,
  operator_id BIGINT UNSIGNED NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_leave_event (balance_id, event_key),
  KEY idx_leave_ledger_business (business_type, business_id, submission_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变额度流水';

CREATE TABLE at_correction_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  attendance_date DATE NOT NULL,
  punch_type TINYINT NOT NULL COMMENT '1上班 2下班',
  corrected_time DATETIME NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1草稿 2审批中 3通过 4拒绝 7撤销',
  flow_instance_id BIGINT UNSIGNED,
  lock_version INT NOT NULL DEFAULT 0,
  submission_no INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_correction_user_date (user_id, attendance_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补卡申请';

CREATE TABLE at_group_member (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  group_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_member_start (user_id, valid_from),
  KEY idx_group_members (group_id, user_id),
  CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤组成员生效区间';

CREATE TABLE at_work_calendar (
  work_date DATE NOT NULL,
  is_workday TINYINT NOT NULL,
  description VARCHAR(128),
  rule_version INT NOT NULL DEFAULT 1,
  PRIMARY KEY (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织工作日历与调休';
```

额度更新与流水插入在同一事务中，以余额行锁或条件更新保证 `total >= frozen + used`。冻结动作的 frozen_delta 为正，通过结算将同量 frozen 转入 used，拒绝释放 frozen。跨年申请按工作日归属拆分到各年度余额，并以相同业务事件加 balance_id 去重。不计额度的假种只保存时长，不写受限余额表。

同一员工请假重叠、考勤组有效区间重叠、每月补卡上限都需锁定员工记录后检查，所有写入路径遵循同一锁顺序。补卡通过不覆盖原始打卡，以申请 ID 关联修正记录并重算日报；批准事件唯一去重。P0 调休以年度统一到期管理，按批次滚动过期为 P1，实施时新增额度批次与消耗分配表。

人事转正/离职需要独立申请状态，不能直接通过员工状态更新接口跳过审批：

```sql
CREATE TABLE hr_lifecycle_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id BIGINT UNSIGNED NOT NULL,
  request_type VARCHAR(16) NOT NULL COMMENT 'REGULARIZE/OFFBOARD',
  effective_date DATE NOT NULL,
  form_data JSON NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1草稿 2审批中 3通过 4拒绝 7撤销',
  flow_instance_id BIGINT UNSIGNED,
  submission_no INT NOT NULL DEFAULT 0,
  lock_version INT NOT NULL DEFAULT 0,
  create_by BIGINT UNSIGNED NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_lifecycle_employee (employee_id, request_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转正和离职申请';
```

审批通过后按 effective_date 生效并写 hr_employee_change，未来生效事项由可重试调度处理，以申请 ID 唯一去重。员工冻结/组织关系调整和历史记录必须一致；调度不得重复创建异动。员工导入只导入允许的初始状态，不能覆盖在途生命周期。

### 14.2 发票占用

```sql
CREATE TABLE fn_invoice (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  invoice_type VARCHAR(32) NOT NULL,
  invoice_code VARCHAR(32) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
  invoice_no VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  file_id BIGINT UNSIGNED NOT NULL,
  owner_user_id BIGINT UNSIGNED NOT NULL,
  occupied_reimburse_id BIGINT UNSIGNED,
  lock_version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoice_identity (invoice_type, invoice_code, invoice_no),
  KEY idx_invoice_occupation (occupied_reimburse_id),
  CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票与当前报销占用';
```

发票身份字段先规范化再写入；提交报销在同一事务内锁定按 ID 排序的发票，校验归属、明细金额、未占用并占用。拒绝/撤销释放占用，已付款永久保留关联。P0 一张发票对应一条报销明细，不支持拆票、跨单分摊与部分付款。`fn_payment.reimburse_id` 唯一限制一次全额付款，状态更新必须校验报销批准和金额，不允许任意 CRUD 改成已付。

### 14.3 空间、公告与日程授权关系

```sql
CREATE TABLE kb_space_member (
  space_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  member_role VARCHAR(16) NOT NULL COMMENT 'ADMIN/EDITOR/VIEWER',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (space_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间角色';

CREATE TABLE nc_notice_recipient (
  notice_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (notice_id, user_id),
  KEY idx_notice_recipient_user (user_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告发布时受众快照';

CREATE TABLE cl_event_attendee (
  event_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  response_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id, user_id),
  KEY idx_attendee_user (user_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程参与人和邀请状态';

CREATE TABLE kb_document_draft (
  document_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  base_version INT NOT NULL,
  content LONGTEXT,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (document_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人自动保存草稿';
```

空间所有者是管理员，公开空间给已登录员工查看权限、私密空间仅所有者、团队空间按成员表授权；P0 全部文件夹继承空间权限。移除成员立即影响详情、搜索、历史和下载。公告发布时物化接收人，后续入职者不自动进入已发布公告；撤回后原接收人也不可见，重新发布生成新公告 ID。统计的分母为接收人快照人数，已读表唯一键保证不重复计数。

会议与预约保持一一对应，参与人只存 cl_event_attendee；创建/修改时锁定会议室并检查重叠，取消时同事务更新日程与预约。共享日程订阅、文件夹权限覆盖、重复实例例外与任务协作者细粒度权限属于 P1，未启用前不要用逗号分隔字段授权。

### 14.4 文件、outbox 与请求幂等

```sql
CREATE TABLE sys_file (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  bucket VARCHAR(64) NOT NULL,
  object_key VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_user_id BIGINT UNSIGNED NOT NULL,
  business_type VARCHAR(32),
  business_id BIGINT UNSIGNED,
  status VARCHAR(16) NOT NULL DEFAULT 'STAGED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object (bucket, object_key),
  KEY idx_file_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私有附件元数据';

CREATE TABLE sys_outbox (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  next_attempt_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_until DATETIME,
  last_error VARCHAR(500),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_time DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event (event_id),
  KEY idx_outbox_dispatch (status, next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务事件与可恢复投递';

CREATE TABLE sys_idempotency (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  operation VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  request_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(16) NOT NULL,
  response_status INT,
  response_body JSON,
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency (user_id, operation, request_key),
  KEY idx_idempotency_expiry (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请求幂等结果';
```

若底座已有同名/同职责文件表或幂等组件，M0 先评估复用，映射到此契约，禁止重复建冲突表。上传先存 STAGED 文件，业务绑定时检查上传者及文件状态；未绑定临时文件按期限清理，已绑定附件只经业务授权访问。

outbox 使用 PENDING/PROCESSING/DONE/FAILED，消费者以锁租约抢占；进程崩溃后可回收过期 PROCESSING。消息写入与 outbox 完成标记放同一事务，推送在提交后进行，重连从消息表补拉。消费错误退避重试，超过阈值保留 FAILED 供修复重放。

幂等操作采用唯一键协调并发，请求摘要冲突返回 409；业务结果与成功幂等记录同事务提交。幂等结果至少保存 24h，清理后仍由业务唯一键阻止重复付款/重复提交流程；不得只依赖 Redis 短锁。响应快照不得保存密码或令牌。

## 15. 迁移前检查与后续模型

必须验证全部 SQL 在目标 MySQL 上可执行，底座字段、BIGINT 有符号性、索引长度、CHECK 执行与 ngram 可用性一致。迁移按版本执行，空库只初始化一次；生产升级不依赖 docker-entrypoint-initdb.d 再次运行。

本稿不宣称覆盖全部 P1/P2 模型：合同证照细目、流程抄送/代理规则、预算流水及项目实体、分批假期到期、目录 ACL、评论收藏、任务依赖、重复日程例外需在相应阶段另行补充。已保留的 P1 字段不能绕过 P0 范围直接暴露给客户端。P0 回收站使用 deleted_at/deleted_by，删除时同时更新状态与 del_flag，恢复反向更新；M3 必须验证 30 天保留、恢复和无引用附件清理，不能直接删除仍被其他业务引用的对象文件。

## 16. AI 能力模型（oa_ai_*，已实施）

AI 模型见 [21 · AI 能力需求与分期计划](21-module-plan-ai.md)，业务表前缀 `oa_ai_`，迁移自 V28 起按阶段独立执行（V28 foundation / V29 chat / V30 rag / V31 copilot / V32 agent），字段级 DDL 已定稿于各阶段迁移（V28 foundation / V29 chat / V30 rag / V31 copilot / V32 agent）。摘要：

| 阶段 | 表 | 职责 |
|---|---|---|
| M1 | `oa_ai_provider` | 模型渠道：厂商类型、base_url、api_key_cipher（AES-256-GCM）、api_key_hint、secret_ref、优先级、启用 |
| M1 | `oa_ai_model` | 模型注册：model_key、能力（chat/vision/embedding/rerank）、上下文窗口、默认参数、默认模型 |
| M1 | `oa_ai_usage_log` | 调用日志：用户、渠道、模型、biz_type、prompt/completion tokens、耗时、状态、错误 |
| M1 | `oa_ai_quota` | 配额：scope_type（user/role）、period_type（day/month）、token/请求限额；唯一约束（scope_type, scope_id, period_type） |
| M2 | `oa_ai_conversation`、`oa_ai_message`、`oa_ai_prompt_template` | 会话、消息（含附件 json、tokens、状态机）、提示词模板 |
| M3 | `oa_ai_kb`、`oa_ai_kb_source`、`oa_ai_kb_chunk` | 知识域、数据源（索引状态）、分块与 embedding（BLOB，预留 store_type 切换向量库） |
| M4 | `oa_ai_copilot_config`、`oa_ai_copilot_task` | 场景开关/模型/模板、异步生成任务 |
| M5 | `oa_ai_tool`、`oa_ai_agent`、`oa_ai_agent_run` | 工具（function/mcp）、Agent 配置、运行轨迹 |

审计字段与软删除口径同既有 `oa_*` 表；`api_key_cipher` 与 embedding 属敏感/二进制列，禁止放入 VARCHAR/DECIMAL，也禁止出现在导出与响应快照。

**相关文档**：
- [05-api-spec.md](05-api-spec.md) · API 接口规范
- [03-architecture.md](03-architecture.md) · 技术架构
