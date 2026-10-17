/**
 * 生成 DTW/1 私有协议需要的密钥材料。
 *
 *   node scripts/gen-dtw-keys.js            # 打印到屏幕
 *   node scripts/gen-dtw-keys.js --write    # 直接写进 .env（缺失项才追加）
 *
 * 产出三条：
 *   DTW_PRIVATE_KEY  —— RSA-2048 私钥（PEM，单行 \n 转义），**只放服务端**
 *   DTW_SALT         —— 客户端 key 派生用的盐（64 位十六进制）
 *   DTW_SALT_ID      —— 盐的代号，轮换时改它就能让旧客户端握手失效
 *
 * 客户端只需要公钥（可公开）+ 盐（可公开），不会拿到私钥。
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const ENV_FILE = path.join(ROOT, '.env');
const write = process.argv.includes('--write');

const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});

const salt = crypto.randomBytes(32).toString('hex');
const saltId = 's' + new Date().toISOString().slice(0, 10).replace(/-/g, '');

const oneLine = privateKey.trim().replace(/\n/g, '\\n');

console.log('===== 客户端内置（可公开）=====');
console.log(publicKey.trim());
console.log('');
console.log('salt      =', salt);
console.log('salt_id   =', saltId);
console.log('fingerprint =', crypto.createHash('sha256').update(publicKey).digest('hex').slice(0, 16));
console.log('');
console.log('===== 服务端 .env（私密）=====');
console.log('DTW_PRIVATE_KEY=' + oneLine);
console.log('DTW_SALT=' + salt);
console.log('DTW_SALT_ID=' + saltId);

if (write) {
  const existing = fs.existsSync(ENV_FILE) ? fs.readFileSync(ENV_FILE, 'utf8') : '';
  const has = (key) => new RegExp(`^${key}=`, 'm').test(existing);
  const lines = [];
  if (!has('DTW_PRIVATE_KEY')) lines.push(`DTW_PRIVATE_KEY=${oneLine}`);
  if (!has('DTW_SALT')) lines.push(`DTW_SALT=${salt}`);
  if (!has('DTW_SALT_ID')) lines.push(`DTW_SALT_ID=${saltId}`);
  if (lines.length) {
    const next = existing + (existing.endsWith('\n') || !existing ? '' : '\n') + '\n# DTW/1 私有协议（gen-dtw-keys.js 生成）\n' + lines.join('\n') + '\n';
    fs.writeFileSync(ENV_FILE, next);
    console.log(`\n已写入 ${ENV_FILE}（${lines.length} 行）`);
  } else {
    console.log('\n.env 里三条都已存在，未改动');
  }
}
