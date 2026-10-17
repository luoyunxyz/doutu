#!/usr/bin/env node
/**
 * 建库建表（+ 可选初始数据）。
 *
 *   node scripts/init-db.js                 # 建表 + 写初始数据
 *   node scripts/init-db.js --drop          # 先删表再建（危险：数据全清）
 *   node scripts/init-db.js --no-seed       # 只建表
 */
import { openDb, initSchema, dropAll, closeDb } from '../src/db/index.js';
import { seedAll } from '../src/db/seed.js';
import config from '../src/config.js';

const args = new Set(process.argv.slice(2));

async function main() {
  const db = await openDb();
  console.log(`[init-db] 方言=${db.dialect} 目标=${db.dialect === 'sqlite' ? config.db.sqliteFile : config.db.mysql.database}`);

  if (args.has('--drop')) {
    console.log('[init-db] 删除既有表 …');
    await dropAll(db);
  }

  const count = await initSchema(db);
  console.log(`[init-db] 建表语句执行完成：${count} 条`);

  if (!args.has('--no-seed')) {
    await seedAll(db);
    console.log('[init-db] 初始数据写入完成');
  }

  const tables = await db.query(
    db.dialect === 'mysql'
      ? 'SELECT TABLE_NAME AS name FROM information_schema.tables WHERE TABLE_SCHEMA = ?'
      : "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
    db.dialect === 'mysql' ? [config.db.mysql.database] : [],
  );
  console.log(`[init-db] 当前表数量：${tables.length}`);
  await closeDb();
}

main().catch(async (err) => {
  console.error('[init-db] 失败：', err);
  await closeDb().catch(() => {});
  process.exit(1);
});
