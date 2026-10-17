import Foundation
import UIKit

/// 启动/崩溃自诊断。
///
/// 为什么需要它：这台 iPhone（iOS 14.3）上装好后**一点就闪退**，而系统的
/// 「设置 → 隐私 → 分析与改进 → 分析数据」里没有生成 Doutu 开头的崩溃报告，
/// 拿不到 `Exception Type` / `Termination Reason`，只能靠 App 自己记。
///
/// 做法：
/// 1. 启动第一行就写一条 `=== LAUNCH ===` 到 Documents/crash.log
///    —— **日志里有这一行**说明 Swift 代码跑起来了（问题在业务/UI）；
///    **没有这一行**说明连 `@main` 都没进来（dyld 加载动态库失败 / 启动即被系统杀掉）。
/// 2. 装 `NSSetUncaughtExceptionHandler` 抓 OC 异常（如 NSInvalidArgumentException）。
/// 3. 装 signal handler 抓 SIGABRT/SIGSEGV 这类崩溃（Swift 运行时错误、野指针等）。
///
/// 配合 Info.plist 里的 `UIFileSharingEnabled`，日志可以直接在「文件」App 里看到：
/// 文件 → 我的 iPhone → 斗图去水印 → crash.log
///
/// ⚠️ 这是**排障期临时保留**的组件：为了能取到现场，日志会一直追加。
/// 正式发布前可以删掉这个文件和 DoutuApp.init() 里的调用。
enum CrashLog {
    static var fileURL: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("crash.log")
    }

    /// 追加一行（失败也不抛，绝不能因为记日志把 App 搞崩）
    static func append(_ text: String) {
        let stamp = ISO8601DateFormatter().string(from: Date())
        guard let data = "\(stamp) \(text)\n".data(using: .utf8) else { return }
        if let handle = try? FileHandle(forWritingTo: fileURL) {
            handle.seekToEndOfFile()
            handle.write(data)
            try? handle.close()
        } else {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    /// 在 App 启动最早时机调用
    static func install() {
        let bundle = Bundle.main
        let version = bundle.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = bundle.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        let system = "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)"
        append("=== LAUNCH v\(version)(\(build)) \(system) \(UIDevice.current.model) ===")

        NSSetUncaughtExceptionHandler { exception in
            let stack = exception.callStackSymbols.joined(separator: "\n")
            CrashLog.append("UNCAUGHT \(exception.name.rawValue): \(exception.reason ?? "-")\n\(stack)")
        }

        let signals: [Int32] = [SIGABRT, SIGILL, SIGSEGV, SIGFPE, SIGBUS, SIGTRAP]
        for sig in signals {
            signal(sig) { received in
                // signal handler 里只能做最简单的写入，写在栈上字符串常量
                CrashLog.append("SIGNAL received: \(received)")
                exit(received)
            }
        }
    }
}
