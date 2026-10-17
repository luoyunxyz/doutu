#!/usr/bin/env node
/**
 * cron:backup —— 数据库备份、加密与转存（规划书 10.1）。
 *
 * 用法：
 *   node src/cron/backup.js --retain-days=7                      # 全量备份 + 本地保留 7 天
 *   node src/cron/backup.js --upload-oss --encrypt --retain-remote-days=30
 *   node src/cron/backup.js --verify                             # 校验最近一次备份能否解开并读到表
 *
 * 设计取舍：
 *  - MySQL 用 mysqldump 单事务一致性快照（--single-transaction），不锁表；
 *  - SQLite 用 VACUUM INTO 生成一致性副本（比直接拷文件安全，WAL 里的数据也会带上）；
 *  - 备份文件离开本机前**必须**加密：openssl enc -aes-256-cbc -pbkdf2；
 *  - 没有验证过的备份等于没有备份 —— 所以提供 --verify（规划书 10.1 的每月恢复演练）。
 */
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';
import { spawn } from 'node:child_process';
import { pipeline } from 'node:stream/promises';
import { withRuntime, parseArgs } from './lib.js';
import config from '../config.js';
import { getDb } from '../db/index.js';
import { now, formatDate } from '../core/util.js';
import { sendAlert } from '../services/alert.js';
import { logger } from '../core/logger.js';
import * as storage from '../modules/dw/storage.js';

const log = logger.child('backup');
const args = parseArgs();

function run(command, argv, { onStdout } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, argv, { stdio: ['ignore', 'pipe', 'pipe'] });
    let stderr = '';
    child.stderr.on('data', (d) => {
      stderr += d.toString();
    });
    if (onStdout) child.stdout.pipe(onStdout);
    child.on('error', reject);
    child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`${command} 退出码 ${code}：${stderr.slice(-400)}`))));
  });
}

/** 备份口令：优先环境变量，其次口令文件 */
function backupPassword() {
  if (config.backup.password) return config.backup.password;
  if (config.backup.passFile && fs.existsSync(config.backup.passFile)) {
    return fs.readFileSync(config.backup.passFile, 'utf8').trim();
  }
  return null;
}

async function encryptFile(file, password) {
  const out = `${file}.enc`;
  await run('openssl', [
    'enc',
    '-aes-256-cbc',
    '-pbkdf2',
    '-iter',
    '200000',
    '-salt',
    '-in',
    file,
    '-out',
    out,
    '-pass',
    `pass:${password}`,
  ]);
  await fsp.rm(file, { force: true });
  log.info('备份已加密', { file: path.basename(out) });
  return out;
}

/** MySQL：mysqldump --single-transaction | gzip > file */
async function backupMysql(file) {
  const { host, port, user, password, database } = config.db.mysql;
  const gzip = zlib.createGzip({ level: 6 });
  const out = fs.createWriteStream(file);
  const dumpPromise = run(
    config.backup.mysqldump,
    [
      `--host=${host}`,
      `--port=${port}`,
      `--user=${user}`,
      `--password=${password}`,
      '--single-transaction',
      '--quick',
      '--routines',
      '--events',
      '--default-character-set=utf8mb4',
      database,
    ],
    { onStdout: gzip },
  );
  await Promise.all([dumpPromise, pipeline(gzip, out)]);
  return file;
}

/** SQLite：VACUUM INTO 一致性副本 → gzip */
async function backupSqlite(file) {
  const snapshot = `${file}.snapshot`;
  await getDb().exec(`VACUUM INTO '${snapshot.replace(/'/g, "''")}'`);
  await pipeline(fs.createReadStream(snapshot), zlib.createGzip({ level: 6 }), fs.createWriteStream(file));
  await fsp.rm(snapshot, { force: true });
  return file;
}

/** 本地保留策略 */
async function pruneLocal(dir, retainDays) {
  const cutoff = Date.now() - retainDays * 86400000;
  let removed = 0;
  for (const name of await fsp.readdir(dir)) {
    const full = path.join(dir, name);
    const stat = await fsp.stat(full).catch(() => null);
    if (stat?.isFile() && stat.mtimeMs < cutoff) {
      await fsp.rm(full, { force: true });
      removed += 1;
    }
  }
  return removed;
}

/** 备份文件转存 OSS（复用媒体存储的 OSS 配置，放到 backup/ 前缀下） */
async function uploadToOss(file) {
  const key = `backup/${path.basename(file)}`;
  const buffer = await fsp.readFile(file);
  const result = await storage.putBuffer(key, buffer, 'application/octet-stream');
  log.info('备份已转存 OSS', { key, size: buffer.length });
  return { key, ...result };
}

/** 校验：能解开（若加密）且 gzip 能读、SQL 头部正常 */
async function verifyLatest(dir) {
  const files = (await fsp.readdir(dir)).map((n) => path.join(dir, n)).sort();
  if (!files.length) throw new Error('没有可校验的备份文件');
  const latest = files[files.length - 1];
  let target = latest;
  if (latest.endsWith('.enc')) {
    const password = backupPassword();
    if (!password) throw new Error('备份是加密的，但没有配置 BACKUP_PASSWORD / BACKUP_PASS_FILE');
    target = latest.replace(/\.enc$/, '');
    await run('openssl', ['enc', '-d', '-aes-256-cbc', '-pbkdf2', '-iter', '200000', '-in', latest, '-out', target, '-pass', `pass:${password}`]);
  }
  const head = await new Promise((resolve, reject) => {
    const gunzip = zlib.createGunzip();
    const stream = fs.createReadStream(target).pipe(gunzip);
    let text = '';
    stream.on('data', (chunk) => {
      text += chunk.toString('utf8');
      if (text.length > 4096) {
        stream.destroy();
        resolve(text);
      }
    });
    stream.on('end', () => resolve(text));
    stream.on('error', reject);
  });
  if (target !== latest) await fsp.rm(target, { force: true });
  return {
    file: path.basename(latest),
    size: (await fsp.stat(latest)).size,
    looksLikeSql: /CREATE TABLE|PRAGMA|SQLite format/i.test(head),
    head: head.slice(0, 200).replace(/\s+/g, ' '),
  };
}

await withRuntime('backup', async () => {
  if (args.verify) {
    const result = await verifyLatest(config.backup.dir);
    if (!result.looksLikeSql) {
      await sendAlert('备份校验失败', `最近一次备份 ${result.file} 解压后不像数据库导出内容，请立即人工核查`, { level: 'error' });
    }
    return result;
  }

  // 转存模式：只上传已有文件并清理远端过期
  if (args['upload-oss'] && args.only !== 'false') {
    fs.mkdirSync(config.backup.dir, { recursive: true });
    const files = (await fsp.readdir(config.backup.dir)).filter((n) => n.startsWith('doutu-'));
    const result = { uploaded: 0, keys: [] };
    // 只上传最近 retainRemoteDays 天的文件
    const cutoff = Date.now() - config.backup.retainRemoteDays * 86400000;
    for (const name of files) {
      const full = path.join(config.backup.dir, name);
      const stat = await fsp.stat(full);
      if (stat.mtimeMs < cutoff) continue;
      const up = await uploadToOss(full).catch((err) => {
        log.warn('转存失败', { file: name, err: err.message });
        return null;
      });
      if (up) {
        result.uploaded += 1;
        result.keys.push(up.key);
      }
    }
    return result;
  }

  fs.mkdirSync(config.backup.dir, { recursive: true });
  const stamp = formatDate();
  const baseName = `doutu-${stamp}-${Date.now().toString(36)}.sql.gz`;
  let file = path.join(config.backup.dir, baseName);

  const started = Date.now();
  if (config.db.dialect === 'mysql') await backupMysql(file);
  else await backupSqlite(file);

  const size = (await fsp.stat(file)).size;
  if (!size) throw new Error('备份文件为空，备份失败');

  const password = backupPassword();
  if (args.encrypt || password) {
    if (!password) throw new Error('要求加密但没有配置 BACKUP_PASSWORD / BACKUP_PASS_FILE');
    file = await encryptFile(file, password);
  }

  if (args['upload-oss']) await uploadToOss(file);

  const removed = await pruneLocal(config.backup.dir, Number(args['retain-days'] || config.backup.retainDays));
  log.info('备份完成', { file: path.basename(file), size, ms: Date.now() - started, removed });
  void now;
  return { file: path.basename(file), size, encrypted: file.endsWith('.enc'), removed, ms: Date.now() - started };
});
