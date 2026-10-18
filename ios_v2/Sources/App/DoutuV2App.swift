import SwiftUI

@main
struct DoutuV2App: App {
    var body: some Scene {
        WindowGroup {
            ControlView()
                .ignoresSafeArea()
                .statusBarHidden(true)
                .preferredColorScheme(.light)
                .onAppear {
                    Router.shared = Router()
                    CrashLog.append("=== Doutu v2 launch ===")
                }
        }
    }
}
