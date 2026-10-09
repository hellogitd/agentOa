<template>
  <main class="login-page">
    <el-card class="login-card">
      <h1>AgentOA</h1>
      <p>协同办公管理平台</p>
      <el-form v-if="!mustChange" @submit.prevent="submit">
        <el-form-item label="账号"><el-input ref="usernameInput" v-model="form.username" autocomplete="username" aria-label="账号" /></el-form-item>
        <el-form-item label="密码"
          ><el-input v-model="form.password" type="password" show-password autocomplete="current-password" aria-label="密码"
        /></el-form-item>
        <el-form-item v-if="captchaEnabled" label="验证码">
          <el-input v-model="form.code" class="code-input" autocomplete="off" aria-label="验证码" />
          <button type="button" class="captcha-refresh" aria-label="看不清？点击刷新验证码" @click="loadCode">
            <img :src="codeUrl" alt="验证码图片" />
          </button>
        </el-form-item>
        <el-checkbox v-model="remember">记住账号</el-checkbox>
        <el-button type="primary" native-type="submit" :loading="busy">登录</el-button>
      </el-form>
      <el-form v-else @submit.prevent="changePassword">
        <el-alert title="首次登录，请设置新密码后重新登录。" :closable="false" />
        <el-form-item label="新密码"
          ><el-input v-model="nextPassword" type="password" show-password autocomplete="new-password" aria-label="新密码"
        /></el-form-item>
        <el-form-item label="确认密码"
          ><el-input v-model="confirmPassword" type="password" autocomplete="new-password" aria-label="确认密码"
        /></el-form-item>
        <p>至少 12 个字符，最多 72 字节，且与初始密码不同。</p>
        <el-button type="primary" native-type="submit" :loading="busy">保存并重新登录</el-button>
      </el-form>
      <p class="hint">会话仅保存在当前页面，刷新后需重新登录。</p>
    </el-card>
  </main>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getCodeImg } from '@/api/login';
import { useUserStore } from '@/store/modules/user';
import { removeToken } from '@/utils/auth';
import request from '@/utils/request';
import type { LoginData } from '@/api/types';
const store = useUserStore();
const router = useRouter();
const route = useRoute();
const usernameInput = ref<{ focus: () => void } | null>(null);
const form = ref<LoginData>({
  clientId: import.meta.env.VITE_APP_CLIENT_ID,
  grantType: 'password',
  tenantId: '000000',
  username: localStorage.getItem('username') || '',
  password: '',
  code: '',
  uuid: ''
});
const remember = ref(!!localStorage.getItem('username'));
const busy = ref(false);
const mustChange = ref(false);
const nextPassword = ref('');
const confirmPassword = ref('');
const captchaEnabled = ref(true);
const codeUrl = ref('');
async function loadCode() {
  const { data } = await getCodeImg();
  captchaEnabled.value = data.captchaEnabled !== false;
  form.value.uuid = data.uuid;
  form.value.code = '';
  codeUrl.value = data.img ? `data:image/gif;base64,${data.img}` : '';
}
async function submit() {
  if (!form.value.username || !form.value.password) return ElMessage.warning('请输入账号和密码');
  busy.value = true;
  try {
    await store.login(form.value);
    if (remember.value) localStorage.setItem('username', form.value.username);
    else localStorage.removeItem('username');
    const { data } = await request.get('/api/v1/auth/profile');
    mustChange.value = data.mustChangePassword;
    if (!mustChange.value) {
      form.value.password = '';
      const target = typeof route.query.redirect === 'string' ? decodeURIComponent(route.query.redirect) : '/';
      await router.replace(target.startsWith('/') && !target.startsWith('//') ? target : '/');
    }
  } catch {
    await loadCode();
  } finally {
    busy.value = false;
  }
}
async function changePassword() {
  if (nextPassword.value.length < 12 || new TextEncoder().encode(nextPassword.value).length > 72 || nextPassword.value !== confirmPassword.value) {
    return ElMessage.warning('密码长度不符合要求或两次输入不一致');
  }
  busy.value = true;
  try {
    await request.put(
      '/api/v1/auth/password',
      { oldPassword: form.value.password, newPassword: nextPassword.value },
      { headers: { repeatSubmit: false } }
    );
    removeToken();
    store.token = '';
    mustChange.value = false;
    form.value.password = '';
    nextPassword.value = '';
    confirmPassword.value = '';
    ElMessage.success('密码已更新，请重新登录');
    await loadCode();
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  await loadCode();
  // 键盘可达：进入页面直接聚焦首个输入框
  usernameInput.value?.focus();
});
</script>
<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #e7f0ff, #f8fafc);
  padding: 24px;
}
.login-card {
  width: min(440px, 100%);
  padding: 24px;
}
h1 {
  color: #1d4ed8;
  margin: 0;
}
p {
  color: #64748b;
  margin-bottom: 28px;
}
.hint {
  font-size: 12px;
  margin: 24px 0 0;
}
.el-button {
  width: 100%;
  margin-top: 18px;
}
.code-input {
  width: 55%;
}
.captcha-refresh {
  width: 40%;
  margin-left: 5%;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  line-height: 0;
}
.captcha-refresh:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.captcha-refresh img {
  width: 100%;
  display: block;
}
.el-alert {
  margin-bottom: 24px;
}
</style>
