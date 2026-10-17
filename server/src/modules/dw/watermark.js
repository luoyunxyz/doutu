/**
 * 水印合成（规划书 3.2）：**必须服务端合成，客户端只能下载成品**。
 *
 * 客户端只能传「要不要水印」，加什么、加在哪、多透明全部由服务端策略决定；
 * 输出即最终产物，成品经签名 URL 下发，客户端没有任何机会拿到未加印的原始流。
 *
 * 落地细节：
 *  - 合成后重新编码（H.264 CRF 23 + AAC 128k + faststart），仅改容器绕不过去；
 *  - 中文字体必须显式指定，服务器没有中文字体时会渲染成方块；
 *  - 可选的时域扰动（轻微缩放 + 噪声）会破坏「水印区域修补」类工具的效果，默认关闭。
 */
import { spawn } from 'node:child_process';
import path from 'node:path';
import fs from 'node:fs';
import config from '../../config.js';
import { logger } from '../../core/logger.js';

const log = logger.child('dw:watermark');

/** ffprobe：拿时长/分辨率，用于决定字号与布局 */
export function probe(file) {
  return new Promise((resolve) => {
    const args = [
      '-v',
      'error',
      '-show_entries',
      'format=duration,size,format_name',
      '-show_entries',
      'stream=codec_type,width,height,duration',
      '-of',
      'json',
      file,
    ];
    const child = spawn(config.media.ffprobe, args);
    let out = '';
    let err = '';
    child.stdout.on('data', (d) => {
      out += d.toString();
    });
    child.stderr.on('data', (d) => {
      err += d.toString();
    });
    const timer = setTimeout(() => child.kill('SIGKILL'), 20000);
    child.on('error', () => {
      clearTimeout(timer);
      resolve(null);
    });
    child.on('close', () => {
      clearTimeout(timer);
      try {
        const json = JSON.parse(out);
        const videoStream = (json.streams || []).find((s) => s.codec_type === 'video') || {};
        resolve({
          durationMs: Math.round(Number(json.format?.duration || videoStream.duration || 0) * 1000),
          size: Number(json.format?.size || 0),
          width: Number(videoStream.width || 0),
          height: Number(videoStream.height || 0),
          format: json.format?.format_name || '',
        });
      } catch {
        resolve(err ? null : null);
      }
    });
  });
}

/** drawtext 文本转义：冒号、单引号、反斜杠、百分号都要处理，否则滤镜串会被截断 */
export function escapeDrawtext(text) {
  return String(text ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/:/g, '\\:')
    .replace(/%/g, '\\%');
}

/** 字体路径转义：Windows 的 C:\Windows\... 在滤镜里必须写成 C\:/Windows/... */
export function escapeFontPath(fontPath) {
  return String(fontPath || '').replace(/\\/g, '/').replace(/:/g, '\\:');
}

/** 水印文案：模板变量替换 */
export function renderText(template, meta = {}) {
  return String(template || '{nickname}')
    .replace(/\{nickname\}/g, meta.nickname || '')
    .replace(/\{user_id\}/g, meta.userId ? String(meta.userId) : '')
    .replace(/\{date\}/g, new Date().toISOString().slice(0, 10))
    .replace(/\{platform\}/g, meta.platformName || meta.platform || '')
    .replace(/\{task_no\}/g, meta.taskNo || '')
    .trim();
}

const POSITION_EXPR = {
  'bottom-right': { x: 'w-tw-24', y: 'h-th-24' },
  'bottom-left': { x: '24', y: 'h-th-24' },
  'top-right': { x: 'w-tw-24', y: '24' },
  'top-left': { x: '24', y: '24' },
  center: { x: '(w-tw)/2', y: '(h-th)/2' },
};

/**
 * 生成 drawtext 滤镜串。
 * 四角轮换（防裁剪）：每 20 秒换一个角，80 秒一轮。
 */
/**
 * 找水印用的中文字体。
 *
 * `.env` 里配的是**绝对路径**（生产上是 `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc`），
 * 一旦换了机器、或还原时忘了 `apt install fonts-wqy-zenhei`，字体就不存在了。
 * 以前只打一条警告、不带 fontfile 继续跑，结果把字体发现交给 ffmpeg 的 fontconfig ——
 * 在缺字体（甚至缺 fontconfig 配置）的机器上会直接**让整个合成任务失败**。
 *
 * 现在按「配置的 → 常见中文字体 → 没有」三级回退，并把实际用到的那个记进日志，
 * 换机器后顶多是字形不同，任务不会挂。
 */
const FONT_CANDIDATES = [
  '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',
  '/usr/share/fonts/truetype/wqy/wqy-microhei.ttc',
  '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc',
  '/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc',
  '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
  'C:/Windows/Fonts/msyh.ttc',
  'C:/Windows/Fonts/simhei.ttf',
];

let fontCache = null;
function resolveFont() {
  if (fontCache) return fontCache;
  const configured = config.media.font;
  if (configured && fs.existsSync(configured)) {
    fontCache = { path: configured, source: '配置' };
  } else {
    const hit = FONT_CANDIDATES.find((p) => fs.existsSync(p));
    fontCache = hit ? { path: hit, source: '自动发现' } : { path: '', source: '无' };
    if (hit) {
      log.warn('配置的中文字体不存在，已自动改用可用字体', { configured, used: hit });
    } else {
      log.warn('没有找到任何中文字体，水印可能渲染为方块（建议装 fonts-wqy-zenhei）', { configured });
    }
  }
  return fontCache;
}

export function buildTextFilters(policy, meta = {}) {
  const text = renderText(policy.template, meta);
  if (!text) return [];
  const opacity = Math.min(1, Math.max(0.05, Number(policy.opacity) || 0.42));
  const font = resolveFont().path;
  const fontPart = font ? `fontfile='${escapeFontPath(font)}':` : '';
  // 字号按 1080p 基准等比缩放：视频高度 4.2%（1080p ≈ 45px），并夹在 16~72 之间
  const fontSize = `max(16\\,min(72\\,h*0.042))`;
  const common =
    `${fontPart}text='${escapeDrawtext(text)}':` +
    `fontsize=${fontSize}:fontcolor=white@${opacity}:` +
    `shadowcolor=black@${Math.min(0.8, opacity + 0.25)}:shadowx=2:shadowy=2:`;

  if (!policy.rotateCorners) {
    const pos = POSITION_EXPR[policy.position] || POSITION_EXPR['bottom-right'];
    return [`drawtext=${common}x=${pos.x}:y=${pos.y}`];
  }

  // 轮换：四个角各占 1/4 周期
  const corners = ['top-left', 'top-right', 'bottom-right', 'bottom-left'];
  return corners.map((corner, index) => {
    const pos = POSITION_EXPR[corner];
    const from = index * 20;
    const to = from + 20;
    return `drawtext=${common}x=${pos.x}:y=${pos.y}:enable='between(mod(t,80),${from},${to})'`;
  });
}

/**
 * 组装完整 ffmpeg 参数。
 * 有 logo 时用 overlay（规划书 3.2 的参考命令），否则只画文字。
 */
export function buildFfmpegArgs({ input, output, policy, meta = {}, logoPath = null }) {
  const filters = buildTextFilters(policy, meta);
  const args = ['-hide_banner', '-loglevel', 'error', '-y', '-i', input];

  if (logoPath && fs.existsSync(logoPath)) {
    args.push('-i', logoPath);
    const opacity = Math.min(1, Math.max(0.05, Number(policy.opacity) || 0.42));
    const pos = POSITION_EXPR[policy.position] || POSITION_EXPR['bottom-right'];
    const overlay =
      `[1:v]scale=iw*${Number(policy.widthRatio) || 0.2}:-1,format=rgba,` +
      `colorchannelmixer=aa=${opacity}[wm];[0:v][wm]overlay=${pos.x}:${pos.y}:format=auto`;
    const chain = filters.length ? `${overlay},${filters.join(',')}` : overlay;
    args.push('-filter_complex', chain, '-map', '0:a?');
  } else if (filters.length) {
    args.push('-vf', filters.join(','));
  }

  args.push(
    '-c:v',
    'libx264',
    '-preset',
    'veryfast',
    '-crf',
    '23',
    '-pix_fmt',
    'yuv420p',
    '-c:a',
    'aac',
    '-b:a',
    '128k',
    '-movflags',
    '+faststart',
    // 弱暗水印（可取证）：写入媒体元数据，肉眼不可见
    '-metadata',
    `comment=doutu uid=${meta.userId || ''} task=${meta.taskNo || ''} ts=${Date.now()}`,
    '-metadata',
    `title=${String(meta.title || '').slice(0, 80)}`,
    output,
  );
  return args;
}

/** 执行 ffmpeg，带硬超时；超时按任务超时处理（不计费） */
export function runFfmpeg(args, timeoutSec = config.media.taskTimeoutSec) {
  return new Promise((resolve, reject) => {
    const child = spawn(config.media.ffmpeg, args);
    let stderr = '';
    child.stderr.on('data', (d) => {
      stderr += d.toString();
      if (stderr.length > 8000) stderr = stderr.slice(-8000);
    });
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      reject(new Error(`FFmpeg 超时（>${timeoutSec}s）`));
    }, Math.max(10, timeoutSec) * 1000);

    child.on('error', (err) => {
      clearTimeout(timer);
      reject(new Error(`无法启动 FFmpeg（${config.media.ffmpeg}）：${err.message}`));
    });
    child.on('close', (code) => {
      clearTimeout(timer);
      if (code === 0) resolve({ ok: true });
      else reject(new Error(`FFmpeg 退出码 ${code}：${stderr.split('\n').filter(Boolean).slice(-3).join(' | ').slice(0, 400)}`));
    });
  });
}

/**
 * 合成主入口：把 input 转成带水印的 output。
 * @returns {Promise<{output, durationMs, elapsedMs, probed}>}
 */
export async function compose({ input, output, policy, meta = {}, logoPath = null }) {
  if (!fs.existsSync(input)) throw new Error(`输入文件不存在：${path.basename(input)}`);
  const started = Date.now();
  const probed = await probe(input);
  const args = buildFfmpegArgs({ input, output, policy, meta, logoPath });
  log.debug('开始合成', { args: args.join(' ').slice(0, 400) });
  await runFfmpeg(args);
  const stat = fs.existsSync(output) ? fs.statSync(output) : null;
  if (!stat || stat.size === 0) throw new Error('合成为空文件');
  return {
    output,
    size: stat.size,
    durationMs: probed?.durationMs || 0,
    elapsedMs: Date.now() - started,
    width: probed?.width || 0,
    height: probed?.height || 0,
  };
}

/**
 * 图集单图合成：同一条水印策略，但输入输出都是静态图片。
 * `-frames:v 1` 保证只产出单帧，`-q:v 3` 是 JPEG 质量（2~5 之间肉眼无损）。
 */
export async function composeImage({ input, output, policy, meta = {}, logoPath = null }) {
  if (!fs.existsSync(input)) throw new Error('输入图片不存在');
  const filters = buildTextFilters(policy, { ...meta, platformName: meta.platformName || '图集' });
  const args = ['-hide_banner', '-loglevel', 'error', '-y', '-i', input];

  if (logoPath && fs.existsSync(logoPath)) {
    args.push('-i', logoPath);
    const opacity = Math.min(1, Math.max(0.05, Number(policy.opacity) || 0.42));
    const pos = POSITION_EXPR[policy.position] || POSITION_EXPR['bottom-right'];
    const overlay =
      `[1:v]scale=iw*${Number(policy.widthRatio) || 0.2}:-1,format=rgba,` +
      `colorchannelmixer=aa=${opacity}[wm];[0:v][wm]overlay=${pos.x}:${pos.y}:format=auto`;
    const chain = filters.length ? `${overlay},${filters.join(',')}` : overlay;
    args.push('-filter_complex', chain);
  } else if (filters.length) {
    args.push('-vf', filters.join(','));
  }

  args.push('-frames:v', '1', '-q:v', '3', '-metadata', `comment=doutu uid=${meta.userId || ''} task=${meta.taskNo || ''}`, output);
  await runFfmpeg(args, 60);
  return { output, size: fs.statSync(output).size };
}

/** 是否可用的 FFmpeg 环境自检（部署验证清单里会跑） */
export async function checkEnvironment() {
  const result = { ffmpeg: false, ffprobe: false, font: false, fontPath: config.media.font, version: null };
  await new Promise((resolve) => {
    const child = spawn(config.media.ffmpeg, ['-hide_banner', '-version']);
    let out = '';
    child.stdout.on('data', (d) => {
      out += d.toString();
    });
    child.on('error', () => resolve());
    child.on('close', () => {
      result.ffmpeg = out.includes('ffmpeg version');
      result.version = out.split('\n')[0] || null;
      resolve();
    });
  });
  await new Promise((resolve) => {
    const child = spawn(config.media.ffprobe, ['-hide_banner', '-version']);
    child.on('error', () => resolve());
    child.on('close', () => {
      result.ffprobe = true;
      resolve();
    });
  });
  result.font = Boolean(config.media.font && fs.existsSync(config.media.font));
  return result;
}

export default {
  probe,
  compose,
  buildFfmpegArgs,
  buildTextFilters,
  renderText,
  escapeDrawtext,
  escapeFontPath,
  runFfmpeg,
  checkEnvironment,
};
