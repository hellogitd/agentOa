package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.agentoa.hr.domain.OaEmployee;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeVo;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 员工档案数据层。
 */
public interface OaEmployeeMapper extends BaseMapperPlus<OaEmployee, OaEmployeeVo> {

    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    default Page<OaEmployeeVo> selectPageEmployeeList(Page<OaEmployee> page, Wrapper<OaEmployee> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "user_id")
    })
    default List<OaEmployeeVo> selectEmployeeList(Wrapper<OaEmployee> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 当日最大工号，用于工号生成。
     */
    @Select("SELECT MAX(employee_no) FROM oa_employee WHERE employee_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxEmployeeNo(@Param("prefix") String prefix);
}
