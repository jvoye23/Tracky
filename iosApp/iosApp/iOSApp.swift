import SwiftUI
import BackgroundTasks
import ComposeApp

/// Installs the BGTaskScheduler launch handlers for every identifier the Kotlin schedulers submit.
///
/// This must run before `startKoinIos()`, which immediately submits both requests from a coroutine:
/// submitting an identifier that is in `BGTaskSchedulerPermittedIdentifiers` but has no handler
/// raises `NSInternalInconsistencyException` and terminates the app. Registering from
/// `iOSApp.init()` is early enough — SwiftUI calls it before `didFinishLaunchingWithOptions`
/// returns, which is the deadline Apple actually imposes.
enum BackgroundTaskSetup {

    private static var didRegister = false

    static func registerHandlers() {
        // BGTaskScheduler.register raises if the same identifier is registered twice.
        guard !didRegister else { return }
        didRegister = true

        register(IosTrashCleanupScheduler.companion.TASK_IDENTIFIER) { task in
            KoinHelperKt.runTrashCleanup { success in
                task.setTaskCompleted(success: success.boolValue)
            }
        }

        register(IosSyncScheduler.companion.TASK_IDENTIFIER) { task in
            KoinHelperKt.runSync { success in
                task.setTaskCompleted(success: success.boolValue)
            }
        }
    }

    private static func register(_ identifier: String, run: @escaping (BGTask) -> Void) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            task.expirationHandler = {
                task.setTaskCompleted(success: false)
            }
            run(task)
        }
        BackgroundTaskRegistry.shared.markRegistered(identifier: identifier)
    }
}

@main
struct iOSApp: App {

    init() {
        // Order matters: handlers first, then Koin — see BackgroundTaskSetup above.
        BackgroundTaskSetup.registerHandlers()
        KoinHelperKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
