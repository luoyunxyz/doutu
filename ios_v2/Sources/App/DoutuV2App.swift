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
                .onChange(of: scenePhase) { phase in
                    if phase == .active { UpdatePrompt.shared.maybeCheck() }
                }
        }
    }
}
// build trigger
