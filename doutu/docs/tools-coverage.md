# 工具页覆盖表（对齐 yidiandian / com.wailian.weishang 4.0.5.39）

来源：`D:\AAA\pojia\yidiandian`、`extraction\tools-page\ACTION-MAP.md`（63 个 ACTION）与
`tools_catalog.json`（工具页 67 个入口）。全部入口在本 App 内独立实现，不跳转微信/支付宝/QQ 等现有模块。

## 1. 本地工具（6）

| 入口 | 实现 | 状态 |
|---|---|---|
| 换头像 | 底图 + 头像 → 裁剪 → 人脸槽位列表 → 合成 → 预览/返回修改/保存 | 真机全流程已验证 |
| 自动替换 | 同上 + 全部人脸 + 水印开关 | 已实现 |
| AI一键模仿 | 最多 9 张图 + 头像 + 替换开关 → 批量识别/替换 → 一键保存 | 已实现 |
| 本地头像 | 列表、添加、预览、长按删除 | 已实现 |
| 编辑面板 | ↑/↓ 调整工具顺序，持久化 | 已实现 |
| 教程 | 图文步骤页 | 已实现 |

## 2. 微信（25 个 ACTION）

| ACTION | 本地页 |
|---|---|
| WECHAT / CREATE_SINGLE_CHAT / CREATE_GROUP_CHAT / SINGLE_CHAT | 模块主页 / 新建聊天 / 单聊 |
| DISCOVER | 朋友圈 |
| ADDRESSBOOK / PUBLIC / NEW_FRENDS | 通讯录 / 公众号 / 通讯录 |
| WALLET_CHANGE / CHANGE_DETAIL / MALL_WALLET / MALL_INDEX | 零钱 / 零钱明细 / 商城钱包 / 商城 |
| PAYMENT_LIST / PAY_SUCCESS / RECEIVE_ASSIST | 支付消息 / 支付成功 / 收款 |
| YEAR_BILL / MY_SEND_RED_ENVELOPE / EDIT_REDENVELOPE | 年度账单 / 已发红包 / 红包 |
| EDIT_TRANSFER / CHANGE_CASHGET | 转账 / 零钱提现 |
| MASS_ASSISTANT / SUSPENSION_BOX / UPLOAD_IMG | 群发助手 / 设置 / 上传图片 |
| REPLACE_HEAD / AUTO_REPLACE_HEAD | 换头像 / 自动替换 |

## 3. 支付宝（21 个 ACTION）

ALIPAY / ALIPAY_* 全部映射到本地页：模块主页、创建单聊、余额宝、口碑、我的、公众号、
余额、转账到银行卡、账单、通讯录、余额明细、红包、转账、总资产、收款助手、收款、
语音播报、支付成功、淘订单、总资产（新）。

## 4. QQ（7）/ 云相册（3）/ 视频（3+1）/ 登录（2）

- QQ：模块主页、创建单聊、未读红点、公众号、在线状态、转账、红包；
- 云相册：相册（缩略图/长按删除）、我的团队/创建团队（成员数、添加成员）；
- 视频：抖音/微视/快手（本地视频列表）、抖音评论（评论输入与本地列表）；
- 登录：本地手机号/密码表单。

## 5. 已验证证据

- 裁剪页：`_crop_screen2.png`（ClipView 风格）；
- 头像槽位列表：`_pick3.png`（原始 → 替换、勾选、删除）；
- 合成预览与固定底部操作栏：`_synth.png`；
- 保存到相册：MediaStore 出现 `doutu_tool_*.jpg`；
- 本地头像：`_local_avatars.png`（生成的头像已入库）。

## 6. 后续

- 部分页面仍共用分类模板（支付成功/总资产/年度账单/语音播报等），继续按参考 UI 分化；
- 63 项逐条点击回归清单；
- 相册/团队/评论的编辑能力。


## 6.1 工具页 UI 已按真机重建（2026-10-21）

工具页不再是 `tools_ref.png` 截图 + 22 个写死热区，而是按 歪点点 4.0.5.39
真机版式整页重画：黄色标题栏、替换头像单行、小微/阿宝/小Q/其它/团队相册/视频
六个分组、44 个入口、真机裁出来的图标、深色模式开关、团队暗号、每组底部
「更多功能及教程」。数据在 `assets/tools_page.json`，图标在
`assets/tools_icons/`，细节见 [tools-page-ui.md](tools-page-ui.md)。

## 7. 全量自检

- Android 1.0.35 起提供「全量回归自检」：67 个 catalog 入口逐项启动并自关；
- 真机结果：67/67 启动成功、无崩溃；
- 清单见 `doutu/docs/tools-regression-checklist.md`。
