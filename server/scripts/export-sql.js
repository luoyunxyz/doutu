#!/usr/bin/env node
/**
 * 把代码里的表定义导出成可人工审阅、可直接导入的 SQL 文件：
 *   sql/schema.mysql.sql      —— 生产 MySQL 8.0 建表
 *   sql/schema.sqlite.sql     —— 本地 SQLite 建表
 *
 * 这两个文件是生成产物，改结构请改 src/db/schema.js 后重新生成（npm run db:sql）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { emitMysql, emitSqlite } from '../src/db/ddl.js';
import { tables } from '../src/db/schema.js';

const outDir = path.resolve(import.meta.dirname, '../sql');
fs.mkdirSync(outDir, { recursive: true });

const header = (label) =>
  [
    '-- ============================================================',
    `--  短视频去水印平台 · ${label} 建表脚本`,
    '--  由 src/db/schema.js 自动生成，请勿手工修改',
    '--  改结构后执行：npm run db:sql',
    `--  生成时间：${new Date().toISOString()}`,
    '-- ============================================================',
    '',
  ].join('\n');

const mysql = `${header('MySQL 8.0')}SET NAMES utf8mb4;\n\n${emitMysql(tables).join('\n\n')}\n`;
const sqlite = `${header('SQLite')}PRAGMA journal_mode = WAL;\n\n${emitSqlite(tables).join('\n\n')}\n`;

fs.writeFileSync(path.join(outDir, 'schema.mysql.sql'), mysql, 'utf8');
fs.writeFileSync(path.join(outDir, 'schema.sqlite.sql'), sqlite, 'utf8');

console.log('[db:sql] 已生成：');
console.log(`  ${path.join(outDir, 'schema.mysql.sql')}  (${mysql.split('\n').length} 行)`);
console.log(`  ${path.join(outDir, 'schema.sqlite.sql')} (${sqlite.split('\n').length} 行)`);
