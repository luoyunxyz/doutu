#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查「手势坐标空间混用」——本项目最容易在换机型时炸的一类 bug。

背景（真实事故）：MainActivity 的「我」页导航判定写成
    float rawX = e.getX() * W / getWidth();          // 已换算成 1080x2388 设计坐标
    if (rawY >= 2212f * getHeight() / H) { ... }     // 又拿屏幕像素比
在 1080x2388 的机器上两边恰好相等，一切正常；换到 1440x3168 后阈值变成 2934，
而 rawY 上限只有 2388 → 判定永远不成立，底部标签栏在「我」页整条失效。

规则：在触摸处理（onTouchEvent / dispatchTouchEvent 的方法体）里，
只允许出现「屏幕 → 设计」的换算，不允许拿 getWidth()/getHeight()/getDisplayMetrics()
去和坐标做比较或运算。

合法写法（各界面统一的模式）：
    float x = event.getX() * Screen.W / Math.max(1, getWidth());
    float y = event.getY() * Screen.H / Math.max(1, getHeight());
    canvas.scale(getWidth() / Screen.W, getHeight() / Screen.H);   // 画布缩放，不在本检查范围

用法：
    python tools/check-ui-coords.py [源码根目录]
退出码 0 = 通过，1 = 发现可疑写法。
"""
import os
import re
import sys

SRC_ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'java')

# 触摸处理方法名
TOUCH_METHOD = re.compile(r'\b(boolean\s+onTouchEvent|boolean\s+dispatchTouchEvent)\s*\(')
# 屏幕尺寸相关的调用
SCREEN_CALL = re.compile(r'getWidth\(\)|getHeight\(\)|getDisplayMetrics\(\)|widthPixels|heightPixels')
# 合法的「屏幕 → 设计」换算：屏幕尺寸只作为**除数**出现
#   event.getX() * DESIGN_W / Math.max(1, getWidth())
#   touchSlop * DESIGN_HEIGHT / Math.max(1, getHeight())
# 反过来写成「设计值 × getHeight() / 设计高」或「getWidth() / 3f」就是混用，会被标记。
LEGAL_CONVERT = re.compile(r'/\s*Math\.max\(1,\s*get(Width|Height)\(\)\)[\s;)\{]*$')
# 设计尺寸常量（换算里用来乘的那个）
DESIGN_CONST = re.compile(r'\b[A-Z][A-Z0-9_]{2,}\b')

problems = []


def method_body(lines, start):
    """从方法声明行开始，用大括号配平取出方法体，返回 (起始行号, 结束行号)。"""
    depth = 0
    seen = False
    for i in range(start, len(lines)):
        depth += lines[i].count('{') - lines[i].count('}')
        if '{' in lines[i]:
            seen = True
        if seen and depth <= 0:
            return start, i
    return start, len(lines) - 1


for root, _dirs, files in os.walk(SRC_ROOT):
    for name in files:
        if not name.endswith('.java'):
            continue
        path = os.path.join(root, name)
        with open(path, encoding='utf-8', errors='replace') as fh:
            lines = fh.read().splitlines()
        i = 0
        while i < len(lines):
            if TOUCH_METHOD.search(lines[i]):
                a, b = method_body(lines, i)
                for j in range(a, b + 1):
                    line = lines[j]
                    code = line.split('//')[0].strip()
                    if not code or not SCREEN_CALL.search(code):
                        continue
                    # 屏幕尺寸只当除数 = 屏幕→设计换算，合法
                    if LEGAL_CONVERT.search(code):
                        continue
                    # 触摸坐标换算成设计坐标的那一行（rawX/rawY/x/y 赋值）也合法
                    if re.search(r'\b(float|double)\s+(rawX|rawY|x|y)\b', code) and 'event.get' in code:
                        continue
                    problems.append((path, j + 1, line.strip()))
                i = b + 1
            else:
                i += 1

rel = lambda p: os.path.relpath(p, os.path.dirname(SRC_ROOT))
if problems:
    print('  发现 %d 处可疑的坐标混用：' % len(problems))
    for p, ln, text in problems:
        print('    %s:%d  %s' % (rel(p), ln, text))
    print('  说明：触摸回调里只允许「屏幕→设计」的换算，')
    print('        不要用 getWidth()/getHeight() 参与坐标判断（换机型会炸）。')
    sys.exit(1)

print('  UI 坐标检查通过：触摸处理里没有屏幕像素与设计坐标混用')
