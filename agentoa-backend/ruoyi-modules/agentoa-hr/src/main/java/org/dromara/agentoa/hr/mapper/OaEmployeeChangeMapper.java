package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.agentoa.hr.domain.OaEmployeeChange;
import org.dromara.agentoa.hr.domain.vo.OaChangeVo;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 员工异动数据层。
 */
public interface OaEmployeeChangeMapper extends BaseMapperPlus<OaEmployeeChange, OaChangeVo> {

    @DataPermission({
        @DataColumn(key = "deptName", value = "old_dept_id")
    })
    default Page<OaChangeVo> selectPageChangeList(Page<OaEmployeeChange> page, Wrapper<OaEmployeeChange> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    @DataPermission({
        @DataColumn(key = "deptName", value = "old_dept_id")
    })
    default List<OaChangeVo> selectChangeList(Wrapper<OaEmployeeChange> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }
}
