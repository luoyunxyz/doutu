import SwiftUI
import UIKit

final class DoutuAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        if let url = launchOptions?[.url] as? URL {
            DeepLinkCenter.handle(url)
        }
        return true
    }

    func application(_ app: UIApplication, open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        DeepLinkCenter.handle(url)
        return true
    }
}

enum DeepLinkCenter {
    static var pending: URL?

    static func handle(_ url: URL) {
        if let router = Router.shared {
            router.handleDeepLink(url)
        } else {
            pending = url
        }
    }

    static func flush(_ router: Router) {
        guard let pending else { return }
        self.pending = nil
        router.handleDeepLink(pending)
    }
}

@main
struct DoutuV2App: App {
    @Environment(\.scenePhase) private var scenePhase
    @UIApplicationDelegateAdaptor(DoutuAppDelegate.self) private var appDelegate

    init() {
        Session.shared.prepareFreshInstall()
    }

    var body: some Scene {
        WindowGroup {
            ScreenHostContainer(screen: UserDefaults.standard.bool(forKey: "terms.accepted")
                                ? HomeV2Screen()
                                : TermsScreen())
                .ignoresSafeArea()
                .statusBarHidden(true)
                .onAppear { UpdatePrompt.shared.maybeCheck() }
                .onOpenURL { url in
                    // onOpenURL 的闭包不一定在主 actor 上，Router 是 @MainActor。
                    Task { @MainActor in
                        Router.shared?.handleDeepLink(url)
                    }
                }
                .onChange(of: scenePhase) { phase in
                    if phase == .active { UpdatePrompt.shared.maybeCheck() }
                }
        }
    }
}
// build trigger
