/**
 * 数据库访问层：MySQL 8.0（生产）与 SQLite（本地/单机，Node 内置 node:sqlite）双方言。
 *
 * 对上只暴露一套方法：query / get / run / insert / update / upsert / count / tx。
 * 业务代码永远不碰方言差异，差异收敛在三处：
 *   1. 建表语句（ddl.js 生成）；
 *   2. upsert 语法（MySQL: ON DUPLICATE KEY UPDATE / SQLite: ON CONFLICT DO UPDATE）；
 *   3. 行级锁（MySQL: SELECT ... FOR UPDATE / SQLite: 事务本身串行）与金额排序的数值化。
 */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import config from '../config.js';
import { logger } from '../core/logger.js';
import { formatDateTime } from '../core/util.js';
import { emitMysql, emitSqlite } from './ddl.js';
import { tables, columnsOf, hasAutoId } from './schema.js';

const SCHEMA_VERSION = '1';

const sqliteRequire = createRequire(import.meta.url);

/** 标识符引用：MySQL 用反引号，SQLite 用双引号。 */
const qid = (dialect, name) => {
  const safe = String(name).replace(/[^\w]/g, '');
  return dialect === 'mysql' ? `\`${safe}\`` : `"${safe}"`;
};

/** 参数归一化：两个驱动都不接受 undefined / boolean / Date / 对象。 */
function normParam(value) {
  if (value === undefined || value === null) return null;
  if (typeof value === 'boolean') return value ? 1 : 0;
  if (value instanceof Date) return formatDateTime(value);
  if (typeof value === 'object') return JSON.stringify(value);
  return value;
}

const normParams = (params = []) => params.map(normParam);

/** 把 where 对象编译成 SQL 片段：支持等值、IN、IS NULL 与显式比较符。 */
function buildWhere(where = {}, dialect = 'mysql') {
  const clauses = [];
  const params = [];
  for (const [key, raw] of Object.entries(where)) {
    const col = qid(dialect, key);
    if (raw === null) {
      clauses.push(`${col} IS NULL`);
    } else if (Array.isArray(raw)) {
      if (raw.length === 0) {
        clauses.push('1 = 0');
        continue;
      }
      clauses.push(`${col} IN (${raw.map(() => '?').join(', ')})`);
      params.push(...raw);
    } else if (raw && typeof raw === 'object' && 'op' in raw) {
      const allowed = new Set(['>=', '<=', '>', '<', '!=', 'like']);
      if (!allowed.has(raw.op)) throw new Error(`不支持的比较运算符: ${raw.op}`);
      clauses.push(`${col} ${raw.op === 'like' ? 'LIKE' : raw.op} ?`);
      params.push(raw.value);
    } else {
      clauses.push(`${col} = ?`);
      params.push(raw);
    }
  }
  return { sql: clauses.length ? clauses.join(' AND ') : '1 = 1', params };
}

function assertColumns(table, data) {
  const cols = columnsOf.get(table);
  if (!cols) throw new Error(`未定义的表: ${table}`);
  for (const key of Object.keys(data)) {
    if (!cols.has(key)) throw new Error(`表 ${table} 不存在字段 ${key}`);
  }
}

/* ------------------------------------------------------------------ */
/*                              MySQL 实现                             */
/* ------------------------------------------------------------------ */

class MysqlDb {
  constructor(pool) {
    this.dialect = 'mysql';
    this.pool = pool;
  }

  q(name) {
    return qid('mysql', name);
  }

  get lockSuffix() {
    return ' FOR UPDATE';
  }

  /** 金额排序：decimal 在 MySQL 里本身就是数值 */
  moneySort(col) {
    return this.q(col);
  }

  async query(sql, params = []) {
    const [rows] = await this.pool.query(sql, normParams(params));
    return Array.isArray(rows) ? rows : [];
  }

  async get(sql, params = []) {
    const rows = await this.query(sql, params);
    return rows.length ? rows[0] : null;
  }

  async run(sql, params = []) {
    const [result] = await this.pool.query(sql, normParams(params));
    return { affectedRows: result.affectedRows ?? 0, insertId: result.insertId ?? 0 };
  }

  async exec(sql) {
    await this.pool.query(sql);
  }

  async insert(table, data) {
    assertColumns(table, data);
    const keys = Object.keys(data);
    if (!keys.length) throw new Error('insert 需要至少一个字段');
    const sql =
      `INSERT INTO ${this.q(table)} (${keys.map((k) => this.q(k)).join(', ')}) ` +
      `VALUES (${keys.map(() => '?').join(', ')})`;
    return this.run(sql, keys.map((k) => data[k]));
  }

  async update(table, where, data) {
    assertColumns(table, data);
    const keys = Object.keys(data);
    if (!keys.length) return { affectedRows: 0 };
    const w = buildWhere(where, this.dialect);
    const sql =
      `UPDATE ${this.q(table)} SET ${keys.map((k) => `${this.q(k)} = ?`).join(', ')} ` +
      `WHERE ${w.sql}`;
    return this.run(sql, [...keys.map((k) => data[k]), ...w.params]);
  }

  async remove(table, where) {
    const w = buildWhere(where, this.dialect);
    return this.run(`DELETE FROM ${this.q(table)} WHERE ${w.sql}`, w.params);
  }

  async upsert(table, uniqueCols, data, updateCols = []) {
    assertColumns(table, data);
    const keys = Object.keys(data);
    const cols = updateCols.length ? updateCols : keys.filter((k) => !uniqueCols.includes(k));
    const sql =
      `INSERT INTO ${this.q(table)} (${keys.map((k) => this.q(k)).join(', ')}) ` +
      `VALUES (${keys.map(() => '?').join(', ')}) ` +
      (cols.length
        ? `ON DUPLICATE KEY UPDATE ${cols.map((k) => `${this.q(k)} = VALUES(${this.q(k)})`).join(', ')}`
        : '');
    return this.run(sql, keys.map((k) => data[k]));
  }

  async count(table, where = {}) {
    const w = buildWhere(where, this.dialect);
    const row = await this.get(`SELECT COUNT(*) AS n FROM ${this.q(table)} WHERE ${w.sql}`, w.params);
    return Number(row?.n || 0);
  }

  async tx(fn) {
    const conn = await this.pool.getConnection();
    const scoped = new MysqlTx(conn);
    try {
      await conn.beginTransaction();
      const result = await fn(scoped);
      await conn.commit();
      return result;
    } catch (err) {
      try {
        await conn.rollback();
      } catch {
        /* 回滚失败时保留原始异常 */
      }
      throw err;
    } finally {
      conn.release();
    }
  }

  async close() {
    await this.pool.end();
  }
}

class MysqlTx extends MysqlDb {
  constructor(conn) {
    super(null);
    this.conn = conn;
  }

  async query(sql, params = []) {
    const [rows] = await this.conn.query(sql, normParams(params));
    return Array.isArray(rows) ? rows : [];
  }

  async run(sql, params = []) {
    const [result] = await this.conn.query(sql, normParams(params));
    return { affectedRows: result.affectedRows ?? 0, insertId: result.insertId ?? 0 };
  }

  async exec(sql) {
    await this.conn.query(sql);
  }

  async tx(fn) {
    // 事务内再开事务用 SAVEPOINT，避免把外层事务提前提交
    const name = `sp_${Math.random().toString(36).slice(2, 10)}`;
    await this.conn.query(`SAVEPOINT ${name}`);
    try {
      const result = await fn(this);
      await this.conn.query(`RELEASE SAVEPOINT ${name}`);
      return result;
    } catch (err) {
      await this.conn.query(`ROLLBACK TO SAVEPOINT ${name}`);
      throw err;
    }
  }

  async close() {
    /* 事务对象不持有连接 */
  }
}

/* ------------------------------------------------------------------ */
/*                            SQLite 实现                              */
/* ------------------------------------------------------------------ */

class SqliteDb {
  constructor(database) {
    this.dialect = 'sqlite';
    this.database = database;
    this._txChain = Promise.resolve();
  }

  q(name) {
    return qid('sqlite', name);
  }

  get lockSuffix() {
    return '';
  }

  /** SQLite 把 decimal 存成 TEXT，直接排序会按字典序，必须数值化 */
  moneySort(col) {
    return `${this.q(col)} + 0`;
  }

  async query(sql, params = []) {
    const stmt = this.database.prepare(sql);
    return stmt.all(...normParams(params));
  }

  async get(sql, params = []) {
    const stmt = this.database.prepare(sql);
    const row = stmt.get(...normParams(params));
    return row === undefined ? null : row;
  }

  async run(sql, params = []) {
    const stmt = this.database.prepare(sql);
    const info = stmt.run(...normParams(params));
    return {
      affectedRows: Number(info.changes || 0),
      insertId: Number(info.lastInsertRowid || 0),
    };
  }

  async exec(sql) {
    this.database.exec(sql);
  }

  async insert(table, data) {
    assertColumns(table, data);
    const keys = Object.keys(data);
    if (!keys.length) throw new Error('insert 需要至少一个字段');
    const sql =
      `INSERT INTO ${this.q(table)} (${keys.map((k) => this.q(k)).join(', ')}) ` +
      `VALUES (${keys.map(() => '?').join(', ')})`;
    return this.run(sql, keys.map((k) => data[k]));
  }

  async update(table, where, data) {
    assertColumns(table, data);
    const keys = Object.keys(data);
    if (!keys.length) return { affectedRows: 0 };
    const w = buildWhere(where, this.dialect);
    const sql =
      `UPDATE ${this.q(table)} SET ${keys.map((k) => `${this.q(k)} = ?`).join(', ')} ` +
      `WHERE ${w.sql}`;
    return this.run(sql, [...keys.map((k) => data[k]), ...w.params]);
  }

  async remove(table, where) {
    const w = buildWhere(where, this.dialect);
    return this.run(`DELETE FROM ${this.q(table)} WHERE ${w.sql}`, w.params);
  }

  async upsert(table, uniqueCols, data, updateCols = []) {
    assertColumns(table, data);
    const keys = Object.keys(data);
    const cols = updateCols.length ? updateCols : keys.filter((k) => !uniqueCols.includes(k));
    const sql =
      `INSERT INTO ${this.q(table)} (${keys.map((k) => this.q(k)).join(', ')}) ` +
      `VALUES (${keys.map(() => '?').join(', ')}) ` +
      (cols.length
        ? `ON CONFLICT (${uniqueCols.map((k) => this.q(k)).join(', ')}) DO UPDATE SET ` +
          cols.map((k) => `${this.q(k)} = excluded.${this.q(k)}`).join(', ')
        : 'ON CONFLICT DO NOTHING');
    return this.run(sql, keys.map((k) => data[k]));
  }

  async count(table, where = {}) {
    const w = buildWhere(where, this.dialect);
    const row = await this.get(`SELECT COUNT(*) AS n FROM ${this.q(table)} WHERE ${w.sql}`, w.params);
    return Number(row?.n || 0);
  }

  /**
   * SQLite 只有一个连接，事务必须串行：用 Promise 链把并发事务排队，
   * 否则两个请求交错执行 BEGIN/COMMIT 会互相踩。
   */
  async tx(fn) {
    const run = this._txChain.then(
      () => this._txInner(fn),
      () => this._txInner(fn),
    );
    this._txChain = run.catch(() => {});
    return run;
  }

  async _txInner(fn) {
    this.database.exec('BEGIN IMMEDIATE');
    try {
      const result = await fn(this);
      this.database.exec('COMMIT');
      return result;
    } catch (err) {
      try {
        this.database.exec('ROLLBACK');
      } catch {
        /* ignore */
      }
      throw err;
    }
  }

  async close() {
    this.database.close();
  }
}

/* ------------------------------------------------------------------ */
/*                            建库 / 建表                              */
/* ------------------------------------------------------------------ */

async function createMysqlPool() {
  let mysql;
  try {
    mysql = await import('mysql2/promise');
  } catch {
    throw new Error('未安装 mysql2。请在服务端目录执行：npm install mysql2（SQLite 模式无需该依赖）');
  }
  const opts = config.db.mysql;
  // ⚠️ charset 必须显式写 utf8mb4：
  //    mysql2 默认是 UTF8_GENERAL_CI（utf8mb3，3 字节），
  //    库和表的字符集虽然是 utf8mb4，但**连接层**按 utf8mb3 编码，
  //    结果就是公告/昵称里的 emoji（👉 这类 4 字节字符）写不进去或变问号。
  const connOpts = {
    host: opts.host,
    port: opts.port,
    user: opts.user,
    password: opts.password,
    charset: 'utf8mb4',
    multipleStatements: false,
  };
  // 先确保库存在（用不指定 database 的连接）
  const bootstrap = await mysql.createConnection(connOpts);
  await bootstrap.query(
    `CREATE DATABASE IF NOT EXISTS \`${opts.database}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci`,
  );
  await bootstrap.end();

  const pool = mysql.createPool({
    ...opts,
    ...connOpts,
    waitForConnections: true,
    queueLimit: 0,
  });
  return new MysqlDb(pool);
}

function createSqliteDb() {
  // Node >= 22.5 内置 node:sqlite，本地开发零安装
  const { DatabaseSync } = requireSqlite();
  const file = config.db.sqliteFile;
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const database = new DatabaseSync(file);
  database.exec('PRAGMA journal_mode = WAL');
  database.exec('PRAGMA foreign_keys = ON');
  database.exec('PRAGMA busy_timeout = 5000');
  database.exec('PRAGMA synchronous = NORMAL');
  return new SqliteDb(database);
}

/** node:sqlite 只能通过 CJS require 拿到（ESM 下用 createRequire 桥接）。 */
function requireSqlite() {
  try {
    return sqliteRequire('node:sqlite');
  } catch (err) {
    throw new Error(
      `当前 Node 版本无法加载 node:sqlite（需要 Node >= 22.5，当前 ${process.version}）。` +
        `请升级 Node，或把 DB_DIALECT 改成 mysql。原始错误：${err.message}`,
    );
  }
}

/**
 * 轻量迁移：给**已存在**的表补字段。
 * `CREATE TABLE IF NOT EXISTS` 不会给老表加列，所以新增字段必须在这里登记一次，
 * 否则老库（本地 SQLite / 线上 MySQL）跑起来会报「no such column」。
 */
const COLUMN_MIGRATIONS = [
  {
    // 「更新推送指定版本」（2026-10-17）：留空=所有版本都收；填了则只有名单里的
    // 来源 version_code 才会看到这条更新（强制/非强制都一样，见 /version/check）。
    table: 'app_version',
    column: 'target_versions',
    mysql: "VARCHAR(255) NULL COMMENT '指定哪些来源版本能收到本更新（逗号分隔 version_code）；空=全部'",
    sqlite: 'TEXT',
  },
  {
    table: 'app_user',
    column: 'avatar',
    mysql: "VARCHAR(255) NULL COMMENT '头像在存储里的 key'",
    sqlite: 'TEXT',
  },
];

async function ensureColumns(db) {
  for (const m of COLUMN_MIGRATIONS) {
    let existing = [];
    try {
      if (db.dialect === 'mysql') {
        existing = (await db.query(`SHOW COLUMNS FROM \`${m.table}\``)).map((r) => r.Field);
      } else {
        existing = (await db.query(`PRAGMA table_info(\`${m.table}\`)`)).map((r) => r.name);
      }
    } catch {
      continue; // 表还不存在（首次建库），CREATE 时已经带了这一列
    }
    if (!existing.includes(m.column)) {
      await db.exec(`ALTER TABLE \`${m.table}\` ADD COLUMN \`${m.column}\` ${m[db.dialect]}`);
    }
  }
}

/** 建表（幂等）。 */
export async function initSchema(db) {
  const statements = db.dialect === 'mysql' ? emitMysql(tables) : emitSqlite(tables);
  for (const sql of statements) {
    await db.exec(sql);
  }
  await ensureColumns(db);
  const existing = await db
    .get('SELECT `value` FROM `schema_meta` WHERE `key` = ?', ['schema_version'])
    .catch(() => null);
  if (existing) {
    await db.update(
      'schema_meta',
      { key: 'schema_version' },
      { value: SCHEMA_VERSION, updated_at: formatDateTime() },
    );
  } else {
    await db.insert('schema_meta', {
      key: 'schema_version',
      value: SCHEMA_VERSION,
      updated_at: formatDateTime(),
    });
  }
  return statements.length;
}

/** 删除所有表（仅 --drop 时调用）。 */
export async function dropAll(db) {
  if (db.dialect === 'mysql') {
    await db.exec('SET FOREIGN_KEY_CHECKS = 0');
    for (const t of [...tables].reverse()) await db.exec(`DROP TABLE IF EXISTS ${qid('mysql', t.name)}`);
    await db.exec('SET FOREIGN_KEY_CHECKS = 1');
    return;
  }
  for (const t of [...tables].reverse()) await db.exec(`DROP TABLE IF EXISTS ${qid('sqlite', t.name)}`);
}

/* ------------------------------------------------------------------ */
/*                              单例                                   */
/* ------------------------------------------------------------------ */

let current = null;

export async function openDb({ dialect = config.db.dialect } = {}) {
  if (current) return current;
  current = dialect === 'mysql' ? await createMysqlPool() : createSqliteDb();
  logger.info('数据库已连接', {
    dialect: current.dialect,
    target: dialect === 'sqlite' ? config.db.sqliteFile : config.db.mysql.database,
  });
  return current;
}

export function getDb() {
  if (!current) throw new Error('数据库尚未初始化，请先 await openDb()');
  return current;
}

export async function closeDb() {
  if (!current) return;
  await current.close();
  current = null;
}

export { qid, tables, hasAutoId };
export default { openDb, getDb, closeDb, initSchema, dropAll };
