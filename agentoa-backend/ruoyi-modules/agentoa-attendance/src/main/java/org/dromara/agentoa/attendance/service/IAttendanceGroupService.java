package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.AttendanceGroupBo;
import org.dromara.agentoa.attendance.domain.bo.AttendanceMemberBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.vo.AttendanceGroupVo;
import org.dromara.agentoa.attendance.domain.vo.AttendanceMemberVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 考勤组服务（API 规范 5.2 / docs/13）。
 */
public interface IAttendanceGroupService {

    PageVo<AttendanceGroupVo> selectPageGroups(AttendanceGroupBo query, AttendancePageQuery page);

    List<AttendanceGroupVo> selectGroups();

    AttendanceGroupVo selectGroupById(Long id);

    AttendanceGroupVo insertGroup(AttendanceGroupBo bo);

    AttendanceGroupVo updateGroup(Long id, AttendanceGroupBo bo);

    /** 删除前检查成员引用 */
    void deleteGroup(Long id);

    PageVo<AttendanceMemberVo> selectMembers(Long groupId, AttendancePageQuery page);

    AttendanceMemberVo addMember(Long groupId, AttendanceMemberBo bo);

    void removeMember(Long groupId, Long memberId);
}
