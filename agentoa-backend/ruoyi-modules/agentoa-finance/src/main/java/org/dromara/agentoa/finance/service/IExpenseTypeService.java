package org.dromara.agentoa.finance.service;

import org.dromara.agentoa.finance.domain.bo.ExpenseTypeBo;
import org.dromara.agentoa.finance.domain.vo.ExpenseTypeVo;

import java.util.List;

/**
 * 费用类型（docs/05 6.2）：树形查询；编码唯一；删除前检查子节点与报销明细引用。
 */
public interface IExpenseTypeService {

    List<ExpenseTypeVo> tree();

    ExpenseTypeVo create(ExpenseTypeBo bo);

    ExpenseTypeVo update(Long id, ExpenseTypeBo bo);

    void delete(Long id);
}
