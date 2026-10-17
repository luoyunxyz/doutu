import fs from 'node:fs';
const p = 'D:/AAA/doutu/ios/Sources/Screens/CollectMoneyScreen.swift';
let t = fs.readFileSync(p, 'utf8');
// 0x9e3779b97f4a7c15 = 11400714819323198485 超出 Int64 范围，必须当 UInt64 用
t = t.replace('private let qrSeed: Int64 = 0x5eed1234abcd', 'private let qrSeed: UInt64 = 0x5eed1234abcd');
t = t.replace('var state = qrSeed ^ 0x9e3779b97f4a7c15', 'var state: UInt64 = qrSeed ^ 0x9e3779b97f4a7c15');
fs.writeFileSync(p, t, 'utf8');
console.log('  fixed');
