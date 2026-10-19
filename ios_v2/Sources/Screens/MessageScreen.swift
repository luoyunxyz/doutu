import UIKit

/// 銆屾秷鎭€嶅垪琛ㄩ〉 鈥斺€?`features/zhi/ui/MessageScreen.java` 鐨?1:1 绉绘锛堝弬鑰冪敾甯?1280脳2816锛屽崟灞忎笉婊氬姩锛夈€?
///
/// 缁撴瀯锛氶《閮ㄩ噰鏍峰浘 + 涓ゆ绾壊鐏帮紙涓棿鐢ㄦ笎鍙樿繃娓★級+ 涓€寮犲ぇ鍦嗚鐧藉崱锛堝叏閮ㄤ細璇?+ 5 琛屼細璇濓級+ 搴曢儴瀵艰埅銆?
/// 鏂囧瓧**鍏ㄩ儴甯︽í鍚戝帇缂╂牎鍑?*锛? 涓郴鏁帮紝鏄?Android 瀵圭潃鍙傝€冨浘閫愭閲忕殑锛夛紝涓嶆妱灏卞涓嶉綈銆?
///
/// 榛戝崱鐨偆锛堝悗鍙板彲鍒囷級鍙奖鍝嶉《閮ㄩ偅涓€鏉★細鎹?`hk_message_bg` 鍒囧浘銆佸瓧鍙樻垚娴呰壊锛屽崱鐗囦互涓嬩粛鏄〉搴曡壊銆?
final class MessageScreen: Screen {
    var zhiTabIndex: Int? { 3 }
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 杩欎竴灞忕殑 draw 涓嶈嚜甯︽粴鍔?translate 鈥斺€?鐢卞涓荤粺涓€婊?
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let navTop: CGFloat = 2560

    final class State: ScreenState {}

    // MARK: - 璋冭壊鏉匡紙Java:44-55锛?
    private let pageBg = Draw.color(0xfff2f3f7)
    private let pageLow = Draw.color(0xfff5f5f5)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff2e2e2e)
    private let headInk = Draw.color(0xff2a2e33)
    private let subInk = Draw.color(0xff949494)
    private let timeInk = Draw.color(0xffcdcdcd)
    private let unread = Draw.color(0xffcccccc)
    private let plusInk = Draw.color(0xff2b3038)
    private let badge = Draw.color(0xffc9d1dc)
    private let goldTint = Draw.color(0xffeacfa4)

    // MARK: - 妯悜鍘嬬缉绯绘暟锛圝ava:63-69锛岄€愭閲忓嚭鏉ョ殑锛?
    private let sxTitle: CGFloat = 1.006
    private let sxHead: CGFloat = 0.995
    private let sxName: CGFloat = 0.984
    private let sxSub: CGFloat = 1.016
    private let sxTime: CGFloat = 0.978
    private let sxSection: CGFloat = 0.963
    private let sxCaption: CGFloat = 1.030

    // MARK: - 鍑犱綍锛圝ava:73-160锛?
    private let plateH: CGFloat = 600
    private let blackHeaderH: CGFloat = 440
    private let blendTop: CGFloat = 1380
    private let blendBottom: CGFloat = 1500

    private let cardRect = CGRect(x: 35, y: 636, width: 1243 - 35, height: 2106 - 636)
    private let cardRadius: CGFloat = 52

    private let titleX: CGFloat = 35
    private let titleBase: CGFloat = 246
    private let titleSize: CGFloat = 64
    private let badgeX: CGFloat = 243
    private let badgeY: CGFloat = 222
    private let badgeR: CGFloat = 44
    private let pillRect = CGRect(x: 316, y: 160, width: 983 - 316, height: 283 - 160)
    private let contactsRect = CGRect(x: 1010, y: 170, width: 1106 - 1010, height: 266 - 170)
    private let broomRect = CGRect(x: 197, y: 176, width: 289 - 197, height: 268 - 176)
    private let plusX: CGFloat = 1200.5
    private let plusY: CGFloat = 220.5
    private let plusR: CGFloat = 37
    private let plusStroke: CGFloat = 6.5
    private let plusArm: CGFloat = 15.5

    private let avPanRect = CGRect(x: 68, y: 330, width: 245 - 68, height: 507 - 330)
    private let avAllRect = CGRect(x: 316, y: 337, width: 480 - 316, height: 502 - 337)
    private let capBase: CGFloat = 583
    private let capSize: CGFloat = 46
    private let capPanCx: CGFloat = 138
    private let capAllCx: CGFloat = 399

    private let headX: CGFloat = 78
    private let headBase: CGFloat = 729
    private let headSize: CGFloat = 50
    private let headIcons: [CGRect] = [
        CGRect(x: 781, y: 678, width: 845 - 781, height: 743 - 678),
        CGRect(x: 905, y: 677, width: 965 - 905, height: 743 - 677),
        CGRect(x: 1022, y: 677, width: 1088 - 1022, height: 743 - 677),
        CGRect(x: 1143, y: 677, width: 1209 - 1143, height: 743 - 677),
    ]
    private let headIconArt = ["msg_ic_at", "msg_ic_mail", "msg_ic_yen", "msg_ic_gift"]

    private let rowNameSize: CGFloat = 58
    private let rowSubSize: CGFloat = 45
    private let rowTimeSize: CGFloat = 39.5
    private let rowNameX: CGFloat = 284
    private let rowSubX: CGFloat = 285
    private let rowTimeRight: CGFloat = 1207

    /// 5 琛屼細璇濓細icon 宸?涓?鍙?涓嬨€佸悕瀛楀熀绾裤€佸壇鏍囬鍩虹嚎銆佹椂闂村熀绾匡紙Java:129-135锛?
    private static let rows: [[CGFloat]] = [
        [77, 827, 243, 993, 889, 957, 883],
        [85, 1094, 225, 1226, 1147, 1215, 1141],
        [97, 1408, 223, 1558, 1476, 1544, 1471],
        [77, 1653, 243, 1820, 1725, 1794, 1720],
        [79, 1906, 239, 2063, 1977, 2041, 1969],
    ]
    private static let rowIconArt = ["msg_ic_box", "msg_ic_follow", "msg_ic_cake", "msg_ic_life", "msg_ic_ai"]
    private static let rowName = ["娑堟伅鐩掑瓙", "鍏虫敞鍔ㄦ€?, "鐢熸棩鎻愰啋", "鐢熸椿鍙?, "AI鐞嗚储鍔╃悊铓傚皬璐?]
    private static let rowSub = [
        "鏀粯瀹濆氨涓氾綔鏄撶珛寰蜂紒涓氬悜浣犲彂鏉ヤ竴涓姇閫掆€?,
        "鏀粯瀹濆競姘戜腑蹇冿細灏婄晫鍥炲簲鍦ㄦ祴璇曚腑V80鈥?,
        "鏈懆鏈?涓ソ鍙嬭繃鐢熸棩锛岄€佷笂绁濈鍚?,
        "銆?銆戝鍗栬ˉ璐村緟棰嗗彇",
        "[鏈嶅姟鍗＄墖]",
    ]
    private static let rowTime = ["11:58", "07:56", "9鏈?1鏃?, "7鏈?1鏃?, "4鏈?4鏃?]
    private let sectionBefore = 2
    private let sectionLabel = "2鍛ㄥ墠鐨勬秷鎭?
    private let sectionX: CGFloat = 78
    private let sectionBase: CGFloat = 1341
    private let sectionSize: CGFloat = 52
    private let dotRow = 3
    private let dotX: CGFloat = 1190.5
    private let dotY: CGFloat = 1775
    private let dotR: CGFloat = 18
    private let tagRow = 4
    private let tagRect = CGRect(x: 754, y: 1920, width: 821 - 754, height: 1986 - 1920)
    private let navX: [CGFloat] = [128, 382, 639, 898, 1151]

    /// 18 寮犲垏鍥撅紙Java:171-193锛?
    private static let artNames = [
        "plate": "msg_bgplate", "search": "msg_search", "broom": "msg_ic_broom",
        "contacts": "msg_ic_contacts", "avPan": "msg_av_pan", "avAll": "msg_av_all",
        "atsign": "msg_ic_at", "mail": "msg_ic_mail", "yen": "msg_ic_yen", "gift": "msg_ic_gift",
        "box": "msg_ic_box", "follow": "msg_ic_follow", "cake": "msg_ic_cake",
        "life": "msg_ic_life", "ai": "msg_ic_ai", "tagAi": "msg_tag_ai",
        "navbar": "msg_navbar", "blackBackground": "hk_message_bg",
    ]
    private var art: [String: UIImage] = [:]

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }   // 鍗曞睆涓嶆粴鍔紙Java:196-198锛?

    // MARK: - 缁樺埗锛圝ava:202-208锛?

    func draw(_ d: Draw, state: ScreenState) {
        drawBackdrop(d)
        drawCardWithRows(d)
        drawHeaderBar(d)
        drawPinnedEntries(d)
        drawNavBar(d)
    }

    /// 椤堕儴閲囨牱鍥?+ 涓ゆ鐏?+ 杩囨浮娓愬彉锛圝ava:211-235锛?
    private func drawBackdrop(_ d: Draw) {
        if LicaiVersion.blackCard(LicaiVersion.current()) {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageLow)
            headerPlate(d, "blackBackground", artHeight: blackHeaderH, bottom: cardRect.minY)
            return
        }
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)
        if let plate = art["plate"] {
            d.art(plate, CGRect(x: 0, y: 0, width: Self.W, height: plateH))
        }
        d.gradient(CGRect(x: 0, y: blendTop, width: Self.W, height: blendBottom - blendTop),
                   from: pageBg, to: pageLow, vertical: true)
        d.rect(CGRect(x: 0, y: blendBottom, width: Self.W, height: Self.H - blendBottom), pageLow)
    }

    /// 鎶婇《閮ㄥ垏鍥剧殑鏈€鍚庝竴琛屽欢浼稿埌鍗＄墖涓婃部锛圝ava:243-259锛夛紝閬垮厤涓棿闇插嚭椤靛簳鑹?
    private func headerPlate(_ d: Draw, _ name: String, artHeight: CGFloat, bottom: CGFloat) {
        guard let bitmap = art[name] else { return }
        let height = min(bottom, artHeight)
        d.art(bitmap, CGRect(x: 0, y: 0, width: Self.W, height: height + 2))
        if height + 1 >= bottom { return }
        d.art(bitmap, src: nil, CGRect(x: 0, y: height + 1, width: Self.W, height: bottom - (height + 1)))
    }

    /// 椤堕儴閭ｄ竴鎺掞紙Java:261-279锛?
    private func drawHeaderBar(_ d: Draw) {
        let blackCard = LicaiVersion.blackCard(LicaiVersion.current())
        let headerColor = blackCard ? Draw.color(0xfff4f4f4) : headInk
        d.textSqueezed("娑堟伅", x: titleX, baseline: titleBase, size: titleSize, headerColor,
                       bold: true, squeeze: sxTitle)
        d.circle(cx: badgeX, cy: badgeY, r: badgeR, badge)
        d.art(art["broom"], broomRect)
        d.art(art["search"], pillRect)
        d.art(art["contacts"], contactsRect)

        // 銆?銆嶆槸鍦嗙幆 + 鍗佸瓧锛岀洿鎺ョ敾锛圝ava:270-278锛?
        let plusColor = blackCard ? goldTint : plusInk
        d.ctx.setStrokeColor(plusColor.cgColor)
        d.ctx.setLineWidth(plusStroke)
        d.ctx.strokeEllipse(in: CGRect(x: plusX - plusR, y: plusY - plusR,
                                       width: plusR * 2, height: plusR * 2))
        d.ctx.move(to: CGPoint(x: plusX - plusArm, y: plusY))
        d.ctx.addLine(to: CGPoint(x: plusX + plusArm, y: plusY))
        d.ctx.move(to: CGPoint(x: plusX, y: plusY - plusArm))
        d.ctx.addLine(to: CGPoint(x: plusX, y: plusY + plusArm))
        d.ctx.strokePath()
    }

    /// 鍗＄墖涓婃部閭ｄ袱涓疆椤跺叆鍙ｏ紙Java:281-287锛?
    private func drawPinnedEntries(_ d: Draw) {
        let color = LicaiVersion.blackCard(LicaiVersion.current()) ? Draw.color(0xfff4f4f4) : ink
        d.art(art["avPan"], avPanRect)
        d.art(art["avAll"], avAllRect)
        d.centerTextSqueezed("鐞嗚储鐩樷€?, cx: capPanCx, baseline: capBase, size: capSize,
                             color, bold: true, squeeze: sxCaption)
        d.centerTextSqueezed("鍏ㄩ儴", cx: capAllCx, baseline: capBase + 1, size: capSize,
                             color, bold: true, squeeze: sxCaption)
    }

    /// 鐧藉崱 + 鍏ㄩ儴浼氳瘽 + 5 琛岋紙Java:289-315锛?
    private func drawCardWithRows(_ d: Draw) {
        d.round(cardRect, radius: cardRadius, cardColor)
        d.textSqueezed("鍏ㄩ儴浼氳瘽", x: headX, baseline: headBase, size: headSize, ink,
                       bold: true, squeeze: sxHead)
        for (i, box) in headIcons.enumerated() where i < headIconArt.count {
            d.art(art[Self.artKey(for: headIconArt[i])], box)
        }

        for (i, row) in Self.rows.enumerated() {
            if i == sectionBefore {
                d.textSqueezed(sectionLabel, x: sectionX, baseline: sectionBase, size: sectionSize,
                               subInk, squeeze: sxSection)
            }
            d.art(art[Self.artKey(for: Self.rowIconArt[i])],
                  CGRect(x: row[0], y: row[1], width: row[2] - row[0], height: row[3] - row[1]))
            d.textSqueezed(Self.rowName[i], x: rowNameX, baseline: row[4], size: rowNameSize,
                           ink, bold: true, squeeze: sxName)
            d.textSqueezed(Self.rowSub[i], x: rowSubX, baseline: row[5], size: rowSubSize,
                           subInk, squeeze: sxSub)
            d.rightTextSqueezed(Self.rowTime[i], right: rowTimeRight, baseline: row[6],
                                size: rowTimeSize, timeInk, squeeze: sxTime)
            if i == dotRow {
                d.circle(cx: dotX, cy: dotY, r: dotR, unread)
            }
            if i == tagRow {
                d.art(art["tagAi"], tagRect)
            }
        }
    }

    /// 搴曟爮灏辨槸鍙傝€冨浘閭ｆ潯甯﹀瓙锛圝ava:317-324锛?
    private func drawNavBar(_ d: Draw) {
        d.art(art["navbar"], CGRect(x: 0, y: Self.navTop, width: Self.W, height: Self.H - Self.navTop))
    }

    /// 璧勬簮鍚?鈫?瀛楀吀閿紙涓よ竟鍛藉悕涓嶅悓锛岃繖閲岀粺涓€鏄犲皠锛?
    private static func artKey(for resource: String) -> String {
        artNames.first { $0.value == resource }?.key ?? resource
    }

    // MARK: - 鍛戒腑锛圝ava:329-385锛?

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y >= Self.navTop { return "nav:\(nearest(navX, x))" }

        if y < 300 {
            if x >= 190 && x < 300 { return "toast:宸叉竻闄ゆ秷鎭彁閱? }
            return "noop"
        }
        if y >= avPanRect.minY - 10 && y <= avPanRect.maxY + 10 { return "noop" }
        if y >= 520 && y < 620 { return "noop" }
        if y < cardRect.minY { return "noop" }

        if y >= cardRect.minY && y < cardRect.maxY {
            if y < 760 { return "noop" }
            if y >= 1290 && y < 1370 { return "noop" }
            let row = rowAt(y)
            if row < 0 { return "noop" }
            // 娑堟伅鐩掑瓙鏄敮浠樻秷鎭〉鐨勫父瑙勫叆鍙ｏ紙Java:362-363锛?
            if row == 0 { return "box" }
            return "noop"
        }
        return "noop"
    }

    /// 鍛戒腑琛岋細琛岀殑涓婁笅娌垮悇鏀惧 18px锛涘垎娈垫爣棰橀偅琛屽啀寰€涓婂 60px锛圝ava:377-385锛?
    private func rowAt(_ y: CGFloat) -> Int {
        for (i, row) in Self.rows.enumerated() {
            var top = row[1] - 18
            let bottom = row[3] + 18
            if i == sectionBefore { top -= 60 }
            if y >= top && y <= bottom { return i }
        }
        return -1
    }

    private func nearest(_ values: [CGFloat], _ x: CGFloat) -> Int {
        var best = 0
        var bestD = CGFloat.greatestFiniteMagnitude
        for (i, v) in values.enumerated() where abs(v - x) < bestD {
            bestD = abs(v - x)
            best = i
        }
        return best
    }
}
