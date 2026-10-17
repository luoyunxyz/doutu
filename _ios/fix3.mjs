import fs from 'node:fs';
const R = 'D:/AAA/doutu/ios/Sources/Screens/';
let p = R + 'PayMsgScreen.swift';
let t = fs.readFileSync(p, 'utf8');
t = t.replace('(Calendar.current.component(.month, from: Date())) + "月统计支出"',
              'String(Calendar.current.component(.month, from: Date())) + "月统计支出"');
fs.writeFileSync(p, t, 'utf8');

p = R + 'ReceiptDetailScreen.swift';
t = fs.readFileSync(p, 'utf8');
t = t.replace('let values = [item.monthSpend', 'let values: [Double] = [item.monthSpend');
t = t.replace('stats.totalCount == 0 ? 0', 'stats.totalCount == 0 ? 0.0');
t = t.replace('let counts = [item.monthCount', 'let counts: [Int] = [item.monthCount');
fs.writeFileSync(p, t, 'utf8');
console.log('  ok');
