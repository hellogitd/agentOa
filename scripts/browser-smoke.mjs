import {chromium} from '../.cache/browser/node_modules/playwright/index.mjs';
import {readFileSync,existsSync,mkdirSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:'msedge',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1000}});
const page=await context.newPage();
const failures=[];
page.on('pageerror',e=>failures.push(e.message));
// C1 断网注入（docs/22 FE-F3-03）：CDP setOffline 不切断已建立的 WebSocket，
// 以 routeWebSocket 代理接管实时通道，测试时可控切断（模拟半开/断网），重连路由可继续放行
let cutActive=false;
let cutRealtime=null;
await page.routeWebSocket(/\/ws(\?|$)/,(ws)=>{
  if(cutActive){ws.close();return;}
  const server=ws.connectToServer();
  server.onClose(()=>{try{ws.close();}catch{}} );
  ws.onClose(()=>{try{server.close();}catch{}} );
  cutRealtime=()=>{try{ws.close();}catch{}} ;
});
// C3: axe-core 无障碍扫描（docs/22 F4-04 自动化初筛，告警不拦门禁）
const axeUrl=new URL('../agentoa-frontend/node_modules/axe-core/axe.min.js',import.meta.url);
const axeSource=existsSync(axeUrl)?readFileSync(axeUrl,'utf8'):null;
const a11y={};
const runAxe=async(label)=>{
  if(!axeSource){console.log(`WARN: axe-core 未安装，跳过无障碍扫描 ${label}`);return;}
  if(!await page.evaluate(()=>Boolean(window.axe))){await page.addScriptTag({content:axeSource});}
  const result=await page.evaluate(async()=>await window.axe.run(document,{resultTypes:['violations']}));
  a11y[label]=result.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.length,help:v.help}));
  console.log(`a11y[${label}]: ${result.violations.length} violations${result.violations.length?` (${result.violations.map(v=>`${v.id}x${v.nodes.length}`).join(', ')})`:''}`);
};
try {
  await page.goto('http://localhost:18080/login');
  await runAxe('login');
  const usernameFile=new URL('../deploy/secrets/admin-current-username',import.meta.url);
  await page.getByRole('textbox').first().fill(existsSync(usernameFile)?readFileSync(usernameFile,'utf8').trim():'admin');
  await page.locator('input[type=password]').fill(readFileSync(new URL('../deploy/secrets/admin-current-password',import.meta.url),'utf8').trim());
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.getByRole('heading',{name:'办公工作台'}).waitFor({timeout:20000});
  await page.getByText('实时连接',{exact:true}).waitFor({timeout:10000});
  await page.screenshot({path:'.cache/workbench.png',fullPage:true});
  const storage=await page.evaluate(()=>({local:{...localStorage},session:{...sessionStorage}}));
  assert.equal('password' in storage.local,false);
  assert.equal('Admin-Token' in storage.local,false);
  assert.equal('sessionObj' in storage.session,false);
  // C1 断网/恢复（docs/22 FE-F3-03）：切断实时通道 + 断网模拟 -> 降级定时拉取 -> 恢复重连实时连接
  cutActive=true;
  if(cutRealtime){cutRealtime();}
  await context.setOffline(true);
  await page.getByText('断线重连中（已降级定时拉取）',{exact:true}).waitFor({timeout:30000});
  cutActive=false;
  await context.setOffline(false);
  await page.getByText('实时连接',{exact:true}).waitFor({timeout:45000});
  await runAxe('workbench');
  // module 3 attendance pages render for the logged-in user (in-SPA navigation: session is memory-only)
  const openAttendancePage=async(path)=>{
    await page.locator(`.el-menu a[href="${path}"]`).first().evaluate((el)=>el.click());
    await page.waitForTimeout(1500);
  };
  await openAttendancePage('/attendance/punch');
  await page.locator('.punch-btn').first().waitFor({timeout:15000});
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openAttendancePage('/attendance/balance');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  // module 4 finance and module 5 notice pages render for the logged-in user
  const openPage=async(path)=>{
    await page.locator(`.el-menu a[href="${path}"]`).first().evaluate((el)=>el.click());
    await page.waitForTimeout(1500);
  };
  await openPage('/finance/reimburse');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/finance/invoice');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/notice/announcement');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/notice/message');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  // module 6 knowledge pages render for the logged-in user
  await openPage('/knowledge/space');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/knowledge/document');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/knowledge/files');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/knowledge/search');
  await openPage('/knowledge/trash');
  await page.locator('.el-tabs').first().waitFor({timeout:15000});
  // module 7 collaboration pages render for the logged-in user
  await openPage('/collaboration/event');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/collaboration/room');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/collaboration/task');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  // module 10 AI pages render for the logged-in user (chat + admin pages)
  await openPage('/ai/chat');
  await page.locator('.ai-chat').first().waitFor({timeout:15000});
  await runAxe('ai-chat');
  await openPage('/aiadmin/provider');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/aiadmin/model');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/aiadmin/prompt');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/aiadmin/usage');
  await page.locator('.el-tabs').first().waitFor({timeout:15000});
  // module 10 AI M4/M5 pages: copilot scenes + tool/agent management
  await openPage('/ai/copilot');
  await page.locator('.ai-copilot').first().waitFor({timeout:15000});
  await openPage('/aiadmin/tool');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await openPage('/aiadmin/agent');
  await page.locator('.ai-agent').first().waitFor({timeout:15000});
  await openAttendancePage('/index');
  // C2 消息端到端时延实测（docs/22 FE-F3）：探针事件 -> outbox 排空 -> WS 推送 -> 未读角标刷新
  const readBadge=async()=>{
    const badges=page.locator('.notice-badge .el-badge__content');
    return (await badges.count())>0?((await badges.first().textContent())??'').trim():'none';
  };
  const badgeBefore=await readBadge();
  const sentAt=Date.now();
  await page.getByRole('button',{name:'创建验证流程'}).click();
  let latency=-1;
  while(Date.now()-sentAt<5000){
    await page.waitForTimeout(150);
    if((await readBadge())!==badgeBefore){latency=Date.now()-sentAt;break;}
  }
  assert.ok(latency>=0,`消息端到端时延超时：未读角标 ${badgeBefore} -> ${await readBadge()}`);
  console.log(`C2 消息端到端时延: ${latency}ms（目标 <=3000ms，硬断言 <=5000ms：outbox 排空节奏 3s）`);
  await page.getByRole('button',{name:'完成本次验证'}).click();
  await page.getByText('流程已完成',{exact:true}).waitFor();
  // workflow 页面渲染 + 无障碍扫描（docs/22 F4-04 关键路径）：
  // 详情页需在途实例，预置「通用申请」发起（LEADER 规则回落部门负责人=admin，任务落本人待办）
  const apiBase='http://localhost:18081';
  const clientId='e5cd7e4891bf95d1d19206ce24a7b32e';
  const adminUser=existsSync(usernameFile)?readFileSync(usernameFile,'utf8').trim():'admin';
  const adminPass=readFileSync(new URL('../deploy/secrets/admin-current-password',import.meta.url),'utf8').trim();
  let seeded=false;
  try {
    const loginRes=await (await fetch(apiBase+'/api/v1/auth/login',{method:'POST',headers:{clientid:clientId,'Content-Type':'application/json'},body:JSON.stringify({username:adminUser,password:adminPass})})).json();
    const apiToken=loginRes.data.accessToken;
    const defs=await (await fetch(apiBase+'/api/v1/wf/definitions',{headers:{clientid:clientId,Authorization:'Bearer '+apiToken}})).json();
    const def=(defs.data||[]).find(d=>d.processKey==='genericApply');
    if(def){
      const title=`browser-smoke 流程详情 ${Date.now()}`;
      const launch=await fetch(apiBase+'/api/v1/wf/generic-requests/launch',{method:'POST',headers:{clientid:clientId,Authorization:'Bearer '+apiToken,'Content-Type':'application/json','Idempotency-Key':'browser-smoke-'+Date.now()},body:JSON.stringify({definitionId:def.id,title,formData:JSON.stringify({title})})});
      seeded=launch.ok;
    }
  } catch(e){console.log(`WARN: 流程实例预置失败（${e.message}），跳过详情页扫描`);}
  await openPage('/workflow/todo');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await runAxe('workflow-todo');
  if(seeded){
    await page.locator('.el-table .el-link').first().click();
    await page.waitForTimeout(1500);
    await runAxe('workflow-detail');
  } else {
    console.log('WARN: 无可点击的待办行，跳过流程详情无障碍扫描');
  }
  await openPage('/workflow/done');
  await page.locator('.el-table').first().waitFor({timeout:15000});
  await page.reload();
  await page.getByRole('button',{name:'登录',exact:true}).waitFor();
  assert.deepEqual(failures,[]);
  // C3 无障碍扫描报告落盘（artifacts/a11y-report.json，告警不拦门禁，人工验收基线）
  const reportUrl=new URL('../artifacts/a11y-report.json',import.meta.url);
  mkdirSync(new URL('.',reportUrl),{recursive:true});
  writeFileSync(reportUrl,JSON.stringify({generatedAt:new Date().toISOString(),scans:a11y},null,2));
  console.log('PASS: browser login, workbench, realtime offline/recovery, workflow todo/done/detail, finance, notice, knowledge, collaboration, AI chat/admin/copilot/tool/agent, message latency, a11y scan, memory-only session and reload logout');
} finally {await browser.close();}
