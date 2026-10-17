#!/usr/bin/env node
/**
 * 生成邀请码（命令行，等价于后台「邀请码管理 → 生成邀请码」）。
 *
 * 业务规则：**一个邀请码只能创建一个账号**。
 * 客户端凭邀请码 + 手机号 + 两次密码自助注册；注册出来默认未激活，
 * 是否开通由后台「账号管理 → 激活账号」决定（未激活时客户端会叠加水印）。
 *
 * 用法：
 *   node scripts/gen-invites.js --count=10
 *   node scripts/gen-invites.js --count=5 --days=30 --level=2 --quota=30 --out=invites.txt
 *   node scripts/gen-invites.js --count=3 --expire=2026-12-31 --remark=双十一活动
 *
 * 参数：
 *   --count   数量（默认 10，上限 10 万）
 *   --days    预设时长(天)。0 = 注册后仍需后台激活（默认 0）
 *   --level   注册后预设等级 1普通 2月卡 3季卡 4年卡 5永久（默认 1）
 *   --device  预设绑定设备数（默认 1）
 *   --quota   预设每日配额，0 = 不限量（默认 20）
 *   --expire  邀请码自身的有效期 YYYY-MM-DD（默认不限）
 *   --remark  备注
 *   --out     导出到文件（每行一个带横线的邀请码）
 */
import fs from 'node:fs';
import { withRuntime, parseArgs } from '../src/cron/lib.js';
import * as inviteService from '../src/services/invite.js';

const args = parseArgs();

await withRuntime('gen-invites', async () => {
  const count = Math.max(1, Math.min(100000, Number(args.count) || 10));
  const days = args.days === undefined ? 0 : Number(args.days);
  const level = Number(args.level || 1);
  const quotaDay = args.quota === undefined ? 20 : Number(args.quota);
  const maxDevice = Number(args.device || 1);

  const result = await inviteService.createBatch({
    count,
    duration: days,
    level,
    quotaDay,
    maxDevice,
    expireAt: args.expire && args.expire !== true ? String(args.expire) : null,
    remark: args.remark && args.remark !== true ? String(args.remark) : '命令行生成',
    adminId: null,
  });

  const display = result.codes.map(inviteService.formatInviteCode);

  console.log('');
  console.log(`批次号  ：${result.batch_no}`);
  console.log(`数量    ：${display.length}`);
  console.log(`注册后  ：${days === 0 ? '未激活（需后台激活）' : `自动开通 ${days} 天`}`);
  console.log(`等级/设备/配额：${level} / ${maxDevice} / ${quotaDay === 0 ? '不限量' : quotaDay}`);
  console.log(`有效期  ：${args.expire && args.expire !== true ? args.expire : '不限'}`);
  console.log(''.padEnd(60, '-'));
  for (const code of display) console.log(code);
  console.log(''.padEnd(60, '-'));
  console.log('一个邀请码只能创建一个账号。');

  if (args.out && args.out !== true) {
    fs.writeFileSync(String(args.out), `\uFEFF${display.join('\r\n')}\r\n`, 'utf8');
    console.log(`已导出：${args.out}`);
  }
  return { batch_no: result.batch_no, count: display.length };
});
