import UIKit
import CoreText

/// 字体工具。
///
/// Android 那边把 `assets/fonts/zhi_asset_number.ttf`（实测是 **Archivo SemiCondensed Semibold**）
/// 当作「数字字体」：参考图里的金额、数字都用它，而汉字和「¥」用系统字体。
/// 一行字里两种字体混排，数字就比汉字窄一号 —— 这正是参考图的样子。
///
/// iOS 侧把这支字体原样做进包里，并在**运行时注册**（这样不用改 Info.plist 的 UIAppFonts，
/// 也就不会和 XcodeGen 里那批 INFOPLIST_KEY_* 设置打架）。
enum Fonts {
    /// 注册成功后可用的 PostScript 名
    static let numberFamily = "ArchivoSemiCondensed-Semibold"

    private static let registered: Bool = {
        guard let url = Bundle.main.url(forResource: numberFamily, withExtension: "ttf",
                                       subdirectory: "Fonts")
            ?? Bundle.main.url(forResource: numberFamily, withExtension: "ttf") else {
            return false
        }
        return CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    /// 数字字体；取不到就退回系统半粗体（宁可字宽略有差异，也不能崩）
    static func number(_ size: CGFloat) -> UIFont {
        _ = registered
        return UIFont(name: numberFamily, size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .semibold)
    }

    /// 汉字 / 符号用系统字体
    static func system(_ size: CGFloat, bold: Bool = false) -> UIFont {
        bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size)
    }

    /// 这个字符该用数字字体吗（数字、点、逗号、减号、百分号等）
    static func usesNumberFace(_ ch: Character) -> Bool {
        ch.isNumber || ".,-+%¥".contains(ch)
    }

    /// 混排宽度：逐字符挑字体累加（对应 Android 的 `mixedWidth`）
    static func mixedWidth(_ text: String, size: CGFloat, bold: Bool) -> CGFloat {
        var total: CGFloat = 0
        for ch in text {
            let font = usesNumberFace(ch) ? number(size) : system(size, bold: bold)
            total += String(ch).size(withAttributes: [.font: font]).width
        }
        return total
    }
}
