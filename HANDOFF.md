# 斗图去水印 iOS V2 交接文档

> 生成时间：2026-10-19  
> 工作目录：`D:\AAA\doutu`  
> 当前分支：`main`  
> 最新提交：`本轮 1.0.0(43)：滑动返回 / 滚动惯性 / 页面转场 / 长按 / 触觉专项`  
> 最新代码版本：`1.0.0 / 43`（已推送，待 Codemagic 构建）  
> 设备当前安装：`2.4.75 / 20115`（已通过 USB 查询确认）

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
    - 2.4.4 在 document capture 阶段按路由 + 顶部右侧位置 + 精确「账单」文本命中它，跳转账单页；
    - 但直接 `#/wechat/wallet/bills` 会让 bundled React Router 渲染 **404 Not Found**，错误页占 48px 把自绘账单页挤到下面；
    - 2.4.5 改成 `#/wechat/wallet?bills=1`：App 只看到已知的 `/wechat/wallet` 路由，不 404；`currentRoute()` 识别 `bills=1` 后让自绘层渲染账单页。
    - 判断条件：`rect.top <= 120 && rect.left >= innerWidth * 0.55`，不会误伤页面中间的「账单」文字。

28. **账单仍然点不动（真机）**
    - 原生 `installHeaderHitTargets` 在右上角常驻两个透明按钮：搜索 `x = right-68…right-42`、加号 `x = right-42…right-2`，y 8…58；
    - 「账单」div 在 `x 255…374, y 48…72`，右侧正好被这两个原生热区盖住，真实触摸根本到不了网页；
    - 2.4.6 让原生只在这些路由显示热区：`''` / `#/wechat` / `#/wechat/index`；其它路由（钱包/账单/服务等）隐藏；
    - 实现：WKUserScript 监听 hashchange，向 `DoutuApp` 桥发 `headerTargets: [home]`，原生按钮默认隐藏，只在主页显示。

29. **账单页仍不可见 / 钱包又贴顶**
    - 真机加载的 `wechat-pay-pages.css` 里 `#fw-pay-pages-host` 的 `inset:0` 丢了，fixed 元素按静态位置落到屏幕下方（实测 host top=845），自绘账单页一直在屏幕外；
    - 2.4.7 只在 `kind === 'bills'` 时给 host 显式 `top/left/right/bottom:0`，账单页钉到视口；钱包仍用 bundled 页面，不改变其 UI；
    - `applyBundledPaySpacing` 不再按 hash 判断，只要可见的钱包 root 在 DOM 里就加 40px —— 即使 hash 还残留 `?bills=1`，钱包也不会再贴顶。

30. **账单右上角三点菜单不显示**
    - iOS 14.3 的 WKWebView 不支持 `inset` 简写：`.fw-bills-menu { position:fixed; inset:0 }` 里的 top/left 全部失效，fixed 元素按静态位置掉到 `top=845`，菜单在屏幕外；
    - 2.4.8 给所有全屏浮层（账单三点菜单、批量添加、账单编辑/选择、收支统计、提现页、银行卡页、toast）补 `top/right/bottom/left: 0` 长写；
    - `#fw-pay-pages-host` 仍由 JS 只在 bills 路由钉住，避免钱包的 bundled 页面被自绘页替换。

31. **零钱 / 零钱通金额不可点击修改**
    - 用户实际看到的是 bundled 钱包/零钱页，原有的 `bindBalanceEditor` 只绑在自绘 `.fw-pay-balance` 上，点不到；
    - 2.4.9 给 bundled 零钱页的 `.font-bold.text-4xl`（520.00）和钱包页「零钱通」金额（¥1314.00）打 `data-fw-edit-balance` / `data-fw-edit-lqt`；
    - 2.4.10 把点击从 document capture 改到 **window capture**：`wechat-chat-actions` 的 document capture `onClick` 会把这些钱包行当聊天行先消费掉；
    - 2.4.12 改成**原位输入**，不再弹窗：
      - `pointerup` / `touchend`（window capture，优先于聊天 onClick）命中金额后，在原数字位置叠一个 `input`，`inputmode=decimal`，立即 `focus()` 弹数字键盘；
      - 输入过程 500ms 防抖写 localStorage；失焦或回车自动收尾；
      - 零钱：写 `fw-balance-amount` + `fw-balance-user-set=1`，保存后更新零钱页数字、钱包页「零钱」行、服务页钱包预览；
      - 零钱通：写 `fw-lingqiantong-amount`，更新钱包页「零钱通」金额；
      - 无弹窗、无保存按钮，改的是现有数字，不新增记录。
    - `syncEditableAmounts` 每 300ms + MutationObserver 重新标记，React 重渲染后仍可点；输入框存在时暂停同步。
    - 2.4.12 的 CSS 在修注释时被整份复制了一遍；2.4.13 已从 58977e8 恢复，仅保留 `inset` 兼容块 + `.fw-inline-amount-input`。

32. **模拟来电界面露出底部 tab bar**
    - `.fw-call` 原来的 `z-index:100400` 低于 `.wechat-bottom-navbar` 的 `z-index:2147483000`，来电全屏层被 tab bar 盖住，看起来页面半透明、底部还是微信 tab；
    - 2.4.14 把 `.fw-call` 提到 **2147483001**，高于 tab bar；来电页完整覆盖，挂断/接听不再被底部栏遮挡。

33. **支付成功页同样露出底部 tab bar**
    - `.fw-pay-success` 原来 `z-index:100300`，低于底部 tab bar 的 2147483000；
    - 2.4.15 把它也提到 **2147483001**，支付成功页完整覆盖，底部「完成」按钮不被 tab bar 压住。

34. **零钱提现页顶部 404**
    - bundled 零钱页的「提现」直接跳 `#/wechat/wallet/withdraw`，React Router 不认这个子路由，渲染 404 错误页；
    - 2.4.16 在 window capture 拦截零钱页的「提现」按钮，改跳 `#/wechat/wallet?withdraw=1`；`currentRoute()` 识别 `withdraw=1` 后让自绘提现页覆盖钱包；
    - `kind === 'withdraw'` 时把自绘 host 钉到视口；返回零钱页时 host 自动收回，显示 bundled 零钱页。

35. **提现键盘底部留灰带**
    - `.fw-wd__keypad` 原来是 `top:581.25px;bottom:0`，键盘容器到 844 高，但 4 行按键只有约 200px，下面空出一条灰带；
    - 2.4.17 改成 `top:auto;bottom:0;height:auto`，按按键内容高度从底部往上排，按键直接落到屏幕最底；底部再加 safe-area padding；
    - 2.4.18 按键从 43.25px 放大到 **52px**，字号 19→22，确认键 15.2→17，删除图标同步放大。

36. **银行卡管理页顶部 404**
    - bundled 钱包页的「银行卡」直接跳 `#/wechat/wallet/cards`，React Router 不认这个子路由；
    - 2.4.19 拦截钱包页「银行卡」按钮，改跳 `#/wechat/wallet?cards=1`；`currentRoute()` 识别 `cards=1`，自绘银行卡页覆盖钱包；
    - `card-add=1` 同样处理；`kind === 'cards'/'cardAdd'` 时 host 钉到视口，返回时收回。

37. **ADB 对照 Android 银行卡页**
    - `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` 后通过 CDP 查看 Android WebView：
      - URL 是 `#/wechat/wallet/cards`，DOM 里同样有 `Unexpected Application Error! 404 Not Found`，但 `#fw-pay-pages-host` 是 `position:fixed;top:0`，把 404 完整盖住；
      - 银行卡页就是同一份 `.fw-bc` 自绘代码（卡面 104px、body top 89px、footer top 760px）。
    - iOS 14.3 的 host 缺 `inset`，掉到 48/845，才把 404 露出来；2.4.19 已钉 host。
    - 2.4.20 进一步在 bills/withdraw/cards/cardAdd 这些全屏自绘页隐藏 `#root`，离开时恢复，保证即使 host 定位异常也不会再看到 404。
    - 结论：iOS 银行卡页与 Android 是同一份 HTML/CSS，不需要重画；差异只是 404 层和 host 定位。

38. **银行卡页视觉对齐参考图**
    - 用户参考图实际是 1380×3036，按 CSS 360×792、dpr 3.833 换算：
      - 红卡区 y=339..738 → 88.4..192.6 CSS，高 104.1；
      - 卡宽 1268 → 330.8 CSS，left 56 → 14.6；
      - 正好等于原版 `.fw-bc` 的 body top 88.75、卡面 104.25、left 14.5，**不是紧凑版**。
    - 2.4.21 误按预览小图改成 72/46/537，反而差距更大；2.4.22 已全部撤回，恢复原版尺寸。
    - 2.4.20 的 `#root` 隐藏和 host 钉 top:0 保留，所以页面不会再有 404 或下移。

39. **银行卡页 iOS 与 Android 比例不一致**
    - 参考图设计稿是 360×792；iOS 390×844 直接按 px 铺，卡面/页脚比例偏（footer 只在 90% 高度）；
    - 2.4.23 在 `render(kind === 'cards')` 时把 `.fw-bc` 固定成 360×792，再按 host 尺寸 `scale(390/360, 844/792)`，整页比例与参考图一致；
    - 缩放后再 `#root` 隐藏 + host top:0，视觉上就是同一张 Android 页面。

40. **银行卡两张卡贴在一起，没有参考图的缝**
    - `.fw-bc__body` 用 `display:flex; gap:7.25px` 排列卡片；iOS 14.3 不支持 flex gap，gap 被忽略，两张卡直接贴住；
    - 2.4.24 给 `.fw-bc__card` 加 `margin-bottom:7.25px`，在卡 1/卡 2、卡 2/添加行之间补出 7.25px 灰缝，再随整页缩放。

41. **小支主页底部导航点不动**
    - `ZhiHomeV2Screen.hitTest` 在 `y >= 2560` 返回 `nav:0..4`，但 Router 的 `nav:` 分支只处理 `LicaiV2Screen/GrowthV2Screen`，ZhiHome 的令牌落到空分支，所以首页/理财/消息/我的全无反应；
    - 2.4.25 在 Router 里补 `ZhiHomeV2Screen` 的映射：0 回顶、1 理财（黑卡进 Growth）、2 视频空、3 消息、4 我；与底栏切图 `zhi_navbar` 的 5 格顺序一致。

42. **理财底栏选中态不变 / 进消息出不来 / 我的进不去 / V1V2V3 无变化**
    - `LicaiV2Screen.drawNav` 直接画 `lci_nav_licai`（未选中深灰），底栏一直像没选中；2.4.26 用 `withTintColor` 把选中理财图标和文字染成 `#1677ff`；
    - Router 原来只给 ZhiHome/Licai/Growth 处理 `nav:`；2.4.26 补 `MessageScreen`（0 返回、1 理财、4 我）和 `MeScreen`（0 返回、1 理财、3 消息），进消息后能点回首页/我的；
    - Licai 的 V1/V2/V3 只改了 `LicaiVersion`，页面绘制完全不读主题；2.4.26 在顶部按 `theme.backgroundRes` 画各自的背景切图，普通版仍用渐变 plate。

43. **财富页底部导航消失（深层修复）**
    - 根因是每个页面自己画底栏：`GrowthV2Screen.drawNav` 只画 `art["nav"]`，资产缺失/被内容盖住时整根消失；页面之间选中态也各画各的，Router 还要逐屏补 `nav:`；
    - 2.4.27 改成 **宿主统一底栏**：`Screen` 新增 `zhiTabIndex`，ZhiHome=0、Licai/Growth=1、Message=3、Me=4；`ScreenHostView.draw` 在页面绘制后统一覆盖一根 1280×256 的底栏（白底、五格、选中项染 #1677ff、home indicator）；
    - 命中也在宿主层优先拦截 `y>=2560`，不再依赖各页面自己的 `hitTest`；这样财富页、消息页、我的页、支首页都共用同一根底栏，选中态和切换都一致。

44. **2.4.27 中文编码事故**
    - 用 PowerShell 批量插 `zhiTabIndex` 时按 ANSI 读写，把 Growth/Licai/Message/Me 的中文注释和字符串写成了乱码；
    - 2.4.28 已从 ba2b6f5 恢复这些文件，再用 edit 工具重新插入 `zhiTabIndex`；宿主底栏、Router、主题背景都保留。

45. **2.4.28 财富页仍看不到底栏**
    - 2.4.27/2.4.28 的底栏是画在页面同一个 CGContext 里；GrowthV2 的 draw 内部有 save/restore + clip，页面的 restore 不配对时会把后画的底栏裁掉/移位，所以财富页依然没有；
    - 2.4.29 改成独立 `ZhiTabBarView` 子视图：白底、五格、选中项，`ScreenHostView` 的 `layoutSubviews` 把它固定在底部 `h*256/2816`，`setScreen` 根据 `zhiTabIndex` 显示/选中；
    - 独立视图不参与页面绘制上下文，财富页/理财页/消息/我的都能稳定看到；点击在子视图的 `touchesEnded` 里直接发 `nav:<i>`，不再依赖页面 hitTest。

46. **2.4.29 仍无底栏 / 卡下拉**
    - 用户实测仍是 2.4.28（设备查询确认），2.4.29 的独立子视图还没装；
    - 2.4.30 给 `setScreen` 加 `ZHITAB` 日志（screen 类型、index、frame、bounds），并修正底栏蓝色为 `Draw.color(0xff1677ff)`；
    - 安装 2.4.30 后若财富页仍无底栏，直接拉 `Documents/crash.log` 里 `ZHITAB` 行即可判断是 `zhiTabIndex` nil、frame 为 0，还是视图被盖住。

47. **2.4.29 财富页仍无底栏（真机）**
    - 设备装的是 2.4.29；独立子视图仍没显示，说明不是绘制上下文问题，而是 frame/层级或 `zhiTabIndex` 在真机上没生效；
    - 2.4.31 在 `setScreen` 里直接按 `bounds` 重算 `zhiTabBar.frame` 并 `bringSubviewToFront`，不再等 `layoutSubviews`；
    - 每次进入支页写 `ZHITAB <Screen> idx=<n> frame=<...> bounds=<...>` 到 `Documents/crash.log`；
    - 下一步按日志定位：无 ZHITAB → zhiTabIndex nil / 不是 ScreenHostView；frame 在屏幕外 → bounds/坐标系问题；frame 正确但看不到 → 上层视图遮挡。

48. **2.4.32 增加每次切页的 SCREEN 日志**
    - 不能只依赖 ZHITAB：如果 `zhiTabIndex` 为 nil 就不会记录；
    - `setScreen` 每次写 `SCREEN <class> zhi=<nil/Int> bounds=<...>`，财富页到底是哪个类、bounds 多少、zhi 是否 nil 一眼可见；
    - 安装 2.4.32 后进财富页，拉 `crash.log`，把 `SCREEN` / `ZHITAB` 两行发来即可继续定位。

49. **根因：zhiTabIndex 不是 protocol 要求**
    - 2.4.32 真机日志实测：
      `SCREEN GrowthV2Screen zhi=nil bounds=(0,0,390,844)`
    - `zhiTabIndex` 之前只写在 `extension Screen` 里，没有写进 `protocol Screen` 的声明；通过 `Screen` 存在类型访问时，Swift 永远拿扩展默认的 nil，GrowthV2 类里的 `{ 1 }` 完全不参与动态派发；
    - 2.4.33 把 `var zhiTabIndex: Int? { get }` 提升为 protocol 要求，扩展仍保留默认 nil；这样 GrowthV2/Licai/Message/Me 的实现才会真正被调用，底栏 idx 不再恒 nil。

50. **V1/V2/V3 原地切换残留**
    - 五档主题的背景、卡面、铭牌、底栏资产差异大，原地 `setNeedsDisplay` 会残留上一套图层，视觉问题很大；
    - 2.4.34 选择主题后不再原地重绘，直接弹「主题已切换 / 请退出并重新打开 App」，点「退出 App」执行 `exit(0)`；下次启动 `LicaiVersion.current()` 按新主题完整绘制；
    - `VersionPicker.requireRestart()` 延迟 0.25s 等 action sheet 收起后弹 alert，避免 presentation 冲突。

51. **财富页资产面板不能下拉**
    - Android 的财富页顶部资产面板可以下拉展开、上拉收起；iOS 只有点箭头的 `toggleExpand`，拖拽完全不处理；
    - 2.4.35 在 `Screen` 增加 **protocol 要求** `verticalDrag/verticalDragEnd`（同样必须写在 protocol 里才会动态派发），`GrowthV2Screen` 实现：
      - 按在资产面板区域时 `expand += dy / panelHeight`，0…1 夹紧；
      - 松手按 0.5 阈值吸附展开/收起；
    - `ScreenHostView` 在纵向手势优先调用 `verticalDrag`，接管时不再滚页面。

52. **上拉时先滚页面而不是先收面板**
    - 2.4.35 的 `verticalDrag` 只在按下点位于面板区域时接管；上拉经常从下面内容区开始，于是页面先滚、面板不关；
    - 2.4.36 规则改成：**只要面板还开着，上拉一律先收起面板**（不论按下点），收到 0 后下一次移动才 return false，把后续上拉交给页面滚动；
    - 面板吃掉位移时重置 `dragStartY/dragStartScroll`，避免收完面板后页面突然跳一段。

53. **财富页眼睛图标切换资产显示**
    - 成长版资产默认 `GrowthStore.shared.hidden = true`，总资产显示 `****`；眼睛的 hitTest 返回 `toggleHide`，但 Router 的 `toggleHide` 只处理 `LicaiV2Screen.State`，Growth 分支完全没接；
    - 2.4.37 在 Router 里补：
      - `screen is GrowthV2Screen` → `GrowthStore.shared.hidden.toggle()` + 重绘；
      - 同时补 `toggleExpand` 的 `GrowthV2Screen.State` 分支，点箭头也能展开/收起。

54. **财富页底栏「首页」图标被压扁**
    - 底栏其它格按 `86×148` 拉伸，但 `lci_nav_home` 是 `225×256` 的宽图（图标+首页文字），按 86×148 画会严重变形；
    - 2.4.38 单独处理第 0 格：未选中时按 `225/1280 * w` 宽、`h` 高画 `lci_nav_home`，保持原始比例；选中时仍用蓝色支字标 + 单独画「首页」。

55. **「我的」页很多明显边框线**
    - 8 张 `me_card*.png` 素材最外圈自带 1~2px 边框，iOS 3x 屏上特别明显；
    - 2.4.39 在 `MeScreen` 绘制每张卡时裁掉源图最外 2px（`d.art(image, src: crop, rect)`），白卡外框线消失；
    - 黑卡主题顶部 `hk_me_bg` 原来分两段画（620+2 与 621~700 拉伸），3x 屏上会出现一条接缝；2.4.39 改成一次画满到 `bottom`，去掉接缝。

56. **卡片仍有浅紫/灰色描边（商家服务、账单）**
    - 2.4.39 裁 2px 后仍有线；查素材像素发现外圈不是实色边框，而是**半透明白边**：直接叠在 `#f2f3f7` 灰底上，半透明边缘混成一条浅紫/灰线；
    - 2.4.40 在每张卡画之前先用同尺寸白圆角垫底（`d.round(rect, radius: 10, .white)`），让半透明边缘融进白色，再画卡片；
    - 这样商家服务、账单、总资产等白卡都不再出现描边。

57. **描边仍在：Android 正文底色其实是白色**
    - ADB 抓 Android「我的」页取样：卡片间隙 `#ffffff`、卡片 `#ffffff`，顶部才是淡蓝渐变；iOS 的 `pageBg` 用了 `#f2f3f7` 灰底，白卡半透明边缘必然混出灰线；
    - 2.4.41 把 `MeScreen.pageBg` 改成 `UIColor.white`，卡片边缘白对白，描边消失；顶部渐变/黑卡头图仍由 `hk_me_bg`/`me_bgplate` 覆盖。

58. **白底后边框反而更明显**
    - 2.4.41 设备实测：卡片顶边仍有一条 `RGB(139,139,139)` 灰线，说明不是灰底混色，而是图片边缘插值/裁切后的 1px 深线；
    - 2.4.42 在每张卡画完后，用 2px 白色圆角描边压住边缘：
      `d.roundStroke(rect.insetBy(dx: 1, dy: 1), radius: 9, .white, width: 2)`；
    - 白线在白色卡面/白色页底上不可见，但会覆盖掉那条灰色插值线。

59. **2.4.42 仍有线；底栏图标不跟主题**
    - 白边放在 rect 内侧，灰线在矩形边界/外侧，没盖住；2.4.43 改成描边居中于边界 `d.roundStroke(rect, radius: 10, .white, width: 2)`，同时覆盖内外各 1px；
    - `ZhiTabBarView` 之前固定用蓝色 `#1677ff`，不看主题；2.4.43 按 `LicaiVersion.theme()`：
      - 普通版：选中蓝，未选中原色；
      - 成长版/V1/V2/V3：选中用 `theme.accent`，未选中用同色 52% 透明度；
      - 首页选中/未选中图标也一起染主题色；重启后五格颜色随主题变化。

60. **按 ADB 的 Android 底栏重做黑卡选中态**
    - ADB 截图对照：普通版选中是蓝色图标+蓝字；成长版/V1/V2/V3 选中态是**深色实心图标 + 主题金色文字**，不是整个图标染金；
    - 2.4.44 重写 `ZhiTabBarView`：
      - 普通版：选中图标整体染蓝；
      - 黑卡：选中图标保持素材深色，覆盖原文字区域后单独用 `theme.accent` 画「理财/首页/视频/消息/我的」；
      - 未选中项保持深色素材；
    - 卡片白边同时向内外各扩 1px：`roundStroke(rect, 2)` + `roundStroke(rect.insetBy(-1), 2)`。

61. **2.4.44 底栏金色文字压在图标上**
    - 通用格图片是 `44/256…192/256` 的区域，文字在图片底部；2.4.44 却按首页的 `146/256` 基线画，金色「理财」压在图标上；
    - 2.4.45 给 `drawLabel` 加 `baseline` 参数：通用格用 `184/256`，首页选中仍用 `146/256`；覆盖白块也下移到 `imageY + ih*0.58`；
    - 卡片素材裁边从 2px 加到 6px，进一步去掉图片自带的深色边缘。

62. **逐字节核对 Android APK 素材**
    - `adb pull base.apk` → `aapt2 dump resources` 映射出 `me_card1…8`、`lci_nav_*`、`me_nav_*`、`zhi_navbar`、`hk_me_bg` 的真实文件；
    - 对比结果：`me_card1…8`、`hk_me_bg` 与 iOS 素材**逐字节完全相同**；`lci_nav_*`/`zhi_navbar` 只是 PNG 重编码，**像素 diff 为 0**；
    - 所以卡片边框不是素材差异，而是 iOS 后加的裁剪/白底/描边自己产生的边；2.4.46 撤掉这些处理，改成和 Android 一样直接按原尺寸画 `me_card*`；
    - 底栏仍用同一批像素级相同的素材，按主题组合深色图标+金色文字。

63. **黑卡底栏真正缺的是 hk_nav_\* 素材**
    - 用户指出 Android 是**深色实心图标**，iOS 是细线描边图标；查 APK 发现 Android 黑卡主题用的是另一套：
      `hk_nav_index/fortune/video/message/me` + `_active`（90×90 JPG）；
    - iOS `Assets.xcassets` 里**完全没有这 10 张图**，所以只能拿普通版的细线 `lci_nav_*` 拼，视觉必然不对；
    - 2.4.47 从 APK 抽取这 10 张图，建好对应 imageset；`ZhiTabBarView` 在黑卡主题走 `hk_nav_*`：
      - 选中：`_active` 深色实心图标 + `theme.accent` 金色文字；
      - 未选中：普通 `hk_nav_*` 深色图标 + 深色文字；
      - 图标按 90/256 比例、文字基线 190/256，贴近 Android 布局。

64. **个人信息手机号默认随机、中间四位隐藏**
    - 之前 `MeScreen` / `ProfileV2Screen` / `Settings` 直接显示 `ProfileStore.phone`，是真实/当前号码；
    - 2.4.48 新增 `ProfileStore.displayPhone`：
      - 首次读取用 `randomPhone()` 生成一个 11 位号码并持久化到 `profile.phone.display`；
      - 展示统一 `maskPhone`：`138****1234`；
      - 不读 `profile.phone`，所以不会显示当前登录号码；
      - 用户手动编辑手机号后，`displayPhoneRaw` 同步更新；
    - 我的页、个人信息页、账号设置页都改用 `displayPhone`。

65. **余额提现页可用余额不可点**
    - `WithdrawV2Screen.drawAmountCard` 只在金额为空时显示「可用余额 ¥…」，但那一行没有 hitTest；`WithdrawStore` 已有 `setAvail` 却没有入口；
    - 2.4.49 在可用余额文字区域加 `hit(..., "avail")`；
    - Router 新增 `case "avail"`：弹数字输入框，当前值 `WithdrawStore.avail`，保存后 `setAvail` 并重绘；金额、校验、提现按钮都读同一个 avail。

66. **2.4.49 点可用余额仍打不开输入**
    - `WithdrawV2Screen.hitTest` 用 `hits.reversed()`，后加入的热区优先；`"amount"` 整块热区 `y 1000…1150` 覆盖了可用余额，且在 `avail` 之后加入，所以永远返回 `"amount"` 打开数字键盘；
    - 2.4.50 把 `avail` 热区移到 `amount/quota/info` 之后加入，reversed 时先命中 `"avail"`，才能弹修改可用余额的输入框。

67. **芝麻信用名片可编辑 + 头像同步**
    - `ZhimaCreditV2Screen.hitTest` 早就返回 `edit:p.label/p.score/...`，但 `State` 没实现 `EditableState`，Router 的通用 `edit:` 分支直接跳过，所以点名片没反应；
    - 2.4.51 给 `ZhimaCreditV2Screen.State` 补 `EditableState`：个人卡 5 个字段 + 企业卡 6 个字段都有标题、当前值、写回；
    - 个人卡原来素材里自带一个默认头像；现在有 `ProfileStore.shared.avatar` 时，按卡片头像位置 `(x+450, y+96, 140, 140)` 圆形裁剪覆盖，默认与个人信息头像同步。

68. **信用名片头像仍不能换 / 小荷包头像不可点**
    - 信用名片之前只有文字字段 hitTest，头像区域落在 `carousel` 上；2.4.52 加 `zhimaAvatar` 热区，弹「随机头像 / 从相册选择」，只改 `State.cardAvatar`（默认仍同步 ProfileStore 头像）；
    - 小荷包 `avatar:i` 令牌之前被通用头像分支吞掉；2.4.52 在 Router 里优先处理：
      - 点已有头像 → 随机头像（xxapi.cn 接口）或相册替换；
      - 点「+」→ 直接调 `PurseAvatarAPI` 拉一张随机头像并 `PurseStore.addAvatar`，满了 5 个给提示；
    - 新增 `PurseStore.setAvatar(_:at:)` 和 `PurseAvatarAPI.swift`；接口返回 `code==200` 的 `data` 图片地址后下载成 UIImage。

69. **娱乐页功能补全**
    - 「撩妹加 V」之前只改 Router 的 `teaseEnabled` 并 toast，`playbookSections` 里的原始行没变，重绘后开关仍回到关闭态；2.4.53 在 `handlePlay` 里：
      - 先读当前行 `enabled`，取反；
      - `setPlaybookSwitch("tease", enabled:)` 写回 `HomeShellScreen.State` / `HomeV2Screen.State` 的原始 JSON；
      - 再 `setNeedsDisplay`，开关立即变化；
    - `Playbook.sections(from:)` 原来把没有 `enabled` 字段的行当成 false 全部丢掉；2.4.53 改成「显式 false 才隐藏，开关行始终保留」；
    - `defaultSections` 从只有「短视频去水印」补成完整兜底：parse / order / call / shutdown / sound / tease，服务端配置拉取失败时六项玩法仍在。

70. **模拟来电页大部分控件只有令牌没有行为**
    - `CallSetupScreen.hitTest` 会吐 `call:time:*`、`call:contact:*`、`call:ring:*`、`name`、`avatar`，但 Router 的 `call:` 分支只处理 start/cancel，其它全部落到默认 `break`；
    - 2.4.54 补齐：
      - 立即/延迟 N 分钟/指定 HH:mm 三档都写回 `timeMode`、`delayMinutes`、`atHour/atMinute`；
      - 开始 → 计算 `pendingAt`，页面出现「已安排」条；取消 → 清空；
      - 联系人、姓名输入、头像选择都会回填当前 State；
      - 铃声选择/恢复默认/试听至少给出明确状态与提示；
    - `avatar` 令牌在 Router 里优先按 `CallSetupScreen.State` 处理，选完回填 `state.avatarImage`，不会再误跳个人资料页。

71. **淘宝订单详情全部信息可编辑、隐私随机化**
    - `TaobaoOrderScreen.hitTest` 早就吐出 `order:address/receiver/shop/goodsTitle/...`，但 Router 只处理 toggle/copy/addCart，其它字段点了没反应；
    - 2.4.55：
      - 新增 `TaobaoOrderScreen.State` 的 `EditableState`，覆盖地址、完整地址、收货人、店名、店铺说明、商品标题/规格/原价/数量/退款说明、实付/总价/优惠/应付、订单号、发票、交易号、创建/成交时间、保障依据/项数、交易状态；
      - `order:receiver` 用「姓名 手机号」一个输入框同时改两项；`order:rec:<i>` 用「标题|价格|标签|销量」改推荐位；
      - `order:pickImage` / `order:pickLogo` 从相册换商品主图和店铺图标；
      - `State.init()` 每次进入随机生成姓名、手机号、地址、店名、发票、订单号、交易号、创建/成交时间，不再使用真实隐私数据。

72. **Codemagic SwiftEmitModule 失败**
    - `TaobaoOrderScreen.State.init()` 是 `ScreenState` 子类的自定义初始化器，但没有调用 `super.init()`；Swift 要求所有路径先完成父类初始化，导致 `SwiftEmitModule` 失败；
    - 2.4.56 在 `State.init()` 末尾补 `super.init()`；
    - 同时把 `PurseAvatarAPI` 的 `try? JSONSerialization... as?` 拆成 `try?` + `as?` 两步，避免可选绑定推断歧义。

73. **2.4.56 仍未编译过：init() 要 override**
    - 无参数的 `init()` 与 `ScreenState` 隐式的 `init()` 同签名，Swift 要求写成 `override init()`；只补 `super.init()` 不够，仍会报 overriding declaration requires an 'override' keyword；
    - 2.4.57 改成 `override init()` 并保留 `super.init()`。

74. **模拟来电只有 toast，不会真的响**
    - 2.4.54 只写 `pendingAt`，没有计时器和来电界面；
    - 2.4.58 新增 `SimulatedCall` + `SimulatedCallViewController`：
      - `call:start` 用 `Timer` 在 `pendingAt` 到点后弹全屏来电；
      - 铃声用 `Resources/web/assets/audio/call.mp3` 循环播放，并每 2 秒震动；
      - 全屏界面有头像、姓名、「邀请你语音通话」、接听/拒绝；
      - 接听后停止铃声，显示「通话中」和计时，挂断按钮退出；
      - App 从后台回来时补查一次是否已到点，避免 Timer 暂停导致不响；
      - `call:cancel` 同时取消计时器；`call:ring:preview` 真正播放 3 秒铃声。

75. **2.4.58 构建报错：CAGradientLayer.autoresizingMask**
    - `CAGradientLayer` 的 `autoresizingMask` 是 `CAAutoresizingMask`，没有 `.flexibleWidth/.flexibleHeight`；
    - 2.4.59 去掉该行，改成持有 `gradientLayer` 并在 `viewDidLayoutSubviews()` 里同步 `frame = view.bounds`。

76. **模拟来电按微信效果重做**
    - 新增 `WeChatContactStore`：隐藏 WKWebView 打开 `http://localhost:18080/`，读 `allProfiles` / chat-actions contacts，取 `remark || nickname` 和头像，下载成 UIImage；
    - 进入模拟来电页时自动把微信通讯录填进 `CallSetupScreen.State.contacts`；点「来电人（从通讯录选择）」标题弹完整列表，点横向头像也可以直接选；
    - 选中联系人同步 `name + avatarImage`；用户仍可改名字；不选人时点头像从相册上传；
    - `SimulatedCallViewController` 按「微」的来电页重做：
      - 对方头像铺满 + 深色虚化背景；
      - 76×76 圆角头像、白色姓名、「邀请你语音通话...」；
      - 底部 67 圆按钮：挂断/接听；接通后变成麦克风/挂断/扬声器，带文字标签；
      - 铃声循环 + 震动保留。

77. **通讯录仍为空 / 头像换不了**
    - 2.4.60 只读 localStorage，而且隐藏 WebView 不一定是「微」启动的服务器；2.4.61：
      - `WeChatContactStore` 先 `LocalWebServer.shared.start` 拿真实端口，再开 `index.html#/wechat`，等 2 秒让 React 路由挂载；
      - override JS 新增 `window.__fwGetCallContacts()`，直接复用 `collectContacts()`（allProfiles + state.contacts + DOM），返回备注名/头像；
      - 原生优先调用这个函数，再退回 localStorage；加 `CrashLog` 记录 raw 数量便于排查；
    - 头像替换改成本地 `QRImagePicker`，选完直接写 `state.avatarImage`，不再依赖上传接口，网络失败也能换。

78. **昵称点不开 / 通讯录缓存**
    - 昵称 `hitTest` 吐的是 `"name"`，但 Router 只有 `case "name" where screen is CollectMoneyV2Screen`，CallSetup 永远匹配不到；2.4.62 改成 `"call:name"`，由 `call:` 分支处理；
    - 微页面每次 `syncNativeAddressBook()` 都通过 `FwContacts` message handler 把 profiles 直接交给原生，`WeChatContactStore` 存进 `UserDefaults`（id/备注名/头像 URL）；打开来电页优先用缓存，不再完全依赖隐藏 WebView 的 origin；
    - 标题热区扩大到 `insetBy(-12,-12)`，更容易点出完整通讯录列表。

79. **微主页退不出去 / 退出后自动重入**
    - `presentWeb` 对连续点击没有去重，可能叠两个 `WeChatV2Host`；dismiss 只关最上面一个，下面那个立刻露出来，看起来像自动重入；
    - 2.4.63 `presentWeb` 先判断当前 top / presented 是否已经是 `WeChatV2Host`，是就只 `open(route:)`，不再 present 第二层；
    - 边缘返回原来无条件先问网页 `__fwHandleChatPayBack`；微主页/小微主页上它可能返回 true，导致永远不 dismiss；
    - 现在先读 `location.hash`，只有聊天/支付/转账子页面才交给网页处理，其它页面直接 dismiss；
    - 返回按钮在 `viewDidLayoutSubviews` 里 `bringSubviewToFront`，避免被 WebView / 顶栏透明命中区盖住。

80. **2.4.63 仍会退出后自动打开**
    - 用户点的是顶部空白菜单的「返回APP主页」；关闭动作结束后，触摸落点可能继续传给首页宫格的「微」，于是又 `presentWeb` 一次；
    - 2.4.64 所有真正的退出都走 `WeChatV2Host.closeHost()`，记录 `lastCloseAt`；`presentWeb` 在关闭后 1 秒内直接 return；
    - 覆盖：左上角返回、边缘右滑、顶部菜单「返回APP主页」、网页 bridge `returnToAppHome` 四条路径。

81. **来电人只有四个可选**
    - 原来用 `UIAlertController.actionSheet`，且 `state.contacts.prefix(60)`；iOS 上 actionSheet 一屏只露出几行，联系人多了看起来像只有四个；
    - 2.4.65 新增 `ContactPickerViewController`：UITableView + 搜索框，列出全部联系人，逐个显示头像/首字；
    - 点「来电人（从通讯录选择）」改为 present 这个列表，不再限制 60 条；
    - `WeChatContactStore` 读取上限从 40 提到 300，前 80 个加载头像，其余先用首字，保证所有名字都能选。

82. **模拟收款声音只响“叮咚”，不是 Android 的播报**
    - 2.4.65 及以前 `Player` 只找 `account_sound_zhi.mp3` / `account_sound_wei.mp3`，包里没有就退回合成的双音 chime，所以听到的不是人声；
    - 从 APK 反编译确认 Android `p.g.b / b.d.q` 的方式：把一条播报拆成 **diaoluo_da + success + 数字音素 + tts_dot + 小数音素 + tts_yuan**，逐个 MediaPlayer 顺序播放；
    - 2.4.66 iOS 复用同一批素材（`web/assets/audio/account_sound/tts_*.mp3`）：
      - 新增 `sequence(amount:wechat:)` 按万/千/百/十/个生成音素序列；
      - `Player` 改成 AVAudioPlayer 队列，一条播完自动播下一条；
      - 金额小数按 `tts_dot` + 每位数字播放，最后 `tts_yuan`；
      - 设置 `.playback` 音频会话，保证静音键之外能正常出声；
      - 素材缺失时才退回原来的 chime。

83. **收款风格点击没反应**
    - `AccountSoundScreen.hitTest` 在 style chip 里已经改完 `s.style / s.randomStyle`，但返回的是 `"noop"`；
    - Router 的 `case "noop": return` 直接返回，不调用 `setNeedsDisplay`，所以状态变了、画面没重绘，看起来像没反应；
    - 2.4.67 改成 `case "noop"` 也 `host?.setNeedsDisplay()`，所有“就地改 State、只吐 noop”的设置项（收款风格、随机金额档位、声音开关等）都会立即刷新。

84. **2.4.67 仍点不动：随机态没有清除**
    - 设备截图确认「随机小支/小微」是选中态；`drawStyleChips` 的 `case 0/1` 条件是 `!s.randomStyle && style==...`；
    - 点「小支收款 / 小微收款」时只改了 `s.style`，没有把 `s.randomStyle` 置 false；随机 chip 继续亮着，看起来就是点击没反应；
    - 2.4.68 在 index 0/1 分支加 `s.randomStyle = false`，再改 `style`；点具体收款方会立即切过去。

85. **撩妹加 V 打开后扫一扫要提示摄像头损坏**
    - 网页 `wechat-chat-actions.js` 早就有 `cameraBroken()` → `openCameraBrokenDialog()`，但原生 `isScanDisabled` 永远回 false；
    - 新增 `TeaseStore`（`play.tease.enabled`），娱乐页切开关时同步写入；
    - `WeChatV2Host` 在 documentStart 注入 `window.__fwScanDisabled`，bridge shim 的 `isScanDisabled()` 同步读它；
    - `viewWillAppear` 再刷新一次，保证已打开的微页面也能拿到最新开关；
    - 原生 `isScanDisabled` 分支也改为返回 `TeaseStore.enabled`；
    - 弹窗文案改为「扫一扫不可用 / 摄像头物理损坏，无法使用扫一扫功能。」；
    - 关闭「撩妹加 V」后 flag 为 false，扫一扫恢复正常进入付款页。

86. **新装默认登录 / 登录密码两遍 / 登录页有水印 / 去水印慢**
    - Keychain 在卸载后仍保留，新安装会继承上一次的 `x-token`；2.4.70 在 `DoutuV2App.init()` 调 `Session.prepareFreshInstall()`，首次启动（`terms.accepted == false`）清空 token/signKey，默认未登录；
    - `LoginView` 登录模式只显示一个密码框；确认密码只在注册/重置新密码时出现；
    - `LoginPresenter` 原来把 `endSuppress` 放在 `present` 的 completion 里，而该 completion 是弹出完成就执行，登录页刚出现水印又被挂回来；改为 `LoginHostingController.viewDidDisappear` 真正关闭时再解除；
    - 去水印解析轮询从固定 1.5s 改为首次 0.15s、之后 0.7s，并防止重复点解析按钮重复起任务。

87. **去水印一直卡在“提交中”**
    - `startParse` / `pollParse` 原来在 `Task { @MainActor in ... }` 里直接 `await ApiClient.call`；DTW 握手包含 PBKDF2 和同步网络，一旦开始就把主线程占住，界面无法响应；
    - 2.4.71 把 `/api/v1/dw/parse` 和 `/api/v1/dw/task/...` 都放进 `Task.detached` 执行，结果再 `await MainActor.run` 回主线程更新 State；
    - 这样握手/网络慢时界面仍可滚动、返回，状态也能及时刷新，不会看起来像整页卡死。

88. **2.4.71 仍长时间停在提交中**
    - 2.4.72 给提交请求加 15 秒超时：超时后 `ParseScreen.failParse` 把进度条改成「解析失败 / 请求超时，请检查网络后重试」，不会再无限停在 8%；
    - `beginParse` / `attachTask` 补上 `polling = true`，防重复提交；
    - 新增 `CrashLog`：
      - `PARSE request start link=...`
      - `PARSE response code=... msg=...`
      - `PARSE request timeout`
      下一次若仍慢，可直接从 crash.log 判断是请求没回来、服务端返回错误，还是已回来但轮询慢。

89. **去水印请求字段名和 Android 不一致**
    - 反编译 Android `DoutuApi.parse`：body 用的是 `{"share_text": link}`；
    - iOS 之前发的是 `{"link": link}`，服务端拿不到分享文本，任务不会正常建立，表现就是一直“提交中”；
    - 2.4.73 改成 `body: ["share_text": link]`，与 Android 完全一致。

90. **改字段后返回 1002 签名校验失败**
    - 设备 crash.log：
      `PARSE response code=1002 msg=签名校验失败`；
    - 反编译 Android `DoutuApi.hmacHex`：`new SecretKeySpec(signKey.getBytes(UTF_8), "HmacSHA256")`，**直接用 signKey 字符串的 UTF-8 字节**；
    - iOS `hmacHex` 之前优先 `DtwCrypto.fromHex(key)`，当 signKey 是 64 位十六进制时会解成 32 字节，签名和服务端不一致；
    - 2.4.74 改成始终 `key.data(using: .utf8)`，与 Android 相同。

91. **45% 停住：轮询返回值用反了**
    - `ParseScreen.applyPollStatus` 的返回值语义是「是否还需要继续轮询」：status 0/1/2 返回 true，status 3/4/5 返回 false；
    - Router 里变量却叫 `done`，判断写成 `if done { showResult } else { poll }`，完全反了：
      - 状态 1（45%）返回 true，立刻当完成处理，不再发下一次轮询；
      - 结果数据里还没有 out_url，所以卡在 45% + 空结果；
    - 2.4.75 改成 `keepPolling` 语义：true → 继续 `pollParse`，false → `showResult`；
    - 同时加轮询错误处理和超时失败，不会无限停在某个百分比；
    - 提交的 `share_text` 优先提取第一个 `http(s)://` 链接，避免整段中文/表情干扰解析。

92. **下载到本地保存不了**
    - 之前 `download` 令牌只登记、Router 的 parse 分支没有实现下载；`ParseScreen` 的 `beginDownload/finishDownload/failDownload` 状态方法一直没被调用；
    - 2.4.76 新增 `MediaSaver`：
      - 视频走 `URLSession.downloadTask` → 临时 mp4 → `PHAssetCreationRequest` 写入相册；
      - 图集逐张下载 → `PHAssetChangeRequest.creationRequestForAsset` 批量写入；
      - `PHPhotoLibrary.requestAuthorization(for: .addOnly)`；
    - Router `download` 分支调用 `downloadParse`，开始/完成/失败都会更新按钮与进度文案；
    - `project.yml` 增加 `NSPhotoLibraryAddUsageDescription`，避免写入相册被系统拒绝；
    - 保存成功后按钮变「打开」，再次点击提示成品已在系统相册。

93. **修改应用图标无法切换**
    - 设备当前未开通会员（首页仍显示“联系客服开通会员”）；`AppIconChooser.present()` 里有 `guard Session.shared.isActivated`，未开通会员直接弹“会员功能”提示，根本进不了图标选择页；
    - Android 这一格没有会员判断，2.4.77 删除这个门槛，所有用户都能打开选择器；
    - 增加 `ICON apply / ICON error` 日志：若 `setAlternateIconName` 在 unsigned/重签安装上返回 -54，可以直接从 crash.log 看到；这种情况下是 iOS 对签名安装的限制，需要正常签名重装。

94. **会员账户也被挡在图标功能外**
    - 用户是会员但本地 `profile.activated` 仍是旧值/空；`refreshMemberStatusIfNeeded` 只在 `status_text` 为空时刷新，导致一直误判；
    - 2.4.78：
      - 恢复「仅会员可换图标」；
      - 点图标时若 `!isActivated` 且已登录，先调用 `refreshMemberStatus`，刷新完再判断是否打开选择器；
      - `refreshMemberStatusIfNeeded` 每次启动/回前台都刷新，不再依赖 status_text；
      - `Session.isActivated` 增加 `expire_at` 兜底：到期日在今天之后也算会员。

95. **切换图标弹「当前安装方式不允许」**
    - 设备日志确认 `ICON error -54`；这是 IconServices 的 -54（找不到备选图标文件），不是会员问题；
    - 旧版 IPA 里备选图标除了 Assets.car，还有散装的 `AppIconAlipay@2x.png` 等文件；当前 XcodeGen 只把图标放在 Assets.car 里，部分安装方式/系统版本解析不到；
    - 2.4.79 从 `.appiconset` 复制 `@2x/@3x` PNG 到 `Resources/AltIcons`，并在 `project.yml` 中作为独立资源加入，确保包根目录同时存在这些文件；
    - 如果仍返回 -54，说明当前 unsigned/重签安装本身被 IconServices 拒绝，需要正常签名安装。

96. **2.4.79 构建失败：AltIcons 目录不存在**
    - `.gitignore` 里 `*.png` 被忽略，2.4.79 提交时 6 个 PNG 实际没进仓库；Codemagic 上只有空的 `Resources/AltIcons`，XcodeGen 报 missing source directory；
    - 2.4.80 用 `git add -f ios_v2/Resources/AltIcons` 强制入库，并在本地用 `git ls-files` 确认 6 个文件都已跟踪。

97. **默认应用名改为「豆图」**
    - `project.yml` 的 `INFOPLIST_KEY_CFBundleDisplayName` 从「斗图去水印」改为「豆图」；
    - `AppIconChooser` 的说明文案同步改成「名称仍是豆图」；
    - 默认主图标仍是 `PreviewAppIconDoutu`，备选图标名不变。

98. **版本号重置为 1.0.0**
    - `MARKETING_VERSION: "1.0.0"`；
    - `CURRENT_PROJECT_VERSION: "1"`；
    - `DtwConfig.appVersion` 本来就是 `1.0.0`，版本号现在完全一致。

99. **App 内生成 WebClip 桌面图标**
    - iOS 不允许 App 静默安装描述文件，所以只能 App 生成 profile → Safari 下载 → 用户在「设置」确认一次；
    - 新增 `WebClipBuilder`：生成带 `Label`、自定义 `Icon`、`URL=doutu://` 的 `com.apple.webClip.managed` profile；
    - `LocalWebServer` 增加 `/__webclip.mobileconfig` 动态路由，以 `application/x-apple-aspen-config` 返回；
    - 图标切换成功后弹「添加到主屏幕」，选择后启动本地服务器并用 Safari 打开 profile；用户在设置里安装后桌面出现对应名称/图标；
    - `project.yml` 加 `postBuildScripts`，构建后往 Info.plist 写 `CFBundleURLTypes`，注册 `doutu://`；
    - 版本保持 `1.0.0`，build 升到 `2`。

100. **一次生成四个 WebClip 图标**
    - 用户要「图标和名称一起切换」，原 App 的 `CFBundleDisplayName` 运行时不能改；
    - 1.0.0(3) 把 `WebClipBuilder` 改成支持多 payload：一个描述文件里包含支付宝 / 支付宝HK / 微信 / 豆图 四个 WebClip；
    - 图标切换成功后提示一次安装，桌面直接出现四个对应名称+图标的入口，点击都通过 `doutu://` 打开同一个 App；
    - 原「豆图」App 图标可以长按「从主屏幕移除」，只保留四个自定义图标。

101. **图标页加常驻 WebClip 入口**
    - 有些设备切换图标成功后没有弹「添加四个图标」，可能是系统提示/弹窗被盖住；
    - 1.0.0(4) 在图标选择页底部加一个常驻按钮「添加到主屏幕（WebClip）」；
    - 点它直接生成四个 WebClip 的描述文件并用 Safari 打开，不再依赖切换成功后的弹窗。

102. **黑色支付宝去 HK；WebClip 分别进入小支/小微**
    - 图标选择器去掉蓝色支付宝重复项，黑金 `PreviewAppIconAlipayHK` 直接显示为「支付宝」；
    - WebClip 从四个改为三个：支付宝 / 微信 / 豆图；
    - `WebClipBuilder` 支持每个 WebClip 独立 URL：
      - 支付宝 → `doutu://zhi` → `ZhiHomeV2Screen` 小支主页；
      - 微信 → `doutu://wechat` → `WeChatV2Host` 小微主页；
      - 豆图 → `doutu://` → 应用首页；
    - `DoutuV2App.onOpenURL` 调用 `Router.handleDeepLink`；加入 pending 深链，防止冷启动时 Router 还没挂好。

103. **1.0.0(5) 构建失败：onOpenURL 与 MainActor**
    - `Router` 是 `@MainActor`，SwiftUI `.onOpenURL` 闭包不保证在主 actor 上，直接调用 `handleDeepLink` 会编译失败；
    - 1.0.0(6) 改成在闭包里 `Task { @MainActor in Router.shared?.handleDeepLink(url) }`；
    - 其余 WebClip 三项、深链、URL scheme 保持不变。

104. **1.0.0(6) 仍编译失败：LegacyDoutuApp**
    - `handleDeepLink` 里用了 `host?.window?.rootViewController` + `dismiss(completion:)` + 逃逸闭包，这是最可疑的编译点；
    - 1.0.0(7) 简化成：
      - `guard scheme == doutu, host != nil`
      - `zhi` 直接 `push(ZhiHomeV2Screen())`
      - `wechat` 直接 `presentWeb(route:"#/wechat")`
      - 不再访问 `window/rootViewController`，不再用 dismiss 闭包；
    - 去掉了 `pendingDeepLink` 和 init 里的深链处理，避免初始化顺序/主线程隔离问题。

105. **WebClip 全部进支付宝、蓝支付宝丢失、登录页 UI、抖/Q**
    - 深链：新增 `DoutuAppDelegate`，同时实现 `didFinishLaunching` 的 `.url` 与 `application(_:open:)`；`DeepLinkCenter` 在 Router 未挂载时缓存 pending，Router init 后 flush；`handleDeepLink` 加 `DEEPLINK` 日志；
    - 支付宝蓝：`options` 恢复 `PreviewAppIconAlipay / AppIconAlipay`，标签「支付宝蓝」；WebClip 四项：支付宝、支付宝蓝（都 `doutu://zhi`）、微信（`doutu://wechat`）、豆图（`doutu://`）；
    - 登录页：删除 Form/NavigationView，按 ADB 截图重做：浅蓝底、蓝紫渐变头图、白色圆角面板、登录/注册新账号胶囊 tab、灰底输入框、蓝色登录按钮、底部客服/邀请码链接和版权说明；
    - 抖/Q：`HomeShellScreen` / `HomeV2Screen` 点 `page:2/3` 返回 `pageUnavailable`，Router toast「该平台暂未开放，敬请期待」，不再设置 servicePage，也不会落到支模块宫格。

106. **1.0.0(8) 构建失败：DoutuV2App**
    - `DoutuAppDelegate` / `DeepLinkCenter` 访问 `Router.shared`（`@MainActor`），但类型本身没有标注 `@MainActor`，在 Xcode 26 SDK 下会报隔离错误；
    - 1.0.0(9) 给 `DoutuAppDelegate` 和 `DeepLinkCenter` 都加 `@MainActor`；
    - 如果仍失败，需要日志里 `DoutuV2App.swift` 的 `error:` 行才能继续定位。

107. **登录页从下往上、邀请码重置无反应**
    - `LoginPresenter` 之前用 `.pageSheet`，iOS 默认从底部弹；
    - 1.0.0(10) 改成 `.fullScreen` + `LoginSlideTransition` / `LoginSlideAnimator`：present 时从右侧推入，dismiss 时向右退出；
    - 「邀请码重置密码」按钮之前直接 `Router.handle("resetInvite")`，但 LoginView 还在模态栈顶，ResetByInviteScreen 被推到模态后面，看起来没反应；
    - 新增 `dismissThen(token)`：先 dismiss 登录页，0.32 秒后再 handle，小支的 ResetByInviteScreen 就能显示；客服入口也走同一逻辑。

108. **抖/Q 允许进入，但功能点击提示待更新**
    - 1.0.0(8) 把 page 2/3 直接拦截，导致连参考页都切不过去；
    - 1.0.0(11) 恢复 `page:2/3` 切换，显示 `dou_reference` / `q_reference` 参考页；
    - 在 page 2/3 下任何内容点击返回 `pageUnavailable`，Router 仍 toast「该平台暂未开放，敬请期待」；
    - 切回微/支仍正常。

109. **邀请码重置仍无反应；登录成功回「我」**
    - 之前 `dismissThen` 用 0.32 秒延迟，模态还没真正消失就 push，ResetByInvite 被推到登录页后面；
    - 新增 `Router.pendingAfterLoginDismiss`：按钮只记录 token 并 dismiss；`LoginHostingController.viewDidDisappear` 里再 `runPendingAfterLoginDismiss()`，保证真正关闭后再 push；
    - 登录/注册成功时调用 `Router.selectProfileTab()`，把 HomeShell/HomeV2 的 `tab` 设成 `.profile`，关闭登录页后自动回到「我」；
    - 客服入口也改为同样的延后执行。

110. **登录页 UI 变形；重置仍可能没反应**
    - 登录页的 header / panel / 输入框之前没有显式 `maxWidth`，SwiftUI 按内容 intrinsic 宽度算，白色面板会缩成一条、头图不铺满，看起来变形；
    - 1.0.0(13) 给 VStack、header、panel、输入框都加 `.frame(maxWidth: .infinity)`，面板固定撑满屏幕宽度；
    - 邀请码重置加双保险：除了 `viewDidDisappear` 的 `runPendingAfterLoginDismiss`，dismiss 后 0.6 秒再调用一次；token 执行后会被清空，不会重复 push。

111. **关闭 ×、登录成功、重置跳转都无反应**
    - 三个动作都依赖 SwiftUI `presentationMode.dismiss()`；在 `.fullScreen` + 自定义 `transitioningDelegate` 下它可能没有真正 dismiss 宿主控制器；
    - 1.0.0(14) 新增 `LoginCloseBox`，持有弱引用 `LoginHostingController`，`LoginView` 通过 `onClose` 直接调用 `controller.dismiss(animated:true)`；
    - × 改为一个 44×44 的 `Text("×")` 按钮，避免 SF Symbol 尺寸/裁剪变形；
    - 登录成功、邀请码重置都走 `closeLogin()`，不再依赖 `presentationMode`；
    - 1.0.0(15) 关闭前先把 `transitioningDelegate` 置 nil，避免自定义 dismiss animator 卡住；present 仍是自定义从右进入。

112. **登录页仍无法关闭 / 变形**
    - 自定义 `UIViewControllerAnimatedTransitioning` 在部分系统上会让 present/dismiss 卡住；
    - 1.0.0(16) 彻底删除 `LoginSlideTransition`，改用系统 `UINavigationController`：
      - 先 present 一个空导航控制器（无动画）；
      - 再 `pushViewController(LoginHostingController, animated:true)`，系统标准从右向左；
      - 关闭直接 `navigation.dismiss(animated:true)`；
    - `LoginCloseBox` 改为弱引用导航控制器，× / 登录成功 / 重置跳转都走同一条 dismiss；
    - 登录布局继续用 maxWidth 撑满，× 用文字按钮。

113. **1.0.0(16) 关闭仍无反应**
    - `LoginCloseBox.navigation` 是 weak，理论上被 present 的 nav 强持有，但为避免它变 nil，改成强引用，close 时先取出 nav 再置 nil；
    - × 按钮扩大到 52×52 并加 `.contentShape(Rectangle())`，避免只有文字可点；
    - 底部两个链接加 `.padding(.vertical, 6)`，扩大点击区域；
    - 加日志：
      - `LOGIN push host=...`
      - `LOGIN close onClose=...`
      - `LOGIN CloseBox nav=...`

114. **顶部头图改用 Android 抠图**
    - 从 ADB 登录页截图裁出 `(30,140)-(1050,600)` 的头图，保存为 `Assets.xcassets/LoginHeader.imageset/LoginHeader.png`；
    - `LoginView.header` 不再用 SwiftUI 画渐变/标题/副标题/胶囊，直接：
      `Image("LoginHeader").resizable().aspectRatio(1020/460, contentMode:.fit)`
    - 白色面板上移量从 -54 改为 -28，匹配抠图底部的面板圆角；
    - 头图 PNG 被 `.gitignore` 忽略，需要 `git add -f`。

115. **头图底部露出白色圆框；顶部 back 栏**
    - 之前裁到 y=600，把白色面板顶部的圆角也裁进去了，显示为一条白色弧线；
    - 重新裁 `(30,145)-(1050,580)`，y=580 之前全是渐变/胶囊，不含白色面板；
    - `LoginView.header` 的 aspectRatio 改成 `1020/435`；
    - `LoginHostingController.viewWillAppear` 再次 `setNavigationBarHidden(true)`，避免 push 后出现导航栏/back 栏。

116. **已登录但去水印提示身份过期**
    - 服务端 token 过期/失效时，parse 返回 `身份过期，请重新登录`，之前只显示失败；
    - 1.0.0(20)：
      - `startParse` 先检查 `Session.isLoggedIn`，未登录直接弹登录，登录成功后自动重试；
      - parse 返回「身份过期/重新登录」时清空 Session、显示登录，登录成功后自动重新提交同一个链接；
      - `LoginPresenter` 增加 `goProfile` 参数，从去水印触发的登录不会跳「我」页；
    - 客户端没有本地会员/次数限制，会员期内是否放行由服务端 token 与会员状态决定；这次修复的是过期 token 未触发重新登录。

117. **全局左边缘右滑返回**
    - `ScreenHostView` 增加 `UIScreenEdgePanGestureRecognizer(edges:.left)`，识别后 `onAction("back")` → `Router.pop()`；替换原来 touchesMoved 里的手写边缘判断，避免双触发，子视图/滚动页也能用；
    - 登录页 `LoginHostingController` 增加左边缘手势，触发 `LoginCloseBox` 关闭；
    - `ContactPickerViewController`、`AppIconChooser` 增加左边缘手势；
    - 小微主页 `WeChatV2Host` 原本已有边缘返回，子页面仍由网页先处理；
    - 根页面（首页/我的/娱乐）栈空时滑动手势不动作。

118. **交互式边缘返回手感**
    - 原来只在手势结束判断 60pt，页面不会跟手；
    - `ScreenHostView` 改成 began/changed/ended 三阶段：
      - began：把 Router 在 push 前抓的上一页快照铺在当前页下面，加阴影；
      - changed：当前页 `transform` 跟手右移，最大一屏宽；
      - ended：进度 >33% 或速度 >650 就完成返回，否则 spring 回弹；
      - 完成时轻触觉反馈，再 `Router.pop()`；
    - Router 增加 `historySnapshots`，push 时抓当前页快照，pop 时恢复上一层快照；
    - `canGoBack` / `onBackGesture` 由 Router 注入，根页面不响应。

119. **1.0.0(22) 编译失败：LegacyDoutuApp**
    - 新增的 `host.canGoBack = { [weak self] in ... }` / `onBackGesture` 是普通闭包，却访问 `@MainActor` 的 `Router.stack` / `pop`，Xcode 26 下报主线程隔离错误；
    - 1.0.0(23) 改成简单 Bool 属性 `host.canGoBack = false/true`，边缘返回触发原有 `onAction("back")`，不再在闭包里访问 Router；
    - `historySnapshots` 从 `[UIView?]` 改成 `[UIView]`，nil 时用空 UIView 占位，避免可选数组的类型推断问题。

120. **真正的编译错误：self?.self.host**
    - 头像/账号未登录分支里写成了：
      `self?.self.host?.setNeedsDisplay()`
    - Swift 会报 `Router` 没有 `self` 成员，这就是每次 LegacyDoutuApp 编译失败的根因；
    - 1.0.0(24) 改成 `self?.host?.setNeedsDisplay()`；
    - 同时把 `LoginPresenter.present(goProfile:false) { ... }` 改成显式 `onDone:` 参数，避免尾随闭包绑定歧义。

121. **微信零钱等深层页面边缘返回**
    - 原来 `WeChatV2Host.handleEdgePan` 只把 conversation/chat/pay/transfer 当子页面，钱包、零钱、账单、提现等深层路由一律直接关闭整个微主页，用户感觉“退了一层却全退了”；
    - 1.0.0(25) 改成统一脚本：
      1. 先调 `__fwHandleChatPayBack()`（聊天/支付浮层/本地覆盖页）；
      2. 没处理且 hash 不是 `#/wechat` 时执行 `history.back()`，逐级回退；
      3. 只有根路由且无浮层时才 `closeHost()`；
    - 这样微信零钱 → 钱包 → 微主页，每次边缘右滑只退一层。

122. **右侧边缘向左滑也返回**
    - 用户实际习惯是从屏幕**右边缘向左**滑；
    - `ScreenHostView` 同时注册 `.left` 和 `.right` 两个 `UIScreenEdgePanGestureRecognizer`：
      - 左边缘向右：`edgeSign=1`，当前页右移；
      - 右边缘向左：`edgeSign=-1`，当前页左移；
      - 速度/进度都乘 `edgeSign`；
    - `WeChatV2Host`、`LoginHostingController`、`ContactPickerViewController`、`AppIconChooser` 都补了右边缘手势；
    - 微信深层路由仍走 `__fwHandleChatPayBack` + `history.back()` 逐级返回。

123. **只保留左边缘右滑；零钱仍无反应**
    - 用户明确不要右边缘，1.0.0(27) 删除所有 `.right` 边缘手势，只保留 `.left` 向右；
    - 零钱/钱包/账单不是 hash 路由，而是 `wechat-pay-pages.js` 自绘层，真正的返回函数是 `window.__fwHandleBack`；
    - 原生边缘脚本改为依次调用：
      1. `__fwHandleBack()`（钱包/零钱/账单/银行卡）；
      2. `__fwHandleChatPayBack()`（聊天/支付浮层）；
      3. `history.back()`（其他深层 hash）；
      4. 都不处理才 `closeHost()`；
    - 这样零钱 → 钱包 → 服务/微主页每次只退一层。

124. **所有模态页统一 window 级边缘返回**
    - 新增 `EdgeBackCenter`：挂在 UIWindow 上的 `UIScreenEdgePanGestureRecognizer(edges:.left)`，任何最上层模态都能收到；
    - `EdgeBackHandling` 协议：
      - `WeChatV2Host` → 执行微信深层返回脚本；
      - `LoginHostingController` → `onEdgeBack`（关闭登录页）；
      - `ContactPickerViewController` → `cancel()`；
      - `AppIconChooser` → `close()`；
      - 其他未知模态 → 直接 `dismiss`；
    - `ScreenHostView.didMoveToWindow` 安装；原生 Canvas 页面仍用自身手势（带上一页快照的交互式效果）；
    - `EdgeBackCenter.shouldHandle()` 用 350ms 门槛，防止 window 手势和页面手势同时触发两次。

125. **1.0.0(28) EmitSwiftModule 失败**
    - `EdgeBackHandling` 是 `@MainActor` 协议，而 `WeChatV2Host` 同时实现 `WKScriptMessageHandler` 等非隔离协议，Xcode 26 在 module 生成阶段报隔离冲突；
    - 1.0.0(29) 删除 `EdgeBackHandling` 协议和所有 conformance，改用 `@objc handleEdgeBack` + `NSSelectorFromString("handleEdgeBack")`：
      - `EdgeBackCenter` 对最上层控制器做 `responds(to:)`，命中就 `perform`；
      - 未命中才 `dismiss`；
    - 不引入跨协议 MainActor 约束，规避 EmitSwiftModule 失败。

126. **1.0.0(29) 仍 EmitSwiftModule 失败**
    - 去掉协议后仍失败，说明问题不在协议，而在新增的 `EdgeBackCenter` / window 手势文件本身或它引起的类型推断；
    - 1.0.0(30) 直接删除 `EdgeBackCenter.swift` 及全部引用，回到每个页面自己的左边缘手势：
      - `ScreenHostView`：原生页面交互式返回；
      - `WeChatV2Host`：微信深层返回；
      - `LoginHostingController`：关闭登录；
      - `ContactPickerViewController`：关闭联系人；
      - `AppIconChooser`：关闭图标选择；
    - 不再有 window 级手势，避免 EmitSwiftModule 失败。

127. **1.0.0(30) 仍 EmitSwiftModule；CI 日志看不到 error**
    - 1.0.0(31) 移除 `@UIApplicationDelegateAdaptor`、`DoutuAppDelegate`、`DeepLinkCenter`，只保留 SwiftUI `.onOpenURL`；这是 module 生成阶段最后一个可疑的跨 actor 声明；
    - `codemagic.yaml` 的 Build unsigned 改成先写完整 build.log，再在最后打印：
      `grep -nE "error:|fatal error:" build.log | tail -40`
      这样用户贴的最后 50 行一定能看到真正的 error 行；
    - 如果仍失败，下一步直接按日志里的 file:line 修，不再猜。

128. **微信钱包逐级返回 + 四个 tab 左右滑**
    - `wechat-pay-pages.js` 已有 `window.__fwHandleBack`：balance→wallet→service，service→#/wechat/my；
    - 1.0.0(32) 原生脚本先调 `__fwHandleBack()`，再调 `__fwHandleChatPayBack()`，最后才 history.back；
    - 四个根 tab（#/wechat、contacts、discover、my）不再 history.back，再滑直接关闭微主页；
    - `WeChatV2Host` 增加 `UIPanGestureRecognizer`：仅在根 tab 页、水平位移 >60、垂直 <60、起点 x>40 时切换：
      - 左滑 → 下一个 tab；
      - 右滑 → 上一个 tab；
      - 顺序：微信 → 通讯录 → 发现 → 我。

129. **1.0.0(32) 编译错误：invalid escape sequence**
    - 具体错误：
      `WeChatV2Host.swift:445:57: error: invalid escape sequence in literal`
    - Swift 多行字符串里的 JS 正则 `/\/$/` 被 Swift 当成 `\/` 转义；
    - 1.0.0(33) 改成不用正则：
      ```js
      var h = String(location.hash || '');
      if (h.charAt(h.length - 1) === '/') h = h.slice(0, -1);
      ```
    - 同时保留 `codemagic.yaml` 最后的 `grep error:`，后续编译错误能直接看到。

130. **微信返回/切 tab 的跟手视觉**
    - 之前微信边缘返回和 tab 切换只在手势结束执行，页面不动；
    - 1.0.0(34)：
      - `makeSwipeSnapshot()` 在 began 抓当前 WKWebView 快照，盖在上面；
      - 边缘手势 changed 时快照跟手右移，松手超过 60pt 滑出后执行 `performEdgeBack()`，否则弹回；
      - tab 手势 changed 时快照跟手左右移，松手后先 `location.hash` 切到目标 tab，再让快照朝对应方向滑出，露出下一层 tab；
      - `tabSwipeActive` 保证左边缘 40pt 内只走边缘返回，不误切 tab；
      - 快照 `isUserInteractionEnabled=false`，不挡网页点击。

131. **滑动时真正露出下一层**
    - 1.0.0(34) 只是把当前页快照滑走，底下还是当前页；
    - 1.0.0(35) 在 began 后先让网页切到下一层，快照盖住，拖动时露出的就是下一层：
      - tab：`tabOriginalHash` 记录当前 tab；`updateTabPreview()` 在 changed 时按方向提前 `location.hash` 到相邻 tab；取消时恢复原 tab；
      - 边缘返回：`prepareEdgePreview()` 判断不是根 tab 后，先执行 `performEdgeBack()` 把网页切到上一层，再让快照跟手滑出；取消时 `history.forward()` 恢复；
      - 根 tab（微信/通讯录/发现/我）不预切，滑出后直接关闭微主页。
    - 1.0.0(36) `updateTabPreview` 增加 `abs(dx) > 20` 门槛，避免竖直滚动时轻微横移就预切 tab。

132. **只让选中层滑动，底层不动**
    - 底层页面切换时 React/CSS 自带路由动画，看起来像两层一起滑；
    - 1.0.0(37) 在 `wechat-chat-actions.css` 增加：
      ```css
      html.fw-swipe-preview * { transition: none !important; animation: none !important; }
      ```
    - 原生在 tab/边缘手势 began 时加 `fw-swipe-preview`，结束/回弹时移除；
    - 这样只有上层快照跟手移动，底层下一层保持静止。

133. **底层仍然会动：改成静态底图**
    - 只禁 CSS 动画不够，WKWebView 本身的路由切换/重绘仍可能位移；
    - 1.0.0(38)：
      - 上层：当前页快照，跟手移动；
      - 底层：等下一层路由渲染 120ms 后，再抓一张 `snapshotView(afterScreenUpdates:true)`，插在上层快照下面；
      - 然后 `webView.isHidden = true`，真正隐藏 WKWebView；
      - 滑动时视觉上只有上层快照移动，底层是静态图片，绝不会跟着动；
      - 结束/取消时 `clearSwipeLayers()` 移除两张快照、恢复 webView、移除 `fw-swipe-preview`；
      - 底层快照准备好前，上层最多先不动（`swipeReady=false`），准备好后再按当前手指位移继续跟手。

134. **滑一半又滑回去会停在新页面**
    - 原因：
      - `tabOriginalHash` 原来是在异步 JS 查询里才赋值，用户快速拖动时可能已经把 hash 切到目标，再记录就记成了目标页；
      - 手势 `.cancelled` 没有走取消分支；
      - 回拉到 20pt 以内时没有立即恢复原 tab；
    - 1.0.0(39)：
      - 用 `webView.url?.fragment` **同步**取原始 hash，不再依赖异步查询；
      - tab/边缘手势同时处理 `.ended` 和 `.cancelled`；
      - tab 回拉到 `abs(dx)<20` 时立刻 `restoreTabAfterCancel()`；
      - 边缘取消时用 `edgeOriginalHash` 直接恢复原路由，不再只靠 `history.forward()`；
      - 底层快照延迟从 120ms 调到 250ms，确保抓到的确实是下一层静态画面。

135. **底层仍动 + 取消闪一下**
    - 1.0.0(39) 在 began 时只加了顶层快照，真正的 WKWebView 还可见，直到 250ms 后抓到下层快照才隐藏；这段时间底层仍可能被手势/路由动画带着动；
    - 1.0.0(40)：
      - began 立刻加一张纯白占位，再隐藏 WKWebView、禁用 scrollView 滚动；从第一帧起底层只能是占位，不可能是活页面；
      - 抓下一层快照时临时取消隐藏，`layoutIfNeeded()` 后用 `snapshotView(afterScreenUpdates:true)` 抓图，马上再隐藏；顶层快照+占位盖着，这一帧不可见；
      - 取消时先把原路由恢复，顶层快照继续盖住，延迟 0.18~0.2s 再 `clearSwipeLayers()`，避免揭盖时看到路由重绘闪屏。

136. **黑色支付宝图标切换异常**
    - `AppIconAlipayHK.appiconset` 的 5 张 PNG 原始都是 RGBA（alpha 全 255）；
    - iOS IconServices 对 alternate icon 的 alpha 通道比较敏感，可能出现切换失败/图标空白；
    - 1.0.0(41) 用 PIL 把黑金支付宝图标全部转成无 alpha 的 RGB PNG（黑底合成），保留原尺寸；
    - 其他 `AppIconAlipay` / `AppIconWechat` 未改。

137. **钱包/余额在 Max 上有明显边框**
    - 微信 WebView 的 viewport 固定为 `width=360, initial-scale=1`；在 430pt 宽的 Pro Max 上，360 排版没有按屏宽放大，右侧/两侧会留下边；
    - `wechat-pay-pages.css` 又用 `100vw/100vh`，与缩放后的视口不一致，白卡和灰色背景没有铺满；
    - 1.0.0(42)：
      - `viewportShim` 用 `screen.width / 360` 计算 `initial-scale`/`maximum-scale`，让 360 基准在 390/430/440 宽屏都铺满；
      - 加 `viewport-fit=cover`；
      - 追加 CSS：`html,body,#root` 高度 100%，`#fw-pay-pages-host` 宽高 100% + inset 0，`.fw-pay-page` min-height 100%；
      - 顶部/底部用 `env(safe-area-inset-top/bottom)` 适配刘海和 Home 指示条。

138. **本轮 iOS 体验专项：滑动返回 / 滚动 / 转场 / 长按 / 触觉**
    - 目标不是「再画一屏」，而是把 27 屏共用的宿主交互补齐到 iOS 的手感。
    - **滚动惯性 + 橡皮筋**（`ScreenHostView`）：
      - 之前 `touchesMoved` 直接把位移写进 `state.scroll`，抬手就停（与 Android 自定义 View 一致，但 iOS 上显得极「卡」）；
      - 现在用 `CADisplayLink` 做惯性（速度按 UIScrollView 的 0.998/ms 衰减）与越界回弹（`x·d·c/(x+d·c)`），
        手指按下立即停住惯性，停手超过 120ms 不再给惯性；
    - **页面转场**（`setScreen(animated:backwards:)`）：
      - push 把旧页面快照向左推出，pop 向右滑出，带阴影；不再「硬切」；
      - 边缘手势提交后走新令牌 `edgeback`，Router 用 `pop(animated:false)`：旧页面已经被手指推走，
        不再叠第二段动画，也不会闪回旧页一帧（之前先 reset 位移、再异步 pop，中间至少露一帧）；
    - **边缘返回视差**：上一页快照从 -30% 平移回 0，当前页带矩形 shadowPath，拖动更接近 iOS 原生；
    - **长按**：改成按住 500ms 立即触发（之前要「按住 → 抬手」才算，且抬手同时被当点击），触发后给中等 Taptic，移动即取消；
    - **触觉**：切首页 tab / 平台页签 / 支底栏、边缘返回、支付密码六位成功，各有明确 Taptic；
    - **微信微主页横向退出**：
      - `presentWeb` 改 `.overFullScreen`，根页面边缘右滑时整个微主页跟手右移，露出下面的首页；
      - 松手按 28% 或速度 700 提交，`dismiss(animated:false)`，不再出现「横向滑 → 整页向下滑走」；
      - 开着浮层/子页时仍走原来的 `__fwHandleBack` / `__fwHandleChatPayBack` 逐层退；
      - 位移/速度改在 `view.superview` 坐标系取，避免视图自身跟手后把 translation 抵消；
    - **横幅滚动动画修复**：`BannerStore.hintFrame()` 的 progress 原来写成 `phase / animMs`
      （phase 已接近 interval 末尾，一进窗口就 ≥1），所以 420ms 的上下滚动从来没真正播过；
      改成 Android 的 `(phase - (interval - anim)) / anim`，并把重绘心跳对齐到窗口起点、滚动期间 30fps；
    - **关机页闪烁**：从 4fps 提到 60fps（只有黑底 + 电池，绘制很轻）；
    - **深色模式**：window 统一 `overrideUserInterfaceStyle = .light`，避免弹窗/键盘/滚动条变黑与页面割裂；
    - **协议扩展静态派发**（重要，已用 Compiler Explorer 验证）：
      - `longPress` / `selfScrolls` 之前只写在 `extension Screen`，宿主通过 `Screen` 存在类型调用
        永远拿到扩展默认值，页面里的实现根本不参与；
      - 已把它们提升为 `protocol Screen` 的要求（与 `zhiTabIndex` / `verticalDrag` 同一处理），
        这样**芝麻/设置页长按**、以及**模拟来电/意见反馈/去水印/邀请码重置的滚动**才真正生效；
      - `usesCameraBackground` 已提升；相机的层级改成**宿主同级的下面**（`insertSubview(_:belowSubview:)`），
        不再用子视图：UIView 的子视图永远盖在自身绘制内容之上，放子视图会把扫描线/按钮全遮住。
        相机页宿主变透明，z 序为「相机 → 页面浮层 → 底栏/转场快照」；
        相机不可用时 `CameraPreviewView` 自己是黑底 + 提示，白色控件仍可见；
      - `carouselDrag/Snap` 仍留在扩展里：Zhima 的实现没有拿到按下点 y，启用后会在任何横向拖动上
        误移轮播，需要先给协议加 `startY` 再做；
    - **`selfScrolls` 也提升为 protocol 要求**：
      - 之前宿主恒读到扩展默认的 `true`，依赖宿主滚动的页面（模拟来电 / 意见反馈 / 去水印 / 邀请码重置…）
        永远滚不动；这些页的背景本来就画到了 `max(H, contentH)`，注释写着「滚到底不会露白底」，
        说明它们一直是按宿主滚动写的；
      - 修正 5 个「声明 false 但自己做了 translate」的页面（Bill / BillV2 / Conversation / HomeShell / HomeV2）
        为 `true`，保持它们原来的行为；其余 false 页面开始真正由宿主滚；
    - **命中坐标统一（同轮已修）**：
      - 宿主 `touchesEnded` 一直会先加 `state.scroll` 再调 `hitTest`；但 18 个实际使用的页面
        又在 `hitTest` 里加了一次 → 滚动后点击整体偏移一个 scroll；
      - 已把全部**实际在用**的 V2 页面改成「内容坐标」：普通内容用宿主传入的 `y`，
        固定顶栏/底栏用 `rawY = y - scroll`（Asset / Balance / Huabei / Yuebao / Licai / Growth /
        Bill / BillDetail / BillAnalytics / CollectMoney / PayMsg / Receipt / SwitchAccount /
        ZhiHome / Zhima / Me / HomeShell / HomeV2）；
      - 只剩**未被 Router 使用的非 V2 旧文件**还保留 `+ s.scroll`，属于死代码，不要再从 Router 里引用它们；
    - 版本 1.0.0(43)。改动文件：`Screen.swift`、`BannerStore.swift`、`LegacyDoutuApp.swift`、
      `WeChatV2Host.swift`、`ZhimaCreditScreen.swift`、`ZhimaCreditV2Screen.swift`、`project.yml`。

---

---

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
