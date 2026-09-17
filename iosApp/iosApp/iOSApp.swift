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

/// Hands the Live Activity's two Swift halves to Kotlin.
///
/// Like the BGTask handlers above, this has to happen before `startKoinIos()`: that starts the
/// notification coordinator, and a timer left running by a previous launch reaches the controller
/// immediately — before the app has a window, let alone a screen.
enum LiveActivitySetup {

    static func register() {
        LiveActivityRegistry.shared.register(bridge: LiveActivityBridgeImpl())

        TimerIntentBridge.shared.handler = { pause in
            await withCheckedContinuation { continuation in
                if pause {
                    KoinHelperKt.onTimerNotificationPause { continuation.resume() }
                } else {
                    KoinHelperKt.onTimerNotificationResume { continuation.resume() }
                }
            }
        }
    }
}

@main
struct iOSApp: App {

    init() {
        // Order matters: handlers first, then Koin — see BackgroundTaskSetup above.
        BackgroundTaskSetup.registerHandlers()
        LiveActivitySetup.register()
        KoinHelperKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
