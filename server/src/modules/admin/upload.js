/**
 * 后台上传 / 管理安装包（2026-10-17）。
 *
 * 为什么要有它：以前发版本得先用 scp 把 APK 传到服务器、再手填下载地址 ——
 * 运营根本做不到，而且中途断了会留下**截断的包**（真实踩到：12.9MB 的半个包挂在线上，
 * 用户下到会装不上）。现在后台自己传、自己管：
 *
 *   POST   /admin/api/versions/upload?name=doutu-1.1.0.apk   原始二进制流（不是 multipart）
 *   GET    /admin/api/versions/files                          列出已上传的包
 *   DELETE /admin/api/versions/files/<name>                   删包
 *
 * 为什么用「原始流」而不是 multipart：core/http.js 对普通请求是把整个 body 读进内存的
 * （默认上限 2MB），36MB 的包必须先落盘。路由声明 `streamBody = true` 后框架就不读体了，
 * 这里直接把 req 流管道写进临时文件 —— 内存占用恒定，断流也不会污染正式文件。
 */
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';

import { ApiError, ERROR } from '../../core/errors.js';
import { logger } from '../../core/logger.js';
import config from '../../config.js';

/** 安装包存放目录（与静态下载路由 /upgrade/app/** 指向同一处） */
export const APK_DIR = path.join(config.rootDir, 'public', 'upgrade', 'app');
/** 单个包的上限：够放任何正常 Android 包，又不至于被人拖垮磁盘 */
const MAX_APK_BYTES = 300 * 1024 * 1024;
/** 文件名白名单：只允许字母数字点横线下划线，且必须以 .apk 结尾 */
const APK_NAME = /^[A-Za-z0-9._-]+\.apk$/;

export function apkUrl(name) {
  return `https://tdapi.luoyun.xyz/upgrade/app/${name}`;
}

/** 文件名安全校验：挡掉路径穿越与奇怪后缀 */
export function safeApkName(raw) {
  const text = String(raw || '').trim();
  // 带路径分隔符/上跳的一律**直接拒绝**，而不是 basename 之后悄悄改名 ——
  // 悄悄改名会让人以为「传的是 a，存下来变成 b」，出问题更难查。
  if (text.includes('/') || text.includes('\\') || text.includes('..')) {
    throw new ApiError(ERROR.PARAM, `文件名「${text}」不合法：只能是一个纯文件名，不能带路径`);
  }
  const name = path.basename(text);
  if (!APK_NAME.test(name)) {
    throw new ApiError(
      ERROR.PARAM,
      `文件名「${raw || '空'}」不合法：只允许字母/数字/点/横线/下划线，并且必须以 .apk 结尾`,
    );
  }
  return name;
}

/** 列目录（按修改时间倒序） */
export async function listApks() {
  await fsp.mkdir(APK_DIR, { recursive: true });
  const names = await fsp.readdir(APK_DIR);
  const out = [];
  for (const name of names) {
    if (!name.toLowerCase().endsWith('.apk')) continue;
    const stat = await fsp.stat(path.join(APK_DIR, name)).catch(() => null);
    if (!stat || !stat.isFile()) continue;
    out.push({ name, size: stat.size, mtime: stat.mtime.toISOString(), url: apkUrl(name) });
  }
  out.sort((a, b) => String(b.mtime).localeCompare(String(a.mtime)));
  return out;
}

/**
 * 把请求体流式落盘。
 *
 * 三个安全点：
 *   1. 先写 `<name>.part`，**校验通过才 rename** —— 中途断线/超限时线上永远不会出现半个包；
 *   2. 边写边数，超过上限立刻中断并删除临时文件；
 *   3. 写完检查 APK 魔数（ZIP 的 "PK\x03\x04"），挡住「传错文件」。
 */
export async function receiveApk(req, rawName) {
  const name = safeApkName(rawName);
  await fsp.mkdir(APK_DIR, { recursive: true });
  const target = path.join(APK_DIR, name);
  const temp = `${target}.part`;

  let written = 0;
  let tooBig = false;
  let header = Buffer.alloc(0);
  req.on('data', (chunk) => {
    written += chunk.length;
    if (header.length < 4) header = Buffer.concat([header, chunk]).subarray(0, 4);
    if (written > MAX_APK_BYTES) tooBig = true;
  });

  try {
    await pipeline(req, fs.createWriteStream(temp));
  } catch (err) {
    await fsp.rm(temp, { force: true });
    throw new ApiError(ERROR.PARAM, `上传中断：${err.message}`);
  }

  if (tooBig) {
    await fsp.rm(temp, { force: true });
    throw new ApiError(ERROR.PARAM, `安装包不能超过 ${Math.round(MAX_APK_BYTES / 1024 / 1024)}MB`);
  }
  if (!written) {
    await fsp.rm(temp, { force: true });
    throw new ApiError(ERROR.PARAM, '没有收到文件内容，请重新选择 APK');
  }
  // APK 本质是 zip：开头必须是 "PK\x03\x04"（空 zip 是 "PK\x05\x06"，也放行）
  const isZip = header.length >= 4 && header[0] === 0x50 && header[1] === 0x4b;
  if (!isZip) {
    await fsp.rm(temp, { force: true });
    throw new ApiError(ERROR.PARAM, '这个文件不像 APK（安装包本质是 zip），请确认选对了文件');
  }

  await fsp.rename(temp, target);
  logger.info('安装包已上传', { name, size: written });
  return { name, size: written, url: apkUrl(name) };
}

export async function removeApk(rawName) {
  const name = safeApkName(rawName);
  const target = path.join(APK_DIR, name);
  const stat = await fsp.stat(target).catch(() => null);
  if (!stat) throw new ApiError(ERROR.NOT_FOUND, '这个安装包不存在');
  await fsp.rm(target, { force: true });
  logger.info('安装包已删除', { name });
  return { name, size: stat.size };
}

export default { APK_DIR, apkUrl, safeApkName, listApks, receiveApk, removeApk };
