import SwiftUI

@main
struct iosApp: App {
    // Bind SwiftUI lifecycle to AppDelegate
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
