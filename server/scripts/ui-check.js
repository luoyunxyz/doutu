#!/usr/bin/env node
/**
 * 后台前端真实渲染检查（用系统 Chrome/Edge 的 DevTools 协议驱动）。
 *
 * 为什么需要它：后台是零构建的原生 JS 单页，语法正确不等于运行时不报错。
 * 这个脚本会真开一个无头浏览器打开后台，收集 console 报错与未捕获异常，
 * 检查关键 DOM 是否渲染出来，并截一张图存到 screenshots/。
 *
 * 用法：
 *   node scripts/ui-check.js                        # 默认检查 http://127.0.0.1:7101/admin
 *   node scripts/ui-check.js --url=http://127.0.0.1:7101/admin --user=admin --pass=xxx
 *   node scripts/ui-check.js --shot=web-admin.png   # 指定截图文件名
 *
 * 需要本机有 Chrome 或 Edge（Windows 上一般都有 Edge）。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const m = /^--([^=]+)(?:=(.*))?$/.exec(a);
    return m ? [m[1], m[2] === undefined ? true : m[2]] : [a, true];
  }),
);

const ROOT = path.resolve(import.meta.dirname, '..');
const URL_TO_CHECK = String(args.url || 'http://127.0.0.1:7101/admin');
const SHOT = String(args.shot || 'web-admin.png');
const PORT = Number(args.port || 19222);
const SIZE = String(args.size || '1440,900');
const USERNAME = args.user ? String(args.user) : null;
const PASSWORD = args.pass ? String(args.pass) : null;

const BROWSERS = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  `${process.env.LOCALAPPDATA}\\Google\\Chrome\\Application\\chrome.exe`,
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
];

function findBrowser() {
  for (const p of BROWSERS) if (p && fs.existsSync(p)) return p;
  return null;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fetchJson(url, retries = 40) {
  for (let i = 0; i < retries; i += 1) {
    try {
      const res = await fetch(url);
      if (res.ok) return await res.json();
    } catch {
      /* 浏览器还没起来 */
    }
    await sleep(250);
  }
  throw new Error(`无法连接 ${url}`);
}

/** 极简 CDP 客户端（Node 内置 WebSocket） */
class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.listeners = [];
    ws.addEventListener('message', (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        if (msg.error) reject(new Error(msg.error.message));
        else resolve(msg.result);
        return;
      }
      for (const fn of this.listeners) fn(msg);
    });
  }

  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          reject(new Error(`CDP 超时：${method}`));
        }
      }, 30000);
    });
  }

  on(fn) {
    this.listeners.push(fn);
  }
}

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    ws.addEventListener('open', () => resolve(new Cdp(ws)));
    ws.addEventListener('error', (e) => reject(new Error(`WebSocket 连接失败：${e.message || 'unknown'}`)));
  });
}

async function main() {
  const browser = findBrowser();
  if (!browser) {
    console.log('⚠️  未找到 Chrome/Edge，跳过真实渲染检查（前端语法仍可用 node --check 验证）');
    process.exit(0);
  }
  console.log(`使用浏览器：${browser}`);

  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'doutu-uicheck-'));
  const child = spawn(
    browser,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-extensions',
      `--window-size=${SIZE}`,
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${userDataDir}`,
      'about:blank',
    ],
    { stdio: 'ignore' },
  );

  const failures = [];
  const consoleErrors = [];
  try {
    const version = await fetchJson(`http://127.0.0.1:${PORT}/json/version`);
    console.log(`浏览器：${version.Browser}`);
    const targets = await fetchJson(`http://127.0.0.1:${PORT}/json/list`);
    const page = targets.find((t) => t.type === 'page');
    if (!page) throw new Error('没有可用的页面目标');

    const cdp = await connect(page.webSocketDebuggerUrl);
    cdp.on((msg) => {
      if (msg.method === 'Runtime.exceptionThrown') {
        const d = msg.params.exceptionDetails;
        consoleErrors.push(`未捕获异常：${d.exception?.description || d.text}`);
      }
      if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        consoleErrors.push(`console.error：${msg.params.args.map((a) => a.value ?? a.description ?? '').join(' ')}`);
      }
      if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
        consoleErrors.push(`页面错误：${msg.params.entry.text}`);
      }
    });

    await cdp.send('Runtime.enable');
    await cdp.send('Log.enable');
    await cdp.send('Page.enable');
    await cdp.send('Network.enable');
    // 设备指标：让页面按给定分辨率布局，截图才有完整侧边栏
    const [width, height] = SIZE.split(',').map(Number);
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: width || 1440,
      height: height || 900,
      deviceScaleFactor: 1,
      mobile: false,
    });

    const response = await fetch(URL_TO_CHECK, { method: 'GET' }).catch(() => null);
    if (!response || !response.ok) {
      throw new Error(`页面不可访问（HTTP ${response ? response.status : 'N/A'}）：${URL_TO_CHECK}`);
    }
    console.log(`页面可访问：HTTP ${response.status}`);

    await cdp.send('Page.navigate', { url: URL_TO_CHECK });
    await sleep(2500);

    // 若提供了账号密码，尝试登录以便截到真实界面
    if (USERNAME && PASSWORD) {
      const loginExpr = `(async () => {
        const pick = (names) => names.map(n => document.querySelector(n)).find(Boolean);
        const user = pick(['input[name=username]','#username','input[type=text]']);
        const pass = pick(['input[name=password]','#password','input[type=password]']);
        if (!user || !pass) return 'no-login-form';
        user.value = ${JSON.stringify(USERNAME)};
        pass.value = ${JSON.stringify(PASSWORD)};
        user.dispatchEvent(new Event('input', { bubbles: true }));
        pass.dispatchEvent(new Event('input', { bubbles: true }));
        const form = document.querySelector('form');
        const btn = pick(['#login-btn','button[type=submit]','.login-btn','button']);
        if (form && form.requestSubmit) form.requestSubmit();
        else if (btn) btn.click();
        else return 'no-submit';
        return 'submitted';
      })()`;
      const loginResult = await cdp.send('Runtime.evaluate', { expression: loginExpr, awaitPromise: true, returnByValue: true });
      console.log(`登录尝试：${loginResult.result.value}`);
      await sleep(2500);
    }

    // 关键 DOM 断言：登录层或主框架至少有一个渲染出来
    const domProbe = await cdp.send('Runtime.evaluate', {
      expression: `JSON.stringify({
        title: document.title,
        bodyLen: document.body ? document.body.innerText.trim().length : 0,
        hasLogin: !!document.querySelector('#login, .login, [data-page=login], input[type=password]'),
        hasLayout: !!document.querySelector('#app, #main, .layout, nav, aside, table'),
        tables: document.querySelectorAll('table').length,
        menus: document.querySelectorAll('nav a, aside a, .menu-item, .nav-item').length,
        text: document.body ? document.body.innerText.slice(0, 300) : ''
      })`,
      returnByValue: true,
    });
    const probe = JSON.parse(domProbe.result.value);
    console.log('DOM 探测：', JSON.stringify({ ...probe, text: undefined }));

    if (!probe.bodyLen) failures.push('页面渲染后 body 文本为空（JS 可能没执行或报错中断）');
    if (!probe.hasLogin && !probe.hasLayout) failures.push('既没有登录表单也没有主框架布局');
    if (USERNAME && PASSWORD) {
      // 带账号密码时必须真的登进去：登录层应消失、主框架与菜单应出现
      if (probe.hasLogin && !probe.menus && !probe.tables) {
        failures.push('登录后仍停在登录层（前端脚本可能没加载或登录接口未走通）');
      }
      if (!probe.menus && !probe.tables) failures.push('登录后没有渲染出菜单或表格');
    }
    if (consoleErrors.length) failures.push(`控制台有 ${consoleErrors.length} 条错误`);

    fs.mkdirSync(path.join(ROOT, 'screenshots'), { recursive: true });
    const shot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    const shotPath = path.join(ROOT, 'screenshots', SHOT);
    fs.writeFileSync(shotPath, Buffer.from(shot.data, 'base64'));
    console.log(`截图已保存：${shotPath}`);

    console.log('\n结论：');
    if (failures.length) {
      for (const f of failures) console.log(`  ✗ ${f}`);
      if (consoleErrors.length) for (const e of consoleErrors.slice(0, 10)) console.log(`      ${e}`);
      console.log(`  页面文本片段：${probe.text.replace(/\s+/g, ' ').slice(0, 200)}`);
    } else {
      console.log('  ✓ 后台页面加载正常、脚本无报错、界面已渲染');
      console.log(`  ✓ 菜单项 ${probe.menus} 个，表格 ${probe.tables} 个`);
    }
    process.exitCode = failures.length ? 1 : 0;
  } finally {
    child.kill();
    await sleep(300);
    fs.rmSync(userDataDir, { recursive: true, force: true });
  }
}

main().catch((err) => {
  console.error('UI 检查失败：', err.message);
  process.exit(1);
});
