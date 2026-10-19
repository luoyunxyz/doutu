# 斗图去水印 iOS V2 交接文档

> 生成时间：2026-10-19  
> 工作目录：`D:\AAA\doutu`  
> 当前分支：`main`  
> 最新提交：`本轮 2.4.43：底栏图标跟随主题，卡片白边外扩`  
> 最新代码版本：`2.4.43 / 20083`（已推送，待 Codemagic 构建）  
> 设备当前安装：`2.4.42 / 20082`（已通过 USB 查询确认）

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
