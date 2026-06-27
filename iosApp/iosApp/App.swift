import SwiftUI

struct AppMain: App {
    // Bind SwiftUI lifecycle to AppDelegate
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

// Swift Main Launcher
@main
struct AppLauncher {
    static func main() {
        if #available(iOS 16.0, *) {
            AppMain.main()
        } else {
            UIApplicationMain(
                CommandLine.argc,
                CommandLine.argv,
                nil,
                NSStringFromClass(AppDelegate.self)
            )
        }
    }
}
