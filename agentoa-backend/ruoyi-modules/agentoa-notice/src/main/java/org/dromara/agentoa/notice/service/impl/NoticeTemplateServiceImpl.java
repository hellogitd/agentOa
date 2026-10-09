package org.dromara.agentoa.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.OaNoticeTemplate;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.bo.NoticeTemplateBo;
import org.dromara.agentoa.notice.domain.bo.TemplateSendBo;
import org.dromara.agentoa.notice.domain.vo.NoticeTemplateVo;
import org.dromara.agentoa.notice.mapper.NoticePreferenceMapper;
import org.dromara.agentoa.notice.mapper.OaNoticeTemplateMapper;
import org.dromara.agentoa.notice.service.INoticeTemplateService;
import org.dromara.agentoa.notice.service.support.NoticeAudienceResolver;
import org.dromara.agentoa.notice.service.support.NoticeOutboxWriter;
import org.dromara.agentoa.notice.service.support.NoticeTemplateRenderer;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通知模板实现（NC-04）：占位符声明校验 + 白名单渲染 + 按受众发送（outbox 同事务）。
 */
@Service
@RequiredArgsConstructor
public class NoticeTemplateServiceImpl implements INoticeTemplateService {

    private final OaNoticeTemplateMapper templateMapper;
    private final NoticeAudienceResolver audienceResolver;
    private final NoticeOutboxWriter outboxWriter;
    private final NoticePreferenceMapper preferenceMapper;

    @Override
    public PageVo<NoticeTemplateVo> list(NoticePageQuery query) {
        LambdaQueryWrapper<OaNoticeTemplate> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaNoticeTemplate::getName, query.getKeyword().trim());
        }
        wrapper.orderByAsc(OaNoticeTemplate::getId);
        IPage<OaNoticeTemplate> result = templateMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        return PageVo.of(result.getRecords().stream().map(this::toVo).toList(),
            result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public NoticeTemplateVo detail(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoticeTemplateVo create(NoticeTemplateBo bo, Long operatorUserId) {
        long existing = templateMapper.selectCount(new LambdaQueryWrapper<OaNoticeTemplate>()
            .eq(OaNoticeTemplate::getTemplateCode, bo.getTemplateCode()));
        if (existing > 0) {
            throw new ServiceException("NT_TPL_CODE_DUPLICATE 模板编码已存在", 409);
        }
        OaNoticeTemplate template = new OaNoticeTemplate();
        apply(template, bo);
        template.setCreateBy(operatorUserId);
        template.setCreateTime(LocalDateTime.now());
        try {
            templateMapper.insert(template);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("NT_TPL_CODE_DUPLICATE 模板编码已存在", 409);
        }
        return toVo(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoticeTemplateVo update(Long id, NoticeTemplateBo bo, Long operatorUserId) {
        OaNoticeTemplate template = require(id);
        if (!template.getTemplateCode().equals(bo.getTemplateCode())) {
            long existing = templateMapper.selectCount(new LambdaQueryWrapper<OaNoticeTemplate>()
                .eq(OaNoticeTemplate::getTemplateCode, bo.getTemplateCode()));
            if (existing > 0) {
                throw new ServiceException("NT_TPL_CODE_DUPLICATE 模板编码已存在", 409);
            }
        }
        apply(template, bo);
        template.setUpdateBy(operatorUserId);
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
        return toVo(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int send(String code, TemplateSendBo bo, Long operatorUserId) {
        OaNoticeTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<OaNoticeTemplate>()
            .eq(OaNoticeTemplate::getTemplateCode, code));
        if (template == null) {
            throw new ServiceException("NT_TPL_NOT_FOUND 模板不存在", 404);
        }
        if (template.getStatus() != OaNoticeTemplate.STATUS_ENABLED) {
            throw new ServiceException("NT_TPL_DISABLED 模板已停用", 409);
        }
        Set<String> declared = NoticeTemplateRenderer.checkDeclared(
            template.getTitleTpl(), template.getContentTpl(), parseVars(template.getVarsJson()));
        Map<String, String> values = bo.getVars() == null ? Map.of() : bo.getVars();
        String title = NoticeTemplateRenderer.render(template.getTitleTpl(), declared, values);
        String content = NoticeTemplateRenderer.render(template.getContentTpl(), declared, values);
        int scopeType = Integer.parseInt(bo.getScopeType());
        List<Long> audience = audienceResolver.resolve(scopeType, bo.getScopeValues());
        if (audience.isEmpty()) {
            throw new ServiceException("NT_AUDIENCE_EMPTY 发送受众为空", 400);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        int delivered = 0;
        for (Long userId : audience) {
            if (!pushEnabled(userId, template.getMsgType())) {
                continue;
            }
            outboxWriter.write("TPL-" + template.getTemplateCode() + "-" + token + "-" + userId, userId,
                template.getMsgType(), title, content, "template", template.getId(), "/notice/message");
            delivered++;
        }
        return delivered;
    }

    // ------------------------------------------------------------ internal

    private void apply(OaNoticeTemplate template, NoticeTemplateBo bo) {
        Set<String> declared = NoticeTemplateRenderer.checkDeclared(
            bo.getTitleTpl(), bo.getContentTpl(), bo.getVars());
        template.setTemplateCode(bo.getTemplateCode().trim());
        template.setName(bo.getName().trim());
        template.setTitleTpl(bo.getTitleTpl());
        template.setContentTpl(bo.getContentTpl());
        template.setMsgType(bo.getMsgType());
        template.setVarsJson(JsonUtils.toJsonString(List.copyOf(declared)));
        template.setStatus(Integer.valueOf(bo.getStatus()));
        template.setRemark(bo.getRemark());
    }

    /** 未配置偏好默认提醒 */
    boolean pushEnabled(Long userId, String msgType) {
        Integer enabled = preferenceMapper.selectEnabled(userId, msgType);
        return enabled == null || enabled != 0;
    }

    static List<String> parseVars(String varsJson) {
        if (varsJson == null || varsJson.isBlank()) {
            return List.of();
        }
        List<String> vars = JsonUtils.parseArray(varsJson, String.class);
        return vars == null ? List.of() : vars;
    }

    private OaNoticeTemplate require(Long id) {
        OaNoticeTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new ServiceException("NT_TPL_NOT_FOUND 模板不存在", 404);
        }
        return template;
    }

    private NoticeTemplateVo toVo(OaNoticeTemplate template) {
        NoticeTemplateVo vo = new NoticeTemplateVo();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setName(template.getName());
        vo.setTitleTpl(template.getTitleTpl());
        vo.setContentTpl(template.getContentTpl());
        vo.setMsgType(template.getMsgType());
        vo.setVars(parseVars(template.getVarsJson()));
        vo.setStatus(template.getStatus());
        vo.setRemark(template.getRemark());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }
}
