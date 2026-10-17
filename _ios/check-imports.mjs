/**
 * 框架 import 检查（2026-10-17，第 95 轮）
 *
 *   node _ios/check-imports.mjs
 *
 * 用在文件里出现的**框架符号**反推该文件必须 import 哪些框架。
 * 缺 import 是首编最常见的错误之一（`cannot find type 'WKWebView' in scope`、
 * `cannot find 'UIApplication' in scope`），而且完全可以从源码静态判定。
 *
 * 判定原则：只认「专属符号」——凡是 UIKit 里独有、不可能来自别处的名字才算证据，
 * 避免把 `View`、`Date` 这种到处都有的词当依据（否则会大面积误报）。
 */
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:\\AAA\\doutu\\ios\\Sources';

/** 框架 → 专属符号 */
const SIGNALS = {
  UIKit: [
    'UIView', 'UIColor', 'UIImage', 'UIFont', 'UIApplication', 'UIViewController',
    'UIAlertController', 'UIAlertAction', 'UILabel', 'UIWindow', 'UIWindowScene',
    'UIBezierPath', 'UIGraphicsImageRenderer', 'UIScreen', 'UIResponder', 'UITouch',
    'UIEvent', 'UIImagePickerController', 'UIStoryboard', 'UIRectEdge', 'UIScrollView',
    'UITableView', 'UICollectionView', 'UIDevice', 'UIPasteboard', 'UIFeedbackGenerator',
  ],
  SwiftUI: [
    'WindowGroup', 'UIViewControllerRepresentable', 'UIViewRepresentable',
    'AppStorage', 'ObservedObject', 'StateObject', 'EnvironmentObject',
  ],
  CoreText: ['CTFont', 'CTLine', 'CTRun', 'CTFontManagerRegisterFontsForURL', 'CTFrame'],
  CoreGraphics: ['CGContext', 'CGAffineTransform', 'CGPath', 'CGGradient', 'CGLayer'],
  WebKit: ['WKWebView', 'WKScriptMessageHandler', 'WKUserContentController',
    'WKWebViewConfiguration', 'WKNavigationDelegate', 'WKScriptMessage',
    'WKURLSchemeHandler', 'WKURLSchemeTask'],
  AVFoundation: ['AVCaptureSession', 'AVCaptureVideoPreviewLayer', 'AVCaptureDevice',
    'AVCaptureDeviceInput', 'AVCaptureVideoDataOutput', 'AVCaptureSessionPreset'],
  Security: ['SecKey', 'SecItemAdd', 'SecItemCopyMatching', 'SecRandomCopyBytes', 'SecPolicy'],
  CryptoKit: ['SymmetricKey', 'AES', 'ChaChaPoly', 'SHA256', 'HMAC', 'Curve25519'],
  PhotosUI: ['PHPickerViewController', 'PHPickerConfiguration'],
  Foundation: ['URLSession', 'JSONSerialization', 'DateFormatter', 'NumberFormatter',
    'UserDefaults', 'NotificationCenter', 'FileManager', 'JSONDecoder', 'JSONEncoder',
    'HTTPURLResponse', 'URLComponents', 'IndexSet', 'Timer'],
};

/** UIKit 已再导出 Foundation，所以有 UIKit 就不需要单独 import Foundation */
const IMPLIED = { UIKit: ['Foundation', 'CoreGraphics'], SwiftUI: ['Foundation', 'UIKit'],
  PhotosUI: ['UIKit'], AVFoundation: ['Foundation'], WebKit: ['Foundation', 'UIKit'] };

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.swift')) out.push(full);
  }
  return out;
}

/** 去掉注释与字符串字面量，避免把注释里提到的框架名当证据 */
function stripNoise(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/[^\n]*/g, ' ')
    .replace(/"""[\s\S]*?"""/g, '""')
    .replace(/"(?:[^"\\\n]|\\.)*"/g, '""');
}

const problems = [];
let checked = 0;

for (const full of walk(DIR)) {
  const raw = fs.readFileSync(full, 'utf8');
  const code = stripNoise(raw);
  const imported = new Set(
    [...raw.matchAll(/^\s*import\s+(\w+)/gm)].map((m) => m[1]),
  );
  // 间接引入
  for (const [framework, implied] of Object.entries(IMPLIED)) {
    if (imported.has(framework)) implied.forEach((f) => imported.add(f));
  }

  const need = new Set();
  for (const [framework, symbols] of Object.entries(SIGNALS)) {
    if (imported.has(framework)) continue;
    for (const sym of symbols) {
      if (new RegExp('\\b' + sym + '\\b').test(code)) { need.add(framework); break; }
    }
  }
  checked += 1;
  if (need.size > 0) {
    problems.push({
      file: path.relative(DIR, full).replace(/\\/g, '/'),
      need: [...need],
      has: [...imported].sort(),
    });
  }
}

console.log(`  检查 ${checked} 个文件的 import`);
console.log('');
if (problems.length === 0) {
  console.log('  [OK]   每个文件该 import 的框架都 import 了');
} else {
  console.log(`  [FAIL] ${problems.length} 个文件缺少 import：`);
  for (const p of problems) {
    console.log(`    ${p.file}`);
    console.log(`        缺: ${p.need.join(', ')}        现有: ${p.has.join(', ') || '（无）'}`);
  }
}
console.log('');
console.log(`结果\n  通过 ${problems.length === 0 ? 1 : 0} 项，失败 ${problems.length} 项`);
process.exit(problems.length === 0 ? 0 : 1);
