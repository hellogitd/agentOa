/**
 * 微信小程序构建前注入 appid（docs/23 H5-H1-03 小程序 appid 配置化）：
 * 读取环境变量 MP_WEIXIN_APPID（可选 MP_WEIXIN_VERSION_NAME / MP_WEIXIN_VERSION_CODE）写入 manifest.json；
 * 未设置时保持 manifest 现值（本地开发者工具可用测试号）。幂等，可重复执行。
 * 用法：node scripts/prepare-mp-weixin.mjs（package.json build:mp-weixin 前置）
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const manifestPath = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'manifest.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

const appid = (process.env.MP_WEIXIN_APPID || '').trim();
if (appid) {
  manifest['mp-weixin'] = manifest['mp-weixin'] || {};
  manifest['mp-weixin'].appid = appid;
  console.log(`mp-weixin appid injected: ${appid}`);
} else {
  console.log('MP_WEIXIN_APPID not set; keeping manifest appid:', manifest['mp-weixin'] && manifest['mp-weixin'].appid);
}

const versionName = (process.env.MP_WEIXIN_VERSION_NAME || '').trim();
const versionCode = (process.env.MP_WEIXIN_VERSION_CODE || '').trim();
if (versionName) manifest.versionName = versionName;
if (versionCode) manifest.versionCode = versionCode;

writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
