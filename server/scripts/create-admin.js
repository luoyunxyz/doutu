#!/usr/bin/env node
/**
 * 创建 / 重置后台管理员账号。
 *
 * 用法：
 *   node scripts/create-admin.js --username=admin --password=YourPassw0rd --role=1
 *   node scripts/create-admin.js --username=admin --reset            # 只重置密码（随机生成）
 *
 * role: 1超管 2运营 3客服 4财务（见 src/db/seed.js 的角色表）
 */
import { openDb, initSchema, getDb, closeDb } from '../src/db/index.js';
import { seedAll } from '../src/db/seed.js';
import { hashPassword } from '../src/core/crypto.js';
import { now, randomToken } from '../src/core/util.js';
import { withRuntime, parseArgs } from '../src/cron/lib.js';

const args = parseArgs();

await withRuntime('create-admin', async () => {
  const db = getDb();
  await seedAll(db);

  const username = String(args.username || 'admin');
  const roleId = Number(args.role || 1);
  const reset = Boolean(args.reset);

  let password = args.password && args.password !== true ? String(args.password) : null;
  if (!password) password = randomToken(6);
  if (password.length < 8) {
    throw new Error('密码至少 8 位（或用 --reset 让脚本生成随机密码）');
  }

  const existing = await db.get('SELECT `id` FROM `admin` WHERE `username` = ?', [username]);
  if (existing) {
    if (!reset && !args.password) {
      throw new Error(`管理员 ${username} 已存在。若要重置密码请加 --reset`);
    }
    await db.update(
      'admin',
      { id: existing.id },
      { password_hash: hashPassword(password), role_id: roleId, status: 1, updated_at: now() },
    );
    await db.update('admin_token', { admin_id: existing.id, revoked: 0 }, { revoked: 1 });
    console.log(`\n[create-admin] 已重置管理员：${username}（角色 ${roleId}）`);
  } else {
    await db.insert('admin', {
      username,
      password_hash: hashPassword(password),
      real_name: args.real_name && args.real_name !== true ? String(args.real_name) : null,
      role_id: roleId,
      phone: null,
      totp_secret: null,
      status: 1,
      created_at: now(),
      updated_at: now(),
    });
    console.log(`\n[create-admin] 已创建管理员：${username}（角色 ${roleId}）`);
  }
  console.log(`  登录地址：http://<你的域名>/admin`);
  console.log(`  用户名  ：${username}`);
  console.log(`  初始密码：${password}`);
  console.log('  ⚠️  请首次登录后立即在「面板管理 → 账号安全」里修改密码并开启 TOTP 二次验证\n');
  return { username, role: roleId };
});

void openDb;
void initSchema;
void closeDb;
