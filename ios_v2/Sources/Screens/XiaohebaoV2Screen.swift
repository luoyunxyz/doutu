import UIKit

/// 「小荷包」页 —— `features/zhi/ui/XiaohebaoV2Screen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 结构：页底色 → 顶部蓝卡（整张抠图，只把「生活费」三个字自己画上去）
/// → 从 942 开始的白页（上方两角圆角，下方铺满）
/// → 头栏（返回 / 铃铛 / 主页+设置两个白药丸）→ 成员头像行（+ 号跟着排）
/// → 总金额块（含隐藏开关、查看权益）→ 转出/转入/功能+插件 → 攒钱目标 + 承诺券
/// → 三个页签（选中加粗 + 橙线下划）→ 空态插画 → 右下「+ 发布」浮起按钮。
///
/// 文字统一带 **0.94 横向压缩**（Java 的 `TEXT_SQUEEZE`，缩放轴在各自锚点上）。
final class XiaohebaoV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    final class State: ScreenState {
        /// 金额是否隐藏（眼睛按钮）
        var hidden = false
        /// 0 动态 / 1 账单 / 2 成员记账（Android 默认 1 账单）
        var tab = 1
    }

    /// 文字横向压缩系数（Java:39）
    private let squeeze: CGFloat = 0.94

    // MARK: - 调色板（Java:42-50）
    private let pageBg = Draw.color(0xfff2f3f7)
    private let sheet = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff2a2c30)
    private let grey = Draw.color(0xff939599)
    private let faint = Draw.color(0xffb9bbc2)
    private let chevronColor = Draw.color(0xffc4c6cb)
    private let buttonBg = Draw.color(0xfff2f2f5)
    private let orange = Draw.color(0xffff8a2b)
    private let plusDisc = Draw.color(0xfff0f1f5)
    private let hairline = Draw.color(0xffececf1)

    // MARK: - 几何（Java:53-151）
    private let backRect = CGRect(x: 50, y: 190, width: 95 - 50, height: 265 - 190)
    private let backStroke: CGFloat = 7
    private let bellRect = CGRect(x: 707, y: 187, width: 769 - 707, height: 240 - 187)
    private let homePill = CGRect(x: 816, y: 163, width: 1000 - 816, height: 266 - 163)
    private let homeLock = CGRect(x: 861, y: 192, width: 900 - 861, height: 245 - 192)
    private let settingsPill = CGRect(x: 1085, y: 163, width: 1255 - 1085, height: 266 - 163)
    private let homeCx: CGFloat = 948
    private let settingsCx: CGFloat = 1170
    private let pillRadius: CGFloat = 51.5
    private let pillTextBase: CGFloat = 250
    private let pillTextSize: CGFloat = 46

    private let headRect = CGRect(x: 39, y: 345, width: 1239 - 39, height: 887 - 345)
    private let headRadius: CGFloat = 36
    private let purseX: CGFloat = 99
    private let purseBase: CGFloat = 436
    private let purseSize: CGFloat = 54

    private let sheetTop: CGFloat = 942
    private let sheetRadius: CGFloat = 28

    private let avatarRect = CGRect(x: 52, y: 1000, width: 165 - 52, height: 1113 - 1000)
    private let avatarR: CGFloat = 56.5
    private let avatarPitch: CGFloat = 156
    private let addCx: CGFloat = 264.5
    private let addCy: CGFloat = 1056.5
    private let addR: CGFloat = 52.5
    private let addArm: CGFloat = 17
    private let addStroke: CGFloat = 5
    private let inviteRight: CGFloat = 1190
    private let inviteBase: CGFloat = 1074
    private let inviteSize: CGFloat = 50
    private let inviteChevron = CGRect(x: 1194, y: 1042, width: 1213 - 1194, height: 1072 - 1042)

    private let totalLabelX: CGFloat = 58
    private let totalLabelBase: CGFloat = 1224
    private let totalLabelSize: CGFloat = 47
    private let amountX: CGFloat = 60
    private let amountBase: CGFloat = 1347
    private let amountSize: CGFloat = 114
    private let hiddenSize: CGFloat = 96
    private let eyeRect = CGRect(x: 283, y: 1184, width: 334 - 283, height: 1224 - 1184)
    private let rightsChevron = CGRect(x: 1194, y: 1298, width: 1213 - 1194, height: 1328 - 1298)
    private let rightsBase: CGFloat = 1330
    private let rightsSize: CGFloat = 50
    private let rightsGap: CGFloat = 12

    private let outButton = CGRect(x: 42, y: 1436, width: 399 - 42, height: 1569 - 1436)
    private let inButton = CGRect(x: 410, y: 1436, width: 769 - 410, height: 1569 - 1436)
    private let fnBox = CGRect(x: 780, y: 1436, width: 1238 - 780, height: 1569 - 1436)
    private let buttonRadius: CGFloat = 24
    private let buttonTextBase: CGFloat = 1519
    private let buttonTextSize: CGFloat = 49
    private let fnIcon = CGRect(x: 921, y: 1454, width: 990 - 921, height: 1522 - 1454)
    private let plIcon = CGRect(x: 1106, y: 1453, width: 1177 - 1106, height: 1524 - 1453)
    private let fnTextCx: CGFloat = 955
    private let plTextCx: CGFloat = 1141
    private let fnTextBase: CGFloat = 1566
    private let fnTextSize: CGFloat = 38
    private let fnDividerX: CGFloat = 1048
    private let fnDividerTop: CGFloat = 1462
    private let fnDividerBottom: CGFloat = 1544

    private let goalRect = CGRect(x: 42, y: 1669, width: 622 - 42, height: 2029 - 1669)
    private let promiseRect = CGRect(x: 660, y: 1669, width: 1230 - 660, height: 2029 - 1669)
    private let promoRadius: CGFloat = 28

    private let tabs = ["动态", "账单", "成员记账"]
    private let tabCx: [CGFloat] = [111, 300, 547]
    private let tabBase: CGFloat = 2187
    private let tabSize: CGFloat = 58
    private let tabUnderline = CGRect(x: 230, y: 2224, width: 165, height: 7)
    private let tabUnderlineMargin: CGFloat = 47

    private let emptyRect = CGRect(x: 520, y: 2482, width: 754 - 520, height: 2618 - 2482)
    private let hintCx: CGFloat = 639.5
    private let hintBase: CGFloat = 2725
    private let hintSize: CGFloat = 45

    private let publishCx: CGFloat = 1140
    private let publishCy: CGFloat = 2455
    private let publishR: CGFloat = 112
    private let publishPlus = CGRect(x: 1117, y: 2408, width: 1165 - 1117, height: 2457 - 2408)
    private let publishBase: CGFloat = 2520
    private let publishSize: CGFloat = 38

    /// 11 张切图（Java:170-180）
    private static let artNames = [
        "head": "xh_head", "avatar": "xh_avatar", "goal": "xh_goal", "promise": "xh_promise",
        "empty": "xh_empty", "bell": "xh_bell", "lock": "xh_lock", "eye": "xh_eye",
        "func": "xh_func", "plugin": "xh_plugin", "publish": "xh_publish",
    ]
    private var art: [String: UIImage] = [:]

    /// 热区（Java:412-424）：按登记顺序，命中时后登记的优先
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private var hits: [Hit] = []

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:189-213）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        hits.removeAll()

        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        drawCard(d)
        // 白页：从 942 起铺到底，只有上面两角是圆的
        d.round(CGRect(x: 0, y: sheetTop, width: Self.W, height: Self.H - sheetTop),
                radius: sheetRadius, sheet)
        d.rect(CGRect(x: 0, y: sheetTop + sheetRadius, width: Self.W,
                      height: Self.H - sheetTop - sheetRadius), sheet)

        drawHeader(d)
        drawAvatarRow(d)
        drawTotal(d, s)
        drawButtons(d)
        drawPromos(d)
        drawTabs(d, s)
        drawEmpty(d)
        drawPublish(d)
    }

    private func hit(_ rect: CGRect, _ token: ActionToken) {
        hits.append(Hit(rect: rect, token: token))
    }

    /// 顶栏（Java:216-232）
    private func drawHeader(_ d: Draw) {
        // 左箭头（两段线）
        strokeLine(d, from: CGPoint(x: backRect.maxX, y: backRect.minY),
                   to: CGPoint(x: backRect.minX, y: backRect.midY),
                   color: ink, width: backStroke)
        strokeLine(d, from: CGPoint(x: backRect.minX, y: backRect.midY),
                   to: CGPoint(x: backRect.maxX, y: backRect.maxY),
                   color: ink, width: backStroke)
        hit(backRect.insetBy(dx: -20, dy: -20), "back")

        d.art(art["bell"], bellRect)
        hit(bellRect.insetBy(dx: -16, dy: -16), "bell")

        d.round(homePill, radius: pillRadius, sheet)
        d.art(art["lock"], homeLock)
        d.centerTextSqueezed("主页", cx: homeCx, baseline: pillTextBase, size: pillTextSize,
                             ink, bold: true, squeeze: squeeze)
        hit(homePill, "home")

        d.round(settingsPill, radius: pillRadius, sheet)
        d.centerTextSqueezed("设置", cx: settingsCx, baseline: pillTextBase, size: pillTextSize,
                             ink, bold: true, squeeze: squeeze)
        hit(settingsPill, "settings")
    }

    /// 顶部蓝卡（整张抠图，圆角裁剪）（Java:235-250）
    private func drawCard(_ d: Draw) {
        if let head = art["head"] {
            d.clipped(to: headRect, radius: headRadius) {
                d.art(head, headRect)
            }
        }
        d.textSqueezed(PurseStore.shared.name, x: purseX, baseline: purseBase, size: purseSize,
                       UIColor.white, bold: true, squeeze: squeeze)
        hit(CGRect(x: headRect.minX + 20, y: purseBase - purseSize, width: 400 - headRect.minX - 20,
                   height: purseSize + 20), "purse")
    }

    /// 成员头像行 + 加号 + 邀请（Java:253-292）
    private func drawAvatarRow(_ d: Draw) {
        let store = PurseStore.shared
        let count = store.avatarCount
        for i in 0..<count {
            let box = CGRect(x: avatarRect.minX + CGFloat(i) * avatarPitch, y: avatarRect.minY,
                             width: avatarRect.width, height: avatarRect.height)
            d.clipped(to: box, radius: avatarR) {
                if let picture = store.avatar(i) {
                    drawCover(d, picture, box)
                } else {
                    d.art(art["avatar"], box)
                }
            }
            hit(box.insetBy(dx: -10, dy: -10), "avatar:\(i)")
        }

        if count < PurseStore.maxAvatars {
            let cx = addCx + CGFloat(count - 1) * avatarPitch
            d.circle(cx: cx, cy: addCy, r: addR, plusDisc)
            strokeLine(d, from: CGPoint(x: cx - addArm, y: addCy), to: CGPoint(x: cx + addArm, y: addCy),
                       color: ink, width: addStroke)
            strokeLine(d, from: CGPoint(x: cx, y: addCy - addArm), to: CGPoint(x: cx, y: addCy + addArm),
                       color: ink, width: addStroke)
            hit(CGRect(x: cx - addR, y: addCy - addR, width: addR * 2, height: addR * 2), "add")
        }

        d.rightTextSqueezed("邀请他人加入", right: inviteRight, baseline: inviteBase,
                            size: inviteSize, ink, squeeze: squeeze)
        chevron(d, inviteChevron)
        hit(CGRect(x: 880, y: 1010, width: 1240 - 880, height: 1090 - 1010), "invite")
    }

    /// 总金额块（Java:295-328）
    private func drawTotal(_ d: Draw, _ s: State) {
        d.textSqueezed("总金额(元)", x: totalLabelX, baseline: totalLabelBase,
                       size: totalLabelSize, grey, squeeze: squeeze)
        d.art(art["eye"], eyeRect)
        hit(eyeRect.insetBy(dx: -14, dy: -14), "eye")

        if s.hidden {
            d.textSqueezed("****", x: amountX, baseline: amountBase, size: hiddenSize,
                           ink, bold: true, squeeze: squeeze)
        } else {
            d.textSqueezed(PurseStore.shared.total, x: amountX, baseline: amountBase,
                           size: amountSize, ink, bold: true, squeeze: squeeze)
        }
        hit(CGRect(x: 40, y: 1240, width: 300, height: 130), "total")

        // 「权益」橙色 + 前面的「查看」灰色，都右对齐接着排（Java:307-326）
        let chevronLeft = rightsChevron.minX - 8
        let rightsWidth = Draw.width("权益", size: rightsSize) * squeeze
        d.rightTextSqueezed("权益", right: chevronLeft, baseline: rightsBase, size: rightsSize,
                            orange, squeeze: squeeze)
        let viewRight = chevronLeft - rightsWidth - rightsGap
        d.rightTextSqueezed("查看", right: viewRight, baseline: rightsBase, size: rightsSize,
                            grey, squeeze: squeeze)
        chevron(d, rightsChevron)
        hit(CGRect(x: 860, y: 1270, width: 1240 - 860, height: 1345 - 1270), "rights")
    }

    /// 转出 / 转入 / 功能 + 插件（Java:331-351）
    private func drawButtons(_ d: Draw) {
        d.round(outButton, radius: buttonRadius, buttonBg)
        d.centerTextSqueezed("转出", cx: outButton.midX, baseline: buttonTextBase,
                             size: buttonTextSize, ink, bold: true, squeeze: squeeze)
        hit(outButton, "out")

        d.round(inButton, radius: buttonRadius, buttonBg)
        d.centerTextSqueezed("转入", cx: inButton.midX, baseline: buttonTextBase,
                             size: buttonTextSize, ink, bold: true, squeeze: squeeze)
        hit(inButton, "in")

        d.round(fnBox, radius: buttonRadius, sheet)
        d.rect(CGRect(x: fnDividerX, y: fnDividerTop, width: 2,
                      height: fnDividerBottom - fnDividerTop), hairline)
        d.art(art["func"], fnIcon)
        d.centerTextSqueezed("功能", cx: fnTextCx, baseline: fnTextBase, size: fnTextSize,
                             ink, squeeze: squeeze)
        d.art(art["plugin"], plIcon)
        d.centerTextSqueezed("插件", cx: plTextCx, baseline: fnTextBase, size: fnTextSize,
                             ink, squeeze: squeeze)
        hit(CGRect(x: fnBox.minX, y: fnBox.minY, width: fnDividerX - fnBox.minX,
                   height: fnBox.height), "func")
        hit(CGRect(x: fnDividerX, y: fnBox.minY, width: fnBox.maxX - fnDividerX,
                   height: fnBox.height), "plugin")
    }

    /// 攒钱目标 + 承诺券（Java:354-373）
    private func drawPromos(_ d: Draw) {
        drawCardArt(d, "goal", goalRect)
        hit(goalRect, "goal")
        drawCardArt(d, "promise", promiseRect)
        hit(promiseRect, "promise")
    }

    private func drawCardArt(_ d: Draw, _ key: String, _ box: CGRect) {
        guard let image = art[key] else { return }
        d.clipped(to: box, radius: promoRadius) {
            d.art(image, box)
        }
    }

    /// 三个页签 + 橙色下划（Java:376-388）
    private func drawTabs(_ d: Draw, _ s: State) {
        for (i, label) in tabs.enumerated() {
            d.centerTextSqueezed(label, cx: tabCx[i], baseline: tabBase, size: tabSize,
                                 ink, bold: i == s.tab, squeeze: squeeze)
            hit(CGRect(x: tabCx[i] - 80, y: tabBase - tabSize, width: 160, height: tabSize + 22),
                "tab:\(i)")
        }
        // 下划线的宽度跟着选中那个词的宽度走（含 0.94 压缩与固定余量）
        let index = min(max(s.tab, 0), tabs.count - 1)
        let half = (Draw.width(tabs[index], size: tabSize, bold: index == 1) * squeeze
            + tabUnderlineMargin) / 2
        d.round(CGRect(x: tabCx[index] - half, y: tabUnderline.minY, width: half * 2,
                       height: tabUnderline.height),
                radius: tabUnderline.height / 2, orange)
    }

    /// 空态（Java:391-394）
    private func drawEmpty(_ d: Draw) {
        d.art(art["empty"], emptyRect)
        d.centerTextSqueezed("未来的存钱和消费等记录会展示在这", cx: hintCx, baseline: hintBase,
                             size: hintSize, faint, squeeze: squeeze)
    }

    /// 右下的「+ 发布」（Java:397-408）
    private func drawPublish(_ d: Draw) {
        d.circle(cx: publishCx, cy: publishCy + 3, r: publishR + 6, Draw.color(0x0f000000))
        d.circle(cx: publishCx, cy: publishCy, r: publishR, sheet)
        d.art(art["publish"], publishPlus)
        d.centerTextSqueezed("发布", cx: publishCx, baseline: publishBase, size: publishSize,
                             ink, squeeze: squeeze)
        hit(CGRect(x: publishCx - publishR, y: publishCy - publishR,
                   width: publishR * 2, height: publishR * 2 + 20), "publish")
    }

    // MARK: - 图元

    /// 短边铺满、长边裁掉（Java:429-439）
    private func drawCover(_ d: Draw, _ image: UIImage, _ box: CGRect) {
        let iw = image.size.width * image.scale
        let ih = image.size.height * image.scale
        guard iw > 0, ih > 0 else { return }
        let scale = max(box.width / iw, box.height / ih)
        let w = iw * scale
        let h = ih * scale
        d.art(image, CGRect(x: box.midX - w / 2, y: box.midY - h / 2, width: w, height: h))
    }

    private func strokeLine(_ d: Draw, from: CGPoint, to: CGPoint, color: UIColor, width: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: from)
        d.ctx.addLine(to: to)
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 右箭头（两段线，Java:326 处的 `chevron`）
    private func chevron(_ d: Draw, _ box: CGRect) {
        let dx = box.width * 0.45
        strokeLine(d, from: CGPoint(x: box.minX - dx / 2, y: box.minY),
                   to: CGPoint(x: box.midX + dx / 2, y: box.midY), color: chevronColor, width: 4.6)
        strokeLine(d, from: CGPoint(x: box.midX + dx / 2, y: box.midY),
                   to: CGPoint(x: box.minX - dx / 2, y: box.maxY), color: chevronColor, width: 4.6)
    }

    // MARK: - 命中（Java:418-424：后登记优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: y)) {
            return hit.token
        }
        return "noop"
    }
}
