package org.dromara.agentoa.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.OaExpenseItem;
import org.dromara.agentoa.finance.domain.OaExpenseType;
import org.dromara.agentoa.finance.domain.bo.ExpenseTypeBo;
import org.dromara.agentoa.finance.domain.vo.ExpenseTypeVo;
import org.dromara.agentoa.finance.mapper.OaExpenseItemMapper;
import org.dromara.agentoa.finance.mapper.OaExpenseTypeMapper;
import org.dromara.agentoa.finance.service.IExpenseTypeService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseTypeServiceImpl implements IExpenseTypeService {

    private final OaExpenseTypeMapper expenseTypeMapper;
    private final OaExpenseItemMapper expenseItemMapper;

    @Override
    public List<ExpenseTypeVo> tree() {
        List<OaExpenseType> all = expenseTypeMapper.selectList(new LambdaQueryWrapper<OaExpenseType>()
            .orderByAsc(OaExpenseType::getSort)
            .orderByAsc(OaExpenseType::getId));
        Map<Long, ExpenseTypeVo> nodes = new HashMap<>();
        for (OaExpenseType type : all) {
            nodes.put(type.getId(), toVo(type));
        }
        List<ExpenseTypeVo> roots = new ArrayList<>();
        for (OaExpenseType type : all) {
            ExpenseTypeVo node = nodes.get(type.getId());
            Long parentId = type.getParentId() == null ? 0L : type.getParentId();
            ExpenseTypeVo parent = nodes.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseTypeVo create(ExpenseTypeBo bo) {
        OaExpenseType type = new OaExpenseType();
        apply(type, bo);
        type.setParentId(bo.getParentId() == null ? 0L : bo.getParentId());
        type.setCreateTime(new Date());
        try {
            expenseTypeMapper.insert(type);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("FINANCE_EXPENSE_TYPE_DUPLICATE 费用类型编码已存在", 409);
        }
        return toVo(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseTypeVo update(Long id, ExpenseTypeBo bo) {
        OaExpenseType type = require(id);
        Long parentId = bo.getParentId() == null ? type.getParentId() : bo.getParentId();
        if (id.equals(parentId)) {
            throw new ServiceException("FINANCE_EXPENSE_TYPE_CYCLE 上级类型不能是自身", 400);
        }
        apply(type, bo);
        type.setParentId(parentId);
        type.setUpdateTime(new Date());
        try {
            expenseTypeMapper.updateById(type);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("FINANCE_EXPENSE_TYPE_DUPLICATE 费用类型编码已存在", 409);
        }
        return toVo(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OaExpenseType type = require(id);
        long children = expenseTypeMapper.selectCount(new LambdaQueryWrapper<OaExpenseType>()
            .eq(OaExpenseType::getParentId, type.getId()));
        if (children > 0) {
            throw new ServiceException("FINANCE_EXPENSE_TYPE_HAS_CHILDREN 存在子类型，禁止删除", 409);
        }
        long referenced = expenseItemMapper.selectCount(new LambdaQueryWrapper<OaExpenseItem>()
            .eq(OaExpenseItem::getExpenseTypeId, type.getId()));
        if (referenced > 0) {
            throw new ServiceException("FINANCE_EXPENSE_TYPE_REFERENCED 已被报销明细引用，禁止删除", 409);
        }
        expenseTypeMapper.deleteById(id);
    }

    private void apply(OaExpenseType type, ExpenseTypeBo bo) {
        type.setName(bo.getName().trim());
        type.setCode(bo.getCode().trim());
        type.setSort(bo.getSort() == null ? 0 : bo.getSort());
        type.setBudgetControl(bo.getBudgetControl() == null ? 0 : bo.getBudgetControl());
        type.setStatus(bo.getStatus() == null ? "0" : bo.getStatus());
        type.setRemark(bo.getRemark());
    }

    private OaExpenseType require(Long id) {
        OaExpenseType type = expenseTypeMapper.selectById(id);
        if (type == null) {
            throw new ServiceException("FINANCE_EXPENSE_TYPE_NOT_FOUND 费用类型不存在", 404);
        }
        return type;
    }

    private ExpenseTypeVo toVo(OaExpenseType type) {
        ExpenseTypeVo vo = new ExpenseTypeVo();
        vo.setId(type.getId());
        vo.setParentId(type.getParentId());
        vo.setName(type.getName());
        vo.setCode(type.getCode());
        vo.setSort(type.getSort());
        vo.setBudgetControl(type.getBudgetControl());
        vo.setStatus(type.getStatus());
        vo.setRemark(type.getRemark());
        return vo;
    }

    /** 保留排序工具方法便于扩展 */
    static void sortChildren(List<ExpenseTypeVo> nodes) {
        nodes.sort(Comparator.comparing(ExpenseTypeVo::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
