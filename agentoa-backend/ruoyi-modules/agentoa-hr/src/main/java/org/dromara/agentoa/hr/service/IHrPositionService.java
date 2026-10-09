package org.dromara.agentoa.hr.service;

import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaJobPositionBo;
import org.dromara.agentoa.hr.domain.vo.OaJobPositionVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;
import java.util.Map;

/**
 * 业务岗位服务（API 规范 3.2）。
 */
public interface IHrPositionService {

    PageVo<OaJobPositionVo> selectPagePositions(OaJobPositionBo query, HrPageQuery page);

    List<OaJobPositionVo> selectPositions(OaJobPositionBo query);

    OaJobPositionVo selectPositionById(Long id);

    boolean checkCodeUnique(OaJobPositionBo bo);

    OaJobPositionVo insertPosition(OaJobPositionBo bo);

    OaJobPositionVo updatePosition(Long id, OaJobPositionBo bo);

    /** 删除前检查员工引用 */
    void deletePosition(Long id);

    /** 岗位编码到 ID 的映射（导入解析用） */
    Map<String, Long> selectPositionIdByCode();
}
