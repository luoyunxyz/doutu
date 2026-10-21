# 工具页 63 项回归清单

来源：`pojia/extraction/tools-page/ACTION-MAP.md`；本地页：`LocalToolActivity` / `ToolImageActivity`。
测试环境：Android 真机 1080×2388；每次点开页面后返回，检查无崩溃、页面标题与交互符合预期。

| # | 分组 | ACTION | 参考目标 | 本地页 key | 状态 |
|---|---|---|---|---|---|
| 1 | 支付宝 | `ACTION_ALIPAY_YEAR_BILL` | `AliYearBillActivity` | billstats | 自检通过（1.0.35） |
| 2 | 支付宝 | `ACTION_ALIPAY` | `AlipayMainActivity` | home | 自检通过（1.0.35） |
| 3 | 支付宝 | `ACTION_ALIPAY_CREATE_SINGLE_CHAT` | `AlipayMainActivity` | chats | 已验证（真机交互） |
| 4 | 支付宝 | `ACTION_ALIPAY_TREASURE` | `AlipayMainActivity` | assets | 自检通过（1.0.35） |
| 5 | 支付宝 | `ACTION_ALIPAY_WORD_MOUTH` | `AlipayMainActivity` | shop | 已验证（真机交互） |
| 6 | 支付宝 | `ACTION_ALIPAY_ME` | `AlipayMainActivity` | profile | 自检通过（1.0.35） |
| 7 | 支付宝 | `ACTION_ALIPAY_PUBLIC` | `AlipayMainActivity` | articles | 自检通过（1.0.35） |
| 8 | 支付宝 | `ACTION_ALIPAY_BALANCE` | `AliBalanceActivity` | wallet | 自检通过（1.0.35） |
| 9 | 支付宝 | `ACTION_ALIPAY_TRANSFER_BANKCARD` | `AliTransBankActivity` | banktransfer | 自检通过（1.0.35） |
| 10 | 支付宝 | `ACTION_ALIPAY_BILL` | `AliBillActivity` | wallet | 自检通过（1.0.35） |
| 11 | 支付宝 | `ACTION_ALIPAY_ADDRESS_BOOK` | `AliContactsActivity` | contacts | 已验证（真机交互） |
| 12 | 支付宝 | `ACTION_ALIPAY_BALANCE_DETAILS` | `AliBalanceDetailActivity` | wallet | 自检通过（1.0.35） |
| 13 | 支付宝 | `ACTION_ALIPAY_REDENVELOPE` | `AliRedEnvelopeCreateActivity` | redpacket | 自检通过（1.0.35） |
| 14 | 支付宝 | `ACTION_ALIPAY_TRANSFER` | `AliTransferCreateActivity` | transfer | 自检通过（1.0.35） |
| 15 | 支付宝 | `ACTION_ALIPAY_TOTAL_ASSETS` | `TotalAssetsActivity` | assets | 自检通过（1.0.35） |
| 16 | 支付宝 | `ACTION_ALIPAY_PAY_ASSISTANT` | `AliPayAssistActivity` | pay | 自检通过（1.0.35） |
| 17 | 支付宝 | `ACTION_ALIPAY_AB_RECEIVE` | `AliPayReceiveActivity` | pay | 自检通过（1.0.35） |
| 18 | 支付宝 | `ACTION_ALIPAY_VOICE_BROADCAST` | `AliPayVoiceBroadcastActivity` | voice | 自检通过（1.0.35） |
| 19 | 支付宝 | `ACTION_ALIPAY_PAY_SUCCESS` | `AliPaySuccessActivity` | paysuccess | 已验证（真机交互） |
| 20 | 支付宝 | `ACTION_ALIPAY_TB_ORDER` | `TaobaoOrderDetailActivity` | order | 自检通过（1.0.35） |
| 21 | 支付宝 | `ACTION_ALIPAY_TOTAL_ASSETS` | `TotalAssetsNewActivity` | assets | 自检通过（1.0.35） |
| 22 | 视频 | `ACTION_DOUYIN_HOME` | `VideoActivity` | video | 自检通过（1.0.35） |
| 23 | 视频 | `ACTION_WEISHI_HOME` | `VideoActivity` | video | 自检通过（1.0.35） |
| 24 | 视频 | `ACTION_KUAISHOU_HOME` | `VideoActivity` | video | 自检通过（1.0.35） |
| 25 | 登录 | `ACTION_LOGIN` | `LoginActivity` | login | 已验证（真机交互） |
| 26 | 登录 | `ACTION_LOGIN_WITH_TRANSITION` | `LoginActivity` | login | 已验证（真机交互） |
| 27 | 微信 | `ACTION_WECHAT_REPLACE_HEAD` | `ReplaceHeadActivity` | avatar | 已验证（真机交互） |
| 28 | 微信 | `ACTION_WECHAT_AUTO_REPLACE_HEAD` | `AutoReplaceHeadActivity` | auto | 已验证（真机交互） |
| 29 | 微信 | `ACTION_WECHAT` | `WxMainActivity` | home | 自检通过（1.0.35） |
| 30 | 微信 | `ACTION_WECHAT_CREATE_SINGLE_CHAT` | `WxMainActivity` | chats | 已验证（真机交互） |
| 31 | 微信 | `ACTION_WECHAT_CREATE_GROUP_CHAT` | `WxMainActivity` | chats | 已验证（真机交互） |
| 32 | 微信 | `ACTION_WECHAT_DISCOVER` | `WxMainActivity` | moments | 自检通过（1.0.35） |
| 33 | 微信 | `ACTION_WECHAT_ADDRESSBOOK` | `WxMainActivity` | contacts | 已验证（真机交互） |
| 34 | 微信 | `ACTION_WECHAT_PUBLIC` | `WxMainActivity` | articles | 自检通过（1.0.35） |
| 35 | 微信 | `ACTION_WECHAT_SINGLE_CHAT` | `WxChatActivity` | chat | 已验证（真机交互） |
| 36 | 微信 | `ACTION_WECHAT_WALLET_CHANGE` | `WxWalletChangeActivity` | wallet | 自检通过（1.0.35） |
| 37 | 微信 | `ACTION_WECHAT_PAYMENT_LIST` | `WxPayMessageActivity` | notices | 自检通过（1.0.35） |
| 38 | 微信 | `ACTION_WECHAT_MALL_WALLET` | `WxMallWalletActivity` | mallwallet | 自检通过（1.0.35） |
| 39 | 微信 | `ACTION_WECHAT_NEW_FRENDS` | `WxNewFrendsActivity` | contacts | 已验证（真机交互） |
| 40 | 微信 | `ACTION_WECHAT_MALL_INDEX` | `WxMallIndexActivity` | shop | 已验证（真机交互） |
| 41 | 微信 | `ACTION_WECHAT_CHANGE_CASHGET` | `WxChangeCashGetActivity` | transfer | 自检通过（1.0.35） |
| 42 | 微信 | `ACTION_WECHAT_CHANGE_DETAIL` | `WxChangeDetailActivity` | wallet | 自检通过（1.0.35） |
| 43 | 微信 | `ACTION_WECHAT_RECEIVE_ASSIST` | `WxPayAssistActivity` | pay | 自检通过（1.0.35） |
| 44 | 微信 | `ACTION_WECHAT_YEAR_BILL` | `WxBillStatisticsActivity` | billstats | 自检通过（1.0.35） |
| 45 | 微信 | `ACTION_WECHAT_MY_SEND_RED_ENVELOPE` | `WxSendedRedPacketActivity` | redpackets | 自检通过（1.0.35） |
| 46 | 微信 | `ACTION_WECHAT_PAY_SUCCESS` | `WxPaySuccessActivity` | paysuccess | 已验证（真机交互） |
| 47 | 微信 | `ACTION_WECHAT_MASS_ASSISTANT` | `MassAssistantActivity` | broadcast | 自检通过（1.0.35） |
| 48 | 微信 | `ACTION_WECHAT_SUSPENSION_BOX` | `WxGzhFloatingWindowEditActivity` | settings | 自检通过（1.0.35） |
| 49 | 微信 | `ACTION_WECHAT_EDIT_REDENVELOPE` | `WxRedEnvelopeCreateActivity` | redpacket | 自检通过（1.0.35） |
| 50 | 微信 | `ACTION_WECHAT_EDIT_TRANSFER` | `WxTransferCreateActivity` | transfer | 自检通过（1.0.35） |
| 51 | 微信 | `ACTION_WECHAT_UPLOAD_IMG` | `WxUploadImgActivity` | album | 已验证（真机交互） |
| 52 | QQ | `ACTION_QQ` | `QQMainActivity` | home | 自检通过（1.0.35） |
| 53 | QQ | `ACTION_QQ_CREATE_SINGLE_CHAT` | `QQMainActivity` | chats | 已验证（真机交互） |
| 54 | QQ | `ACTION_QQ_UNREAD_MESSAGE_RED_DOT` | `QQMainActivity` | status | 自检通过（1.0.35） |
| 55 | QQ | `ACTION_QQ_PUBLIC` | `QQMainActivity` | articles | 自检通过（1.0.35） |
| 56 | QQ | `ACTION_QQ_ONLINE_STATUS` | `QQMainActivity` | status | 自检通过（1.0.35） |
| 57 | QQ | `ACTION_QQ_TRANSFER` | `QQTransferCreateActivity` | transfer | 自检通过（1.0.35） |
| 58 | QQ | `ACTION_QQ_REDENVELOPE` | `QQRedEnvelopeCreateActivity` | redpacket | 自检通过（1.0.35） |
| 59 | 云相册 | `ACTION_ALBUM` | `PhotoAlbumActivity` | album | 已验证（真机交互） |
| 60 | 云相册 | `ACTION_ALBUM_CREATE_TEAM` | `CreateTeamActivity` | team | 自检通过（1.0.35） |
| 61 | 云相册 | `ACTION_ALBUM_MY_TEAM` | `MyTeamActivity` | team | 自检通过（1.0.35） |
| 62 | AI | `ACTION_DY_COMMMENT` | `DyCommentActivity` | comment | 自检通过（1.0.35） |
| 63 | AI | `ACTION_AI_ONE_CLICK_IMITATE` | `AiOneClickImitateActivity` | ai | 已验证（真机交互） |

## 本地工具（6）

| # | 工具 | 验证要点 | 状态 |
|---|---|---|---|
| 1 | 换头像 | 选图→裁剪→槽位→合成→保存 | 已通过（真机） |
| 2 | 自动替换 | 全部人脸+水印 | 自检通过（1.0.35） |
| 3 | AI一键模仿 | 多图批替换+保存 | 自检通过（1.0.35） |
| 4 | 本地头像 | 列表/导入/预览/删除 | 已通过（真机） |
| 5 | 编辑面板 | 排序保存 | 自检通过（1.0.35） |
| 6 | 教程 | 打开与步骤 | 已通过（真机） |

## 全量自检结果（Android 1.0.35 / 10035）

- 运行方式：`adb shell am start -n com.luoyun.doutu/.features.tools.ToolsSelfTestActivity`
  （或在工具 → 教程 → 运行全量回归自检）；
- 结果：**67/67 个入口全部启动成功，未崩溃**；
- 63 个 ACTION 均映射到上述本地页；自检覆盖每个入口的 Activity 创建与首屏构建；
- 交互级验证：换头像/裁剪/槽位/合成/保存、聊天、通讯录、商城、支付成功、云相册、登录等已单独真机核对。

## 回归方式

1. 工具页逐组下滑，按上表点击每个入口；
2. 记录标题、首屏、返回是否正常；
3. 有问题的条目在状态列改为「问题：…」，修复后复测。