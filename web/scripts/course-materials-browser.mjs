import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { resolve } from 'node:path';
import process from 'node:process';
import { setTimeout as delay } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

// Real Vue controls against isolated fixtures. Never contacts the real backend/model.
const driver = process.env.PHASE1_CDP_DRIVER;
if (!driver)
  throw new Error('Set PHASE1_CDP_DRIVER to the installed design-taste-frontend/scripts/lib/cdp.mjs driver (see docs/phase1-acceptance.md).');
const driverUrl = pathToFileURL(driver).href;
const { Browser } = await import(driverUrl);
const output = process.env.PHASE1_BROWSER_OUTPUT || '/tmp/aicoding-course-materials-user-browser';
mkdirSync(output, { recursive: true });
const requests = [];
const checks = [];
const interceptionErrors = [];
const sessions = new Map([
  ['201', { id: '201', sessionTitle: '课程 Java', chatMode: 'learning', knowledgeId: '101' }],
  ['202', { id: '202', sessionTitle: '普通会话', chatMode: 'programming', knowledgeId: null }],
  ['203', { id: '203', sessionTitle: '第二门课程', chatMode: 'learning', knowledgeId: null }],
]);
const materialA = { id: '9007199254740993001', name: 'Java 课程学习参考资料与示例.md', size: 1800, suffix: 'md', textLength: 100 };
const materialB = { id: '9007199254740993002', name: '第二门课程.pdf', size: 20000, suffix: 'pdf', textLength: 200 };
let currentCourseMaterials = [materialA];
let materialDelay = 0;
let downloadDelay = 0;
let downloadError = false;
let authError = false;
let failMaterials = false;
let listErrorCode = 0;
let documentExample = false;
let documentMime = 'text/plain';
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
  requests.push({ url: req.url, method: req.method, body, authorization: req.headers.authorization, clientId: req.headers.clientid });
  const url = new URL(req.url, 'http://fixture');
  let result = { code: 200, data: [], rows: [], total: 0 };
  if (/\/materials\/[^/]+\/download$/.test(url.pathname)) {
    await delay(downloadDelay);
    res.setHeader('Content-Type', downloadError || authError ? 'application/json' : documentMime);
    if (!downloadError && !authError)
      res.setHeader('Content-Disposition', 'attachment; filename=api-example.md');
    res.end(downloadError || authError ? JSON.stringify({ code: authError ? 401 : 500, msg: authError ? '登录已失效' : '资料下载失败，请重试' }) : documentExample ? '{"code":401,"msg":"Example response in API lesson"}' : 'Fixture material content');
    return;
  }
  if (/^\/learning\/sessions\/[^/]+\/materials$/.test(url.pathname)) {
    const id = url.pathname.split('/')[3];
    const items = id === '201' ? [...currentCourseMaterials] : id === '203' ? [materialB] : [];
    const failure = listErrorCode;
    await delay(materialDelay);
    result = failure ? { code: failure, msg: 'stale list error' } : failMaterials ? { code: 500, msg: '资料列表暂不可用' } : { code: 200, data: items };
  }
  else if (url.pathname === '/knowledge/bases/101') {
    result = { code: 200, data: { id: '101', name: 'Java 开发规范' } };
  }
  else if (url.pathname === '/system/session/list') {
    result = { code: 200, rows: [...sessions.values()], total: sessions.size };
  }
  else if (url.pathname.startsWith('/system/session/')) {
    result = { code: 200, data: sessions.get(url.pathname.split('/').pop()) };
  }
  else if (url.pathname === '/system/message/list') {
    result = { code: 200, rows: [], total: 0 };
  }
  else if (url.pathname === '/learning/mine') {
    result = { code: 200, rows: [{ id: '401', courseId: '501', title: 'Java 课程', courseVersion: 1, totalChapters: 1, completedCount: 0, version: 1 }], total: 1 };
  }
  else if (url.pathname === '/learning/mine/401') {
    result = { code: 200, data: { id: '401', courseId: '501', title: 'Java 课程', courseVersion: 1, chapters: [{ id: 'basics', title: '基础章节', content: '学习变量与表达式。' }], completedChapters: [], version: 1, materials: [...currentCourseMaterials] } };
  }
  else if (url.pathname === '/system/model/modelList') {
    result = { code: 200, data: [{ id: 1, modelName: 'fixture-model', modelDescribe: '验收模型' }] };
  }
  else if (url.pathname === '/workflow/public/search') {
    result = { code: 200, data: { records: [], total: 0 } };
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
async function click(selector) {
  await waitFor(`!!document.querySelector(${JSON.stringify(selector)})`, selector);
  await page.eval(selector => document.querySelector(selector).click(), selector);
}
async function go(path) {
  await page.goto(`http://127.0.0.1:15173${path}`, { settleMs: 0 });
  await waitFor('!!document.querySelector("#app")?.textContent.trim()', 'Vue rendered');
}

try {
  for (let attempt = 0; attempt < 120 && !viteLog.includes('15173'); attempt++) await delay(100);
  browser = await Browser.launch();
  page = await browser.newPage({ width: 1440, height: 1000 });
  await page.send('Page.addScriptToEvaluateOnNewDocument', { source: `localStorage.setItem('user', JSON.stringify({token:'course-fixture',userInfo:{userId:7,username:'课程验收用户'}}));` });
  await page.send('Fetch.enable', { patterns: [{ urlPattern: '*', requestStage: 'Request' }] });
  browser.on(page.sessionId, 'Fetch.requestPaused', async (event) => {
    try {
      const url = new URL(event.request.url);
      if (url.origin === 'http://127.0.0.1:15173' || url.origin === apiOrigin || url.protocol === 'data:' || url.protocol === 'blob:') {
        await page.send('Fetch.continueRequest', { requestId: event.requestId });
        return;
      }
      await page.send('Fetch.failRequest', { requestId: event.requestId, errorReason: 'BlockedByClient' });
    }
    catch (error) {
      if (!/InterceptionId|Session closed|Target closed/.test(error.message))
        interceptionErrors.push(error.message);
    }
  });
  const routeTo = async path => page.eval(async (path) => {
    const { default: router } = await import('/src/routers/index.ts');
    await router.push(path);
  }, path);
  const clickText = async (text, selector = 'button') => page.eval(({ text, selector }) => [...document.querySelectorAll(selector)].find(e => e.textContent.trim() === text)?.click(), { text, selector });
  const setToken = async token => page.eval(async (token) => {
    const { useUserStore } = await import('/src/stores/modules/user.ts');
    useUserStore().resetAuthExpiredHandling();
    useUserStore().setToken(token);
  }, token);
  const waitMaterialRequest = async (start) => {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (requests.slice(start).some(request => request.url === '/learning/sessions/201/materials'))
        return;
      await delay(50);
    }
    throw new Error('Pending session materials request did not start');
  };
  const runFixRegression = async (mode) => {
    authError = false;
    downloadError = false;
    failMaterials = false;
    listErrorCode = 0;
    materialDelay = 0;
    await setToken('review-account-a');
    if (mode === 'json-document') {
      await routeTo('/learning');
      await waitFor('!!document.querySelector(".course-list article")');
      await clickText('继续学习');
      await waitFor('!!document.querySelector(".el-drawer .course-materials")');
      await page.eval(() => {
        window.fixtureReviewUrls = [];
        const create = URL.createObjectURL.bind(URL);
        URL.createObjectURL = (blob) => {
          window.fixtureReviewUrls.push(blob);
          return create(blob);
        };
      });
      documentExample = true;
      for (const mime of ['text/plain', 'application/octet-stream']) {
        documentMime = mime;
        const before = await page.eval('window.fixtureReviewUrls.length');
        await click('.el-drawer .course-materials button');
        await delay(500);
        assert.equal(await page.eval('JSON.parse(localStorage.getItem("user")).token'), 'review-account-a', 'A successful lesson document must not expire authentication');
        assert.equal(await page.eval('window.fixtureReviewUrls.length'), before + 1, 'JSON-shaped lesson text must download as an attachment');
        assert.equal(await page.eval('window.fixtureReviewUrls.at(-1).text()'), '{"code":401,"msg":"Example response in API lesson"}');
      }
      documentExample = false;
      documentMime = 'text/plain';
      pass('TXT/MD attachment with code401 JSON lesson text downloads without auth side effects');
    }
    else {
      await routeTo('/chat/202');
      listErrorCode = 401;
      materialDelay = 700;
      let start = requests.length;
      await routeTo('/chat/201');
      await waitMaterialRequest(start);
      await setToken('review-account-b');
      await delay(900);
      assert.equal(await page.eval('JSON.parse(localStorage.getItem("user")).token'), 'review-account-b', 'An old session-list401 must not expire the new account');
      pass('delayed session-list401 after account switch cannot log out replacement account');
      listErrorCode = 0;
      materialDelay = 0;
      await routeTo('/chat/202');
      listErrorCode = 403;
      materialDelay = 700;
      start = requests.length;
      await routeTo('/chat/201');
      await waitMaterialRequest(start);
      listErrorCode = 0;
      materialDelay = 0;
      await routeTo('/chat/203');
      await waitFor('document.querySelector(".course-materials")?.textContent.includes("第二门课程")');
      await delay(900);
      assert.equal(await page.eval('location.pathname'), '/chat/203', 'A stale list403 must not redirect the current session');
      assert.equal(await page.eval('JSON.parse(localStorage.getItem("user")).token'), 'review-account-b');
      pass('delayed session-list403 after session switch has no navigation or auth side effects');
      await routeTo('/chat/202');
      listErrorCode = 401;
      await routeTo('/chat/201');
      await waitFor('!JSON.parse(localStorage.getItem("user")).token');
      pass('current session-list401 retains the existing auth-expiry flow');
      listErrorCode = 0;
    }
  };
  // Warm lazy chat dependencies before assertions. Cold Vite optimization may reload once.
  for (let warm = 0; warm < 3; warm++) {
    try {
      await page.goto('http://127.0.0.1:15173/chat/201', { settleMs: 4000 });
      await waitFor('!!document.querySelector(".sender-wrapper .course-materials")');
      break;
    }
    catch (error) {
      if (warm === 2)
        throw error;
      await delay(1000);
    }
  }
  if (process.env.COURSE_REGRESSION) {
    await runFixRegression(process.env.COURSE_REGRESSION);
  }
  else {
    await go('/learning');
    await waitFor('!!document.querySelector(".course-list article")');
    await clickText('继续学习');
    await waitFor('document.querySelector(".el-drawer .course-materials")?.textContent.includes("Java 课程")');
    pass('learning enrollment displays its snapshot material name, size and download');
    await page.eval(() => {
      window.fixtureUrls = [];
      window.fixtureRevoked = [];
      const create = URL.createObjectURL.bind(URL);
      URL.createObjectURL = (blob) => {
        const url = create(blob);
        window.fixtureUrls.push(url);
        return url;
      };
      const revoke = URL.revokeObjectURL.bind(URL);
      URL.revokeObjectURL = (url) => {
        window.fixtureRevoked.push(url);
        revoke(url);
      };
    });
    await click('.el-drawer .course-materials button');
    await waitFor('window.fixtureUrls.length===1');
    await delay(1100);
    assert.equal(await page.eval('window.fixtureRevoked.length'), 1);
    const enrolledDownload = requests.find(r => r.url.includes('/learning/mine/401/materials/'));
    assert.equal(enrolledDownload.authorization, 'Bearer course-fixture');
    assert.ok(enrolledDownload.clientId);
    pass('enrollment download uses authenticated relative endpoint and revokes its URL');
    currentCourseMaterials = [materialA, materialB];
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('document.querySelector(".el-drawer .course-materials")?.textContent.includes("第二门课程.pdf")');
    assert.equal(await page.eval('document.querySelector(".chapter-content").textContent.includes("学习变量与表达式。")'), true);
    pass('returning to an open learning drawer refreshes uploaded attachments and keeps its chapter');
    currentCourseMaterials = [materialA];
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('document.querySelector(".el-drawer .course-materials") && !document.querySelector(".el-drawer .course-materials").textContent.includes("第二门课程.pdf")');
    pass('learning drawer removes attachments no longer bound to the course');
    downloadError = true;
    await click('.el-drawer .course-materials button');
    await waitFor('document.querySelector(".el-drawer .course-materials").textContent.includes("资料下载失败")');
    assert.equal(await page.eval('window.fixtureUrls.length'), 1);
    downloadError = false;
    pass('JSON error blob surfaces inline and never creates a document');
    await page.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
    await delay(250);
    assert.equal(await page.eval('document.querySelector(".course-materials").scrollWidth <= document.querySelector(".el-drawer__body").clientWidth'), true);
    await page.eval(() => document.querySelector('.course-materials button').focus());
    assert.equal(await page.eval('document.activeElement.tagName'), 'BUTTON');
    await shot('learning-materials-mobile');
    pass('390px learning attachments fit and downloads receive keyboard focus');
    await page.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
    await routeTo('/chat/201');
    await waitFor('document.querySelector(".sender-wrapper .course-materials")?.textContent.includes("Java 课程")');
    await shot('chat-materials-desktop');
    pass('restored course session loads material summaries from its separate endpoint');
    currentCourseMaterials = [materialA, materialB];
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('document.querySelector(".sender-wrapper .course-materials")?.textContent.includes("第二门课程.pdf")');
    pass('returning to an existing conversation refreshes newly uploaded course attachments');
    currentCourseMaterials = [materialA];
    await page.eval(() => window.dispatchEvent(new Event('focus')));
    await waitFor('document.querySelector(".sender-wrapper .course-materials") && !document.querySelector(".sender-wrapper .course-materials").textContent.includes("第二门课程.pdf")');
    pass('existing conversation removes attachments no longer bound to the course');
    const before = await page.eval('window.fixtureUrls.length');
    downloadDelay = 1200;
    await click('.sender-wrapper .course-materials button');
    await routeTo('/chat/202');
    await waitFor('location.pathname==="/chat/202" && !document.querySelector(".course-materials")');
    await delay(1350);
    assert.equal(await page.eval('window.fixtureUrls.length'), before);
    downloadDelay = 0;
    pass('switching to an ordinary session removes material area and cancels the pending download');
    materialDelay = 1200;
    await routeTo('/chat/201');
    await waitRequest(r => r.url === '/learning/sessions/201/materials', 'slow materials');
    await delay(200);
    materialDelay = 0;
    await routeTo('/chat/203');
    await waitFor('document.querySelector(".course-materials")?.textContent.includes("第二门课程")');
    await delay(1350);
    assert.equal(await page.eval('document.querySelector(".course-materials").textContent.includes("Java 课程")'), false);
    pass('late material response from previous session cannot replace the current session list');
    failMaterials = true;
    await routeTo('/chat/201');
    await waitFor('document.querySelector(".sender-wrapper").textContent.includes("资料列表暂不可用")');
    assert.equal(await page.eval('!!document.querySelector(".course-materials")'), false);
    failMaterials = false;
    await clickText('重试', '.sender-wrapper button');
    await waitFor('document.querySelector(".course-materials")?.textContent.includes("Java 课程")');
    pass('session material failure clears stale list and retry recovers');
    await page.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: false });
    await delay(250);
    assert.equal(await page.eval('document.querySelector(".course-materials").scrollWidth <= document.querySelector(".sender-wrapper").clientWidth'), true);
    await shot('chat-materials-mobile');
    pass('390px chat attachment list stays within the composer width');
    downloadDelay = 1200;
    await click('.course-materials button');
    await page.eval(async () => {
      const { useUserStore } = await import('/src/stores/modules/user.ts');
      useUserStore().setToken('account-two');
    });
    await delay(1350);
    assert.equal(await page.eval('!!document.querySelector(".course-materials")'), false);
    assert.equal(await page.eval('window.fixtureUrls.length'), before);
    downloadDelay = 0;
    pass('account changes clear attachment state and suppress an old account download');
    await routeTo('/chat/202');
    materialDelay = 1000;
    await routeTo('/chat/201');
    await delay(200);
    await page.eval(async () => {
      const { useUserStore } = await import('/src/stores/modules/user.ts');
      useUserStore().setToken('account-three');
    });
    await delay(1200);
    assert.equal(await page.eval('!!document.querySelector(".course-materials")'), false);
    materialDelay = 0;
    pass('late list response after account change is discarded');

    await routeTo('/chat/202');
    await routeTo('/chat/201');
    await waitFor('!!document.querySelector(".course-materials")');
    await page.eval(() => {
      const original = Blob.prototype.text;
      Blob.prototype.text = async function () {
        const text = await original.call(this);
        if (this.type.includes('application/json') && text.includes('"code":401')) {
          Blob.prototype.text = original;
          window.fixtureJsonParsing = true;
          await new Promise(resolve => setTimeout(resolve, 1000));
        }
        return text;
      };
    });
    authError = true;
    await click('.course-materials button');
    await waitFor('window.fixtureJsonParsing === true');
    await page.eval(async () => {
      const { useUserStore } = await import('/src/stores/modules/user.ts');
      useUserStore().setToken('account-four');
    });
    await delay(1200);
    assert.equal(await page.eval('JSON.parse(localStorage.getItem("user")).token'), 'account-four');
    assert.equal(await page.eval('window.fixtureUrls.length'), before);
    authError = false;
    pass('an old download 401 parsed after account switch cannot expire the new account');

    await routeTo('/chat/202');
    await routeTo('/chat/201');
    await waitFor('!!document.querySelector(".course-materials")');
    authError = true;
    await click('.course-materials button');
    await waitFor('!document.querySelector(".course-materials")');
    assert.equal(await page.eval('JSON.parse(localStorage.getItem("user")).token || null'), null);
    pass('download JSON 401 follows the existing auth expiry flow');
    await runFixRegression('json-document');
    await runFixRegression('stale-list');
  }
  assert.equal(interceptionErrors.length, 0, JSON.stringify(interceptionErrors));
  assert.equal(page.consoleErrors.length, 0, JSON.stringify(page.consoleErrors));
  console.log(`${checks.length} user browser scenarios passed; isolated APIs only.`);
}
catch (error) {
  if (page) {
    await page.screenshot({ path: `${output}/failure.png`, format: 'png', fullPage: true }).catch(() => {});
    writeFileSync(`${output}/failure-dom.txt`, await page.eval('document.body.innerText').catch(() => ''));
  }
  throw error;
}
finally {
  writeFileSync(`${output}/requests.json`, JSON.stringify(requests, null, 2));
  writeFileSync(`${output}/checks.json`, JSON.stringify(checks, null, 2));
  writeFileSync(`${output}/vite.log`, viteLog);
  await browser?.close();
  vite.kill();
  api.closeAllConnections();
  await new Promise(resolve => api.close(resolve));
}
