import SwiftUI
import FirebaseCore

@main
struct iOSApp: App {
    /// `App()` reaches for `Firebase.auth` on its first frame, so this has to have
    /// run before any Compose content exists — hence `init`, not `onAppear`.
    /// Without `GoogleService-Info.plist` in the bundle this traps on launch,
    /// which is the loud failure we want: a silently Firebase-less app looks fine
    /// until it quietly stops syncing.
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
