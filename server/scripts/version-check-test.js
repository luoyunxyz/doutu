/**
 * 版本推送（/api/v1/version/check）自检。
 *
 *   node scripts/version-check-test.js
 *
 * 背景：2026-10-17 线上「后台发了版本、客户端没反应」。服务端这条链路本身是通的，
 * 但有几个坑会让运营以为没生效，这里逐个钉死：
 *   · 版本号没比线上大 → 永远推不出去（后台保存时会提示）
 *   · 渠道填了别的词 → 客户端不带渠道（= all），精确匹配就什么都收不到（现在兜底 all）
 *   · gray_percent 以前存了不用，现在真的按设备哈希灰度，且强制更新不吃灰度
 *   · min_version 命中要强制升级
 *
 * 环境变量必须在 import 业务模块前设好（config.js 一被 import 就读 env）。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'version-check-test.db');

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });

const { openDb, initSchema, closeDb, getDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { default: ApiClient } = await import('./lib/client.js');
const { default: config } = await import('../src/config.js');
const { now } = await import('../src/core/util.js');

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) {
    pass += 1;
    console.log(`  ✓ ${name}`);
  } else {
    fail += 1;
    console.log(`  ✗ ${name} ${extra}`);
  }
};

/** 直接落库发布一个版本（绕过后台接口，测的是检查逻辑） */
async function publish({ platform = 'android', code, name = `v${code}`, channel = 'all', gray = 100, force = 0, minVersion = null, status = 1, url = 'https://example.com/app.apk', targets = null }) {
  const ts = now();
  const r = await getDb().insert('app_version', {
    platform,
    version_code: code,
    version_name: name,
    channel,
    url,
    patch_url: null,
    patch_md5: null,
    size: null,
    changelog: `${name} 更新`,
    force,
    gray_percent: gray,
    min_version: minVersion,
    target_versions: targets,
    status,
    published_at: ts,
    created_at: ts,
  });
  return r.insertId;
}

async function main() {
  const db = await openDb({ dialect: 'sqlite' });
  await initSchema(db);
  await seedAll(db);
  await openKv();

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;

  const ask = async (versionCode, { channel = null, device = 'dev-alpha' } = {}) => {
    const client = new ApiClient(base, {
      masterKey: config.security.masterKey,
      deviceId: device,
    });
    const q = `/api/v1/version/check?platform=android&version_code=${versionCode}` + (channel ? `&channel=${channel}` : '');
    const res = await client.get(q, { key: 'root' });
    return res.body;
  };

  console.log('\n① 一个版本都没有');
  const empty = await ask(1);
  ok('不推更新', empty.data.has_update === false, JSON.stringify(empty.data));
  ok('说明是「没有更新的版本」', empty.data.reason === 'no_newer_release', String(empty.data.reason));

  console.log('\n② 发布 v2（全量）');
  await publish({ code: 2 });
  const v2 = await ask(1);
  ok('客户端看到 has_update', v2.data.has_update === true, JSON.stringify(v2.data));
  ok('不是强制', v2.data.force === false);
  ok('带上版本名', v2.data.latest.version_name === 'v2', String(v2.data.latest?.version_name));
  ok('reason=has_update', v2.data.reason === 'has_update');

  const same = await ask(2);
  ok('已经是 v2 的客户端不再收到推送', same.data.has_update === false, JSON.stringify(same.data));

  const older = await ask(3);
  ok('比 v2 新的客户端不降级推送', older.data.has_update === false);

  console.log('\n③ 客户端不带渠道时不该被渠道卡住（v3 只发 official）');
  await publish({ code: 3, channel: 'official' });
  const viaAll = await ask(1);
  ok('不带渠道的客户端照样收到', viaAll.data.has_update === true, JSON.stringify(viaAll.data));
  ok('推的是 v3', viaAll.data.latest.version_code === 3, String(viaAll.data.latest?.version_code));

  const viaOfficial = await ask(1, { channel: 'official' });
  ok('带同渠道的客户端也收到', viaOfficial.data.has_update === true);

  const otherChannel = await ask(1, { channel: 'huawei' });
  ok('声明了别的渠道 → 只认自己的和 all（不会拿到 official）', otherChannel.data.latest.version_code === 2, String(otherChannel.data.latest?.version_code));

  console.log('\n④ 版本号最大者胜出（渠道只打平手）');
  await publish({ code: 4, channel: 'all' });
  const highest = await ask(1, { channel: 'official' });
  ok('official 渠道客户端拿到更新的 v4', highest.data.latest.version_code === 4, String(highest.data.latest?.version_code));

  console.log('\n⑤ 下架的版本不推');
  await publish({ code: 5, status: 0 });
  const offline = await ask(1);
  ok('status=0 不参与推送', offline.data.latest.version_code === 4, String(offline.data.latest?.version_code));

  console.log('\n⑥ 灰度');
  const grayDevices = [];
  for (let i = 0; i < 40; i += 1) grayDevices.push(`gray-dev-${i}`);
  await publish({ code: 6, gray: 30 });
  let hit = 0;
  for (const d of grayDevices) {
    const r = await ask(1, { device: d });
    if (r.data.has_update) hit += 1;
  }
  ok('灰度 30% 时只有一部分设备收到', hit > 0 && hit < grayDevices.length, `收到 ${hit}/40`);
  const missReason = await ask(1, { device: grayDevices[0] });
  ok('灰度响应里带 reason 与桶号', typeof missReason.data.gray_bucket === 'number', JSON.stringify(missReason.data));

  await publish({ code: 7, gray: 0 });
  const zero = await ask(1, { device: 'dev-alpha' });
  ok('灰度 0% 谁都收不到该版本', zero.data.has_update === false, JSON.stringify(zero.data));
  ok('说明是灰度没命中（而不是退回更旧的版本）', zero.data.reason === 'gray_miss', String(zero.data.reason));

  console.log('\n⑦ 强制更新：force 不吃灰度');
  await publish({ code: 8, gray: 0, force: 1 });
  const forced = await ask(1, { device: 'dev-alpha' });
  ok('强制版本照推（无视灰度）', forced.data.latest.version_code === 8, String(forced.data.latest?.version_code));
  ok('force=true', forced.data.force === true);

  console.log('\n⑧ min_version 命中即强制');
  await publish({ code: 9, minVersion: 5 });
  const minHit = await ask(1);
  ok('旧客户端被强制', minHit.data.has_update === true && minHit.data.force === true, JSON.stringify(minHit.data));
  const minMiss = await ask(9);
  ok('新客户端不受影响', minMiss.data.has_update === false, JSON.stringify(minMiss.data));

  console.log('\n⑨ 平台隔离');
  const ios = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: 'ios-dev' });
  const iosRes = await ios.get('/api/v1/version/check?platform=ios&version_code=1', { key: 'root' });
  ok('iOS 拿不到 Android 的版本', iosRes.body.data.has_update === false, JSON.stringify(iosRes.body.data));

  console.log('\n⑩ 指定版本（只推给名单里的来源版本）');
  // 前面已经有一堆更高的版本在线上（4..9），这里单独造一个干净平台来测，免得互相干扰
  await getDb().remove('app_version', { platform: 'android' });
  await publish({ code: 10, targets: '1' });
  const t1 = await ask(1);
  ok('名单里的版本收到更新', t1.data.has_update === true, JSON.stringify(t1.data));
  ok('推的就是那条版本', Number(t1.data.latest.version_code) === 10, String(t1.data.latest?.version_code));
  const t2 = await ask(2);
  ok('不在名单里的版本收不到', t2.data.has_update === false, JSON.stringify(t2.data));
  ok('原因标明是「不在指定版本里」', t2.data.reason === 'version_not_targeted', String(t2.data.reason));
  const t3 = await ask(3);
  ok('其他版本同样收不到', t3.data.has_update === false, String(t3.data.reason));

  console.log('\n⑪ 指定版本 + 强制更新：一样只推给名单');
  await getDb().remove('app_version', { platform: 'android' });
  await publish({ code: 20, force: 1, targets: '1' });
  const f1 = await ask(1);
  ok('名单里的版本被强制', f1.data.has_update === true && f1.data.force === true, JSON.stringify(f1.data));
  const f2 = await ask(2);
  ok('非名单版本连强制更新都收不到', f2.data.has_update === false && f2.data.force === false, JSON.stringify(f2.data));
  ok('原因同样是「不在指定版本里」', f2.data.reason === 'version_not_targeted', String(f2.data.reason));

  console.log('\n⑫ 指定版本 + min_version：非名单版本仍然安静');
  await getDb().remove('app_version', { platform: 'android' });
  await publish({ code: 30, minVersion: 5, targets: '2' });
  const m2 = await ask(2);
  ok('名单里且低于 min_version → 强制', m2.data.has_update === true && m2.data.force === true, JSON.stringify(m2.data));
  const m3 = await ask(3);
  ok('非名单版本不受 min_version 影响', m3.data.has_update === false, JSON.stringify(m3.data));

  console.log('\n⑬ 多条版本时「最高且适用于你」的那条胜出');
  await getDb().remove('app_version', { platform: 'android' });
  await publish({ code: 40, targets: '1' });
  await publish({ code: 41, targets: '2' });
  const pick1 = await ask(1);
  ok('版本 1 拿到 40（41 不适用于它）', Number(pick1.data.latest?.version_code) === 40, String(pick1.data.latest?.version_code));
  const pick2 = await ask(2);
  ok('版本 2 拿到 41', Number(pick2.data.latest?.version_code) === 41, String(pick2.data.latest?.version_code));
  const pick3 = await ask(3);
  ok('版本 3 两条都不适用 → 没有更新', pick3.data.has_update === false, JSON.stringify(pick3.data));

  console.log('\n⑭ 留空 = 所有版本都收（历史行为不变）');
  await getDb().remove('app_version', { platform: 'android' });
  await publish({ code: 50 });
  for (const code of [1, 2, 3, 49]) {
    const r = await ask(code);
    ok(`版本 ${code} 收到全量更新`, r.data.has_update === true, JSON.stringify(r.data));
  }

  console.log('\n⑮ 后台接口：填写与回显');
  const { hashPassword } = await import('../src/core/crypto.js');
  const { randomHex, formatDateTime, addMinutes } = await import('../src/core/util.js');
  const adminId = await getDb().insert('admin', {
    username: 'veradmin', password_hash: hashPassword('VerAdmin@2026'), real_name: '版本自检',
    role_id: 1, status: 1, created_at: now(), updated_at: now(),
  });
  const adminToken = randomHex(32);
  const ts2 = now();
  await getDb().insert('admin_token', {
    admin_id: adminId.insertId, token: adminToken, ip: '127.0.0.1', ua: 'diag',
    expire_at: formatDateTime(addMinutes(new Date(), 30)), last_seen: ts2, revoked: 0, created_at: ts2, updated_at: ts2,
  });
  const adminClient = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: 'ver-admin' });
  const created = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '6.0.0', platform: 'android', url: 'https://example.com/app.apk',
    update_mode: 'optional', changelog: 'x', target_versions: '2, 1 ,2',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('后台保存成功', created.body.code === 0, JSON.stringify(created.body).slice(0, 140));
  const saved = await getDb().get("SELECT `target_versions` FROM `app_version` WHERE `version_name` = '6.0.0'");
  ok('去重排序后落库为 "1,2"', String(saved.target_versions) === '1,2', String(saved.target_versions));

  const edited = await adminClient.post(`${config.security.adminPath}/api/versions/60`, {}, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  void edited;
  const putRes = await adminClient.call('PUT', `${config.security.adminPath}/api/versions/${(await getDb().get("SELECT `id` FROM `app_version` WHERE `version_name` = '6.0.0'")).id}`, { target_versions: '' }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('改成空串 = 恢复全量', putRes.body.code === 0, JSON.stringify(putRes.body).slice(0, 120));
  const cleared = await getDb().get("SELECT `target_versions` FROM `app_version` WHERE `version_name` = '6.0.0'");
  ok('库里存成了 null', cleared.target_versions === null, String(cleared.target_versions));

  const list = await adminClient.get(`${config.security.adminPath}/api/versions`, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  const listed = (list.body.data?.rows || []).find((r) => r.version_name === '6.0.0');
  ok('列表接口带回 target_versions 字段', listed && 'target_versions' in listed, listed ? JSON.stringify(listed).slice(0, 120) : '（没找到这条版本）');

  console.log('\n⑯ 版本号重复：必须给清晰提示，不能是「服务器异常」');
  // 先正常建一个，再用同样的版本号建一次 —— 这条曾经在生产上抛出未捕获的
  // Duplicate entry 异常，前台只看到「服务异常，请稍后重试」，谁都查不出是版本号重复。
  const dupBody = {
    version_name: '7.0.0', platform: 'android', url: 'https://example.com/app.apk',
    update_mode: 'optional', changelog: 'x', target_versions: '',
  };
  const first = await adminClient.post(`${config.security.adminPath}/api/versions`, dupBody, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('第一次创建成功', first.body.code === 0, JSON.stringify(first.body).slice(0, 140));
  const second = await adminClient.post(`${config.security.adminPath}/api/versions`, dupBody, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('第二次不是 5000（服务异常）', second.body.code !== 5000, String(second.body.code));
  ok('第二次给出「已发布过」的人话', second.body.code === 1001 && /已经发布过/.test(second.body.msg), second.body.msg);

  // 渠道里有头部空格的老坑：过去会绕过预检查直接撞唯一键
  const spaced = await adminClient.post(`${config.security.adminPath}/api/versions`, { ...dupBody, channel: ' all ' }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('渠道带空格也认得出来（trim 后仍是 all）', spaced.body.code === 1001 && /已经发布过/.test(spaced.body.msg), JSON.stringify(spaced.body).slice(0, 140));

  console.log('\n⑰ 客户端上报版本（后台勾选列表的数据源）');
  // 用两个本次运行还没问过的版本号：写入按「每版本每分钟一次」节流，
  // 前面步骤已经问过的版本号在节流窗口内不会再写（这是设计，不是 bug）
  await ask(91, { device: 'cv-dev-1' });
  await ask(92, { device: 'cv-dev-2' });
  await new Promise((r) => setTimeout(r, 80));
  const seen = await getDb().query('SELECT version_code, version_name, hit_count FROM client_version WHERE version_code IN (91, 92) ORDER BY version_code');
  ok('客户端上报的版本被记下来', seen.length === 2, JSON.stringify(seen));
  ok('版本 91 记到了', seen.some((r) => Number(r.version_code) === 91));
  ok('版本 92 也记到了', seen.some((r) => Number(r.version_code) === 92));
  const before91 = Number(seen.find((r) => Number(r.version_code) === 91)?.hit_count);
  await ask(91, { device: 'cv-dev-3' });
  await new Promise((r) => setTimeout(r, 80));
  const after91 = Number((await getDb().get('SELECT hit_count FROM client_version WHERE version_code = 91')).hit_count);
  ok('同一版本在节流窗口内不会重复计数', after91 === before91, `before=${before91} after=${after91}`);

  const adminList = await adminClient.get(`${config.security.adminPath}/api/versions`, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('后台列表带回 client_versions', Array.isArray(adminList.body.data?.client_versions), JSON.stringify(adminList.body.data).slice(0, 120));
  ok('client_versions 里有版本 91', (adminList.body.data.client_versions || []).some((c) => Number(c.version_code) === 91));

  console.log('\n⑱ 指定了 ≥ 本次版本号的目标 → 给出「收不到」的提醒');
  const badTarget = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '9.9.9', platform: 'android', url: 'https://example.com/app.apk',
    update_mode: 'optional', changelog: 'x', target_versions: '80,81',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('仍然保存成功（只是提醒）', badTarget.body.code === 0, JSON.stringify(badTarget.body).slice(0, 140));
  const badNotes = (badTarget.body.data?.notes || []).join(' ');
  ok('提醒里点名了收不到的目标', /80/.test(badNotes) && /不会收到/.test(badNotes), badNotes);

  console.log('\n⑲ 版本号按 1.2.3 填，内部编码自动换算（后台不再要求填整数）');
  const { parseVersionName, versionCodeOf, formatVersionCode } = await import('../src/core/version.js');
  const cases = [
    ['1.0.0', 10000],
    ['1.0.1', 10001],
    ['1.2.3', 10203],
    ['2.0.0', 20000],
    ['1.0.99', 10099],
    ['v1.2', 10200],
    ['1', 10000],
    ['1.2', 10200],
  ];
  let codesOk = true;
  for (const [name, code] of cases) {
    if (versionCodeOf(name) !== code) codesOk = false;
  }
  ok('1.2.3 这类版本号换算正确', codesOk, cases.map(([n]) => n + '→' + versionCodeOf(n)).join(' '));
  ok('非法版本号被拒', versionCodeOf('abc') === null && versionCodeOf('1.2.3.4') === null && versionCodeOf('') === null);
  ok('次版本号超过 99 被拒', versionCodeOf('1.100.0') === null);
  ok('编码能还原成版本号', formatVersionCode(10203) === '1.2.3', formatVersionCode(10203));
  ok('解析结果带回三段数字', parseVersionName('1.2.3').major === 1 && parseVersionName('1.2.3').minor === 2);

  // 后台：只给版本号（不再给整数），服务端自己换算
  const byName = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '3.2.1', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('只填版本号也能建（后台自动换算）', byName.body.code === 0, JSON.stringify(byName.body).slice(0, 140));
  const savedName = await getDb().get("SELECT `version_code`, `version_name` FROM `app_version` WHERE `version_name` = '3.2.1'");
  ok('库里存的是 3.2.1 与 30201', Number(savedName?.version_code) === 30201 && savedName?.version_name === '3.2.1', JSON.stringify(savedName));

  // 非法版本号要给人话
  const badName = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '这是中文', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('非法版本号给出人话提示', badName.body.code === 1001 && /1\.2\.3/.test(badName.body.msg), badName.body.msg);

  // 显式传整数但与版本号不符 → 拒绝（避免「显示的版本号」和「比对用的整数」不一致）
  const mismatch = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '4.0.0', version_code: 7, platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('整数与版本号对不上会被拒绝', mismatch.body.code === 1001 && /不一致/.test(mismatch.body.msg), mismatch.body.msg);

  // 编辑时改版本号：内部编码跟着走
  const editRow = await getDb().get("SELECT `id` FROM `app_version` WHERE `version_name` = '3.2.1'");
  const renamed = await adminClient.call('PUT', `${config.security.adminPath}/api/versions/${editRow.id}`, { version_name: '3.2.2' }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('编辑版本号成功', renamed.body.code === 0, JSON.stringify(renamed.body).slice(0, 120));
  const afterEdit = await getDb().get('SELECT `version_code`, `version_name` FROM `app_version` WHERE `id` = ?', [editRow.id]);
  ok('内部编码跟着更新为 30202', Number(afterEdit.version_code) === 30202, JSON.stringify(afterEdit));

  console.log('\n⑳ 客户端编译里的公式必须与服务端一致');
  // 这一项要读客户端的 build.gradle，依赖「服务端与客户端同级」的项目布局。
  // 独立还原出来的服务端只有 server/ 一棵树 —— 这时**跳过而不是崩掉**：
  // 否则一份完好的备份会因为这条断言被误判成「还原失败」（做还原演练时真实踩到）。
  const gradlePath = path.resolve(ROOT, '..', 'doutu', 'app', 'build.gradle');
  if (fs.existsSync(gradlePath)) {
    const gradle = fs.readFileSync(gradlePath, 'utf8');
    ok('build.gradle 用 10000/100 的系数', /10000/.test(gradle) && /minor|nums\[1\]/.test(gradle));
    ok('build.gradle 的 versionCode 由 versionName 换算', /versionCode\s+doutuVersionCode\(/.test(gradle));
  } else {
    console.log(`  ⏭  跳过（没找到客户端工程：${gradlePath}）—— 只部署服务端时属正常`);
  }

  console.log('\n㉑ 「只推给某些版本」是正常说明，不能当成「谁都收不到」来拦');
  // 真实踩过：前台拿 /不会收到/ 去匹配提示文案，把「本次只推给 X，其他版本不会收到提示」
  // 这条正常说明也当成了拦截条件 —— 于是「1.1.0 只推给 1.0.0/1.0.1」这种完全正确的发布被误弹警告。
  await getDb().remove('client_version', {});
  await ask(10000, { device: 'blk-1' });
  await ask(10001, { device: 'blk-2' });
  await new Promise((r) => setTimeout(r, 120));
  await getDb().remove('app_version', { platform: 'android' });

  const valid = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.1.0', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional',
    target_versions: '10000,10001', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('正确的定向发布保存成功', valid.body.code === 0, JSON.stringify(valid.body).slice(0, 160));
  ok('没有 blocker（不该弹警告）', Array.isArray(valid.body.data?.blockers) && valid.body.data.blockers.length === 0, JSON.stringify(valid.body.data?.blockers));
  const validNotes = (valid.body.data?.notes || []).join(' ');
  ok('但有「只推给…」的说明', /只推给/.test(validNotes), validNotes);
  ok('说明里用的是版本号而不是裸编码', /1\.0\.0/.test(validNotes) && /1\.0\.1/.test(validNotes), validNotes);
  ok('保存提示不出现「注意」字样（因为不是警告）', !/注意/.test(valid.body.msg), valid.body.msg);

  // 真正该拦的情况：目标版本 ≥ 本次版本号
  const beyondTarget = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.1.1', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional',
    target_versions: '20000', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('目标比本次还新 → 有 blocker', (beyondTarget.body.data?.blockers || []).length > 0, JSON.stringify(beyondTarget.body.data?.blockers));
  ok('blocker 文案点明了原因', /不会收到这次更新/.test((beyondTarget.body.data?.blockers || []).join(' ')), (beyondTarget.body.data?.blockers || []).join(' '));

  // 版本号没比线上大 → 也是 blocker
  const staleOne = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.5', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('版本号比线上小 → 有 blocker', (staleOne.body.data?.blockers || []).length > 0, JSON.stringify(staleOne.body.data?.blockers));

  await getDb().remove('app_version', { platform: 'android' });

  console.log('\n㉒ 重复版本号的提示要像人话（用版本号，不是内部编码）');
  await getDb().remove('app_version', { platform: 'android' });
  const first102 = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.2', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('第一次建 1.0.2 成功', first102.body.code === 0, JSON.stringify(first102.body).slice(0, 140));
  const again102 = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.2', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('重复时给出人话', again102.body.code === 1001, String(again102.body.code));
  ok('提示里是版本号 1.0.2（不是 10002）', /1\.0\.2/.test(again102.body.msg) && !/10002/.test(again102.body.msg), again102.body.msg);
  ok('提示里给了下一个建议版本号 1.0.3', /1\.0\.3/.test(again102.body.msg), again102.body.msg);
  ok('提示里说了「想改就点编辑」', /编辑/.test(again102.body.msg), again102.body.msg);
  const { bumpVersionName } = await import('../src/core/version.js');
  ok('进位正确：1.0.99 → 1.1.0', bumpVersionName('1.0.99') === '1.1.0', bumpVersionName('1.0.99'));
  ok('进位正确：1.99.99 → 2.0.0', bumpVersionName('1.99.99') === '2.0.0', bumpVersionName('1.99.99'));

  console.log('\n㉓ 最低兼容版本号也收 1.2.3');
  const withMin = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.4', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', changelog: 'x', min_version: '1.0.2',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('填 1.0.2 能保存', withMin.body.code === 0, JSON.stringify(withMin.body).slice(0, 140));
  const minRow = await getDb().get("SELECT `min_version` FROM `app_version` WHERE `version_name` = '1.0.4'");
  ok('落库换算成 10002', Number(minRow?.min_version) === 10002, JSON.stringify(minRow));
  const badMin = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.5', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', min_version: 'abc',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('非法最低版本号给人话', badMin.body.code === 1001 && /1\.0\.0/.test(badMin.body.msg), badMin.body.msg);

  await getDb().remove('app_version', { platform: 'android' });

  console.log('\n㉔ 指定版本也能填版本号（1.0.1），认不出来必须报错而不是默默变成「全部」');
  const targetByName = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.6', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', changelog: 'x',
    target_versions: '1.0.0,1.0.1',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('填版本号能保存', targetByName.body.code === 0, JSON.stringify(targetByName.body).slice(0, 140));
  const rowByName = await getDb().get("SELECT `target_versions` FROM `app_version` WHERE `version_name` = '1.0.6'");
  ok('落库换算成 10000,10001', String(rowByName && rowByName.target_versions) === '10000,10001', String(rowByName && rowByName.target_versions));

  // 关键：看不懂的值必须报错。以前它会被 Number() 变成 NaN 再被过滤掉 → 变成「全部版本」，
  // 也就是「只想推给 1.0.1」悄悄变成「推给所有人」—— 数据面的事故。
  const junkTarget = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.7', platform: 'android', channel: 'all',
    url: 'https://example.com/app.apk', update_mode: 'optional', target_versions: '第一版',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('看不懂的指定版本被拒绝', junkTarget.body.code !== 0, JSON.stringify(junkTarget.body).slice(0, 140));
  ok('没有悄悄存成「全部版本」', !(await getDb().get("SELECT `id` FROM `app_version` WHERE `version_name` = '1.0.7'")));
  ok('提示里说明了正确格式', /1\.0\.1/.test(junkTarget.body.msg || ''), junkTarget.body.msg);

  await getDb().remove('app_version', { platform: 'android' });

  console.log('\n㉕ 下载地址必须像安装包（防止用户下载到网页）');
  const notApk = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.8', platform: 'android', channel: 'all',
    url: 'https://api.lvxiaodong.com/doc/head', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('网页地址被拒绝', notApk.body.code !== 0, JSON.stringify(notApk.body).slice(0, 160));
  ok('提示里说明了原因（下载到的会是网页）', /装不上|不是安装包/.test(notApk.body.msg || ''), notApk.body.msg);
  ok('没有落库', !(await getDb().get("SELECT `id` FROM `app_version` WHERE `version_name` = '1.0.8'")));

  const apkUrl = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.8', platform: 'android', channel: 'all',
    url: 'https://tdapi.luoyun.xyz/upgrade/app/doutu-1.0.1.apk', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('.apk 地址正常通过', apkUrl.body.code === 0, JSON.stringify(apkUrl.body).slice(0, 140));

  // 确实要指向别处时，勾「允许任意地址」可以放行
  const forcedUrl = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.9', platform: 'android', channel: 'all',
    url: 'https://example.com/download?id=7', update_mode: 'optional', changelog: 'x', allow_any_url: true,
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('勾选「允许任意地址」后放行', forcedUrl.body.code === 0, JSON.stringify(forcedUrl.body).slice(0, 140));

  // iOS 不受 .apk 限制（plist 地址本来就不长这样）
  const iosUrl = await adminClient.post(`${config.security.adminPath}/api/versions`, {
    version_name: '1.0.9', platform: 'ios', channel: 'all',
    url: 'https://example.com/app.plist', update_mode: 'optional', changelog: 'x',
  }, { key: 'root', headers: { 'X-Admin-Token': adminToken } });
  ok('iOS 不强制 .apk', iosUrl.body.code === 0, JSON.stringify(iosUrl.body).slice(0, 140));

  await getDb().remove('app_version', {});

  server.close();
  await closeKv();
  await closeDb();
  console.log(`\n结果\n  通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
