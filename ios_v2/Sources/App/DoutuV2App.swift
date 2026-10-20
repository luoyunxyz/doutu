import SwiftUI

@main
struct DoutuV2App: App {
    @Environment(\.scenePhase) private var scenePhase

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
