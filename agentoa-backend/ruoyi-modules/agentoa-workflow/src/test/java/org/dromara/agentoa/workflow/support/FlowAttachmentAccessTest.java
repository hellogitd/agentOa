package org.dromara.agentoa.workflow.support;

import org.dromara.agentoa.workflow.domain.bo.FormSchemaBo;
import org.dromara.agentoa.workflow.service.support.FlowAttachmentAccess;
import org.dromara.agentoa.workflow.service.support.FormSchemaSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 流程表单附件引用判定与附件字段类型白名单（docs/23 H5-H3-01）。
 */
class FlowAttachmentAccessTest {

    @Test
    void matchesStringFileIdInTopLevelField() {
        String form = "{\"reason\":\"补卡\",\"attachment\":\"345678901234567890\"}";
        assertThat(FlowAttachmentAccess.referencedInForm(345678901234567890L, form)).isTrue();
    }

    @Test
    void matchesNumericFileId() {
        String form = "{\"fileId\":345678901234567890}";
        assertThat(FlowAttachmentAccess.referencedInForm(345678901234567890L, form)).isTrue();
    }

    @Test
    void matchesFileIdNestedInListRows() {
        String form = "{\"details\":[{\"amount\":\"12.00\",\"attachments\":[\"345678901234567890\"]}]}";
        assertThat(FlowAttachmentAccess.referencedInForm(345678901234567890L, form)).isTrue();
    }

    @Test
    void rejectsUnreferencedFileId() {
        String form = "{\"attachment\":\"345678901234567890\"}";
        assertThat(FlowAttachmentAccess.referencedInForm(999L, form)).isFalse();
    }

    @Test
    void rejectsIdThatIsOnlyASubstringOfAStoredValue() {
        String form = "{\"attachment\":\"345678901234567890\"}";
        assertThat(FlowAttachmentAccess.referencedInForm(45678901234567890L, form)).isFalse();
        assertThat(FlowAttachmentAccess.referencedInForm(34567890123456789L, form)).isFalse();
    }

    @Test
    void rejectsBlankOrUnparsableFormData() {
        assertThat(FlowAttachmentAccess.referencedInForm(1L, null)).isFalse();
        assertThat(FlowAttachmentAccess.referencedInForm(1L, "")).isFalse();
        assertThat(FlowAttachmentAccess.referencedInForm(1L, "{not json")).isFalse();
    }

    @Test
    void acceptsFileAndImageFieldTypes() {
        FormSchemaBo.FormFieldBo image = new FormSchemaBo.FormFieldBo();
        image.setKey("photo");
        image.setLabel("现场照片");
        image.setType("image");
        FormSchemaBo.FormFieldBo file = new FormSchemaBo.FormFieldBo();
        file.setKey("attachment");
        file.setLabel("附件");
        file.setType("file");
        FormSchemaBo schema = new FormSchemaBo();
        schema.getFields().add(image);
        schema.getFields().add(file);
        assertThatCode(() -> FormSchemaSupport.validateAndSerialize(schema)).doesNotThrowAnyException();
    }
}
