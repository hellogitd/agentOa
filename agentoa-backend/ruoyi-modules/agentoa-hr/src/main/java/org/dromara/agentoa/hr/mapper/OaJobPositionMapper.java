package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.agentoa.hr.domain.OaJobPosition;
import org.dromara.agentoa.hr.domain.vo.OaJobPositionVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 业务岗位数据层。
 */
public interface OaJobPositionMapper extends BaseMapperPlus<OaJobPosition, OaJobPositionVo> {

    default Page<OaJobPositionVo> selectPagePositionList(Page<OaJobPosition> page, Wrapper<OaJobPosition> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    default List<OaJobPositionVo> selectPositionList(Wrapper<OaJobPosition> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }
}
