<template>
  <div>
    <el-upload
      v-if="type === 'url'"
      :action="upload.url"
      :before-upload="handleBeforeUpload"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      class="editor-img-uploader"
      name="file"
      :show-file-list="false"
      :headers="upload.headers"
    >
      <i ref="uploadRef"></i>
    </el-upload>
  </div>
  <div class="editor">
    <quill-editor
      ref="quillEditorRef"
      v-model:content="content"
      content-type="html"
      :options="options"
      :style="styles"
      @ready="onEditorReady"
      @text-change="(e: any) => $emit('update:modelValue', content)"
    />
  </div>
</template>

<script setup lang="ts">
import '@vueup/vue-quill/dist/vue-quill.snow.css';

import { QuillEditor, Quill } from '@vueup/vue-quill';
import { propTypes } from '@/utils/propTypes';
import request, { globalHeaders } from '@/utils/request';
import { safeUrl } from '@/utils/markdown';
import { sanitizeRichHtml } from '@/utils/sanitize';
import { checkEditorImage } from '@/utils/upload';

defineEmits(['update:modelValue']);

const props = defineProps({
  /* 编辑器的内容 */
  modelValue: propTypes.string,
  /* 高度 */
  height: propTypes.number.def(400),
  /* 最小高度 */
  minHeight: propTypes.number.def(400),
  /* 只读 */
  readOnly: propTypes.bool.def(false),
  /* 上传文件大小限制(MB) */
  fileSize: propTypes.number.def(5),
  /* 类型（base64格式、url格式） */
  type: propTypes.string.def('url')
});

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const upload = reactive<UploadOption>({
  headers: globalHeaders(),
  url: import.meta.env.VITE_APP_BASE_API + '/resource/oss/upload'
});
const quillEditorRef = ref();
const uploadRef = ref<HTMLDivElement>();

const options = ref<any>({
  theme: 'snow',
  bounds: document.body,
  debug: 'warn',
  modules: {
    // 工具栏配置
    toolbar: {
      container: [
        ['bold', 'italic', 'underline', 'strike'], // 加粗 斜体 下划线 删除线
        ['blockquote', 'code-block'], // 引用  代码块
        [{ list: 'ordered' }, { list: 'bullet' }], // 有序、无序列表
        [{ indent: '-1' }, { indent: '+1' }], // 缩进
        [{ size: ['small', false, 'large', 'huge'] }], // 字体大小
        [{ header: [1, 2, 3, 4, 5, 6, false] }], // 标题
        [{ color: [] }, { background: [] }], // 字体颜色、字体背景颜色
        [{ align: [] }], // 对齐方式
        ['clean'], // 清除文本格式
        ['link', 'image', 'video'] // 链接、图片、视频
      ],
      handlers: {
        image: (value: boolean) => {
          if (value) {
            // 调用element图片上传
            uploadRef.value.click();
          } else {
            Quill.format('image', true);
          }
        }
      }
    }
  },
  placeholder: '请输入内容',
  readOnly: props.readOnly
});

const styles = computed(() => {
  const style: any = {};
  if (props.minHeight) {
    style.minHeight = `${props.minHeight}px`;
  }
  if (props.height) {
    style.height = `${props.height}px`;
  }
  return style;
});

const content = ref('');
watch(
  () => props.modelValue,
  (v: string) => {
    if (v !== content.value) {
      content.value = v || '<p></p>';
    }
  },
  { immediate: true }
);

// 图片上传成功返回图片地址
const handleUploadSuccess = (res: any) => {
  // 如果上传成功
  if (res.code === 200 && res.data?.url) {
    insertImage(res.data.url);
  } else {
    proxy?.$modal.msgError('图片插入失败');
  }
  proxy?.$modal.closeLoading();
};

/** 插入图片（URL 协议白名单校验后落编辑器） */
const insertImage = (url: string) => {
  const quill = toRaw(quillEditorRef.value)?.getQuill();
  const safe = safeUrl(url, { allowDataImage: false });
  if (!quill || !safe) {
    proxy?.$modal.msgError('图片插入失败');
    return;
  }
  const range = quill.getSelection(true);
  const index = range ? range.index : quill.getLength();
  quill.insertEmbed(index, 'image', safe);
  quill.setSelection(index + 1);
};

// 图片上传前拦截（docs/22 F5-03：MIME + 大小，与聊天附件口径一致）
const handleBeforeUpload = (file: any) => {
  const check = checkEditorImage(file, props.fileSize);
  if (!check.ok) {
    proxy?.$modal.msgError(check.reason);
    return false;
  }
  proxy?.$modal.loading('正在上传文件，请稍候...');
  return true;
};

// 图片失败拦截
const handleUploadError = (err: any) => {
  proxy?.$modal.msgError('上传文件失败');
};

/** 粘贴/外部图片统一走 sys_file 上传通道（禁 data: 内联） */
const uploadImageFile = async (file: File) => {
  const check = checkEditorImage(file, props.fileSize);
  if (!check.ok) {
    proxy?.$modal.msgError(check.reason);
    return;
  }
  const form = new FormData();
  form.append('file', file);
  proxy?.$modal.loading('正在上传文件，请稍候...');
  try {
    const res: any = await request({
      url: '/resource/oss/upload',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    if (res.code === 200 && res.data?.url) {
      insertImage(res.data.url);
    } else {
      proxy?.$modal.msgError('图片插入失败');
    }
  } catch (error: any) {
    proxy?.$modal.msgError(error?.msg || '上传文件失败');
  } finally {
    proxy?.$modal.closeLoading();
  }
};

/**
 * 粘贴拦截（docs/22 F5-03 最小安全加固）：
 * - 图片文件（截图等）转 sys_file 上传后按 URL 插入，禁 data: 内联；
 * - 富文本 HTML 先经标签/属性白名单 + safeUrl 清洗再入编辑器。
 */
const handlePaste = (event: ClipboardEvent) => {
  const files = Array.from(event.clipboardData?.files ?? []).filter((file: File) => (file.type ?? '').startsWith('image/'));
  if (files.length > 0) {
    event.preventDefault();
    files.forEach((file) => uploadImageFile(file));
    return;
  }
  const html = event.clipboardData?.getData('text/html');
  if (!html) {
    return;
  }
  const quill = toRaw(quillEditorRef.value)?.getQuill();
  if (!quill) {
    return;
  }
  event.preventDefault();
  const clean = sanitizeRichHtml(html);
  if (!clean) {
    return;
  }
  const range = quill.getSelection(true);
  quill.clipboard.dangerouslyPasteHTML(range ? range.index : quill.getLength(), clean);
};

const onEditorReady = (quill: any) => {
  quill?.root?.addEventListener('paste', handlePaste);
};

onBeforeUnmount(() => {
  toRaw(quillEditorRef.value)?.getQuill()?.root?.removeEventListener('paste', handlePaste);
});
</script>

<style>
.editor-img-uploader {
  display: none;
}
.editor,
.ql-toolbar {
  white-space: pre-wrap !important;
  line-height: normal !important;
}
.quill-img {
  display: none;
}
.ql-snow .ql-tooltip[data-mode='link']::before {
  content: '请输入链接地址:';
}
.ql-snow .ql-tooltip.ql-editing a.ql-action::after {
  border-right: 0;
  content: '保存';
  padding-right: 0;
}
.ql-snow .ql-tooltip[data-mode='video']::before {
  content: '请输入视频地址:';
}
.ql-snow .ql-picker.ql-size .ql-picker-label::before,
.ql-snow .ql-picker.ql-size .ql-picker-item::before {
  content: '14px';
}
.ql-snow .ql-picker.ql-size .ql-picker-label[data-value='small']::before,
.ql-snow .ql-picker.ql-size .ql-picker-item[data-value='small']::before {
  content: '10px';
}
.ql-snow .ql-picker.ql-size .ql-picker-label[data-value='large']::before,
.ql-snow .ql-picker.ql-size .ql-picker-item[data-value='large']::before {
  content: '18px';
}
.ql-snow .ql-picker.ql-size .ql-picker-label[data-value='huge']::before,
.ql-snow .ql-picker.ql-size .ql-picker-item[data-value='huge']::before {
  content: '32px';
}
.ql-snow .ql-picker.ql-header .ql-picker-label::before,
.ql-snow .ql-picker.ql-header .ql-picker-item::before {
  content: '文本';
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value='1']::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value='1']::before {
  content: '标题1';
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value='2']::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value='2']::before {
  content: '标题2';
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value='3']::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value='3']::before {
  content: '标题3';
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value='4']::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value='4']::before {
  content: '标题4';
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value='5']::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value='5']::before {
  content: '标题5';
}
.ql-snow .ql-picker.ql-header .ql-picker-label[data-value='6']::before,
.ql-snow .ql-picker.ql-header .ql-picker-item[data-value='6']::before {
  content: '标题6';
}
.ql-snow .ql-picker.ql-font .ql-picker-label::before,
.ql-snow .ql-picker.ql-font .ql-picker-item::before {
  content: '标准字体';
}
.ql-snow .ql-picker.ql-font .ql-picker-label[data-value='serif']::before,
.ql-snow .ql-picker.ql-font .ql-picker-item[data-value='serif']::before {
  content: '衬线字体';
}
.ql-snow .ql-picker.ql-font .ql-picker-label[data-value='monospace']::before,
.ql-snow .ql-picker.ql-font .ql-picker-item[data-value='monospace']::before {
  content: '等宽字体';
}
</style>
