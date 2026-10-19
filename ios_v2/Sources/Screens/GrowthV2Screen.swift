import UIKit
import CoreText

/// 銆屾垚闀跨増銆嶇悊璐㈤〉 鈥斺€?`features/zhi/ui/GrowthV2Screen.java` 鐨?1:1 绉绘锛?280脳2816锛夈€?
///
/// 涓庢櫘閫氱増锛坄LicaiV2Screen`锛夊叡鐢ㄧ姸鎬?`LicaiV2Screen.State`锛屼絾鏁撮〉鏄?*榛戝崱鐨偆**锛?
/// 椤堕儴涓夋鍙傝€冨浘妯垏鏉★紙鐘舵€佹爮 / 澶存爮 / 鍗″簳锛夛紝涓棿鍙睍寮€鐨勪繚闅滈潰鏉匡紝
/// 涓嬮潰鏄竴寮犲法澶х殑鐧借壊銆宻heet銆嶏紝閲岄潰鍒嗐€屽競鍦?/ 鏈嶅姟銆嶄袱涓〉绛俱€?
///
/// 涓変釜瑕佺収鎶勭殑缁嗚妭锛?
///   路 **闈㈡澘涓ょ鐢ㄦ潯甯︽贩鍚?*锛坄panelTop` / `panelBottom` / `panelFoot`锛夆€斺€?浠庡弬鑰冨浘鍙栦竴琛屽儚绱?
///     涓庨潰鏉垮簳鑹叉嫾鎴?2 琛屽皬浣嶅浘锛屾媺浼稿悗閫愬垪杩囨浮锛岃繖鏍峰睍寮€鏃朵笉浼氬嚭鐜版í绾?
///   路 **`fitText`**锛氬弬鑰冨浘鐨勪腑鏂囧瓧姣旂郴缁熷瓧绐勶紝鎵€浠ユ寜缁欏畾瀹藉害鍐嶅帇涓€娆?
///   路 **甯傚満椤电鐨勬墍鏈夊唴瀹规暣浣撲笅绉?`MARKET_INSERT`(250)** 鈥斺€?鍥犱负鐞嗚储甯堝崱鏄澶栧姞鐨?
final class GrowthV2Screen: Screen {
    var zhiTabIndex: Int? { 1 }
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let navTop: CGFloat = LicaiV2Screen.navTop
    static let headerBottom: CGFloat = 600
    static let panelHeight: CGFloat = 785
    static let sheetTop: CGFloat = 838
    static let contentHeight: CGFloat = 2816
    static let marketInsert: CGFloat = 250

    /// 涓庢櫘閫氱増鍏辩敤鐘舵€?
    final class State: ScreenState {
        var expand: CGFloat = 0
        var selectedTab = 0
    }

    private let sx: CGFloat = 0.93

    // MARK: - 璋冭壊鏉匡紙Java:149-173锛?
    private let pageBg = Draw.color(0xfff2f3f7)
    private let cardWhite = Draw.color(0xffffffff)
    private let sheetLow = Draw.color(0xfff2f3f8)
    private let red = Draw.color(0xffc0342b)
    private let moveInk = Draw.color(0xffdd5f6b)
    private let allocInk = Draw.color(0xff16202b)
    private let tagBg = Draw.color(0xffeaf1f9)
    private let tagInk = Draw.color(0xff2b7fd4)
    private let gold = Draw.color(0xffdac5ad)
    private let blackInk = Draw.color(0xff2c3038)
    private let labelInk = Draw.color(0xff8b93a8)
    private let holderInk = Draw.color(0xffe4ded6)
    private let panelLabel = Draw.color(0xffe2ddd6)
    private let panelValue = Draw.color(0xffb4b6c0)
    private let panelCaption = Draw.color(0xff7f8798)
    private let panelCaret = Draw.color(0xffccceda)
    private let panelWash = Draw.color(0xff10192f)
    private let panelBlend: CGFloat = 96
    private let panelFoot: CGFloat = 4

    // MARK: - 鍑犱綍锛圝ava:84-289锛?
    private let topPlate = CGRect(x: 0, y: 0, width: 1280, height: 134)
    private let headerPlate = CGRect(x: 0, y: 128, width: 1280, height: 592 - 128)
    private let backPlate = CGRect(x: 0, y: 592, width: 1280, height: 920 - 592)
    private let cardBox = CGRect(x: 22, y: 592, width: 1258 - 22, height: 838 - 592)
    private let tabBox = CGRect(x: 0, y: 700, width: 1280, height: 910 - 700)
    private let sheetRadius: CGFloat = 56
    private let gridTop: CGFloat = 41
    private let themeCard = CGRect(x: 22, y: 592, width: 1258 - 22, height: 873 - 592)

    private let advisor = CGRect(x: 36, y: 1358, width: 1244 - 36, height: 1572 - 1358)
    private let advisorRadius: CGFloat = 28
    private let advisorAvatar = CGRect(x: 70, y: 1391, width: 223 - 70, height: 1544 - 1391)
    private let advisorTalk = CGRect(x: 921, y: 1427, width: 999 - 921, height: 1502 - 1427)
    private let advisorBook = CGRect(x: 1087, y: 1428, width: 1163 - 1087, height: 1502 - 1428)
    private let advisorRole = "璐㈠瘜榛戝崱鐞嗚储甯?
    private let advisorNameX: CGFloat = 252
    private let advisorNameBase: CGFloat = 1451
    private let advisorNameSize: CGFloat = 58
    private let advisorNameW: CGFloat = 102
    private let advisorRoleX: CGFloat = 253
    private let advisorRoleBase: CGFloat = 1521
    private let advisorRoleSize: CGFloat = 52
    private let advisorRoleW: CGFloat = 312
    private let advisorButtonInk = Draw.color(0xffa8846a)
    private let advisorNameInk = Draw.color(0xff232326)
    private let advisorRoleInk = Draw.color(0xff6f6f74)

    private let tabCx: [CGFloat] = [144, 436]
    private let tabBase: CGFloat = 813
    private let tabSize: CGFloat = 52
    private let tabAdvance: CGFloat = 106
    private let tabUnderline: CGFloat = 97
    private let tabOn = Draw.color(0xff8c6958)
    private let tabOff = Draw.color(0xff9a9a9a)

    private let marketCard = CGRect(x: 37, y: 528, width: 1242 - 37, height: 660 - 528)
    private let marketNameX: CGFloat = 82
    private let marketBase: CGFloat = 616
    private let marketSize: CGFloat = 58
    private let marketValueX: [CGFloat] = [429, 693, 928]
    private let marketArrow = CGRect(x: 1150, y: 561, width: 1196 - 1150, height: 607 - 561)

    private let labelSize: CGFloat = 50
    private let valueSize: CGFloat = 74
    private let valueBase: CGFloat = 508
    private let eyeRect = CGRect(x: 278, y: 373, width: 352 - 278, height: 414 - 373)
    private let rateRect = CGRect(x: 400, y: 342, width: 692 - 400, height: 420 - 342)
    private let stars: [[CGFloat]] = [[56, 101, 146, 192], [808, 853, 899, 945],
                                      [1071, 1116, 1161, 1208]]
    private let starSize: CGFloat = 83
    private let starBase: CGFloat = 525
    private let holderX: CGFloat = 986
    private let holderBase: CGFloat = 678
    private let holderSize: CGFloat = 48
    private let holderMax: CGFloat = 238

    private let fundsTop: CGFloat = 1518
    private let fundsH: CGFloat = 404
    private let discoverTop: CGFloat = 1938
    private let discoverH: CGFloat = 274
    private let bannerTop: CGFloat = 2238
    private let bannerH: CGFloat = 184
    private let blackTop: CGFloat = 2428
    private let blackH: CGFloat = 132
    private let blackLabelX: CGFloat = 40
    private let blackLabelBase: CGFloat = 2556
    private let blackLabelSize: CGFloat = 58
    private let rankRight: CGFloat = 1196

    private let panelSize: CGFloat = 44
    private let panelBase: [CGFloat] = [640, 741, 848, 966, 1070, 1192, 1292]
    private let panelA: CGFloat = 121
    private let panelB: CGFloat = 259
    private let panelC: CGFloat = 660
    private let panelD: CGFloat = 761
    private let navX: [CGFloat] = [128, 382, 639, 898, 1151]

    private let fundSelfX: CGFloat = 78
    private let fundSelfBase: CGFloat = 1799
    private let fundSelfSize: CGFloat = 61
    private let fundTag = CGRect(x: 299, y: 1745, width: 510 - 299, height: 1802 - 1745)
    private let fundTagX: CGFloat = 313
    private let fundTagBase: CGFloat = 1789
    private let fundTagSize: CGFloat = 43
    private let fundTagRadius: CGFloat = 10
    private let rank1Base: CGFloat = 1788
    private let rank2Base: CGFloat = 1871
    private let rankSize: CGFloat = 45
    private let mktX: CGFloat = 465
    private let mktBase: CGFloat = 2165
    private let mktSize: CGFloat = 36
    private let allocX: CGFloat = 658
    private let allocBase: CGFloat = 2114
    private let allocSize: CGFloat = 36
    private let marketKeys = ["idx2", "move2", "idxp2"]

    private let gridLabel = ["浣欓瀹?, "绋冲仴鐞嗚储", "鍩洪噾", "榛勯噾", "鑲＄エ",
                             "瀹氭湡", "淇濋櫓", "楂樼鐞嗚储", "鍏ㄧ悆鎶曡祫", "鏇村"]
    private let gridCx: [CGFloat] = [155, 396, 636, 877, 1130]
    private let gridBase: [CGFloat] = [1093, 1323]

    private let serviceTitles = ["璐㈠瘜瑙勫垝", "榛戝崱鐞嗚储", "璐㈠瘜绉佷韩浼?, "涓撳睘涓撳鍥㈤槦",
                                 "璧勪骇閰嶇疆", "楂樼淇濋櫓", "绋庡姟绛瑰垝", "璐㈠瘜浼犳壙"]
    private let serviceMarks = ["瑙?, "鍗?, "浜?, "涓?, "閰?, "淇?, "绋?, "浼?]
    private let serviceColors: [UInt32] = [0xffc69b70, 0xff6c82a8, 0xffc98669, 0xff718c83,
                                           0xffa98a6f, 0xff8b9bb8, 0xffbfa07e, 0xff93a89b]
    private let serviceCx: [CGFloat] = [185, 490, 795, 1100]
    private let serviceRowCy: [CGFloat] = [1008, 1260]
    private let serviceLabelBase: [CGFloat] = [1114, 1366]
    private let serviceCaptionBase: [CGFloat] = [1162, 1414]
    private let marketNames = ["绾虫柉杈惧厠", "涓婅瘉鎸囨暟", "娣辫瘉鎴愭寚", "鍒涗笟鏉挎寚"]
    private let marketPoints = ["idx2", "bidx0", "bidx1", "bidx2"]
    private let marketChanges = ["idxp2", "bpct0", "bpct1", "bpct2"]
    private let marketRowBase: CGFloat = 2190
    private let marketRowStep: CGFloat = 92

    private var art: [String: UIImage] = [:]
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private var hits: [Hit] = []
    private(set) var selectedTab = 0

    init() {
        let mapping = [
            "top": "gs_top", "header": "gs_header", "back": "gs_back", "panel": "gs_panel",
            "card": "gs_card", "tab": "gs_tab", "arrow": "gs_arrow", "lower": "gl_lower",
            "gs_grid": "gs_grid", "funds": "gs_funds", "discover": "gs_discover",
            "banner": "gs_banner", "black": "gs_black", "advisor": "lci_advisor",
            "advisorTalk": "lci_advisor_talk", "advisorBook": "lci_advisor_book", "nav": "gs_nav",
        ]
        for (key, res) in mapping {
            if let image = UIImage(named: res) { art[key] = image }
        }
        // 浜旀。涓婚鐨勯摥鐗?/ 鍗＄墖 / 鎸佸崱鏉?
        for (key, res) in [(1, "licai_badge_growth"), (2, "licai_badge_v1"),
                           (3, "licai_badge_v2"), (4, "licai_badge_v3")] {
            if let image = UIImage(named: res) { art["theme_badge_\(key)"] = image }
        }
        for (key, res) in [(1, "licai_card_growth"), (2, "licai_card_v1"),
                           (3, "licai_card_v2"), (4, "licai_card_v3")] {
            if let image = UIImage(named: res) { art["theme_card_\(key)"] = image }
        }
        for (key, res) in [(1, "licai_holder_growth"), (2, "licai_holder_v1"),
                           (3, "licai_holder_v2"), (4, "licai_holder_v3")] {
            if let image = UIImage(named: res) { art["theme_holder_\(key)"] = image }
        }
        // 闈㈡澘涓ょ鐨勮繃娓℃潯锛圝ava load() 鏈熬锛?
        if let header = art["header"] {
            art["panelTop"] = Self.blendStrip(header, row: Int(header.size.height * header.scale) - 1,
                                              plateAbove: true)
        }
        if let back = art["back"] {
            art["panelBottom"] = Self.blendStrip(back, row: 0, plateAbove: false)
            art["panelFoot"] = Self.rowStrip(back, row: 0)
        }
    }

    /// 鍙栧弬鑰冨浘鐨勪竴琛屽仛鎴?1 鍍忕礌楂樼殑灏忓浘锛圝ava:340-347锛?
    private static func rowStrip(_ plate: UIImage, row: Int) -> UIImage? {
        guard let cg = plate.cgImage else { return nil }
        let rect = CGRect(x: 0, y: row, width: cg.width, height: 1)
        return cg.cropping(to: rect).map { UIImage(cgImage: $0) }
    }

    /// 鍙傝€冨浘鐨勪竴琛?+ 闈㈡澘搴曡壊鍚勪竴琛岋紝鎷兼垚 2 鍍忕礌楂樼殑杩囨浮鏉★紙Java:350-360锛?
    private static func blendStrip(_ plate: UIImage, row: Int, plateAbove: Bool) -> UIImage? {
        guard let cg = plate.cgImage, let strip = cg.cropping(to: CGRect(x: 0, y: row,
                                                                        width: cg.width, height: 1))
        else { return nil }
        let width = cg.width
        let size = CGSize(width: width, height: 2)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            let wash = UIColor(red: 0x10 / 255.0, green: 0x19 / 255.0, blue: 0x2f / 255.0, alpha: 1)
            if plateAbove {
                UIImage(cgImage: strip).draw(in: CGRect(x: 0, y: 0, width: width, height: 1))
                wash.setFill()
                ctx.fill(CGRect(x: 0, y: 1, width: width, height: 1))
            } else {
                wash.setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: width, height: 1))
                UIImage(cgImage: strip).draw(in: CGRect(x: 0, y: 1, width: width, height: 1))
            }
        }
    }

    private func unfold(_ s: State) -> CGFloat {
        Self.panelHeight * max(0, min(1, s.expand))
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        let content = selectedTab == 1 ? Self.contentHeight
                                       : Self.contentHeight + Self.marketInsert
        return max(0, content + Self.panelHeight * s.expand - Self.H)
    }

    // MARK: - 缁樺埗锛圝ava:387-426锛?

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let off = unfold(s)
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: 0, width: Self.W, height: Self.headerBottom + off))
        d.art(art["top"], topPlate)
        d.art(art["header"], headerPlate)
        drawLiveHeader(d)
        drawPanelWash(d, off: off)
        drawPanel(d, off: off)
        d.ctx.restoreGState()

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: off)
        d.art(art["back"], backPlate)
        d.art(art["theme_card_\(LicaiVersion.current())"], themeCard)
        drawHolder(d)
        drawSheet(d)
        d.ctx.saveGState()
        let sheetBottom = Self.contentHeight + Self.marketInsert
        d.ctx.clip(to: CGRect(x: 0, y: Self.sheetTop, width: Self.W, height: sheetBottom - Self.sheetTop))
        if selectedTab == 0 { drawMarketContent(d) } else { drawServiceContent(d) }
        d.ctx.restoreGState()
        d.ctx.restoreGState()

        d.ctx.restoreGState()

        if s.scroll > Self.headerBottom - 150 {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: 150), pageBg)
        }
    }

    /// 涓変釜鏁板瓧鎴栧洓棰楁槦锛圝ava:437-450锛?
    private func drawLiveHeader(_ d: Draw) {
        if GrowthStore.shared.hidden {
            for group in stars {
                for x in group {
                    d.text("*", x: x, baseline: starBase, size: starSize, gold)
                }
            }
            return
        }
        d.text(GrowthStore.shared.value("total"), x: 50, baseline: valueBase, size: valueSize, gold)
        d.rightText(GrowthStore.shared.value("yesterday"), right: 1030, baseline: valueBase,
                    size: valueSize, gold)
        d.rightText(GrowthStore.shared.value("family"), right: 1215, baseline: valueBase,
                    size: valueSize, gold)
        d.text("浠?, x: 1230, baseline: valueBase - 10, size: labelSize, labelInk)
    }

    /// 闈㈡澘搴曡壊涓庝袱绔繃娓★紙Java:459-480锛?
    private func drawPanelWash(_ d: Draw, off: CGFloat) {
        let bottom = 592 + max(off, 8)
        d.rect(CGRect(x: 0, y: 592, width: Self.W, height: bottom - 592), panelWash)
        guard off > 0 else { return }
        let blend = min(panelBlend, max(0, off / 2 - panelFoot))
        if blend > 1 {
            d.art(art["panelTop"], CGRect(x: 0, y: 592, width: Self.W, height: blend))
            d.art(art["panelBottom"], CGRect(x: 0, y: bottom - blend, width: Self.W,
                                            height: blend - panelFoot))
        }
        d.art(art["panelFoot"], CGRect(x: 0, y: bottom - panelFoot, width: Self.W,
                                       height: panelFoot * 2 + 2))
    }

    /// 闈㈡澘閲岀殑涓冭锛圝ava:488-516锛?
    private func drawPanel(_ d: Draw, off: CGFloat) {
        let store = GrowthStore.shared
        d.text("浣欓瀹?, x: panelA, baseline: panelBase[0], size: panelSize, panelLabel)
        d.text(store.value("reserve"), x: panelB, baseline: panelBase[0], size: panelSize, panelValue)
        d.text(store.value("reserveProfit"), x: 506, baseline: panelBase[0], size: panelSize, panelValue)
        d.text("鍩洪噾", x: panelC, baseline: panelBase[0], size: panelSize, panelLabel)
        d.text(store.value("fund"), x: panelD, baseline: panelBase[0], size: panelSize, panelValue)
        d.text(store.value("fundProfit"), x: 1002, baseline: panelBase[0], size: panelSize, panelValue)

        d.text("鏇村璧勪骇鏈嶅姟", x: panelA, baseline: panelBase[1], size: panelSize, panelLabel)
        d.text("鍘荤湅鐪?, x: 487, baseline: panelBase[1], size: panelSize, panelLabel)
        d.text("鎴戠殑淇濋殰璧勪骇", x: panelA, baseline: panelBase[2], size: panelSize, panelCaption)

        d.text("淇濋櫓", x: panelA, baseline: panelBase[3], size: panelSize, panelLabel)
        d.text("1浠戒繚鍗曚繚闅滀腑", x: 221, baseline: panelBase[3], size: panelSize, panelValue)
        d.text("鍌ㄨ搫|鍏昏€?, x: panelC, baseline: panelBase[3], size: panelSize, panelLabel)
        d.text(store.value("savings"), x: 857, baseline: panelBase[3], size: panelSize, panelValue)

        d.text("鎴戠殑棰濆害", x: panelA, baseline: panelBase[4], size: panelSize, panelCaption)
        d.text("鑺卞憲", x: panelA, baseline: panelBase[5], size: panelSize, panelLabel)
        d.text(store.value("huabei"), x: 218, baseline: panelBase[5], size: panelSize, panelValue)
        d.text("鍊熷憲", x: 657, baseline: panelBase[5], size: panelSize, panelLabel)
        d.text("绉掑埌璐︼紝闅忔椂杩?, x: 755, baseline: panelBase[5], size: panelSize, panelValue)

        d.text("澶囩敤閲?, x: 120, baseline: panelBase[6], size: panelSize, panelLabel)
        d.text("鍘讳娇鐢?, x: 255, baseline: panelBase[6], size: panelSize, panelValue)
        d.text("缃戝晢璐?, x: panelC, baseline: panelBase[6], size: panelSize, panelLabel)
        d.text("鏇存柊棰濆害", x: 792, baseline: panelBase[6], size: panelSize, panelValue)
        caret(d, cx: 639, off: off)
    }

    /// 鐧借壊 sheet锛圝ava:519-534锛?
    private func drawSheet(_ d: Draw) {
        let sheetBottom = Self.contentHeight + Self.marketInsert
        let sheet = CGRect(x: 0, y: Self.sheetTop - 1, width: Self.W, height: sheetBottom - (Self.sheetTop - 1))
        d.clipped(to: sheet, radius: sheetRadius) {
            d.gradient(CGRect(x: 0, y: Self.sheetTop, width: Self.W, height: 520),
                       from: cardWhite, to: sheetLow, vertical: true)
        }
        d.art(art["tab"], tabBox)
        if let grid = art["gs_grid"] {
            let h = grid.size.height * grid.scale
            d.art(grid, CGRect(x: 0, y: Self.sheetTop + gridTop, width: Self.W, height: h))
        }
        drawTabLabels(d)
    }

    /// 銆屽競鍦?/ 鏈嶅姟銆嶄袱涓〉绛撅紙Java:536-547锛?
    private func drawTabLabels(_ d: Draw) {
        let market = selectedTab == 0
        label(d, "甯傚満", cx: tabCx[0], baseline: tabBase, size: tabSize,
              color: market ? tabOn : tabOff)
        label(d, "鏈嶅姟", cx: tabCx[1], baseline: tabBase, size: tabSize,
              color: market ? tabOff : tabOn)
        let cx = tabCx[market ? 0 : 1]
        d.round(CGRect(x: cx - tabUnderline / 2, y: 838, width: tabUnderline, height: 7),
                radius: 4, Draw.color(0xff94705e))
    }

    /// 鎸夊弬鑰冨浘缁欏畾瀹藉害鍘嬩竴娆″啀鐢伙紙Java:554-568锛?
    private func fitText(_ d: Draw, _ value: String, x: CGFloat, baseline: CGFloat, size: CGFloat,
                         color: UIColor, bold: Bool, width: CGFloat) {
        let natural = Draw.width(value, size: size, bold: bold) * sx
        let squeeze = width / max(1, natural)
        d.textSqueezed(value, x: x, baseline: baseline, size: size, color, bold: bold,
                       squeeze: sx * squeeze)
    }

    /// 椤电鏂囧瓧锛氬眳涓笖鍘嬪埌鍙傝€冩瀹斤紙Java:571-584锛?
    private func label(_ d: Draw, _ value: String, cx: CGFloat, baseline: CGFloat, size: CGFloat,
                       color: UIColor) {
        let natural = Draw.width(value, size: size) * sx
        let squeeze = tabAdvance / max(1, natural)
        d.centerTextSqueezed(value, cx: cx, baseline: baseline, size: size, color,
                             squeeze: sx * squeeze)
    }

    /// 甯傚満椤电鐨勫唴瀹癸紙Java:586-609锛?
    private func drawMarketContent(_ d: Draw) {
        drawAdvisor(d)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: Self.marketInsert)
        drawMarketRow(d)
        d.art(art["funds"], CGRect(x: 0, y: fundsTop, width: Self.W, height: fundsH))
        drawFundFigures(d)
        d.art(art["discover"], CGRect(x: 0, y: discoverTop, width: Self.W, height: discoverH))
        drawDiscoverFigures(d)
        d.art(art["banner"], CGRect(x: 0, y: bannerTop, width: Self.W, height: bannerH))
        d.art(art["black"], CGRect(x: 0, y: blackTop, width: Self.W, height: blackH))
        // 榛戝崱涓撳尯鐨勬渶鍚庝竴琛岃閾哄埌椤甸潰搴曢儴锛圝ava 鐨?drawTail锛?
        if let black = art["black"], let cg = black.cgImage,
           let lastRow = cg.cropping(to: CGRect(x: 0, y: cg.height - 1, width: cg.width, height: 1)) {
            d.art(UIImage(cgImage: lastRow), CGRect(x: 0, y: blackTop + blackH, width: Self.W,
                                                   height: Self.H - (blackTop + blackH)))
        }
        d.text("榛戝崱涓撳尯", x: blackLabelX, baseline: blackLabelBase, size: blackLabelSize,
               blackInk, bold: true)
        d.ctx.restoreGState()
    }

    /// 鐞嗚储甯堝崱锛圝ava:612-645锛?
    private func drawAdvisor(_ d: Draw) {
        d.round(advisor, radius: advisorRadius, cardWhite)
        if let picture = AdvisorStore.shared.avatar ?? art["advisor"] {
            d.clipped(to: advisorAvatar, radius: advisorAvatar.width / 2) {
                d.art(picture, advisorAvatar)
            }
        }
        fitText(d, AdvisorStore.shared.name, x: advisorNameX, baseline: advisorNameBase,
                size: advisorNameSize, color: advisorNameInk, bold: true, width: advisorNameW)
        fitText(d, advisorRole, x: advisorRoleX, baseline: advisorRoleBase,
                size: advisorRoleSize, color: advisorRoleInk, bold: false, width: advisorRoleW)
        // 涓や釜鎸夐挳鏄弬鑰冨浘鐨勫瓧褰紝鎸?alpha 娑傛垚涓€绉嶅ⅷ鑹?
        for (name, box) in [("advisorTalk", advisorTalk), ("advisorBook", advisorBook)] {
            guard let glyph = art[name] else { continue }
            d.clipped(to: box, radius: 0) {
                d.art(glyph.tinted(advisorButtonInk), box)
            }
        }
    }

    /// 鍩洪噾鑷€?/ 鍩洪噾鎺掕 鐨勬暟瀛楋紙Java:654-664锛?
    private func drawFundFigures(_ d: Draw) {
        let store = GrowthStore.shared
        d.text(store.value("fundSelfPct"), x: fundSelfX, baseline: fundSelfBase,
               size: fundSelfSize, pageRedInk, bold: true)
        d.round(fundTag, radius: fundTagRadius, tagBg)
        d.text(store.value("fundSelfTag"), x: fundTagX, baseline: fundTagBase,
               size: fundTagSize, tagInk)
        d.rightText(store.value("rankPct0"), right: rankRight, baseline: rank1Base,
                    size: rankSize, pageRedInk)
        d.rightText(store.value("rankPct1"), right: rankRight, baseline: rank2Base,
                    size: rankSize, pageRedInk)
    }

    private var pageRedInk: UIColor { Draw.color(0xffc43942) }

    /// 閫夊ソ鍝佲€︾洏鍙嬪湀 閭ｄ袱涓暟瀛楋紙Java:667-670锛?
    private func drawDiscoverFigures(_ d: Draw) {
        let store = GrowthStore.shared
        d.text(store.value("windMove"), x: mktX, baseline: mktBase, size: mktSize,
               moveInk, bold: true)
        d.text(store.value("allocPct"), x: allocX, baseline: allocBase, size: allocSize,
               allocInk, bold: true)
    }

    /// 鏈嶅姟椤电锛圝ava:672-714锛?
    private func drawServiceContent(_ d: Draw) {
        d.rect(CGRect(x: 0, y: Self.sheetTop + gridTop, width: Self.W, height: 72 - gridTop), cardWhite)
        d.rect(CGRect(x: 0, y: Self.sheetTop + 72, width: Self.W,
                      height: Self.contentHeight - (Self.sheetTop + 72)), sheetLow)

        d.round(CGRect(x: 36, y: 930, width: 1244 - 36, height: 1545 - 930), radius: 34, cardWhite)
        for row in 0..<2 {
            for col in 0..<4 {
                let i = row * 4 + col
                let cx = serviceCx[col]
                let cy = serviceRowCy[row]
                d.round(CGRect(x: cx - 48, y: cy - 48, width: 96, height: 96), radius: 48,
                        Draw.color(serviceColors[i]))
                d.centerText(serviceMarks[i], cx: cx, baseline: cy + 19, size: 48, .white)
                d.centerText(serviceTitles[i], cx: cx, baseline: serviceLabelBase[row],
                             size: 40, Draw.color(0xff292a2d))
                d.centerText("涓撳睘鏈嶅姟", cx: cx, baseline: serviceCaptionBase[row],
                             size: 28, Draw.color(0xff92969d))
            }
        }
        d.rect(CGRect(x: 65, y: 1451, width: 1215 - 65, height: 2), Draw.color(0xffeff0f2))
        d.text("涓洪粦鍗＄敤鎴锋彁渚涗笓灞炴潈鐩婁笌璐㈠瘜绠＄悊鏈嶅姟", x: 82, baseline: 1516, size: 34,
               Draw.color(0xff777c84))
        d.text("鏌ョ湅鍏ㄩ儴", x: 1040, baseline: 1516, size: 30, Draw.color(0xff9b7257))

        d.round(CGRect(x: 36, y: 1575, width: 1244 - 36, height: 1975 - 1575), radius: 34, cardWhite)
        d.round(CGRect(x: 84, y: 1615, width: 200, height: 200), radius: 100,
                Draw.color(0xffe9edf4))
        d.centerText("鐞?, cx: 184, baseline: 1743, size: 96, Draw.color(0xff8b725e))
        d.text("涓撳睘鐞嗚储甯?, x: 330, baseline: 1685, size: 48, Draw.color(0xff25262a))
        d.text("涓轰綘鎻愪緵涓€瀵逛竴璐㈠瘜瑙勫垝鏈嶅姟", x: 330, baseline: 1756, size: 34,
               Draw.color(0xff858990))
        d.round(CGRect(x: 330, y: 1815, width: 245, height: 75), radius: 38,
                Draw.color(0xfff3eee9))
        d.centerText("鍦ㄧ嚎鍜ㄨ", cx: 452, baseline: 1866, size: 34, Draw.color(0xff9b7257))
        d.round(CGRect(x: 800, y: 1815, width: 245, height: 75), radius: 38,
                Draw.color(0xffedf3fb))
        d.centerText("鐢佃瘽鑱旂郴", cx: 922, baseline: 1866, size: 34, Draw.color(0xff567aab))

        d.round(CGRect(x: 36, y: 2005, width: 1244 - 36, height: 2530 - 2005), radius: 34, cardWhite)
        d.text("甯傚満琛屾儏", x: 82, baseline: 2088, size: 46, Draw.color(0xff28292d))
        for i in 0..<marketNames.count {
            let base = marketRowBase + CGFloat(i) * marketRowStep
            d.text(marketNames[i], x: 82, baseline: base, size: 42, Draw.color(0xff55585e))
            d.rightText(GrowthStore.shared.value(marketPoints[i]), right: 985, baseline: base,
                        size: 42, Draw.color(0xff34363a))
            let change = GrowthStore.shared.value(marketChanges[i])
            d.rightText(change, right: 1195, baseline: base, size: 42,
                        change.hasPrefix("+") ? red : Draw.color(0xff0f9070))
        }
    }

    /// 绾虫柉杈惧厠閭ｄ竴琛岋紙Java:717-727锛?
    private func drawMarketRow(_ d: Draw) {
        d.round(CGRect(x: marketCard.minX, y: Self.sheetTop + marketCard.minY,
                       width: marketCard.width, height: marketCard.height), radius: 30, cardWhite)
        d.text("绾虫柉杈惧厠", x: marketNameX, baseline: Self.sheetTop + marketBase,
               size: marketSize, Draw.color(0xff26262a))
        for (i, key) in marketKeys.enumerated() {
            d.text(GrowthStore.shared.value(key), x: marketValueX[i],
                   baseline: Self.sheetTop + marketBase, size: marketSize, red)
        }
        d.art(art["arrow"], CGRect(x: marketArrow.minX, y: Self.sheetTop + marketArrow.minY,
                                   width: marketArrow.width, height: marketArrow.height))
    }

    /// 鎸佸崱浜虹鍚嶏紙Java:736-751锛夛細鎵嬪啓浣?+ 瓒呭鏃跺帇缂?
    ///
    /// Android 鐢?`Typeface.create("cursive")`锛沬OS 娌℃湁鍚屽悕鏃忥紝鐢?*鏂滀綋绯荤粺瀛?*鏇夸唬锛?
    /// 鍐嶆寜 `HOLDER_MAX` 鍘嬬缉銆傝繖閲岀洿鎺ヨ蛋 `CoreText`锛屽洜涓?`Draw` 鐨勬帴鍙ｄ笉甯﹀瓧浣撴棌鍙傛暟銆?
    private func drawHolder(_ d: Draw) {
        let name = GrowthStore.shared.value("holder")
        guard !name.isEmpty else { return }
        let font = UIFont.italicSystemFont(ofSize: holderSize)
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: holderInk,
        ]
        let line = CTLineCreateWithAttributedString(
            NSAttributedString(string: name, attributes: attrs))
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let squeeze = width > holderMax ? holderMax / width : 1
        d.ctx.saveGState()
        d.ctx.textMatrix = .identity
        d.ctx.translateBy(x: 0, y: Self.H)
        d.ctx.scaleBy(x: squeeze, y: -1)
        d.ctx.textPosition = CGPoint(x: holderX / squeeze, y: Self.H - holderBase)
        CTLineDraw(line, d.ctx)
        d.ctx.restoreGState()
    }

    /// 闈㈡澘搴曡竟鐨勭澶达紙Java:757-763锛?
    private func caret(_ d: Draw, cx: CGFloat, off: CGFloat) {
        let open = max(0, min(1, off / Self.panelHeight))
        let cy = 572.5 + off
        let dir = 1 - 2 * open
        d.ctx.saveGState()
        d.ctx.setStrokeColor(panelCaret.cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: cx - 19, y: cy - 10.5 * dir))
        d.ctx.addLine(to: CGPoint(x: cx, y: cy + 10.5 * dir))
        d.ctx.addLine(to: CGPoint(x: cx + 19, y: cy - 10.5 * dir))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 搴曢儴瀵艰埅锛圝ava:902-917锛?
    private func drawNav(_ d: Draw) {
        guard let nav = art["nav"] else { return }
        d.rect(CGRect(x: 0, y: Self.navTop, width: Self.W, height: Self.H - Self.navTop), .white)
        d.art(nav, CGRect(x: 0, y: Self.navTop, width: Self.W, height: Self.H - Self.navTop))
    }

    private func hit(_ rect: CGRect, _ token: ActionToken) {
        hits.append(Hit(rect: rect, token: token))
    }

    // MARK: - 鍛戒腑锛圝ava:767-900锛?

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        hits.removeAll()
        // 鍛戒腑浼氭敼椤电锛屾墍浠ヨ鑳藉湪娴嬭瘯閲岀敓鏁?
        let token = resolve(s, x: x, y: y)
        return token
    }

    private func resolve(_ s: State, x: CGFloat, y: CGFloat) -> ActionToken {
        if y >= Self.navTop { return "nav:\(nearest(navX, x))" }
        let off = unfold(s)
        let cy = y + s.scroll
        let panelBottom = Self.headerBottom + off

        if x >= eyeRect.minX - 20 && x <= eyeRect.maxX + 10
            && cy >= eyeRect.minY - 20 && cy <= eyeRect.maxY + 12 {
            return "toggleHide"
        }
        if x >= rateRect.minX && x <= rateRect.maxX
            && cy >= rateRect.minY && cy <= rateRect.maxY {
            return "gedit:rate"
        }
        let caretY = 572.5 + off
        if x > 569 && x < 709 && cy > caretY - 40 && cy < caretY + 45 { return "toggleExpand" }
        if cy >= Self.headerBottom && cy <= panelBottom {
            if cy >= panelBase[0] - 40 && cy <= panelBase[0] + 22 {
                if x >= 250 && x <= 480 { return "gedit:reserve" }
                if x >= 486 && x <= 640 { return "gedit:reserveProfit" }
                if x >= 750 && x <= 970 { return "gedit:fund" }
                if x >= 985 { return "gedit:fundProfit" }
                return "noop"
            }
            if cy >= panelBase[1] - 36 && cy <= panelBase[1] + 22 { return "noop" }
            if cy >= panelBase[3] - 36 && cy <= panelBase[3] + 22 {
                return x >= 850 ? "gedit:savings" : "noop"
            }
            if cy >= panelBase[5] - 36 && cy <= panelBase[5] + 22 {
                return (x >= 210 && x <= 460) ? "gedit:huabei" : "noop"
            }
            if cy >= panelBase[6] - 36 && cy <= panelBase[6] + 22 { return "noop" }
            return "toggleExpand"
        }
        let sheetTop = Self.sheetTop + off
        if cy >= tabBox.minY + off && cy <= sheetTop {
            selectedTab = x < 280 ? 0 : 1
            return "redraw"
        }
        let cardTop = cardBox.minY + off
        if cy >= cardTop + 12 && cy <= cardTop + 108 && x >= 950 { return "gedit:holder" }
        if cy >= cardTop + 38 && cy <= cardTop + 106 && x >= 56 && x <= 414 { return "version" }
        if cy >= cardTop && cy <= cardTop + cardBox.height { return "noop" }
        if cy >= 440 && cy <= 545 {
            if x < 300 { return "gedit:total" }
            if x > 700 && x < 1030 { return "gedit:yesterday" }
            if x > 1040 { return "gedit:family" }
        }
        if cy < sheetTop { return "version" }

        let sy = cy - off
        if selectedTab == 1 {
            if sy >= 930 && sy <= 1545 { return "noop" }
            if sy >= 1575 && sy <= 1975 { return "noop" }
            if sy >= 2005 && sy <= 2530 {
                if sy < marketRowBase - 42 { return "noop" }
                let row = min(marketNames.count - 1,
                              max(0, Int((sy - (marketRowBase - 42)) / marketRowStep)))
                return "gedit:" + (x >= 990 ? marketChanges[row] : marketPoints[row])
            }
            return "noop"
        }
        if sy >= advisor.minY - 8 && sy <= advisor.maxY + 8 {
            if sy >= advisorTalk.minY - 6 && sy <= advisorTalk.maxY + 6 {
                if x >= advisorBook.minX - 6 { return "noop" }
                if x >= advisorTalk.minX - 6 { return "noop" }
            }
            if x >= advisorAvatar.minX - 8 && x <= advisorAvatar.maxX + 8
                && sy >= advisorAvatar.minY - 8 && sy <= advisorAvatar.maxY + 8 {
                return "advisorAvatar"
            }
            if x >= advisorNameX - 8 && x <= advisorNameX + advisorNameW + 60
                && sy >= advisorNameBase - advisorNameSize && sy <= advisorNameBase + 14 {
                return "advisorName"
            }
            return "noop"
        }
        let my = sy - Self.marketInsert
        if my >= Self.sheetTop + marketCard.minY && my <= Self.sheetTop + marketCard.maxY {
            for i in stride(from: marketKeys.count - 1, through: 0, by: -1) {
                if x >= marketValueX[i] - 20 { return "gedit:" + marketKeys[i] }
            }
            return "noop"
        }
        if my >= Self.sheetTop + gridTop && my < Self.sheetTop + marketCard.minY { return "noop" }
        if my >= fundTag.minY - 6 && my <= fundTag.maxY + 16 {
            if x >= 60 && x <= 292 { return "gedit:fundSelfPct" }
            if x > 292 && x <= 540 { return "gedit:fundSelfTag" }
        }
        if my >= rank1Base - 52 && my <= rank1Base + 24 && x >= 990 { return "gedit:rankPct0" }
        if my >= rank2Base - 52 && my <= rank2Base + 24 && x >= 990 { return "gedit:rankPct1" }
        if my >= mktBase - 42 && my <= mktBase + 20 && x >= 440 && x <= 610 { return "gedit:windMove" }
        if my >= allocBase - 42 && my <= allocBase + 20 && x >= 630 && x <= 800 { return "gedit:allocPct" }
        if my >= fundsTop && my <= fundsTop + fundsH { return "noop" }
        if my >= discoverTop && my <= discoverTop + discoverH { return "noop" }
        if my >= bannerTop && my <= bannerTop + bannerH { return "noop" }
        return "noop"
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

private extension UIImage {
    /// 鎸?alpha 娑傛垚涓€绉嶉鑹诧紙Android 鐨?`PorterDuff.Mode.SRC_IN`锛?
    func tinted(_ color: UIColor) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            let rect = CGRect(origin: .zero, size: size)
            color.setFill()
            UIRectFill(rect)
            draw(in: rect, blendMode: .destinationIn, alpha: 1)
        }
    }
}
