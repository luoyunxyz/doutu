# 工具页 UI 重建（对齐 歪点点 4.0.5.39 真机）

> 2026-10-21。设备：V2408A / 1440×3168，参考包 `com.wailian.weishang` 4.0.5.39。

## 1. 以前是什么样

工具页原来贴的是 `assets/tools_ref.png` 一张真机截图，只有 22 个写死的矩形热区
（`TOOL_HOT`），页面不能滚动，截图以外的分组（阿宝 / 小Q / 其它 / 团队相册 / 视频）
根本看不到。

## 2. 现在是什么样

`MainActivity.NativeView` 里整页重画（`drawTools()`），不再用截图：

| 区块 | 说明 |
|---|---|
| 黄色标题栏 | 0–234（设计坐标），`#F9DB61`，居中「工具」 |
| 替换头像 | 单行：168px 图标 + 名称 + 右箭头 |
| 分组标题行 | 168px 图标 + 名称 + 右箭头 + 底部分隔线；点整行进对应模块主页 |
| 4 列网格 | 单元格 264×272.25，图标槽 132、圆底 0.86×，标签 33px，超 4 字按 4 字断两行 |
| 深色模式 | 仅小微组；图标 + 文案 + 开关，状态存 `doutu_tools/dark_mode` |
| 团队暗号 | 仅团队相册组；输入框 + 黄色「确定」，点开弹框输入 |
| 更多功能及教程 | 每组底部胶囊按钮，进 `ToolsTutorialActivity` |
| 滚动 | 整页可拖，最大滚动量按内容高度算；拖动过的手指松手不再误点 |

数据在 `app/src/main/assets/tools_page.json`：

- 六个分组、44 个入口，顺序/名称/图标/跳转目标全部照真机；
- 图标在 `app/src/main/assets/tools_icons/`，是 `_ops/extract_tool_icons.py`
  从真机截图里裁出来、扣掉灰底/白底后的 PNG（48 个，深色模式不留灰边）。

## 3. 入口去向

| 前缀 | 行为 |
|---|---|
| `wechat:<route>` | `WeiHomeActivity` 页内深链（`#/wechat/contacts`、`feature:group-chat`…） |
| `alipay` / `act:*` | 小支/支付宝现有页面（余额、账单、总资产、支付消息、年账单、收款记录、提现、到账声音、淘宝账单…） |
| `tool:avatar/auto/ai` | `ToolImageActivity`（换头像 / 自动替换 / AI 一键模仿） |
| `local:<key>` | `LocalToolActivity`（红包、转账、群发、公众号、已发红包、上传截图、QQ、团队、视频、评论…） |

微信 / 支付宝两类入口已经是现成的微信 / 支付宝复刻页；`local:*` 这批还是原先
的简化本地页，**下一轮按真机截图逐页对齐**（红包、转账、群发助手、公众号、
QQ 主页、团队相册、视频模块）。

## 4. 教程页

`ToolsTutorialActivity` 也已经按真机重做：黄色标题栏 + 返回、六个页签
（微信/支付宝/QQ/其他/云相册/视频）、橙色指示条、空状态幽灵图 + 「没有更多内容了」。
线上参考包的教程条目本来就是空的，所以空状态即真机状态。

## 5. 回归

```powershell
adb shell am start -n com.luoyun.doutu/com.luoyun.doutu.Icon.doutu
# 点底部「工具」；下滑检查 6 个分组、深色模式、团队暗号、教程按钮
adb shell am start -n com.luoyun.doutu/.features.tools.ToolLauncherActivity --es key tutorial
```

已核对：浅色/深色两套、滚动、分组标题跳转、通讯录深链、替换头像、红包、群发助手、
教程页、深色开关持久化。
