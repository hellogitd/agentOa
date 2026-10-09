-- Notice NC-04 权限种子缺口修复：NoticeScheduleController 的 list/detail/runs 要求 nt:schedule:list，
-- 但 V17 只种了 add/edit/remove/run，导致除超管外无人能读定时推送与执行记录（smoke 里 HR 读 runs 403）。
-- 补一个查询按钮并授予 HR（role 20，与 V17 的模板/推送管理口径一致）。

INSERT INTO sys_menu VALUES(2930, '推送查询', 2421, 5, '', '', '', 1, 0, 'F', '0', '0', 'nt:schedule:list', '#', 100, 1, sysdate(), null, null, '查询定时推送与执行记录');

INSERT INTO sys_role_menu VALUES(20, 2930);
