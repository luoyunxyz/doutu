import fs from 'node:fs';
const p = 'D:/AAA/doutu/ios/Sources/App/DoutuApp.swift';
const lines = fs.readFileSync(p, 'utf8').split('\n');
// 这些行位于 `guard let host` 之后的函数里，应使用局部（已解包）的 host
for (const no of [63, 64, 70, 120, 128]) {
  lines[no - 1] = lines[no - 1].split('self.host.').join('host.');
}
fs.writeFileSync(p, lines.join('\n'), 'utf8');
console.log('  已把 5 行改回局部 host');
