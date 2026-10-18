import UIKit

/// 「设置」页 —— `account/SettingsActivity.java`（541 行）的 1:1 移植。
///
/// ## 参考画布
/// Java 这一页是**原生控件布局**（`ScrollView` + `LinearLayout` + `dp()`），没有 `W`/`H`
/// 常量，所以按移植约定取 **1280×2816**（与本工程绝大多数页面同一张参考画布）。
/// 换算关系写死在 `dp(_:)` 里：`1dp = 1280 / 360 ≈ 3.5556` 参考像素
/// （Android 参考机的宽度按 360dp 计），于是 16sp 标签 ≈ 56.9px —— 与
/// `ProfileV2Screen` 的标签 57px 正好对上，两屏并排看字号一致。
///
/// ## 与 Android 的逐条对应
/// * Java 的 `row(label, value, …)` 四行（头像 / 用户名 / 手机号 / 会员状态）→ `Geo` 里四个矩形；
/// * Java 的分隔线（`dp(1)`、左缩进 `dp(18)`）→ `Geo.cardDividers`；
/// * Java 的 `GradientDrawable` 圆角 → `Draw.round` / `Draw.roundStroke`；
/// * Java 的 `AvatarView`（圆形头像 / 首字）→ `drawAvatar`（同一个渐变 + cover 裁法）；
/// * Java 的 `CrownIcon`（纯 Path 小金冠）→ `drawCrown`（同样 6 个点 + 底部横条）。
///
/// ## 状态
/// Java 的 `refresh()`（Java:389-398）把 `Session` 的值写进 `TextView`；iOS 这边画布是
/// 即时重绘的，所以 `State` 用**计算属性**直接读 `Session` / `ProfileStore` ——
/// 等价于「每次 onResume 都 refresh 一次」，不会出现读到旧值的问题。
///
/// ## 系统能力
/// 换头像用系统相册（`AvatarPicker.present`，iOS 14 的 `PHPickerViewController`，
/// 不需要相册权限），改用户名 / 会员状态 / 恢复默认头像 / 退出登录这几处
/// Java 弹的是 `AlertDialog`，iOS 侧由 Router 弹原生 `UIAlertController` ——
/// 本屏只负责发令牌（见文件末尾的令牌清单）。
///
/// | Java | iOS 等价实现 |
/// | --- | --- |
/// | Java:291-305 `pickAvatar()` / `onActivityResult` | `AvatarPicker.present`（PHPicker，iOS 14+，免权限） |
/// | Java:343-385 `readAndEncode()` 压 320px JPEG | `AvatarCompressor.jpegBase64`（AuthService.swift:137-150） |
/// | Java:326 `DoutuApi.uploadAvatar` | `AuthService.uploadAvatar`（AuthService.swift:112-122） |
/// | Java:449 `DoutuApi.changeUsername` | `AuthService.changeUsername`（AuthService.swift:101-109） |
/// | Java:491 `DoutuApi.logout` | `AuthService.logout()`（AuthService.swift:124-133，会清 token 与本地头像） |
/// | Java:403 `DoutuApi.status` | **无封装**：需用 `ApiClient.shared.call("POST", "/api/v1/auth/status")` 另写 |
/// | Java:134 `DoutuApi.removeAvatar` | **无封装**：恢复默认头像接口 iOS 侧还没接（见 `avatarRestore` 令牌） |
/// | Java:499 `Toast` | Router 的 `"toast:<文案>"` 令牌 → `showToast` |
/// | Java:507 `open(Context)` | Router `push(SettingsV2Screen(), state: SettingsV2Screen.State())` |
final class SettingsV2Screen: Screen {
    /// Java 没有 W/H：本页原本是 dp 布局，这里统一到 1280×2816 参考画布
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// Java 的滚动是外层 `ScrollView`（Java:68-77）做的，draw 里没有 translate
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// 1dp → 参考画布像素（1280 / 360，见类型注释）
    private static func dp(_ value: CGFloat) -> CGFloat { value * 1280.0 / 360.0 }

    // MARK: - 调色板（Java:36-40）

    /// Java:36 `INK`
    private let ink = Draw.color(0xff1b1f27)
    /// Java:37 `SUB`（Java 里声明了但整页没用到，仅登记）
    private let subUnused = Draw.color(0xff8a8f9a)
    /// Java:38 `LINE`
    private let line = Draw.color(0xffeff1f6)
    /// Java:39 `BRAND`（Java 里声明了但整页没用到，仅登记）
    private let brandUnused = Draw.color(0xff2f6fed)
    /// Java:40 `CARD_BG`（整页底色）
    private let pageBg = Draw.color(0xfff6f8fe)

    /// Java:272 值文本 0xFF6B7280
    private let valueInk = Draw.color(0xff6b7280)
    /// Java:283 箭头 0xFFB5BCC8
    private let chevronInk = Draw.color(0xffb5bcc8)
    /// Java:205 退出登录文字 0xFF2B3A55
    private let logoutInk = Draw.color(0xff2b3a55)
    /// Java:210 退出登录描边 0xFF9FB3D9
    private let logoutStroke = Draw.color(0xff9fb3d9)
    /// Java:536 小金冠 0xFFE8B33C
    private let crownGold = Draw.color(0xffe8b33c)
    /// Java AvatarView:23-24 头像渐变两端
    private let avatarFrom = Draw.color(0xff2f6fed)
    private let avatarTo = Draw.color(0xff8a6bff)

    // MARK: - 字号（Java 的 sp，逐处标注行号）

    /// Java:236 行标签 16sp
    private var labelSize: CGFloat { Self.dp(16) }
    /// Java:271 值文本 16sp
    private var valueSize: CGFloat { Self.dp(16) }
    /// Java:87 返回箭头 38sp
    private var backSize: CGFloat { Self.dp(38) }
    /// Java:98 标题 20sp（粗体）
    private var titleSize: CGFloat { Self.dp(20) }
    /// Java:282 箭头 22sp
    private var chevronSize: CGFloat { Self.dp(22) }
    /// Java:203 退出登录 17sp（粗体）
    private var logoutSize: CGFloat { Self.dp(17) }

    // MARK: - 几何

    /// 布局矩形。
    ///
    /// 每一项都对应 Java 里一层 `LayoutParams`：
    /// 根 `padding(18,20,18,26)`（Java:74）→ header `dp(56)`（Java:82）→ 白卡 `topMargin 12`
    /// （Java:116）→ 卡片内逐行（Java:227-267 的 `row()`）→ 底部 `spacer`（weight=1，Java:196-198）
    /// → 描边胶囊 `topMargin 28`（Java:215）。
    private struct Geo {
        let header: CGRect
        let backBox: CGRect
        let card: CGRect
        let avatarRow: CGRect
        let nameRow: CGRect
        let phoneRow: CGRect
        let memberRow: CGRect
        let cardDividers: [CGRect]
        let logout: CGRect
        /// 内容总高度（含根的下内边距），用于算滚动量
        let contentHeight: CGFloat
    }

    private lazy var geo: Geo = SettingsV2Screen.makeGeo()

    private static func makeGeo() -> Geo {
        let padH = dp(18)            // Java:74 左右内边距
        let padTop = dp(20)          // Java:74 上内边距
        let padBottom = dp(26)       // Java:74 下内边距
        let headerH = dp(56)         // Java:82 标题栏高度
        let cardTopGap = dp(12)      // Java:116 cardParams.topMargin
        let rowPadding = dp(16)      // Java:231 row padding 上下左右（右是 dp16）
        let rowPaddingV = dp(16)     // Java:231
        let dividerH = dp(1)         // Java:191 / 260 分隔线高度

        let cardX = padH
        let cardW = W - padH * 2
        let cardTop = padTop + headerH + cardTopGap

        // Java:123 头像行：值固定 dp(56)×dp(56)，行高 = 56 + 上下各 dp16
        let avatarRowH = dp(56) + rowPaddingV * 2
        // 其余两行（用户名 / 手机号）：单行 16sp + 上下各 dp16
        let textRowH = dp(19) + rowPaddingV * 2
        // 会员行：padding 上下各 dp18（Java:163），行高取 22sp 箭头那一档（约 26dp）
        let memberRowH = dp(26) + dp(18) * 2

        var y = cardTop
        let avatarRow = CGRect(x: cardX, y: y, width: cardW, height: avatarRowH)
        y += avatarRowH

        // Java:257-264 / 188-194：分隔线 `MATCH_PARENT` 高 `dp(1)`，`leftMargin dp(18)`
        var dividers: [CGRect] = []
        func appendDivider() {
            dividers.append(CGRect(x: cardX + dp(18), y: y, width: cardW - dp(18),
                                   height: dividerH))
            y += dividerH
        }

        appendDivider()
        let nameRow = CGRect(x: cardX, y: y, width: cardW, height: textRowH)
        y += textRowH
        appendDivider()
        let phoneRow = CGRect(x: cardX, y: y, width: cardW, height: textRowH)
        y += textRowH
        appendDivider()
        let memberRow = CGRect(x: cardX, y: y, width: cardW, height: memberRowH)
        y += memberRowH

        let card = CGRect(x: cardX, y: cardTop, width: cardW, height: y - cardTop)

        // Java:200-218 描边胶囊：文字 17sp（约 21dp 高）+ 上下各 dp16 内边距
        let logoutH = dp(21) + rowPadding * 2
        let logout = CGRect(x: cardX, y: H - padBottom - logoutH,
                            width: cardW, height: logoutH)

        return Geo(
            header: CGRect(x: cardX, y: padTop, width: cardW, height: headerH),
            // Java:92-94 返回按钮 dp(56)×dp(56)，START|CENTER_VERTICAL
            backBox: CGRect(x: cardX, y: padTop, width: dp(56), height: dp(56)),
            card: card,
            avatarRow: avatarRow,
            nameRow: nameRow,
            phoneRow: phoneRow,
            memberRow: memberRow,
            cardDividers: dividers,
            logout: logout,
            contentHeight: logout.maxY + padBottom
        )
    }

    /// 文本基线：Android 的 `TextView` 是**垂直居中**排一行文字，
    /// 基线相对行框中线偏下约 `0.37 * 字号`（SF 的 ascender 0.955 / descender -0.212）。
    private func baseline(_ midY: CGFloat, _ size: CGFloat) -> CGFloat {
        midY + size * 0.37
    }

    // MARK: - 状态

    /// Java 的四个 `TextView` + `AvatarView` 的可变状态。
    ///
    /// 全部用计算属性直接读本地会话，语义等于 Java 每次 `onResume` 都调 `refresh()`
    /// （Java:60-63 / 389-398），因此不需要外部再喂一次数据。
    final class State: ScreenState {
        /// Java:390 `AvatarStore.bitmap()`（有上传图才有值）
        var avatar: UIImage? { ProfileStore.shared.avatar }
        /// Java:390 `firstChar(Session.displayName())`
        var initial: String { SettingsV2Screen.firstChar(Session.shared.displayName) }
        /// Java:392 用户名：`Session.username()` 为空时退回 `displayName()`
        var username: String {
            let name = UserDefaults.standard.string(forKey: "profile.name") ?? ""
            return name.isEmpty ? Session.shared.displayName : name
        }
        /// Java:393 + 416-419 手机号（中间四位打码）
        var phoneMasked: String { SettingsV2Screen.maskPhone(ProfileStore.shared.phone) }
        /// Java:394-396 会员状态文案
        var memberText: String {
            guard Session.shared.isActivated else { return "未开通会员 · 带水印" }
            let expire = Session.shared.expireAt
            let day = expire.isEmpty
                ? "-"
                : String(expire.prefix(10)).replacingOccurrences(of: "-", with: ".")
            return "VIP 会员 · 有效期至 " + day
        }
        /// Java:397 会员状态颜色（已开通金色 / 未开通灰）
        var memberColor: UIColor {
            Session.shared.isActivated
                ? SettingsV2Screen.memberGoldColor
                : SettingsV2Screen.valueInkColor
        }
    }

    // MARK: - 绘制（Java:67-221 buildUi 的等价物）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()

        // Java:69 整页底色 CARD_BG
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        drawHeader(d)
        drawCard(d, s)
        drawLogout(d)
    }

    /// 标题栏：返回箭头 + 居中「设置」（Java:79-104）
    private func drawHeader(_ d: Draw) {
        let g = geo
        // Java:85-94 「‹」38sp，居中在 dp(56) 盒子里；Java:90 有 dp(6) 下内边距，
        // 所以文字中心比盒子中心高 3dp
        d.centerText("‹", cx: g.backBox.midX,
                     baseline: baseline(g.backBox.midY - Self.dp(3), backSize),
                     size: backSize, ink)
        // Java:96-104 「设置」20sp 粗体，整行居中（FrameLayout MATCH_PARENT）
        d.centerText("设置", cx: g.header.midX, baseline: baseline(g.header.midY, titleSize),
                     size: titleSize, ink, bold: true)
    }

    /// 白卡：底部阴影 + 圆角 + 四行 + 三条分隔线（Java:106-194）
    private func drawCard(_ d: Draw, _ s: State) {
        let g = geo

        // Java:113 card.setElevation(dp(6))：Android 由框架画阴影，画布上要自己补一层
        d.ctx.saveGState()
        d.ctx.setShadow(offset: CGSize(width: 0, height: Self.dp(3)), blur: Self.dp(6),
                        color: UIColor(white: 0, alpha: 0.10).cgColor)
        d.ctx.setFillColor(UIColor.white.cgColor)
        d.ctx.addPath(UIBezierPath(roundedRect: g.card,
                                   cornerRadius: Self.dp(18)).cgPath)   // Java:111 dp(18)
        d.ctx.fillPath()
        d.ctx.restoreGState()

        // Java:188-194 / 257-264 三条分隔线（1dp，左缩进 18dp，颜色 LINE）
        for divider in g.cardDividers {
            d.rect(divider, line)
        }

        drawAvatarRow(d, s)
        drawValueRow(d, row: g.nameRow, label: "用户名", value: s.username)
        drawValueRow(d, row: g.phoneRow, label: "手机号", value: s.phoneMasked)
        drawMemberRow(d, s)
    }

    /// 头像行：左「头像」+ 右侧圆形头像（Java:120-147）
    private func drawAvatarRow(_ d: Draw, _ s: State) {
        let row = geo.avatarRow
        d.text("头像", x: row.minX + Self.dp(18), baseline: baseline(row.midY, labelSize),
               size: labelSize, ink)
        // Java:123 row(…, avatarView, true, dp(56), dp(56))：固定 dp56 的圆形，排在箭头**左边**
        // （Java 的 addView 顺序是 标签 → 占位 → 头像 → 箭头），行右内边距 dp16（Java:231）
        let side = Self.dp(56)
        let avatarRight = chevronLayout(row).valueRight + Self.dp(8)   // = 箭头左边
        let box = CGRect(x: avatarRight - side, y: row.midY - side / 2,
                         width: side, height: side)
        drawAvatar(d, in: box, image: s.avatar, initial: s.initial)
        // 头像行同样带箭头（Java:123 的 withChevron = true）
        drawChevron(d, row)
    }

    /// 圆形头像：实色 + 斜向渐变打底；有图按 cover 裁进圆，没图画首字（AvatarView.java:44-80）
    private func drawAvatar(_ d: Draw, in box: CGRect, image: UIImage?, initial: String) {
        let cx = box.midX
        let cy = box.midY
        let r = min(box.width, box.height) / 2
        guard r > 0 else { return }

        // AvatarView:53-55 实色打底
        d.circle(cx: cx, cy: cy, r: r, avatarFrom)

        // AvatarView:56-57 LinearGradient(0,0 → w,h) 叠在圆内（斜向，Draw.gradient 只有横/纵，
        // 所以这里直接用 CGGradient 画对角）
        if let space = CGColorSpace(name: CGColorSpace.sRGB),
           let grad = CGGradient(colorsSpace: space,
                                 colors: [avatarFrom.cgColor, avatarTo.cgColor] as CFArray,
                                 locations: [0, 1]) {
            d.ctx.saveGState()
            d.ctx.addEllipse(in: box)
            d.ctx.clip()
            d.ctx.drawLinearGradient(grad,
                                     start: CGPoint(x: box.minX, y: box.minY),
                                     end: CGPoint(x: box.maxX, y: box.maxY),
                                     options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
            d.ctx.restoreGState()
        }

        if let image {
            // AvatarView:60-72 圆形裁剪 + cover（短边铺满、长边居中裁掉）
            d.ctx.saveGState()
            d.ctx.addEllipse(in: box)
            d.ctx.clip()
            d.artFill(image, in: box)
            d.ctx.restoreGState()
        } else {
            // AvatarView:74-78 首字：字号 r*0.9、白色粗体、基线 cy + r*0.33、水平居中
            d.centerText(initial, cx: cx, baseline: cy + r * 0.33, size: r * 0.9,
                         UIColor.white, bold: true)
        }
    }

    /// 「标签 + 右对齐值 + 箭头」一行（Java:227-267 的 `row()`，值 `setGravity(END)`）
    private func drawValueRow(_ d: Draw, row: CGRect, label: String, value: String) {
        let labelX = row.minX + Self.dp(18)
        d.text(label, x: labelX, baseline: baseline(row.midY, labelSize),
               size: labelSize, ink)
        let chevron = chevronLayout(row)
        // Java:247-252 值是 weight=1 右对齐，且自带 dp(8) 右内边距；
        // Java:275 `ellipsize(END)` 超宽要截断加省略号
        let boxLeft = labelX + Draw.width(label, size: labelSize)
        let fitted = fit(value, size: valueSize, maxWidth: chevron.valueRight - boxLeft)
        d.text(fitted, x: chevron.valueRight - Draw.width(fitted, size: valueSize),
               baseline: baseline(row.midY, valueSize), size: valueSize, valueInk)
        drawChevron(d, row)
    }

    /// 会员行：小金冠 + 「会员状态」+ 紧跟标签的值 + 箭头（Java:159-194）
    private func drawMemberRow(_ d: Draw, _ s: State) {
        let row = geo.memberRow
        let crownW = Self.dp(24)          // Java:167
        let crownH = Self.dp(21)
        let crownX = row.minX + Self.dp(18)   // Java:163 padding left dp18
        drawCrown(d, in: CGRect(x: crownX, y: row.midY - crownH / 2,
                                width: crownW, height: crownH))

        // Java:171-176 标签 16sp（Java:168 crown 的 rightMargin = dp10）
        let labelX = crownX + crownW + Self.dp(10)
        d.text("会员状态", x: labelX, baseline: baseline(row.midY, labelSize),
               size: labelSize, ink)
        // Java:177-182 值：`Gravity.START` + `leftMargin dp10`，紧跟标签
        let valueX = labelX + Draw.width("会员状态", size: labelSize) + Self.dp(10)
        let available = chevronLayout(row).valueRight + Self.dp(8) - valueX
        let fitted = fit(s.memberText, size: valueSize, maxWidth: available)
        d.text(fitted, x: valueX, baseline: baseline(row.midY, valueSize),
               size: valueSize, s.memberColor)

        drawChevron(d, row)
    }

    /// 箭头「›」22sp（Java:279-286）
    private func drawChevron(_ d: Draw, _ row: CGRect) {
        let layout = chevronLayout(row)
        d.text("›", x: layout.textX, baseline: baseline(row.midY, chevronSize),
               size: chevronSize, chevronInk)
    }

    /// 箭头与值右边界的位置（Java:231 行右内边距 dp16 / Java:284 箭头左内边距 dp4 /
    /// Java:252 值右内边距 dp8）
    private func chevronLayout(_ row: CGRect) -> (textX: CGFloat, valueRight: CGFloat) {
        let chevronRight = row.maxX - Self.dp(16)
        let textWidth = Draw.width("›", size: chevronSize)
        let textX = chevronRight - textWidth
        let viewLeft = textX - Self.dp(4)
        return (textX: textX, valueRight: viewLeft - Self.dp(8))
    }

    /// 小金冠：6 点路径 + 底部横条（Java:514-539 的 `CrownIcon.onDraw`）
    private func drawCrown(_ d: Draw, in box: CGRect) {
        let w = box.width
        let h = box.height
        guard w > 0, h > 0 else { return }
        let left = box.minX + w * 0.06      // Java:527
        let right = box.minX + w * 0.94
        let top = box.minY + h * 0.10
        let base = box.minY + h * 0.72
        let points: [(CGFloat, CGFloat)] = [
            (left, base),                              // Java:528
            (left, top + h * 0.16),                    // Java:529
            (box.minX + w * 0.34, top + h * 0.46),     // Java:530
            (box.minX + w * 0.5, top),                 // Java:531
            (box.minX + w * 0.66, top + h * 0.46),     // Java:532
            (right, top + h * 0.16),                   // Java:533
            (right, base),                             // Java:534
        ]
        d.path(points: points, fill: crownGold)
        // Java:538 drawRect(left, h*0.80, right, h*0.94)
        d.rect(CGRect(x: left, y: box.minY + h * 0.80, width: right - left, height: h * 0.14),
               crownGold)
    }

    /// 底部描边胶囊「退出登录」（Java:200-218）
    private func drawLogout(_ d: Draw) {
        let box = geo.logout
        // Java:207-211 白底 + dp30 圆角 + dp1.6 描边
        d.round(box, radius: Self.dp(30), UIColor.white)
        d.roundStroke(box, radius: Self.dp(30), logoutStroke, width: Self.dp(1.6))
        // Java:202-206 「退出登录」17sp 粗体居中
        d.centerText("退出登录", cx: box.midX, baseline: baseline(box.midY, logoutSize),
                     size: logoutSize, logoutInk, bold: true)
    }

    // MARK: - 滚动

    func maxScroll(_ state: ScreenState) -> CGFloat {
        // Java 用 ScrollView 包整页（Java:68-77）。参考画布 1280×2816 上内容只有约 2724px，
        // 所以实际恒为 0；这里保留同一公式，以后内容变高不必改函数。
        max(0, geo.contentHeight - Self.H)
    }

    // MARK: - 命中（对应 Java 各处 setOnClickListener，顺序与 Java 一致）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let g = geo
        let point = CGPoint(x: x, y: y)
        // Java:91 back.setOnClickListener(v -> finish())
        if g.backBox.contains(point) { return "back" }
        // Java:124 avatarRow → AvatarUpload.pick（iOS: AvatarPicker.present，系统相册）
        if g.avatarRow.contains(point) { return "avatar" }
        // Java:152 用户名行 → renameDialog()（iOS: Router 弹原生 Alert + AuthService.changeUsername）
        if g.nameRow.contains(point) { return "editName" }
        // Java:157 手机号行 → toast("手机号不可自助修改，请联系客服")
        if g.phoneRow.contains(point) {
            return "toast:手机号不可自助修改，请联系客服"
        }
        // Java:185 会员行 → memberDialog()
        if g.memberRow.contains(point) { return "member" }
        // Java:217 退出登录 → confirmLogout()
        if g.logout.contains(point) { return "logout" }
        return "noop"
    }

    /// 长按只有头像行挂了监听（Java:125-147）
    func longPress(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken? {
        guard geo.avatarRow.contains(CGPoint(x: x, y: y)) else { return nil }
        let s = (state as? State) ?? State()
        // Java:126-129 已经是默认头像 → 只提示
        if s.avatar == nil {
            return "toast:当前就是默认头像，点一下可以上传"
        }
        // Java:130-145 「恢复默认头像」确认框 → DoutuApi.removeAvatar
        return "avatarRestore"
    }

    // MARK: - 工具（Java:416-424）

    /// 单行截断（对应 Java:274-275 的 `setSingleLine(true)` + `ellipsize(END)`）。
    /// Android 的超长值会自动变成「…」，画布上要自己按宽度裁。
    private func fit(_ text: String, size: CGFloat, maxWidth: CGFloat) -> String {
        guard maxWidth > 0 else { return "" }
        guard Draw.width(text, size: size) > maxWidth else { return text }
        let ellipsis = "…"
        var chars = Array(text)
        while !chars.isEmpty {
            chars.removeLast()
            let candidate = String(chars) + ellipsis
            if Draw.width(candidate, size: size) <= maxWidth { return candidate }
        }
        return ellipsis
    }

    /// 138****8826 这种打码形式（Java:416-419）
    static func maskPhone(_ phone: String) -> String {
        guard phone.count >= 7 else { return phone.isEmpty ? "-" : phone }
        let head = String(phone.prefix(3))
        let tail = String(phone.suffix(4))
        return head + "****" + tail
    }

    /// 取首字；空串给「登」（Java:421-424）
    static func firstChar(_ text: String) -> String {
        guard let first = text.first else { return "登" }
        return String(first)
    }

    /// `Session.statusText()`（Session.java:109-111）读的是服务端下发的 `status_text`。
    /// iOS 侧目前没把这个字段落盘，缺失时按会员开关给等价文案。
    static var statusText: String {
        let saved = UserDefaults.standard.string(forKey: "profile.status_text") ?? ""
        if !saved.isEmpty { return saved }
        return Session.shared.isActivated ? "正常" : "未开通会员"
    }

    /// 会员状态弹窗正文（Java:465-474 的 `StringBuilder`）
    static func memberBody(_ s: State) -> String {
        var lines: [String] = []
        lines.append("用户名：" + s.username)             // Java:466
        lines.append("手机号：" + s.phoneMasked)           // Java:467
        lines.append("状态：" + statusText)                // Java:468
        if Session.shared.isActivated {
            // Java:470
            let expire = Session.shared.expireAt
            lines.append("到期：" + (expire.isEmpty ? "-" : expire))
        } else {
            lines.append("")                                // Java:472 的 '\n'
            lines.append("未开通会员也可以正常使用，只是界面与成品会带水印。")
            lines.append("开通后水印会自动消失，请联系客服在后台为你开通。")
        }
        return lines.joined(separator: "\n")
    }

    /// 各类弹窗/ Toast 的文案，逐条照抄 Java（供 Router 弹 `UIAlertController` 时取用）
    enum Copy {
        /// Java:428-433
        static let renameTitle = "修改用户名"
        static let renameMessage = "用户名同时也是登录账号，改完可立即用新用户名登录，手机号不受影响。"
        static let renameHint = "2~20 位，中文/字母/数字/下划线"
        /// Java:441-442
        static let renameCancel = "取消"
        static let renameSave = "保存"
        /// Java:451 修改失败兜底
        static let renameFail = "修改失败"
        /// Java:464
        static let memberTitleActivated = "会员状态"
        static let memberTitleFree = "未开通会员"
        /// Java:478-479
        static let memberClose = "关闭"
        static let memberRefresh = "刷新状态"
        /// Java:485-487
        static let logoutTitle = "退出登录"
        static let logoutMessage = "退出后仍然可以继续使用，只是不再记录你的使用额度。"
        static let logoutCancel = "取消"
        static let logoutConfirm = "退出"
        static let logoutDone = "已退出登录"          // Java:492
        /// Java:131-135 恢复默认头像
        static let avatarRestoreTitle = "恢复默认头像"
        static let avatarRestoreMessage = "会删掉已上传的头像，回到用户名首字。"
        static let avatarRestoreCancel = "取消"
        static let avatarRestoreConfirm = "恢复"
        /// Java:137 + 143
        static let avatarRestoreFail = "操作失败"
        static let avatarRestored = "已恢复默认头像"
        /// Java:126 长按已经是默认头像时的提示
        static let avatarDefaultHint = "当前就是默认头像，点一下可以上传"
        /// Java:157
        static let phoneLocked = "手机号不可自助修改，请联系客服"
        /// Java:118-119 后台刷新
        static let statusExpired = "登录已过期，请重新登录"
    }

    // 供 `State.memberColor` 使用（嵌套类型里不能直接取外层实例属性）
    fileprivate static let memberGoldColor = Draw.color(0xffb8860b)
    fileprivate static let valueInkColor = Draw.color(0xff6b7280)
}

// MARK: - 令牌清单（本屏 hitTest / longPress 的返回值）
//
//   "back"            返回上一页（Java:91 finish()）→ Router 已有分支（pop）
//   "avatar"          点头像行：系统相册选图并上传
//                     Java:124 AvatarUpload.pick → iOS AvatarPicker.present（PHPicker，iOS 14）
//   "editName"        点用户名行：改用户名
//                     Java:152 renameDialog → iOS 用 EditPrompt/Copy.renameTitle，
//                     保存走 AuthService.changeUsername（/api/v1/auth/profile）
//   "toast:手机号不可自助修改，请联系客服"   点手机号行（Java:157，纯 Toast）
//   "member"          点会员行：会员状态弹窗（Java:185 memberDialog，正文见 memberBody）
//                     注意：Router 现有 "member" 分支是「会员中心」提示，语义需改指本页
//   "logout"          点退出登录：确认框（Java:217 confirmLogout）
//                     → iOS AuthService.logout() 后再 pop
//   "avatarRestore"   长按有图头像：恢复默认头像确认框（Java:130-145）
//                     → 需要 DoutuApi.removeAvatar 的 iOS 等价实现（当前 AuthService 没有）
//   "toast:当前就是默认头像，点一下可以上传"  长按默认头像（Java:126）
//   "noop"            其它区域
//
// 没有素材依赖：头像圆、小金冠、箭头全部是代码绘制（Java 侧同样是 Path / TextView）。
