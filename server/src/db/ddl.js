/**
 * DDL 生成器：同一份表定义 → MySQL 8.0 与 SQLite 两种建表语句。
 *
 * 为什么用生成而不是两份 .sql：
 *   两份手写 DDL 一定会漂移（改了 MySQL 忘了 SQLite）。这里表定义只有一处，
 *   `npm run db:sql` 会把生成结果落到 sql/schema.mysql.sql 与 sql/schema.sqlite.sql，
 *   可以直接导入 / 进版本库 / 交 DBA 审。
 *
 * 类型映射（关键差异，见 README 的「双方言说明」）：
 *   decimal(10,2) → TEXT  存 "12.34" 字符串（SQLite 没有精确十进制，NUMERIC 会退化成浮点，
 *                          金额绝不能用浮点，所以用字符串 + 全整数分运算，见 core/money.js）
 *   datetime      → TEXT  存 "YYYY-MM-DD HH:mm:ss"
 *   json          → TEXT  存 JSON 字符串（MySQL 的 json 类型会自动解析，双方言行为会不一致）
 */

const IDENT_RE = /^[A-Za-z_][A-Za-z0-9_]*$/;

function assertIdent(name) {
  if (!IDENT_RE.test(name)) throw new Error(`非法标识符: ${name}`);
  return name;
}

const MYSQL_TYPES = {
  id: 'bigint UNSIGNED',
  bigint: 'bigint UNSIGNED',
  int: 'int',
  tinyint: 'tinyint',
  varchar: (c) => `varchar(${c.len || 255})`,
  char: (c) => `char(${c.len || 32})`,
  decimal: (c) => `decimal(${c.precision || 10},2)`,
  date: 'date',
  datetime: 'datetime',
  text: 'text',
};

const SQLITE_TYPES = {
  id: 'INTEGER',
  bigint: 'INTEGER',
  int: 'INTEGER',
  tinyint: 'INTEGER',
  varchar: 'TEXT',
  char: 'TEXT',
  decimal: 'TEXT',
  date: 'TEXT',
  datetime: 'TEXT',
  text: 'TEXT',
};

const q = (dialect, ident) => (dialect === 'mysql' ? `\`${assertIdent(ident)}\`` : `"${assertIdent(ident)}"`);

function defaultValue(dialect, col) {
  if (col.default === undefined) return '';
  const v = col.default;
  if (v === null) return ' DEFAULT NULL';
  if (typeof v === 'number') return ` DEFAULT ${v}`;
  if (v === 'CURRENT_TIMESTAMP') {
    return dialect === 'mysql' ? ' DEFAULT CURRENT_TIMESTAMP' : " DEFAULT (datetime('now','localtime'))";
  }
  return ` DEFAULT '${String(v).replace(/'/g, "''")}'`;
}

function columnClause(dialect, col) {
  const types = dialect === 'mysql' ? MYSQL_TYPES : SQLITE_TYPES;
  const t = types[col.type];
  if (!t) throw new Error(`未知字段类型: ${col.type}`);
  const type = typeof t === 'function' ? t(col) : t;

  if (col.type === 'id') {
    if (dialect === 'mysql') return `${q(dialect, col.name)} ${type} NOT NULL AUTO_INCREMENT`;
    return `${q(dialect, col.name)} ${type} PRIMARY KEY AUTOINCREMENT`;
  }
  if (col.type === 'bigint') {
    return `${q(dialect, col.name)} ${type}${col.notNull ? ' NOT NULL' : ''}${defaultValue(dialect, col)}`;
  }
  const parts = [`${q(dialect, col.name)} ${type}`];
  if (col.notNull) parts.push('NOT NULL');
  const def = defaultValue(dialect, col);
  if (def) parts.push(def.trim());
  if (dialect === 'mysql' && col.comment) parts.push(`COMMENT '${col.comment.replace(/'/g, "''")}'`);
  return parts.join(' ');
}

const indexName = (prefix, cols) => `${prefix}_${cols.join('_')}`;

/** 生成建表语句数组（每个元素是一条可独立执行的 SQL）。 */
export function emitSchema(dialect, tables) {
  const statements = [];
  for (const table of tables) {
    assertIdent(table.name);
    const lines = table.columns.map((c) => `  ${columnClause(dialect, c)}`);
    // 少数聚合表没有自增 id（如 revenue_daily 用 stat_date 做主键）
    const pkCol = table.primaryKey || (table.columns.some((c) => c.type === 'id') ? 'id' : null);
    if (!pkCol) throw new Error(`表 ${table.name} 缺少主键定义`);

    if (dialect === 'mysql') {
      lines.push(`  PRIMARY KEY (${q(dialect, pkCol)})`);
      for (const cols of table.uniques || []) {
        lines.push(`  UNIQUE KEY ${q(dialect, indexName('uk', cols))} (${cols.map((c) => q(dialect, c)).join(', ')})`);
      }
      for (const cols of table.indexes || []) {
        lines.push(`  KEY ${q(dialect, indexName('idx', cols))} (${cols.map((c) => q(dialect, c)).join(', ')})`);
      }
      const suffix = `) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='${(table.comment || table.name).replace(/'/g, "''")}'`;
      statements.push(`CREATE TABLE IF NOT EXISTS ${q(dialect, table.name)} (\n${lines.join(',\n')}\n${suffix};`);
      continue;
    }

    // SQLite：自增 id 列上已经写了 INTEGER PRIMARY KEY AUTOINCREMENT，不能再加表级主键
    const idIsInline = pkCol === 'id' && table.columns.some((c) => c.type === 'id');
    if (!idIsInline) lines.push(`  PRIMARY KEY (${q(dialect, pkCol)})`);
    statements.push(`CREATE TABLE IF NOT EXISTS ${q(dialect, table.name)} (\n${lines.join(',\n')}\n);`);
    for (const cols of table.uniques || []) {
      statements.push(
        `CREATE UNIQUE INDEX IF NOT EXISTS ${q(dialect, indexName('uk', cols))} ON ${q(dialect, table.name)} (${cols
          .map((c) => q(dialect, c))
          .join(', ')});`,
      );
    }
    for (const cols of table.indexes || []) {
      statements.push(
        `CREATE INDEX IF NOT EXISTS ${q(dialect, indexName('idx', cols))} ON ${q(dialect, table.name)} (${cols
          .map((c) => q(dialect, c))
          .join(', ')});`,
      );
    }
  }
  return statements;
}

export const emitMysql = (tables) => emitSchema('mysql', tables);
export const emitSqlite = (tables) => emitSchema('sqlite', tables);
export { q as quoteIdent };
