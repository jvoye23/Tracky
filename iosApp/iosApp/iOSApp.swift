import SwiftUI
import BackgroundTasks
import ComposeApp

private let trashCleanupTaskIdentifier = "com.jvcs.tracky.trashcleanup"

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        // Register the trash-cleanup handler before launch finishes, as required by BGTaskScheduler.
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: trashCleanupTaskIdentifier,
            using: nil
        ) { task in
            task.expirationHandler = {
                task.setTaskCompleted(success: false)
            }
            KoinHelperKt.runTrashCleanup { success in
                task.setTaskCompleted(success: success.boolValue)
            }
        }

        return true
    }
}


@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        KoinHelperKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
