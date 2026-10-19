import UIKit

/// 「个人信息」页 —— `features/zhi/ui/ProfileScreen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 结构很简单：顶栏（返回箭头 + 标题 + 耳机图标）、一张五行的信息卡（头像/昵称/数字形象/
/// 支付宝账号/身份信息）、下面两张静态卡片（切图），右下角一个二维码图标。
///
/// 文字都带横向拉伸校准（Android 用 `paint.setTextScaleX` 量的）：
/// 标题 1.014、标签 1.023、值 0.996、账号 0.984 —— 不抄这几个数，逐字比对就对不上。
final class ProfileScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    final class State: ScreenState {}

    // MARK: - 调色板（Java:35-39）
    private let pageBg = Draw.color(0xfff5f5f5)
    private let card = Draw.color(0xffffffff)
    private let divider = Draw.color(0xffededed)
    private let labelInk = Draw.color(0xff252525)
    private let valueInk = Draw.color(0xff909090)

    // 横向拉伸校准（Java:41-44）
    private let sxLabel: CGFloat = 1.023
    private let sxValue: CGFloat = 0.996
    private let sxAccount: CGFloat = 0.984
    private let sxTitle: CGFloat = 1.014

    // MARK: - 几何（Java:47-75）
    private let backRect = CGRect(x: 44, y: 184, width: 90 - 44, height: 256 - 184)
    private let titleX: CGFloat = 136
    private let titleBase: CGFloat = 240.5
    private let titleSize: CGFloat = 63
    private let headsetRect = CGRect(x: 1156, y: 180, width: 1240 - 1156, height: 260 - 180)

    private let card1 = CGRect(x: 41, y: 350, width: 1238 - 41, height: 1253 - 350)
    private let cardRadius: CGFloat = 30
    private let dividers: [CGFloat] = [569, 740, 912, 1084]
    private let rowBase: [CGFloat] = [475.5, 671.5, 843.5, 1012, 1183]
    private let nameValueBase: CGFloat = 668
    private let accountValueBase: CGFloat = 1009
    private let rowLabels = ["头像", "昵称", "数字形象", "支付宝账号", "身份信息"]
    private let labelX: CGFloat = 81.5
    private let labelSize: CGFloat = 57

    private let avatarCell = CGRect(x: 983, y: 390, width: 1120 - 983, height: 527 - 390)
    private let avatarRadius: CGFloat = 16
    private let valueRight: CGFloat = 1119.5
    private let valueSize: CGFloat = 51
    private let accountSize: CGFloat = 52

    private let rowChevron = CGRect(x: 1146, y: 427, width: 1188 - 1146, height: 491 - 427)
    private let qrIcon = CGRect(x: 1060, y: 1346, width: 1120 - 1060, height: 1406 - 1346)

    private let card2 = CGRect(x: 41, y: 1295, width: 1238 - 41, height: 1635 - 1295)
    private let card3 = CGRect(x: 41, y: 1676, width: 1238 - 41, height: 2015 - 1676)

    /// 七张切图（Java:84-90）
    private static let artNames = [
        "back": "pf_ic_back", "headset": "pf_ic_headset", "chevron": "pf_ic_chevron",
        "qr": "pf_ic_qr", "thumb": "pf_avatar_thumb", "card2": "pf_card2", "card3": "pf_card3",
    ]
    private var art: [String: UIImage] = [:]

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:95-137）

    func draw(_ d: Draw, state: ScreenState) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.art(art["back"], backRect)
        d.text("个人信息", x: titleX, baseline: titleBase, size: titleSize, labelInk, sx: sxTitle)
        d.art(art["headset"], headsetRect)

        // 五行信息卡
        d.round(card1, radius: cardRadius, card)
        for y in dividers {
            d.rect(CGRect(x: labelX, y: y, width: card1.maxX - labelX, height: 1.5), divider)
        }
        for (i, label) in rowLabels.enumerated() {
            d.text(label, x: labelX, baseline: rowBase[i], size: labelSize, labelInk, sx: sxLabel)
        }
        // 值列：只有用户自己拥有的两行有值（昵称 / 支付宝账号）
        d.rightText(ProfileStore.shared.name, right: valueRight, baseline: nameValueBase,
                    size: valueSize, valueInk, sx: sxValue)
        d.rightText(ProfileStore.shared.displayPhone, right: valueRight, baseline: accountValueBase,
                    size: accountSize, valueInk, sx: sxAccount)
        // 每行右侧的箭头（按行的基线上下各 32 贴图）
        for base in rowBase {
            d.art(art["chevron"], CGRect(x: rowChevron.minX, y: base - 32,
                                         width: rowChevron.width, height: 64))
        }

        // 头像：有上传就用用户的，否则用切图占位
        let avatar = ProfileStore.shared.avatar ?? art["thumb"]
        d.clipped(to: avatarCell, radius: avatarRadius) {
            d.art(avatar, avatarCell)
        }

        d.art(art["card2"], card2)
        d.art(art["card3"], card3)
        d.art(art["qr"], qrIcon)
    }

    // MARK: - 命中（Java:142-172，顺序与区间照抄）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < 300 {
            if x < 120 { return "back" }
            return "noop"
        }
        if y >= card1.minY && y <= card1.maxY {
            let row = rowAt(y)
            switch row {
            case 0: return "avatar"
            case 1: return "editName"
            case 3: return "editPhone"
            default: return "noop"
            }
        }
        return "noop"
    }

    /// 五行各自的范围（Java:165-172）
    private func rowAt(_ y: CGFloat) -> Int {
        if y < 350 { return -1 }
        let edges: [CGFloat] = [350, 569, 740, 912, 1084, 1253]
        for i in 0..<5 where y >= edges[i] && y < edges[i + 1] { return i }
        return -1
    }
}
