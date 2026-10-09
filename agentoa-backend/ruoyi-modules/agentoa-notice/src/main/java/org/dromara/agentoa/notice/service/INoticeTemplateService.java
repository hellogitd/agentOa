package org.dromara.agentoa.notice.service;

import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.domain.bo.NoticeTemplateBo;
import org.dromara.agentoa.notice.domain.bo.TemplateSendBo;
import org.dromara.agentoa.notice.domain.vo.NoticeTemplateVo;

/**
 * 通知模板（NC-04）：受控 {var} 占位符模板 CRUD 与按受众发送。
 */
public interface INoticeTemplateService {

    PageVo<NoticeTemplateVo> list(NoticePageQuery query);

    NoticeTemplateVo detail(Long id);

    NoticeTemplateVo create(NoticeTemplateBo bo, Long operatorUserId);

    NoticeTemplateVo update(Long id, NoticeTemplateBo bo, Long operatorUserId);

    void delete(Long id);

    /** 按模板编码渲染并发送（受众 + 变量），返回投递人数 */
    int send(String code, TemplateSendBo bo, Long operatorUserId);
}
