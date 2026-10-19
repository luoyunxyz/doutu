# 斗图去水印 iOS V2 交接文档

> 生成时间：2026-10-19  
> 工作目录：`D:\AAA\doutu`  
> 当前分支：`main`  
> 最新提交：`本轮 2.4.4：钱包右上角「账单」可点击`  
> 最新代码版本：`2.4.4 / 20044`（已推送，待 Codemagic 构建）  
> 设备当前安装：`2.3.7 / 20037`（已通过 USB 查询确认）

---

## 1. 项目目标

把安卓端「斗图去水印」完整复刻到 iOS，要求：

- 功能、界面、UI 与安卓一致；
- Bundle ID：`com.luoyun.doutu`；
- App 名称：`斗图去水印`；
- 保持 DTW/1 服务端协议：
  - 登录、激活、会员、水印；
  - 配置、版本强更；
  - 邀请码注册；
- 微信模块必须完整可用：
  - 微信主页、通讯录、发现、我；
  - 聊天、朋友圈、消息；
  - 加号菜单、添加朋友、群聊、扫一扫、收付款；
  - 批量添加好友、批量生成聊天记录；
- 数据持久化：后台退出再打开，好友、朋友圈、消息必须保留；
- 未激活/非会员必须显示水印，不能自动激活会员；
- 用户明确不喜欢 GitHub Actions / Codemagic 的复杂流程，但目前 iOS 只能走 Codemagic。

---

## 2. 当前构建/安装链路

### 构建

- 平台：Codemagic
- 工作流：`ios-v2-unsigned`
- 分支：`main`
- 实例：`mac_mini_m2`
- Xcode：`latest`
- 产物：`Doutu-final-unsigned.ipa`
- 配置文件：
  - `codemagic.yaml`
  - `ios_v2/codemagic.yaml`

### 安装

- 使用「全能签」安装 unsigned IPA；
- 每次修改后必须提升版本号，否则覆盖安装可能仍沿用旧包；
- 设备：iPhone 14.3，USB 连接，i4Tools 实时屏幕已开启；
- 查询已安装版本：

```powershell
python -m pymobiledevice3 apps query com.luoyun.doutu
```

重点看：

```text
CFBundleShortVersionString
CFBundleVersion
```

### 版本号规则

`ios_v2/project.yml`：

```yaml
MARKETING_VERSION: "2.2.4"
CURRENT_PROJECT_VERSION: "20024"
```

每轮修复必须同时提升这两个值，再构建。

> 之前大量“改了没效果”都是因为设备上还是旧版本，代码并未安装。

---

## 3. 关键文件

```text
ios_v2/
  project.yml
  codemagic.yaml
  Sources/
    App/
      DoutuV2App.swift
      LegacyDoutuApp.swift
    Web/
      WeChatV2Host.swift          # 微信 WKWebView、原生手势、日志、桥接
      LocalWebServer.swift        # 本地 HTTP 服务，固定 18080
  Resources/
    web/
      index.html
      overrides/
        wechat-chat-actions.js    # 微信主要 override，头像/消息/滑动/菜单/页面
        wechat-chat-actions.css   # 微信主要样式
      assets/                     # 内置图片、头像、图标
_ios/
  v2-set-icons.sh                 # 图标 + ATS localhost 例外
scripts/
  build-ios-v2-mac.sh             # macOS 本地构建脚本
HANDOFF.md                        # 本文档
```

---

## 4. 微信页面架构

- iOS 用 `WKWebView` 加载包内 web app；
- `LocalWebServer` 固定端口 `18080`，保证 localStorage origin 不变；
- `LocalWebServer` 已改成 **全 App 单例**，避免重复打开微信页抢端口失败退回 `app://` 导致数据丢失；
- `WeChatV2Host` 负责：
  - WKWebView 创建；
  - 原生透明头部按钮（加号/搜索）；
  - 顶部空白点击打开工具菜单；
  - 边缘返回手势；
  - JS 错误写入 `Documents/crash.log`；
  - DebugBridge，用于 USB 远程执行 JS / 截图 / 日志。

### 本地服务

- 端口：`18080`
- `NSAppTransportSecurity` 已在 `_ios/v2-set-icons.sh` 中为 `localhost` 加例外；
- 若 18080 被占用，会失败，必须保证单例且不重复绑定。

### 数据持久化

- 微信数据存 `localStorage`；
- 关键 key：`fake-world.wechat.chat-actions.v1`；
- 固定 origin 才能保留数据；
- 不要把端口改回随机端口。

---

## 5. 头像系统（当前实现）

### 5.1 头像来源

- 朋友圈/好友列表头像：`images.xxapi.cn` 的接口 CDN 图片池；
- 头像池在 `wechat-chat-actions.js` 的 `AVATAR_API_POOL`；
- 按名字 hash 固定映射，保证同一个名字稳定同一张；
- 失败时使用 `bundledAvatarFor(seed)` 生成确定性内联头像；
- 不允许所有好友共用 `CONTACT_FALLBACKS[0].avatar`。

### 5.2 已修复的关键坑

#### `avatarSeedHash` 返回字符串

原实现：

```js
return hash.toString(36);
```

后续当作数字索引：

```js
AVATAR_API_POOL[hash % 24]   // NaN
palette[hash % 7]            // NaN
```

结果：

- 头像 URL 变成 `undefined`；
- SVG `fill="undefined"` 渲染成黑底白字；
- 日志出现 `http://localhost:18080/undefined`。

已改成返回数字：

```js
return hash;
```

#### 共享本地兜底

原生列表行头像曾经全是：

```text
/assets/user-avatar-10-ZKj7tiz_.jpg
```

因为 `CONTACT_FALLBACKS[0]` 被共用。已改为按名字调用接口/本地确定性头像。

#### CSS 4px 覆盖

旧规则：

```css
.wechat-dialogue-list > * > [class*="avatar"]:first-child {
  margin-right: 4px !important;
}
```

优先级高于后续 32px，导致怎么改都只显示 4px。已删除，并用更高优先级规则覆盖。

#### fake-world 旧 CDN 挂起

“我”的消息头像曾经是：

```text
https://cdn-fakeworld.azureedge.net/fakeworld/ppf3dl.jpg
naturalWidth = 0
complete = false
```

该请求一直挂起，不触发 `onerror`，所以一直是灰色方块。

已加：

- 识别 `fakeworld` / `azureedge` 死链；
- `paintMessageAvatar` 统一解析头像；
- `onerror` 回退；
- 1.8 秒 watchdog 超时回退；
- `referrerPolicy = 'no-referrer'`。

### 5.3 统一入口

所有消息头像走：

```js
paintMessageAvatar(img, side, preferred)
```

其中 outgoing（「我」发出的）再走：

```js
applySelfAvatar(img, preferred)
```

`applySelfAvatar` 内部使用与「我」页相同的 `__fwResolveProfileAvatar`，不要再在其它地方直接：

```js
img.src = ...
```

否则会重新引入灰色/共享/死链问题。

---

## 6. 消息与滑动（当前实现）

### 6.1 打招呼消息

打招呼两条只由：

```js
greeting.mine
greeting.friend
```

渲染一次。

`drawMessages` 每次绘制前会清掉 `friend.messages` 中：

- `item === greeting.mine/friend`；
- `item.type === 'greeting'`；
- 文案等于“我是林克”；
- 文案等于“我通过了你的朋友验证请求…”；
- 文案等于“以上是打招呼的消息”。

这样旧数据即使复制了多份，也只会显示两份。

### 6.2 滑动切换发送人

滑动完成后不再：

```js
drawMessages(true);
```

只原地修改当前行：

```js
row.classList.toggle('fw-chat-message--incoming', ...);
row.classList.toggle('fw-chat-message--outgoing', ...);
```

然后：

- 重新排列头像和气泡；
- 调用 `paintMessageAvatar(avatar, target)`；
- `setMessageSide` 保存这一条；
- 其他消息完全不动。

这是“只改变选中那一条、不凭空多消息”的关键。

### 6.3 已确认

用户已确认：**滑动切换本身正常了**。  
当前剩余问题是：切换后“我”的头像曾经灰色，2.2.4 已针对 fakeworld 死链修复，待最终视觉确认。

---

## 7. 间距/布局当前值

```css
/* 微信主页列表 */
.fw-local-friend-row--fallback { gap: 18px !important; }
.fw-local-friend-row--fallback > img { margin-right: 12px !important; }

/* 聊天气泡与头像 */
.fw-chat-message { gap: 56px !important; align-items: flex-start !important; }
.fw-chat-message__avatar { flex: 0 0 40px !important; }
.fw-chat-message__bubble {
  max-width: calc(100% - 132px) !important;
  margin: 0 6px !important;
}

/* 聊天页顶部 */
.fw-local-conversation__header {
  height: 88px !important;
  flex: 0 0 88px !important;
  padding-top: 12px !important;
}

/* 主页列表底部滚动空间 */
.wechat-dialogue-list {
  padding-bottom: 88px !important;
  box-sizing: border-box !important;
}
```

> 用户对间距反复调整过：太近 → 太远 → 再收回。当前值是 18px 行距 + 12px 头像边距；如果用户仍说不对，先量实际 DOM，不要继续盲加 CSS。

---

## 8. 主页滚动

设备实测：

```text
clientHeight = 719
scrollHeight = 719
```

10 个联系人刚好铺满，没有滚动余量，最后一行被底栏压住。

已加：

- `.wechat-dialogue-list { padding-bottom: 88px; }`
- JS 每 700ms 确保 `overflow-y:auto; touch-action:pan-y`；
- 原生：

```swift
webView.scrollView.isScrollEnabled = true
webView.scrollView.panGestureRecognizer.isEnabled = true
```

2.2.1 起已包含，待用户确认。

---

## 9. 已修复的历史问题

1. **白屏**
   - ATS 阻止 localhost；
   - 已在 `_ios/v2-set-icons.sh` 加 `NSExceptionDomains:localhost`。

2. **重启丢数据**
   - 随机端口导致 origin 变化；
   - 固定 18080 + 单例。

3. **头像每次变**
   - 原远程随机头像接口每次换图；
   - 改为固定池 + 按名字 hash。

4. **所有头像一样**
   - 批量生成 `avatarIndex` 相同；
   - `CONTACT_FALLBACKS[0]` 共用；
   - 已改为按 `name + id` 取头像并迁移旧数据。

5. **黑底白字头像**
   - `avatarSeedHash` 返回字符串 → `NaN`；
   - 已改回数字。

6. **加号/顶部空白无反应**
   - override JS 语法错误，整段没执行；
   - 已修复并用 `node --check` 验证；
   - 顶部工具菜单现在只在微信主页触发，子页绝不触发。

7. **批量添加被顶部手势抢走**
   - 顶部手势现在先判断 hash + 子页 overlay；
   - 非主页直接 return。

8. **滑动后多出消息**
   - 全量 `drawMessages(true)` + greeting 双数据源；
   - 已改为单 greeting 源 + 原地改单行，不重绘。

9. **切换后头像灰色**
   - 我的头像走了 fakeworld 死链；
   - 已加死链识别 + watchdog + 本地兜底。

10. **JS 语法错误导致“所有修改无效”**
    - 一次头像 patch 多了一个 `}`；
    - 整个 `wechat-chat-actions.js` 不执行；
    - 表现是加号、顶部菜单、置顶清理、头像全部无效。

11. **聊天「我」头像与「我」页不是同一张**
    - 「我」页把 `profile.avatarInfo` 交给 `__fwResolveProfileAvatar` 解析（旧 fakeworld 链接会映射成本地 `user-avatar-1`）；
    - 聊天页以前直接拒绝 fakeworld 并换成随机头像池，所以永远对不上；
    - 2.2.5 起所有 outgoing 头像（普通/转账/红包/通话/滑动切换）统一走 `applySelfAvatar`，复用同一个解析器并缓存结果；
    - 打开会话时先解析再画第一屏，避免先闪一张随机脸。

12. **通讯录页面行与行重叠**
    - 通讯录滚动区是 `wechat-scroll-area flex flex-col`，每个「新的朋友 / 星标 / 字母分组」都是 flex item；
    - 内容超高时默认 `flex-shrink:1` 会把分组压扁（实测 4 行只分到 41px，行内 48px 的布局互相重叠）；
    - 2.2.6 起按路由给 `body` 加 `fw-contacts-tab`，只对通讯录把滚动区直接子项设为 `flex:0 0 auto`，恢复分组高度并让滚动区真正滚动；其它 tab 不受影响。

13. **收款后会话页叠成三层**
    - 收款/转账流程会再次打开同一个会话，而 `openLocalFriendConversation` 之前只 `remove` 第一个 `.fw-local-conversation`；
    - 旧会话页留在 DOM 里，同一个「打招呼 + 两张转账卡片」被重复画 2～3 层，视觉上行与行重叠；
    - 2.2.7 起打开会话先清掉所有旧会话页，`closeLocalConversation` 也一次关闭所有会话页，不再留下残影。

14. **收款后仍重叠 / 返回键回到应用首页**
    - 2.2.7 清掉重复会话页后，收款重绘时仍可能保留旧页，且聊天消息行是 flex item，消息变多时会被压扁重叠；
    - 2.2.8 在 `drawMessages` 前再兜底只保留当前会话页；消息行加 `flex:0 0 auto`，长聊天只滚动不压缩；
    - 原生左侧边缘手势改为先执行 `__fwHandleChatPayBack`：聊天页先收回「微信」列表，转账详情先回聊天页；网页没处理时才退出整个微主页，不再直接回应用首页。

15. **气泡离头像太近 / 切换发送人后卡片方向不更新**
    - iOS 14.3 的 WKWebView 不支持 flex `gap`，`.fw-chat-message` 上所有 `gap: 56px` 实际等于 0，只剩气泡 6px margin；
    - 2.2.9 改用头像 `margin-left/right: 12px`，气泡边到头像 18px（尾巴 6px 后仍有 12px 可见距离）；
    - 滑动只改一行时，转账/红包/通话卡片的背景图、`fw-tf-state--in`、图标顺序是创建时写死的；2.2.9 在 `finishSwipe` 调 `syncMessageRowSide`，卡片尾巴/状态/图标实时跟着发送人换边。

16. **底部 7 项工具菜单在朋友圈/子页也能弹出**
    - 原生 `blankTapped` 之前用 `h.indexOf('#/wechat') === 0` 判断主页，`#/wechat/discover`、`#/wechat/contacts`、`#/wechat/my` 都算主页；
    - 2.3.0 改成精确路径 `''` / `#/wechat` / `#/wechat/index`，并把 `.fw-moments-feed`、`.fw-moments-page`、好友资料等浮层加入排除列表；
    - 现在只在微信主页顶部空白处弹出。

17. **朋友圈背景不显示 / 发送人灰块 / 底部切换栏露出**
    - 封面和发送人头像仍是 fakeworld 死链，真机上一直挂起、不触发 `onerror`，封面只剩灰渐变、头像只剩灰块；
    - 2.3.0 让 `applyCoverImage` 和 `buildTimelineItem` 都走 `__fwResolveProfileAvatar` 解析成本地图，头像再加 1.2s watchdog；
    - 朋友圈全屏时给 `body` 加 `fw-moments-open`，CSS 隐藏 `.wechat-bottom-navbar`，返回后自动移除。

18. **朋友圈头像贴正文 / 图片空白**
    - 头像和正文之间还是 flex `gap: 9px`，iOS 14.3 不生效，实际 0px；2.3.1 用头像 `margin-right: 12px` 拉开；
    - 单图是 `width/height:auto`，fakeworld 图没加载时 `naturalWidth=0`，整张图直接 0x0；2.3.1 先给 150x112 占位，`onload` 后按原比例调整，多图保持 72x72；
    - 图片本身也走 `__fwResolveProfileAvatar` 换本地图，加 1.2s watchdog，不再灰块/空白。

19. **个人信息页取消无效 / 返回弹换头像**
    - 编辑器 overlay 在捕获阶段 `stopPropagation`，把面板里的「取消 / 保存」点击也拦掉了；2.3.1 改为冒泡阶段拦截，按钮先执行；
    - 旧逻辑在 document 上监听「头像附近/整行右半边」，还把头像的 3 层祖先都绑了点击，点返回键会冒泡到祖先打开换头像；
    - 2.3.1 只绑定个人信息页顶部的头像 `img` 本体，移除整行透明热区和 document 级热区，返回键恢复正常。

20. **好友资料页头像离昵称太近**
    - `.fw-friend-profile__hero { gap: 22px }` 在 iOS 14.3 的 WKWebView 里同样不生效，实测头像右缘和昵称左缘间距是 0；
    - 2.3.2 用 `.fw-friend-profile__avatar-button { margin-right: 22px }` 补回间距。

21. **好友资料页顶到屏幕最上沿**
    - 真机隐藏状态栏后 `env(safe-area-inset-top)` 实测为 0，`.fw-friend-profile-page` 的 `padding-top` 也就是 0，返回箭头和页头贴顶；
    - 2.3.3 改成 `calc(env(safe-area-inset-top, 0px) + 12px)`；用户反馈仍然偏短，2.3.4 加大到 **+24px**，header top 从 0 → 24、头像 top 从 66 → 90。

22. **微信主页「Windows 微信已登录」底部弹层被底栏盖住**
    - 弹层 `.fw-device-sheet` 的 z-index 是 20100，而 `.wechat-bottom-navbar` 是 2147483000，底栏把「不显示」和底部安全区整个盖住；
    - 2.3.5 把弹层 z-index 提到 2147483001，打开时给 `body` 加 `fw-device-sheet-open` 隐藏底栏；
    - 面板加 `padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 6px)`，并补上底部滑入/滑出动画；
    - 现在四个选项（iPad / Mac / Windows / 不显示）都能完整看到和点击。

23. **设备登录横条隐藏后又被工具菜单带出来**
    - `applyDeviceRow` 之前只改 `row.style.display`，React 重新渲染时会把行内样式覆盖，点顶部工具菜单 / 打开设备登录弹窗后已隐藏的横条又出现；
    - 2.3.6 增加 `body.fw-device-row-hidden` + CSS `display:none !important` 锁住隐藏状态，React 怎么重绘都不会显形；
    - 工具菜单点「开启电脑设备登录显示」延后 120ms 打开弹窗，避免工具菜单关闭时补发的 click 落到弹窗「保存」上，把横条重新打开。

24. **设备登录弹窗的勾选框看不出来能点**
    - 原生 18×18 checkbox 在 iOS 上太小、没有开关语义；
    - 2.3.7 改成 46×26 的 iOS 拨动开关（灰/绿轨道 + 白色圆点），保留隐藏的 `<input type=checkbox>` 的 checked 状态；
    - 开关跟当前状态一致：隐藏时灰色，开启时绿色；不再默认勾选，避免用户误保存把横条重新打开。

25. **点「Windows 微信已登录」不弹设备切换**
    - 设备登录横条位于 CSS y 82~125，正好落在原生 `blankTapped` 的 y<=140 工具菜单热区里；
    - 原生先弹 7 项工具菜单，网页的 click 虽然打开了设备弹层，也被工具菜单盖住/关掉；
    - 2.3.8 在原生 blankTapped 里用 `document.elementFromPoint(x,y).closest('.wechat-device-row')` 命中判断，点横条时直接 return 0，只让网页打开设备切换弹层；点横条上方的真正空白才弹工具菜单。

26. **钱包 / 零钱页顶部贴屏幕**
    - 这两个路由实际显示的是 bundled React 页面（`.relative.flex.flex-col.overflow-hidden`），顶部没有 safe-area，返回箭头和标题贴屏幕最上沿；
    - 2.3.9 在 `applyBundledPaySpacing` 里按路由找到整页 root，加 `padding-top:16px !important` + `box-sizing:border-box`；
    - 用户反馈 16px、32px 偏短，2.4.1 加到 56px；又反馈太宽，2.4.2 回调到 44px；再反馈短一点，2.4.3 微调到 **40px**，离开钱包/零钱页时自动收回；
    - 自绘的 `.fw-pay-page` 本身已有 31px 顶部 padding，不受影响。

27. **钱包右上角「账单」点不动**
    - bundled 钱包页右上角的「账单」只是一个 `<div class="flex items-center justify-end">账单</div>`，没有 onClick；自绘页的 `.fw-wallet-bill` 在另一个 host 上，用户点不到；
    - 2.4.4 在 document capture 阶段按路由 + 顶部右侧位置 + 精确「账单」文本命中它，直接 `location.hash = '/wechat/wallet/bills'` 进入账单页；
    - 判断条件：`rect.top <= 120 && rect.left >= innerWidth * 0.55`，不会误伤页面中间的「账单」文字。

---

## 10. 当前状态

### 已完成/已确认

- 微信主页、聊天页可打开；
- 好友头像按名字区分；
- 滑动切换发送人正常；
- 滑动不再多出消息；
- 聊天页顶部加高；
- 加号菜单/顶部工具菜单可用；
- 子页不会触发顶部 7 项工具；
- 本地服务固定端口，数据保留。

### 待确认

1. 2.2.5 下聊天「我」的头像是否与「我」页完全一致（本地模拟已确认都解析为 `user-avatar-1`）；
2. 微信主页上下滚动、最后一行是否完整；
3. 列表头像/网名 18px/12px 是否合适；
4. 气泡/头像 56px 是否合适；
5. 聊天页顶部 88px 是否合适；
6. 滑动后只变一条、不多消息是否稳定；
7. 服务端控制：
   - 未激活/非会员显示水印；
   - 激活后水印消失；
   - `app_latest_version_code > 20024` 强更；
   - 邀请码注册；
8. 全页面逐页验收：首页、微信、通讯录、发现、我、支付、账单、余额、理财等。

### 已知未完成

- 备用 App 图标在 全能签 证书下返回 `-54`，已忽略；
- GitHub Actions 已停止产出新 artifact，当前只用 Codemagic；
- 设备曾多次 USB 断开，WebDAV 调试服务会停止，属正常现象；
- 朋友圈/消息持久化需继续实测；
- 服务端水印/强更/邀请注册未做最终端到端验收。

---

## 11. 常用调试命令

### 查版本

```powershell
python -m pymobiledevice3 apps query com.luoyun.doutu
```

### 截图

```powershell
python -m pymobiledevice3 developer dvt screenshot D:\AAA\ipa\shot.png
```

### 启动 WebDAV 读容器日志

```powershell
python -m pymobiledevice3 apps webdav com.luoyun.doutu --host 127.0.0.1 --bind-port 8099 --readonly
```

然后：

```powershell
Invoke-WebRequest 'http://127.0.0.1:8099/Documents/crash.log' -OutFile D:\AAA\ipa\crash.log
Get-Content D:\AAA\ipa\crash.log -Tail 30
```

### 通过 DebugBridge 执行 JS

`D:\AAA\ipa\share\cmd.json`：

```json
{"seq":10001,"token":"webjs:<base64-js>"}
```

打开微信：

```json
{"seq":10000,"token":"weiCell:0:0"}
```

JS 结果会写到 `Documents/crash.log` 的 `WEBJS result=...`。

### JS 语法检查（必须做）

```powershell
node --check ios_v2/Resources/web/overrides/wechat-chat-actions.js
```

---

## 12. 重要踩坑清单

1. **先查版本，再谈效果**
   设备版本不对，所有“没变化”都是假象。

2. **每次改完必须 bump 版本**
   `2.2.4 / 20024` 这种双版本都要改。

3. **改 JS 必须 `node --check`**
   一个多余 `}` 会让整个 override 静默失效。

4. **不要用 PowerShell here-string 直接写含中文的 JS/Python**
   容易出现编码/转义问题；优先用 `write` 工具或 ASCII/Unicode 转义。

5. **不要直接 `img.src = ...` 设消息头像**
   必须走 `paintMessageAvatar`。

6. **不要在滑动后 `drawMessages`**
   只原地改当前行。

7. **不要恢复 `CONTACT_FALLBACKS[0]` 作为好友默认头像**
   会再次出现所有人一样。

8. **不要用 fakeworld / azureedge CDN**
   设备上会挂起，不触发 onerror。

9. **CSS 优先级要实际读 computed style**
   之前 4px 规则用 `[class*="avatar"]:first-child` 压过 32px。

10. **WebDAV 调试服务会随 USB 断开停止**
    重新插线后再启动即可。

11. **本地服务必须单例、固定 18080**
    否则 localStorage origin 变化，数据丢失。

12. **顶部手势只允许微信主页触发**
    添加朋友/群聊/扫码/批量添加等子页必须 return。

---

## 13. 建议的新窗口开场提示

可以把下面这段直接发给新窗口的 AI：

```text
项目在 D:\AAA\doutu，iOS V2 在 ios_v2，安卓参考在 doutu。
当前最新代码 809cbe4，版本 2.2.4 / 20024，设备已安装 2.2.4。
先读 HANDOFF.md，再查设备版本，不要重复改已经修过的头像 hash、共享兜底、
滑动重绘等问题。当前优先验证：
1) 我发送的消息头像是否正常；
2) 微信主页能否上下滚动；
3) 滑动是否只改选中一条且不多消息；
4) 列表/气泡间距；
5) 服务端水印、强更、邀请注册。
改 JS 后必须 node --check，改版本后必须 Codemagic 构建并由全能签安装。
```

---

## 14. 关键结论

这个项目最难的不是页面数量，而是 **WKWebView 里的原生 React 页面 + override JS + 本地服务 + 远程头像 CDN + 旧持久化数据** 叠加。

已经确认的根因链：

```text
随机端口 → localStorage origin 变化 → 数据丢
旧随机头像接口 → 每次换脸
共享 CONTACT_FALLBACKS[0] → 所有头像一样
avatarSeedHash 返回字符串 → NaN → undefined URL / 黑色 SVG
fakeworld CDN 挂起 → onerror 不触发 → 我的头像灰色
滑动后 drawMessages 全量重绘 + greeting 双源 → 消息重复
CSS :first-child !important → 头像边距永远 4px
```

后续修复应优先保证：

- **单一数据源**；
- **单一头像入口**；
- **不重绘整页，只改目标行**；
- **所有失败都有本地确定性兜底**；
- **每次安装后先验证版本号**。
