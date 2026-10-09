package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.agentoa.hr.domain.OaContract;
import org.dromara.agentoa.hr.domain.vo.OaContractVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDate;
import java.util.List;

/**
 * 合同数据层。表内无部门列，数据范围由服务层按员工档案鉴权（对齐财务模块做法）。
 */
public interface OaContractMapper extends BaseMapperPlus<OaContract, OaContractVo> {

    default Page<OaContractVo> selectPageContractList(Page<OaContract> page, Wrapper<OaContract> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    default List<OaContractVo> selectContractList(Wrapper<OaContract> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /** 到期区间内的生效合同（续签提醒） */
    default List<OaContract> selectExpiringBetween(LocalDate from, LocalDate to) {
        return this.selectList(new LambdaQueryWrapper<OaContract>()
            .eq(OaContract::getStatus, OaContract.STATUS_ACTIVE)
            .isNotNull(OaContract::getEndDate)
            .ge(OaContract::getEndDate, from)
            .le(OaContract::getEndDate, to)
            .orderByAsc(OaContract::getEndDate));
    }
}
