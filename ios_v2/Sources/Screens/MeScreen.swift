import UIKit

/// 鎴戠殑锛堝皬鏀富椤电殑銆屾垜鐨勩€嶉〉锛夆€斺€?`features/zhi/ui/MeScreen.java` 鐨?1:1 绉绘銆?
///
/// 绉绘瑕佺偣锛堝鐓?Java 婧愮爜閫愭鎶勶紝娉ㄩ噴閲屾爣浜?Java 琛屽彿浠ヤ究鏍稿锛夛細
///   - 鍙傝€冪敾甯?W=1280 H=2816锛屽唴瀹归珮 CONTENT_H=4067锛屽簳閮ㄥ鑸粠 NAV_TOP=2560 寮€濮?
///   - 椤甸潰搴曡壊 PAGE_BG锛屽崱鐗囥€乥anner銆佸ご鍍忕瓑鏄綅鍥撅紙鏉ヨ嚜 Android 鐨?res/drawable锛?
///   - 椤堕儴鍥涗釜鎸夐挳锛堣繑鍥?鑰虫満/榻胯疆/绠ご锛夈€佸ご鍍忋€佸悕瀛椼€佹墜鏈哄彿鑳跺泭閮芥槸杩欓噷鐢荤殑
///   - 搴曢儴瀵艰埅鐢?5 鏍硷細娑堟伅 / 閫氳褰?/ 鍙戠幇 / 鎴戯紙鍏朵腑涓ゆ牸鏇挎崲鎴愭湰椤电殑閫変腑鎬侊級
final class MeScreen: Screen {
    var zhiTabIndex: Int? { 4 }
    // MARK: - 鍙傝€冪敾甯冿紙Java MeScreen.java:30-34锛?
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let NAV_TOP: CGFloat = 2560
    static let CONTENT_H: CGFloat = 4067

    /// 椤甸潰鐘舵€侊紙Java 鐨?MeScreen.State锛?
    final class State: ScreenState {
        /// 銆屽畬鎴愯韩浠姐€嶆í骞呭彲鐢ㄥ畠鑷繁鐨?脳 鍏虫帀
        var banner = true
    }

    // MARK: - 璋冭壊鏉匡紙Java:44-48锛?
    private let pageBg = Draw.color(0xfff2f3f7)
    private let barBg = Draw.color(0xfffafbff)
    private let phonePill = Draw.color(0xffe3ecf6)
    private let phoneInk = Draw.color(0xff2e4162)
    private let nameInk = Draw.color(0xff27292d)

    /// 鏂囧瓧妯悜鎷変几鏍″噯锛圝ava:51-52锛屾寜鍙傝€冨浘閫愭閲忓嚭鏉ョ殑锛?
    private let sxName: CGFloat = 1.020
    private let sxPhone: CGFloat = 1.000

    // MARK: - 鍑犱綍锛圝ava:55-103锛?
    private let plateH: CGFloat = 700
    private let blackHeaderH: CGFloat = 620

    private let backOld = CGRect(x: 782, y: 182, width: 996 - 782, height: 258 - 182)
    private let headset = CGRect(x: 1032, y: 178, width: 1114 - 1032, height: 262 - 178)
    private let gear = CGRect(x: 1148, y: 172, width: 1234 - 1148, height: 266 - 172)
    private let headChevron = CGRect(x: 1166, y: 367, width: 1202 - 1166, height: 422 - 367)

    private let avatar = CGRect(x: 85, y: 280, width: 313 - 85, height: 508 - 280)
    private let avatarRadius: CGFloat = 44

    private let nameX: CGFloat = 363
    private let nameBase: CGFloat = 366
    private let nameSize: CGFloat = 63
    private let phonePillRect = CGRect(x: 362, y: 412, width: 584 - 362, height: 482 - 412)
    private let phoneCx: CGFloat = 473
    private let phoneBase: CGFloat = 459
    private let phoneSize: CGFloat = 36
    private let phonePillRadius: CGFloat = 35

    private let bannerRect = CGRect(x: 56, y: 554, width: 1224 - 56, height: 664 - 554)
    private let bannerDone = CGRect(x: 912, y: 576, width: 1100 - 912, height: 644 - 576)
    private let bannerClose = CGRect(x: 1118, y: 570, width: 1196 - 1118, height: 650 - 570)

    private let cardLeft: CGFloat = 28
    private let cardRight: CGFloat = 1250
    /// 8 寮犳湇鍔″崱鐨勪笂涓嬭竟鐣岋紙鍐呭鍧愭爣锛孞ava:84-86锛?
    private let cards: [(CGFloat, CGFloat)] = [
        (698, 841), (878, 1303), (1341, 1484), (1521, 2123),
        (2161, 2665), (2702, 3089), (3126, 3513), (3550, 3937),
    ]

    /// 璧勪骇鍗＄殑 4 琛岋紙Java:91-93锛?
    private let assetTop: CGFloat = 1540
    private let assetRowH: CGFloat = 153.5

    /// 鍥炬爣缃戞牸鐨勫洓鍒椾腑蹇冿紙Java:96-97锛?
    private let gridCx: [CGFloat] = [180, 486, 792, 1098]
    private let gridColW: CGFloat = 306

    // MARK: - 浣嶅浘

    /// 涓?Android res/drawable 鐨勬槧灏勶細杩欓噷鐢ㄨ祫婧愬悕鍙栧浘锛宍Assets.swift` 閲岀櫥璁般€?
    private static let artNames = [
        "plate": "me_bgplate", "backOld": "me_backold", "headset": "me_headset",
        "gear": "me_gear", "headChevron": "me_chevron", "avatar": "me_avatar",
        "banner": "me_banner",
        "card1": "me_card1", "card2": "me_card2", "card3": "me_card3", "card4": "me_card4",
        "card5": "me_card5", "card6": "me_card6", "card7": "me_card7", "card8": "me_card8",
        "navMsg": "me_nav_msg", "navMe": "me_nav_me", "navbar": "msg_navbar",
        "blackBackground": "hk_me_bg",
    ]
    private var art: [String: UIImage] = [:]

    /// 璧勬枡锛堝搴?Android 鐨?ProfileStore锛氬悕瀛椼€佹墜鏈哄彿銆佸ご鍍忥級
    private let profile: ProfileStore

    init(profile: ProfileStore = .shared) {
        self.profile = profile
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    // MARK: - 婊氬姩锛圝ava:137-139锛?

    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, Self.CONTENT_H - Self.NAV_TOP)
    }

    // MARK: - 缁樺埗锛圝ava:143-168锛?

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let blackCard = LicaiVersion.blackCard(LicaiVersion.current())

        // Java:147 鈥斺€?鍏堥摵鏁撮〉搴曠伆
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        // Java:150-158 椤堕儴澶у浘锛堥粦鍗＄毊鑲?or 鏅€氬簳鏉匡級
        if blackCard {
            headerPlate(d, "blackBackground", artHeight: blackHeaderH, bottom: plateH)
        } else if let plate = art["plate"] {
            d.art(plate, CGRect(x: 0, y: 0, width: Self.W, height: plateH))
        }

        drawHeader(d, blackCard: blackCard)

        if s.banner {
            d.art(art["banner"], bannerRect)
        }
        // Java:163-165 鍏紶鏈嶅姟鍗?
        for (i, card) in cards.enumerated() {
            d.art(art["card\(i + 1)"], CGRect(x: cardLeft, y: card.0,
                                              width: cardRight - cardLeft, height: card.1 - card.0))
        }
        d.ctx.restoreGState()

        drawNav(d, blackCard: blackCard)
    }

    /// Java:175-190
    private func headerPlate(_ d: Draw, _ name: String, artHeight: CGFloat, bottom: CGFloat) {
        guard let bitmap = art[name] else { return }
        let height = min(bottom, artHeight)
        d.art(bitmap, CGRect(x: 0, y: 0, width: Self.W, height: height + 2))
        if height + 1 >= bottom { return }
        // 浣嶅浘鏈€鍚庝竴琛屽欢浼稿埌鍗＄墖椤堕儴锛岀洊浣忚繃婊ゆ畫鐣欑殑娴呰壊鎺ョ紳锛圝ava:187-189锛?
        let srcW = bitmap.size.width * bitmap.scale
        let srcH = bitmap.size.height * bitmap.scale
        _ = (srcW, srcH)
        d.art(bitmap, src: nil, CGRect(x: 0, y: height + 1, width: Self.W, height: bottom - (height + 1)))
    }

    /// Java:192-218
    private func drawHeader(_ d: Draw, blackCard: Bool) {
        let tint: UIColor? = blackCard ? Draw.color(0xffeacfa4) : nil
        d.art(art["backOld"], backOld)
        drawTinted(d, "headset", headset, tint)
        drawTinted(d, "gear", gear, tint)
        drawTinted(d, "headChevron", headChevron, tint)

        // 澶村儚锛氫紭鍏堢敤鐢ㄦ埛璁剧疆鐨勫ご鍍忥紝娌℃湁灏辩敤绱犳潗榛樿鍥?
        let avatarImage = profile.avatar ?? art["avatar"]
        d.clipped(to: avatar, radius: avatarRadius) {
            d.art(avatarImage, avatar)
        }

        d.text(profile.name, x: nameX, baseline: nameBase, size: nameSize,
               blackCard ? Draw.color(0xfff4f4f4) : nameInk, bold: true, sx: sxName)
        d.round(phonePillRect, radius: phonePillRadius, phonePill)
        d.centerText(profile.phone, cx: phoneCx, baseline: phoneBase, size: phoneSize,
                     phoneInk, sx: sxPhone)
    }

    /// 甯︾潃鑹茬殑浣嶅浘锛堝搴?Java 鐨?PorterDuffColorFilter SRC_IN锛?
    private func drawTinted(_ d: Draw, _ name: String, _ r: CGRect, _ tint: UIColor?) {
        guard let image = art[name] else { return }
        guard let tint else { d.art(image, r); return }
        let template = image.withRenderingMode(.alwaysTemplate)
        d.ctx.saveGState()
        d.ctx.setFillColor(tint.cgColor)
        d.clipped(to: r, radius: 0) {
            d.art(template, r)
        }
        d.ctx.restoreGState()
    }

    /// Java:226-237
    private func drawNav(_ d: Draw, blackCard: Bool) {
        d.art(art["navbar"], CGRect(x: 0, y: Self.NAV_TOP, width: Self.W, height: Self.H - Self.NAV_TOP))
        d.rect(CGRect(x: 768, y: Self.NAV_TOP + 1, width: Self.W - 768, height: 173), barBg)
        d.art(art["navMsg"], CGRect(x: 768, y: Self.NAV_TOP, width: 1024 - 768, height: 173))
        d.art(art["navMe"], CGRect(x: 1024, y: Self.NAV_TOP, width: Self.W - 1024, height: 173))
        _ = blackCard
    }

    // MARK: - 鍛戒腑娴嬭瘯锛圝ava:242-316锛屽垎鏀『搴忓繀椤讳笌 Java 瀹屽叏涓€鑷达級

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()

        // 搴曢儴瀵艰埅锛? 鏍硷紝鍙栨渶杩戠殑鍒楀績锛圝ava:243锛?
        if y >= Self.NAV_TOP {
            let centers: [CGFloat] = [128, 382, 639, 898, 1151]
            var best = 0
            var bestD = CGFloat.greatestFiniteMagnitude
            for (i, c) in centers.enumerated() where abs(c - x) < bestD {
                bestD = abs(c - x)
                best = i
            }
            if best == 3 { return "nav:3" }   // 鏈〉閫変腑椤?
            return "nav:\(best)"
        }

        let cy = y + s.scroll

        // 椤堕儴韬唤鍖猴紙Java:246-255锛?
        if cy < 540 {
            if cy > 160 && cy < 280 {
                if x >= backOld.minX - 20 && x <= backOld.maxX + 20 { return "noop" }
                if x >= headset.minX - 20 && x <= headset.maxX + 10 { return "noop" }
                if x >= gear.minX - 10 { return "noop" }
            }
            // 鏁存潯韬唤琛岋紙澶村儚銆佸悕瀛椼€佹墜鏈哄彿锛夐兘杩涗釜浜轰俊鎭?
            if x >= 60 && x <= 1110 { return "profile" }
            return "noop"
        }

        // 瀹屾垚韬唤妯箙锛圝ava:256-260锛?
        if cy >= bannerRect.minY && cy <= bannerRect.maxY {
            if x >= bannerDone.minX && x <= bannerDone.maxX { return "noop" }
            if x >= bannerClose.minX && x <= bannerClose.maxX { return "bannerClose" }
            return "noop"
        }

        if cy < cards[0].0 { return "noop" }
        if cy <= cards[0].1 { return "noop" }
        if cy <= cards[1].1 { return "noop" }

        // 璐﹀崟鍗?鈫?璐﹀崟椤碉紙Java:269锛?
        if cy <= cards[2].1 { return "bills" }

        // 璧勪骇鍗＄殑鍥涜锛圝ava:270-279锛?
        if cy <= cards[3].1 {
            var row = Int((cy - assetTop) / assetRowH)
            row = max(0, min(3, row))
            switch row {
            case 0: return "assets"
            case 1: return "balance"
            case 2: return "yuebao"
            case 3: return "huabei"
            default: return "noop"
            }
        }

        // 鍚庨潰鍥涘紶鍗＄殑鍥炬爣缃戞牸锛欽ava 閲?grid() 鐩墠涓€寰嬭繑鍥?noop锛堝皻鏈帴鍔熻兘锛?
        if cy <= cards[4].1 { return "noop" }
        if cy <= cards[5].1 { return cy < 2830 ? "noop" : "noop" }
        if cy <= cards[6].1 { return cy < 3255 ? "noop" : "noop" }
        if cy <= cards[7].1 { return cy < 3680 ? "noop" : "noop" }
        return "noop"
    }
}
