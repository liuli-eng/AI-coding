import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { resolve } from 'node:path';
import process from 'node:process';
import { setTimeout as delay } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

// Real Vue controls against an isolated fixture server; never contacts the real backend/model.
const driver = process.env.PHASE1_CDP_DRIVER;
if (!driver)
  throw new Error('Set PHASE1_CDP_DRIVER to the installed design-taste-frontend/scripts/lib/cdp.mjs driver (see docs/phase1-acceptance.md).');
const driverUrl = pathToFileURL(driver).href;
const { Browser } = await import(driverUrl);
const { probe, layout, app } = await import(new URL('./probe.mjs', driverUrl));
const { judge, judgeLayout, judgeApp, APP_SKIP } = await import(new URL('./rules.mjs', driverUrl));
const output = process.env.PHASE1_BROWSER_OUTPUT || '/tmp/aicoding-phase1-browser';
mkdirSync(output, { recursive: true });
const requests = [];
const checks = [];
const designFindings = [];
const interceptionErrors = [];
const bases = [
  { id: '101', name: 'Java 开发规范', description: '团队 Java 编码规范、异常处理与接口约定。', documentCount: 8, updateTime: '2026-10-28 09:30:00' },
  { id: '102', name: '产品使用手册', description: '常见操作与问题排查步骤。', documentCount: 0, updateTime: null },
  ...Array.from({ length: 12 }, (_, index) => ({ id: String(index + 103), name: `开发资料 ${index + 1}`, description: '接口设计与服务开发参考资料。', documentCount: index + 1, updateTime: '2026-10-27 10:00:00' })),
];
const sessions = new Map([
  ['201', { id: 201, sessionTitle: '已绑定开发规范', chatMode: 'programming', knowledgeId: '101' }],
  ['202', { id: '202', sessionTitle: '普通未绑定会话', chatMode: 'debugging', knowledgeId: null }],
  ['203', { id: '203', sessionTitle: '已绑定使用手册', chatMode: 'learning', knowledgeId: '102' }],
  ['2081234567890123456', { id: '2081234567890123456', sessionTitle: '大整数会话', chatMode: 'programming', knowledgeId: '101' }],
]);
const workflow = { uuid: 'wf-fixture', title: '代码检查流程', remark: '检查代码与整理结果。', nodes: [{ uuid: 'start', workflowComponentId: 1, inputConfig: { user_inputs: [{ name: 'question', title: '问题', type: 1 }] } }], edges: [] };
let nextId = 300;
let failCreate = false;
let failList = false;
let listDelay = 0;
let sessionDelay = 0;
let historyDelay = 0;
let sendDelay = 0;
let courseStarts = 0;
const sessionDelays = new Map();
let failSession = false;
const disabled = new Set();
const inaccessible = new Set();
const detailDelays = new Map();
const api = createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  if (req.method === 'OPTIONS') {
    res.end();
    return;
  }
  let raw = '';
  for await (const chunk of req) raw += chunk;
  const body = raw ? JSON.parse(raw) : undefined;
  requests.push({ url: req.url, method: req.method, body });
  const url = new URL(req.url, 'http://fixture');
  let result = { code: 200, data: [], rows: [], total: 0 };
  if (url.pathname === '/knowledge/bases') {
    await delay(listDelay);
    const keyword = url.searchParams.get('keyword') || '';
    const matches = bases.filter(base => !disabled.has(base.id) && base.name.includes(keyword));
    const page = Number(url.searchParams.get('pageNum') || 1);
    const size = Number(url.searchParams.get('pageSize') || 12);
    result = failList ? { code: 500, msg: 'fixture list failure' } : { code: 200, rows: matches.slice((page - 1) * size, page * size), total: matches.length };
  }
  else if (url.pathname.startsWith('/knowledge/bases/')) {
    const id = url.pathname.split('/').pop();
    await delay(detailDelays.get(id) || 0);
    result = disabled.has(id) ? { code: 500, msg: '知识库已停用' } : inaccessible.has(id) ? { code: 500, msg: '知识库暂不可用' } : { code: 200, data: bases.find(base => base.id === id) };
  }
  else if (url.pathname === '/system/session' && req.method === 'POST') {
    if (disabled.has(body?.knowledgeId)) {
      result = { code: 500, msg: '知识库已停用' };
    }
    else if (failCreate) {
      result = { code: 500, msg: 'fixture create failure' };
    }
    else {
      const id = nextId++;
      sessions.set(String(id), { ...body, id });
      result = { code: 200, data: id };
    }
  }
  else if (url.pathname === '/system/session/list') {
    result = { code: 200, rows: [...sessions.values()], total: sessions.size };
  }
  else if (url.pathname.startsWith('/system/session/')) {
    await delay(sessionDelays.get(url.pathname.split('/').pop()) || sessionDelay);
    result = failSession ? { code: 500, msg: 'fixture session failure' } : { code: 200, data: sessions.get(url.pathname.split('/').pop()) };
  }
  else if (url.pathname === '/system/message/list') {
    await delay(historyDelay);
    result = { code: 200, rows: [], total: 0 };
  }
  else if (url.pathname === '/learning/mine') {
    result = { code: 200, rows: [{ id: '401', courseId: '501', title: '并发控制课程', courseVersion: 1, totalChapters: 1, completedCount: 0, version: 1 }], total: 1 };
  }
  else if (url.pathname === '/learning/mine/401') {
    result = { code: 200, data: { id: '401', courseId: '501', title: '并发控制课程', courseVersion: 1, chapters: [{ id: 'basics', title: '基础章节', content: '学习请求取消与会话隔离。' }], completedChapters: [], version: 1 } };
  }
  else if (url.pathname === '/learning/mine/401/chapters/basics/session') {
    const id = String(450 + courseStarts++);
    sessions.set(id, { id, sessionTitle: '课程待发问题', chatMode: 'learning', knowledgeId: null });
    sessionDelays.set(id, 1800);
    result = { code: 200, data: { sessionId: id, initialMessage: `课程待发问题-${id}`, created: true } };
  }
  else if (url.pathname === '/system/model/modelList') {
    result = { code: 200, data: [{ id: 1, modelName: 'fixture-model', modelDescribe: '验收模型' }] };
  }
  else if (url.pathname === '/agent/agent/agentOptions') {
    result = { code: 200, data: [{ id: 601, agentName: '代码审查助手', agentDescribe: '代码审查助手', modelName: 'fixture-model' }] };
  }
  else if (url.pathname === '/workflow/public/search') {
    result = { code: 200, data: { records: [workflow], total: 1 } };
  }
  else if (url.pathname === '/workflow/public/wf-fixture') {
    result = { code: 200, data: workflow };
  }
  else if (url.pathname === '/chat/send') {
    await delay(sendDelay);
    res.setHeader('Content-Type', 'text/event-stream');
    res.end(body.enableWorkFlow
      ? 'event: [START]\ndata: {}\n\nevent: [DONE]\ndata: {"output":{"value":"固定流程验收结果"}}\n\n'
      : 'data: {"event":"content","content":"固定验收回答"}\n\ndata: {"event":"done","done":true}\n\n');
    return;
  }
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify(result));
});
await new Promise(resolve => api.listen(0, '127.0.0.1', resolve));
const apiOrigin = `http://127.0.0.1:${api.address().port}`;
const webRoot = resolve(import.meta.dirname, '..');
const fixtureConfig = resolve(output, 'vite.fixture.config.mts');
writeFileSync(fixtureConfig, `import projectConfig from ${JSON.stringify(resolve(webRoot, 'vite.config.mts'))};\nexport default async context => ({...await projectConfig(context), cacheDir: ${JSON.stringify(resolve(output, 'vite-cache'))}});\n`);
const vite = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--config', fixtureConfig, '--host', '127.0.0.1', '--port', '15173', '--strictPort'], { cwd: webRoot, env: { ...process.env, VITE_API_URL: apiOrigin }, stdio: ['ignore', 'pipe', 'pipe'] });
let viteLog = '';
vite.stdout.on('data', (chunk) => {
  viteLog += chunk;
});
vite.stderr.on('data', (chunk) => {
  viteLog += chunk;
});
let browser;
let page;
async function waitFor(expression, message = expression) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await page.eval(expression))
      return;
    await delay(100);
  }
  throw new Error(`Timed out: ${message}`);
}
async function waitRequest(predicate, message) {
  for (let attempt = 0; attempt < 100; attempt++) {
    const request = requests.findLast(predicate);
    if (request)
      return request;
    await delay(100);
  }
  throw new Error(`Request missing: ${message}`);
}
function pass(name) {
  checks.push(name);
  console.log(`PASS ${name}`);
}
async function shot(name) {
  if (!name.includes('error') && !name.includes('loading') && name !== 'failure')
    await waitFor('document.querySelectorAll(".el-message").length === 0', 'transient messages dismissed');
  await delay(150);
  await page.screenshot({ path: `${output}/${name}.png`, format: 'png', fullPage: true });
}
async function inspectLibrary(width) {
  const metrics = await page.eval(probe);
  const layoutMetrics = await page.eval(layout);
  const appMetrics = await page.eval(app);
  const findings = [
    ...[...judge(metrics, { width }), ...judgeLayout(layoutMetrics, { width })].filter(finding => !APP_SKIP.has(finding.id) && !['no-title', 'small-title'].includes(finding.id)),
    ...judgeApp(appMetrics, metrics, { width }),
  ];
  designFindings.push({ width, findings });
}
async function click(selector) {
  await waitFor(`!!document.querySelector(${JSON.stringify(selector)})`, selector);
  await page.eval(selector => document.querySelector(selector).click(), selector);
}
async function input(selector, value) {
  await waitFor(`!!document.querySelector(${JSON.stringify(selector)})`, selector);
  await page.eval(({ selector, value }) => {
    const element = document.querySelector(selector);
    element.focus();
    const prototype = element.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value);
    element.dispatchEvent(new Event('input', { bubbles: true }));
  }, { selector, value });
}
async function enterQuestion(content) {
  await delay(400);
  await waitFor('!!document.querySelector(".chat-sender textarea") && !document.querySelector(".chat-sender textarea").disabled');
  await input('.chat-sender textarea', content);
  await delay(100);
  await page.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  await page.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
}
async function go(path) {
  await page.goto(`http://127.0.0.1:15173${path}`, { settleMs: 0 });
  await waitFor('!!document.querySelector("#app")?.textContent.trim()', 'Vue rendered');
}
async function navigate(path) {
  if (path === '/app-market') {
    await page.eval(() => [...document.querySelectorAll('.workbench-entry')].find(element => element.textContent.includes('应用市场')).click());
  }
  else {
    await click(`a[href="${path}"]`);
  }
  await waitFor(`location.pathname === ${JSON.stringify(path)}`);
}
async function newChat() {
  await click('.new-chat-entry');
  await waitFor('location.pathname === "/chat" && !!document.querySelector("#chat-knowledge")');
}
const reviewFailures = [];
async function reviewCase(name, run) {
  try {
    await run();
    pass(name);
  }
  catch (error) {
    reviewFailures.push({ name, error: error.message });
    console.error(`FAIL ${name}: ${error.message}`);
    await shot(`review-error-${reviewFailures.length}`).catch(() => {});
  }
  finally {
    historyDelay = 0;
    sendDelay = 0;
  }
}
async function sidebarSession(title, id) {
  await page.eval(title => [...document.querySelectorAll('[class*=conversation]')].filter(element => element.textContent.trim() === title).at(-1).click(), title);
  await waitFor(`location.pathname === "/chat/${id}"`);
}
async function logoutFromControl() {
  await click('.avatar-container .el-avatar');
  await page.eval(() => [...document.querySelectorAll('.popover-content-box-item')].find(element => element.textContent.includes('退出登录')).click());
  await waitFor('!!document.querySelector(".el-message-box")');
  await page.eval(() => [...document.querySelectorAll('.el-message-box button')].find(element => element.textContent.includes('确认退出')).click());
  await waitFor('!document.querySelector(".avatar-container .el-avatar") || !JSON.parse(localStorage.getItem("user")).token');
}
async function sessionIdRegressions() {
  await page.setViewport({ width: 1440, height: 1000 });
  for (const { id, title, name } of [
    { id: '201', title: '已绑定开发规范', name: 'numeric' },
    { id: '2081234567890123456', title: '大整数会话', name: 'large-string' },
  ]) {
    await reviewCase(`${name} session ID restores, stays selected in sidebar and sends its exact string ID`, async () => {
      await go(`/chat/${id}`);
      await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
      await delay(250);
      assert.equal(await page.eval('document.querySelector(".chat-sender textarea").disabled'), false, `${name} detail must enable the composer`);
      const state = await page.eval((title) => {
        const session = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('session');
        return { currentId: session.currentSession.id, listId: session.sessionList.find(item => item.sessionTitle === title).id };
      }, title);
      assert.deepEqual(state, { currentId: id, listId: id });
      assert(await page.eval(title => document.querySelector('.conversation-item.active')?.textContent.includes(title), title), 'restored sidebar row must be active');
      const direct = `ID回归${name}直接提问`;
      await enterQuestion(direct);
      const directSend = await waitRequest(request => request.url === '/chat/send' && request.body.content === direct, 'ID direct send');
      assert.equal(directSend.body.sessionId, id);
      await navigate('/knowledge');
      await sidebarSession(title, id);
      const sidebar = `ID回归${name}侧栏提问`;
      await enterQuestion(sidebar);
      const sidebarSend = await waitRequest(request => request.url === '/chat/send' && request.body.content === sidebar, 'ID sidebar send');
      assert.equal(sidebarSend.body.sessionId, id);
      assert(await page.eval(title => document.querySelector('.conversation-item.active')?.textContent.includes(title), title), 'clicked sidebar row must remain active');
      await shot(`review-id-${name}`);
    });
  }
  assert.equal(reviewFailures.length, 0, JSON.stringify(reviewFailures, null, 2));
}
async function reviewRegressions() {
  await page.setViewport({ width: 1440, height: 1000 });
  await reviewCase('delayed history blocks manual send and dispatches pending first question exactly once', async () => {
    await go('/knowledge');
    await click('button[aria-label="使用Java 开发规范开始对话"]');
    await waitFor('location.pathname === "/chat" && !!document.querySelector("#chat-knowledge")');
    historyDelay = 1800;
    sendDelay = 2800;
    const content = '评审待发首条问题';
    await enterQuestion(content);
    const created = await waitRequest(request => request.url === '/system/session' && request.body?.sessionContent === content, 'review create');
    const id = [...sessions.values()].find(session => session.sessionContent === created.body.sessionContent).id;
    await waitRequest(request => request.url.startsWith('/system/message/list?') && request.url.includes(`sessionId=${id}`), 'delayed history request');
    await delay(150);
    const blocked = await page.eval('document.querySelector(".chat-sender textarea").disabled');
    if (!blocked)
      await enterQuestion('评审加载期间手动问题');
    await delay(4700);
    const first = requests.filter(request => request.url === '/chat/send' && request.body.content === content);
    const manual = requests.filter(request => request.url === '/chat/send' && request.body.content === '评审加载期间手动问题');
    assert.deepEqual({ blocked, firstSends: first.length, manualSends: manual.length }, { blocked: true, firstSends: 1, manualSends: 0 });
    assert.equal(first[0].body.knowledgeId, '101');
    assert.equal(await page.eval('document.querySelectorAll(".user-content").length'), 1);
    assert(await page.eval('document.body.textContent.includes("评审待发首条问题")'));
    await shot('review-history-ready');
  });

  for (const reset of ['new conversation', 'logout']) {
    await reviewCase(`late course session cannot reinstall pending content after ${reset}`, async () => {
      await go('/learning');
      await click('.course-list article button');
      await waitFor('!!document.querySelector(".chapter-actions button")');
      const before = courseStarts;
      await page.eval(() => [...document.querySelectorAll('.chapter-actions button')].find(element => element.textContent.includes('进入 AI 教学')).click());
      const id = String(450 + before);
      await waitRequest(request => request.url === `/system/session/${id}`, 'delayed course session detail');
      if (reset === 'new conversation') {
        await page.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'k', code: 'KeyK', windowsVirtualKeyCode: 75, modifiers: 2 });
        await page.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'k', code: 'KeyK', windowsVirtualKeyCode: 75 });
      }
      else {
        await logoutFromControl();
      }
      await delay(2500);
      const state = await page.eval(() => ({
        path: location.pathname,
        pending: document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('chat').pendingMessage,
      }));
      const sends = requests.filter(request => request.url === '/chat/send' && request.body.content === `课程待发问题-${id}`);
      assert.deepEqual({ ...state, sends: sends.length }, { path: '/chat', pending: null, sends: 0 });
    });
  }

  for (const mode of ['agent', 'workflow']) {
    await reviewCase(`${mode} sidebar switch restores bound ordinary context and saved knowledge payload`, async () => {
      await go('/app-market');
      await click(`button[aria-label="使用${mode === 'agent' ? '代码审查助手' : '代码检查流程'}"]`);
      await waitFor('location.pathname === "/chat" && !!document.querySelector(".agent-select")');
      const applicationQuestion = `评审${mode}独立问题`;
      await enterQuestion(applicationQuestion);
      await waitRequest(request => request.url === '/chat/send' && request.body.content === applicationQuestion, 'application send before switch');
      await sidebarSession('已绑定开发规范', '201');
      await waitRequest(request => request.url.startsWith('/system/message/list?') && request.url.includes('sessionId=201'), 'ordinary history after sidebar switch');
      await delay(400);
      const labelVisible = await page.eval('!!document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
      const question = `评审${mode}返回普通问题`;
      await enterQuestion(question);
      const request = await waitRequest(request => request.url === '/chat/send' && request.body.content === question, 'ordinary send after sidebar switch');
      assert.deepEqual({ labelVisible, knowledgeId: request.body.knowledgeId, agentId: request.body.agentId, workflow: request.body.enableWorkFlow, runner: request.body.workFlowRunner }, { labelVisible: true, knowledgeId: '101', agentId: undefined, workflow: undefined, runner: undefined });
      await shot(`review-${mode}-to-bound`);
    });
  }
  assert.equal(reviewFailures.length, 0, JSON.stringify(reviewFailures, null, 2));
}
try {
  for (let attempt = 0; attempt < 100; attempt++) {
    try {
      if ((await fetch('http://127.0.0.1:15173')).ok)
        break;
    }
    catch {}
    await delay(100);
  }
  browser = await Browser.launch();
  page = await browser.newPage({ width: 1440, height: 1000 });
  await page.send('Page.addScriptToEvaluateOnNewDocument', { source: `localStorage.setItem('user', JSON.stringify({token:'phase1-fixture',userInfo:{userId:7,username:'验收用户'}}));` });
  // Block all external traffic, including accidental production API calls or external images.
  await page.send('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] });
  browser.on(page.sessionId, 'Fetch.requestPaused', async (event) => {
    try {
      const origin = new URL(event.request.url).origin;
      if (origin === 'http://127.0.0.1:15173' || origin === apiOrigin || event.request.url.startsWith('data:'))
        await page.send('Fetch.continueRequest', { requestId: event.requestId });
      else await page.send('Fetch.failRequest', { requestId: event.requestId, errorReason: 'BlockedByClient' });
    }
    catch (error) {
      // A navigation can cancel a paused image before CDP resumes it.
      if (!/Invalid InterceptionId|Session closed|Target closed/.test(error.message))
        interceptionErrors.push(error.message);
    }
  });

  if (process.env.KNOWLEDGE_STATUS_ONLY) {
    await go('/chat/201');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
    disabled.add('101');
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("知识库已停用")');
    pass('existing session rechecks on focus and identifies disabled source');
    await shot('knowledge-disabled-desktop');
    disabled.clear();
    await page.eval(() => document.querySelector('.session-knowledge button').click());
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
    pass('reenabling source restores existing session on retry');
    await go('/knowledge');
    await waitFor('document.querySelectorAll(".knowledge-list article").length === 12');
    disabled.add('101');
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('!document.querySelector(".knowledge-list")?.textContent.includes("Java 开发规范") && document.querySelectorAll(".knowledge-list article").length === 12');
    pass('focus refresh removes disabled library from client list');
    disabled.clear();
    await click('.knowledge-workbench header button');
    await waitFor('[...document.querySelectorAll("button")].some(button => button.getAttribute("aria-label") === "使用Java 开发规范开始对话")');
    await click('button[aria-label="使用Java 开发规范开始对话"]');
    await waitFor('!!document.querySelector("#chat-knowledge")');
    disabled.add('101');
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('document.querySelector(".knowledge-picker")?.textContent.includes("知识库已停用")');
    assert.equal(await page.eval('document.querySelector("#app").__vue_app__.config.globalProperties.$pinia._s.get("chat").draftKnowledge.id'), '101');
    pass('disabled draft stays bound and shows recoverable warning');
    const question = '停用知识库的问题保留测试';
    await enterQuestion(question);
    await waitFor('document.querySelector(".create-error")?.textContent.includes("知识库已停用")');
    assert.equal(await page.eval('location.pathname'), '/chat');
    assert.equal(await page.eval('document.querySelector(".chat-sender textarea").value'), question);
    pass('disabled create preserves typed question and selected source');

    await page.setViewport({ width: 390, height: 844 });
    await shot('knowledge-disabled-mobile');
    assert.equal(await page.eval('document.documentElement.scrollWidth <= innerWidth'), true);
    await page.eval(() => document.querySelector('.knowledge-picker button').focus());
    assert.equal(await page.eval('document.activeElement.tagName'), 'BUTTON');
    pass('disabled state at 390px fits and retry receives keyboard focus');
    disabled.clear();
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('!document.querySelector(".knowledge-picker")?.textContent.includes("知识库已停用")');
    pass('draft recovers after reenabling');
  }
  else {
  // Warm lazy controls before measurements: Vite may reload after optimizing a new style import.
  await go('/chat');
  await waitFor('!!document.querySelector("#chat-knowledge")');
  await delay(2000);
  await go('/app-market');
  await waitFor('!!document.querySelector(".application-card")');
  await delay(2000);
  await go('/learning');
  await waitFor('!!document.querySelector(".course-list article")');
  await delay(2000);
  await go('/knowledge');
  await waitFor('document.querySelectorAll(".knowledge-list article").length === 12');
  await delay(2000);
  if (!process.env.PHASE1_REVIEW_ONLY && !process.env.PHASE1_ID_REVIEW_ONLY) {
    listDelay = 1200;
    await click('.knowledge-workbench header button');
    await waitFor('!!document.querySelector("[aria-busy=true]")', 'loading state');
    await shot('knowledge-loading');
    await waitFor('document.querySelectorAll(".knowledge-list article").length === 12');
    assert.equal(await page.eval('document.querySelector("h1")?.textContent.trim()'), '知识库');
    await shot('knowledge-desktop');
    await inspectLibrary(1440);
    pass('knowledge route, loading state, 12 summaries, total and metadata');
    listDelay = 0;
    await click('.el-pagination .btn-next');
    await waitFor('document.querySelectorAll(".knowledge-list article").length === 2');
    assert(requests.some(request => request.url.includes('pageNum=2') && request.url.includes('pageSize=12')));
    pass('pagination requests page 2 and displays remaining rows');
    await input('input[aria-label="搜索知识库名称"]', '不存在');
    await click('.knowledge-search button');
    await waitFor('document.body.textContent.includes("没有找到匹配的知识库")');
    await shot('knowledge-empty');
    pass('search resets page and shows empty state');
    failList = true;
    await input('input[aria-label="搜索知识库名称"]', '');
    await click('.knowledge-search button');
    await waitFor('document.body.textContent.includes("知识库加载失败")');
    await shot('knowledge-error');
    failList = false;
    await click('.el-alert button');
    await waitFor('document.querySelectorAll(".knowledge-list article").length === 12');
    pass('list error and retry recover actual API response');
    await delay(3200);
    await page.setViewport({ width: 768, height: 1000 });
    await shot('knowledge-tablet');
    await inspectLibrary(768);
    await page.setViewport({ width: 390, height: 844, mobile: true });
    await shot('knowledge-narrow');
    await inspectLibrary(390);
    assert(await page.eval('document.documentElement.scrollWidth <= innerWidth'), 'knowledge screen overflows');
    pass('390px library has no horizontal overflow');
    await click('button[aria-label="使用Java 开发规范开始对话"]');
    await waitFor('location.pathname === "/chat" && !!document.querySelector("#chat-knowledge")');
    await waitFor('document.querySelector(".knowledge-picker")?.textContent.includes("知识库")');
    assert(await page.eval('document.querySelector(".knowledge-picker")?.textContent.includes("Java 开发规范")'));
    await shot('chat-draft-narrow');
    pass('start-chat preserves selected draft through new-conversation reset');
    failCreate = true;
    await enterQuestion('请解释团队异常处理规范');
    await waitFor('document.body.textContent.includes("对话创建失败")');
    assert.equal(await page.eval('document.querySelector(".chat-sender textarea").value'), '请解释团队异常处理规范');
    assert.equal(await page.eval('localStorage.getItem("chatContent")'), null);
    assert.equal(requests.filter(request => request.url === '/chat/send').length, 0);
    assert.equal(requests.findLast(request => request.method === 'POST' && request.url === '/system/session').body.knowledgeId, '101');
    await shot('chat-create-error');
    pass('create failure preserves typed question and draft, without stale pending send');
    failCreate = false;
    await enterQuestion('请解释团队异常处理规范');
    const send = await waitRequest(request => request.url === '/chat/send', 'first send');
    assert.equal(send.body.knowledgeId, '101');
    assert.equal(send.body.content, '请解释团队异常处理规范');
    assert.equal(send.body.sessionId, '300');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
    await shot('chat-bound-narrow');
    pass('successful create and first ordinary send use saved knowledge ID');
    await page.setViewport({ width: 1440, height: 1000 });
    await go('/chat/300');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
    await enterQuestion('刷新后继续提问');
    const refreshed = await waitRequest(request => request.url === '/chat/send' && request.body.content === '刷新后继续提问', 'refresh send');
    assert.equal(refreshed.body.knowledgeId, '101');
    await shot('chat-bound-desktop');
    pass('direct refresh restores saved binding and safe summary name before send');
    await page.eval(() => [...document.querySelectorAll('.conversations-item')].find(element => element.textContent.includes('普通未绑定会话'))?.click());
    // Conversation library class names vary; fall back to its rendered text control only.
    if (await page.eval('location.pathname !== "/chat/202"')) {
      await page.eval(() => [...document.querySelectorAll('[class*=conversation]')].filter(element => element.textContent.trim() === '普通未绑定会话').at(-1)?.click());
    }
    await waitFor('location.pathname === "/chat/202"');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("未使用知识库")');
    await enterQuestion('切换到未绑定会话');
    const unbound = await waitRequest(request => request.url === '/chat/send' && request.body.content === '切换到未绑定会话', 'unbound send');
    assert.equal(unbound.body.knowledgeId, undefined);
    pass('switch conversation clears previous bound label and outgoing knowledge ID');
    detailDelays.set('101', 1400);
    await page.eval(() => [...document.querySelectorAll('[class*=conversation]')].filter(element => element.textContent.trim() === '已绑定开发规范').at(-1).click());
    await waitFor('location.pathname === "/chat/201" && document.querySelector(".session-knowledge")?.textContent.includes("正在加载")');
    await page.eval(() => [...document.querySelectorAll('[class*=conversation]')].filter(element => element.textContent.trim() === '已绑定使用手册').at(-1).click());
    await waitFor('location.pathname === "/chat/203" && document.querySelector(".session-knowledge")?.textContent.includes("产品使用手册")');
    await delay(1600);
    assert(await page.eval('document.querySelector(".session-knowledge").textContent.includes("产品使用手册")'));
    detailDelays.clear();
    pass('late previous knowledge detail cannot overwrite the switched session label');
    sessionDelay = 1600;
    await go('/chat/203');
    await waitFor('document.body.textContent.includes("正在恢复会话")');
    assert(await page.eval('document.querySelector(".chat-sender textarea").disabled'), 'composer enabled before restore');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("产品使用手册")');
    sessionDelay = 0;
    pass('direct session restore blocks composer until the owned session loads');
    inaccessible.add('102');
    await go('/chat/203');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("该知识库暂不可用")');
    assert(!await page.eval('document.querySelector(".session-knowledge").textContent.includes("102")'));
    await shot('chat-knowledge-unavailable');
    inaccessible.clear();
    await click('.session-knowledge button');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("产品使用手册")');
    pass('inaccessible/deleted summary shows generic unavailable state and retry');
    failSession = true;
    await go('/chat/201');
    await waitFor('document.body.textContent.includes("会话加载失败")');
    assert(await page.eval('document.querySelector(".chat-sender textarea").disabled'));
    failSession = false;
    await click('.sender-wrapper .el-alert button');
    await waitFor('document.querySelector(".session-knowledge")?.textContent.includes("Java 开发规范")');
    pass('session restore error blocks send and retry restores session');
    await newChat();
    assert(!await page.eval('document.querySelector(".knowledge-picker")?.textContent.includes("Java 开发规范")'));
    pass('new conversation resets previous knowledge selection');
    await click('.knowledge-picker .el-select__wrapper');
    await input('#chat-knowledge', '产品');
    await waitRequest(request => request.url.startsWith('/knowledge/bases?') && request.url.includes(`keyword=${encodeURIComponent('产品')}`), 'remote knowledge search');
    await page.eval(() => [...document.querySelectorAll('.el-select-dropdown__item')].find(element => element.textContent.trim() === '产品使用手册').click());
    await enterQuestion('从选择器选择知识库');
    const selectedCreate = await waitRequest(request => request.url === '/system/session' && request.body?.sessionContent === '从选择器选择知识库', 'picker create');
    const selectedSend = await waitRequest(request => request.url === '/chat/send' && request.body.content === '从选择器选择知识库', 'picker send');
    assert.equal(selectedCreate.body.knowledgeId, '102');
    assert.equal(selectedSend.body.knowledgeId, '102');
    pass('remote picker selection drives actual create and send payloads');
    await newChat();
    await navigate('/knowledge');
    await waitFor('!!document.querySelector(".knowledge-list article button")');
    await click('button[aria-label="使用Java 开发规范开始对话"]');
    await waitFor('!!document.querySelector("#chat-knowledge")');
    await navigate('/app-market');
    await click('button[aria-label="使用代码审查助手"]');
    await waitFor('location.pathname === "/chat" && !!document.querySelector(".agent-select")');
    assert(!await page.eval('!!document.querySelector("#chat-knowledge")'));
    await enterQuestion('智能体独立提问');
    const agentCreate = await waitRequest(request => request.url === '/system/session' && request.body?.sessionContent === '智能体独立提问', 'agent create');
    const agentSend = await waitRequest(request => request.url === '/chat/send' && request.body.content === '智能体独立提问', 'agent send');
    assert.equal(agentCreate.body.knowledgeId, undefined);
    assert.equal(agentSend.body.knowledgeId, undefined);
    assert.equal(agentSend.body.agentId, 601);
    await shot('chat-agent');
    pass('agent mode omits ordinary draft/saved knowledge on create and send');
    await newChat();
    await navigate('/knowledge');
    await waitFor('!!document.querySelector(".knowledge-list article button")');
    await click('button[aria-label="使用Java 开发规范开始对话"]');
    await waitFor('!!document.querySelector("#chat-knowledge")');
    await navigate('/app-market');
    await click('button[aria-label="使用代码检查流程"]');
    await waitFor('location.pathname === "/chat" && !!document.querySelector(".agent-select")');
    await enterQuestion('工作流独立提问');
    const workflowCreate = await waitRequest(request => request.url === '/system/session' && request.body?.sessionContent === '工作流独立提问', 'workflow create');
    const workflowSend = await waitRequest(request => request.url === '/chat/send' && request.body.content === '工作流独立提问', 'workflow send');
    assert.equal(workflowCreate.body.knowledgeId, undefined);
    assert.equal(workflowSend.body.knowledgeId, undefined);
    assert.equal(workflowSend.body.agentId, undefined);
    assert.equal(workflowSend.body.enableWorkFlow, true);
    assert.equal(workflowSend.body.workFlowRunner.uuid, 'wf-fixture');
    await waitFor('document.body.textContent.includes("固定流程验收结果") && document.body.textContent.includes("执行完成")');
    await shot('chat-workflow');
    pass('workflow mode omits ordinary knowledge and sends workflow runner');
    await newChat();
    await navigate('/knowledge');
    await waitFor('!!document.querySelector(".knowledge-list article button")');
    await click('button[aria-label="使用Java 开发规范开始对话"]');
    await waitFor('!!document.querySelector("#chat-knowledge")');
    await click('.avatar-container .el-avatar');
    await page.eval(() => [...document.querySelectorAll('.popover-content-box-item')].find(element => element.textContent.includes('退出登录')).click());
    await waitFor('!!document.querySelector(".el-message-box")');
    await page.eval(() => [...document.querySelectorAll('.el-message-box button')].find(element => element.textContent.includes('确认退出')).click());
    await waitFor('!document.querySelector("#chat-knowledge") && document.body.textContent.includes("登录")');
    assert.equal(await page.eval('localStorage.getItem("chatContent")'), null);
    pass('logout clears draft/pending content and hides authenticated selector');
  }
  if (!process.env.PHASE1_ID_REVIEW_ONLY)
    await reviewRegressions();
  await sessionIdRegressions();
  }
  assert.equal(interceptionErrors.length, 0, JSON.stringify(interceptionErrors));
  assert.equal(page.consoleErrors.length, 0, JSON.stringify(page.consoleErrors));
  pass('no uncaught browser exceptions');
  console.log(`${checks.length} browser scenarios passed; all API responses mocked at ${apiOrigin}`);
}
catch (error) {
  if (page) {
    await shot('failure').catch(() => {});
    writeFileSync(`${output}/failure-dom.txt`, await page.eval('document.body.innerText').catch(() => 'unavailable'));
  }
  throw error;
}
finally {
  writeFileSync(`${output}/requests.json`, JSON.stringify(requests, null, 2));
  writeFileSync(`${output}/checks.json`, JSON.stringify(checks, null, 2));
  writeFileSync(`${output}/review-failures.json`, JSON.stringify(reviewFailures, null, 2));
  writeFileSync(`${output}/design-findings.json`, JSON.stringify(designFindings, null, 2));
  writeFileSync(`${output}/vite.log`, viteLog);
  await browser?.close();
  vite.kill();
  api.close();
}
