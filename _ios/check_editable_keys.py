# -*- coding: utf-8 -*-
"""
可编辑键的「存 / 取」一致性检查（2026-10-17）

    python _ios/check_editable_keys.py

起因：`BalanceScreen` / `HuabeiScreen` / `YuebaoScreen` 的绘制侧用 `s.nums[key]` /
`s.texts[key]` 取覆盖值，改的值则由 `EditableStates.swift` 里的 `applyEdit` 写回。
如果某个键「能被点到、但绘制从不读」，改了也白改 —— 用户看到的是「没反应」。

做法：对每个屏，取出 hitTest 里 emit 的 `num:` / `text:` 键集合，
与绘制里 `s.nums["..."]` / `s.texts["..."]` 读取的键集合做差集。
"""
import io
import os
import re
import sys

SCREENS = {
    'BalanceScreen': 'BalanceScreen.swift',
    'HuabeiScreen': 'HuabeiScreen.swift',
    'YuebaoScreen': 'YuebaoScreen.swift',
}

SRC = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   'ios', 'Sources', 'Screens')

EMIT = re.compile(r'"(?:num|text):([A-Za-z][\w.]*)"')
READ_NUMS = re.compile(r's\.nums\["([\w.]+)"\]')
READ_TEXTS = re.compile(r's\.texts\["([\w.]+)"\]')

problems = 0
for name, filename in SCREENS.items():
    path = os.path.join(SRC, filename)
    if not os.path.isfile(path):
        print('  跳过（不存在）: ' + filename)
        continue
    src = io.open(path, encoding='utf-8').read()
    emitted = set(EMIT.findall(src))
    read = set(READ_NUMS.findall(src)) | set(READ_TEXTS.findall(src))
    missing = sorted(emitted - read)

    print('  ' + name)
    print('    emit %d 个键 / 绘制读取 %d 个键' % (len(emitted), len(read)))
    if missing:
        problems += len(missing)
        print('    [FAIL] 改了不会显示的键：' + ', '.join(missing))
    else:
        print('    [OK]   每个可编辑键在绘制里都有读取')
    orphan = sorted(read - emitted)
    if orphan:
        note = ', '.join(orphan[:8]) + (' …' if len(orphan) > 8 else '')
        print('    （绘制读了但不可编辑的键：%s）' % note)
    print('')

print('结果')
print('  通过 %d 项，失败 %d 项' % (len(SCREENS), problems))
sys.exit(0 if problems == 0 else 1)
