/**
 * 把 Android 侧新增的三个「给原生调」的网页函数搬到 iOS 的 override（2026-10-24）。
 *
 *   node _ios/port-wechat-helpers.mjs
 *
 * 背景：Android 在 2026-10-21 之后加了
 *   · `__fwOpenToolPay(type)`  —— 工具页「红包 / 转账」直达收付款页
 *   · `__fwOpenGzhDialog()`    —— 工具页「公众号」在微信主页弹新建公众号表单
 *   · `__fwAddFriend(...)`     —— 原生「创建单聊」走同一条落库路径
 * 这三个都是给**原生**调用的入口，iOS 的网页包一直没有（iOS 侧也还没有原生调用方）。
 * 现在 iOS 补了「工具」Tab，需要它们。
 *
 * 做法：按锚点把 Android 的三段源码原样插进 iOS 的 override，另外把「电脑设备行」
 * 的默认值从 Windows 改成 hidden（Android 同一轮改的）。
 * 幂等：目标函数已存在就跳过。
 *
 * 注意：两份文件的换行不同（Android 是 CRLF），所以锚点**不带换行**，
 * 插入前把片段统一成目标文件的换行风格。
 */
import fs from 'node:fs';

const ANDROID = 'doutu/app/src/main/assets/fake_world/overrides/wechat-chat-actions.js';
const IOS = 'ios_v2/Resources/web/overrides/wechat-chat-actions.js';

const android = fs.readFileSync(ANDROID, 'utf8');
let ios = fs.readFileSync(IOS, 'utf8');
const crlf = ios.includes('\r\n');

/** 取「某个函数前面那段注释 + 函数体」：从函数名回溯到最近的 `/**`，到 endNeedle 处结束 */
function blockBefore(text, functionNeedle, endNeedle) {
  const fnAt = text.indexOf(functionNeedle);
  if (fnAt < 0) throw new Error(`找不到函数：${functionNeedle}`);
  const docAt = text.lastIndexOf('/**', fnAt);
  const endAt = text.indexOf(endNeedle, fnAt);
  if (endAt < 0) throw new Error(`找不到结束锚点：${endNeedle}`);
  const start = docAt < 0 ? fnAt : docAt;
  return text.slice(start, endAt);
}

const normalize = (text) => (crlf ? text.replace(/\r?\n/g, '\r\n') : text.replace(/\r?\n/g, '\n'));

let changed = 0;

// ① 工具页两个直达入口：插在 `function installChatPayActions()` 之前
{
  const block = normalize(blockBefore(android, 'window.__fwOpenToolPay', 'function installChatPayActions'));
  if (ios.includes('__fwOpenToolPay')) {
    console.log('· 工具页两个直达入口已存在，跳过');
  } else {
    const at = ios.indexOf('  function installChatPayActions()');
    if (at < 0) throw new Error('iOS 找不到 installChatPayActions 锚点');
    ios = ios.slice(0, at) + block + ios.slice(at);
    changed += 1;
    console.log(`✓ 插入 __fwOpenToolPay / __fwOpenGzhDialog（${block.split('\n').length} 行）`);
  }
}

// ② 创建单聊的落库函数：插在 `function openAddFriendDialog()` 之前
{
  const block = normalize(blockBefore(android, 'window.__fwAddFriend', 'function openAddFriendDialog'));
  if (ios.includes('__fwAddFriend')) {
    console.log('· __fwAddFriend 已存在，跳过');
  } else {
    const at = ios.indexOf('  function openAddFriendDialog()');
    if (at < 0) throw new Error('iOS 找不到 openAddFriendDialog 锚点');
    ios = ios.slice(0, at) + block + ios.slice(at);
    changed += 1;
    console.log(`✓ 插入 __fwAddFriend（${block.split('\n').length} 行）`);
  }
}

// ③ 电脑设备行默认隐藏（Android 2026-10-21 同轮改动）
{
  const before = "return DEVICE_ROW_OPTIONS.some(option => option.value === value) ? value : 'Windows';";
  const after = "return DEVICE_ROW_OPTIONS.some(option => option.value === value) ? value : 'hidden';";
  if (ios.includes(after)) {
    console.log('· 设备行默认值已是 hidden，跳过');
  } else if (ios.includes(before)) {
    ios = ios.split(before).join(after);
    changed += 1;
    console.log('✓ 设备行默认值 Windows → hidden');
  } else {
    console.log('⚠️ 没找到设备行默认值那一行，跳过');
  }
}

if (changed === 0) {
  console.log('\n没有需要改的（幂等）');
} else {
  fs.writeFileSync(IOS, ios, 'utf8');
  console.log(`\n写入 ${IOS}（${changed} 处改动）`);
}
