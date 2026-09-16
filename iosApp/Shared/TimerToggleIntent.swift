import AppIntents
import Foundation

/// Pause and Play on the Live Activity.
///
/// `LiveActivityIntent` is the reason the widget never has to reach Kotlin: iOS performs this kind
/// of intent **in the app's process**, not the extension's. The type is compiled into both binaries
/// so the widget's button can name it, but the copy that actually runs is the app's — and the app
/// is where the handler below gets installed.
struct TimerToggleIntent: LiveActivityIntent {

    static var title: LocalizedStringResource = "Pause or resume the timer"
    static var isDiscoverable: Bool = false

    /// True pauses the running timer, false resumes the paused one.
    @Parameter(title: "Pause")
    var pause: Bool

    init() {}

    init(pause: Bool) {
        self.pause = pause
    }

    func perform() async throws -> some IntentResult {
        await TimerIntentBridge.shared.toggle(pause: pause)
        return .result()
    }
}

/// Where the app installs what [TimerToggleIntent] should actually do.
///
/// Nil in the widget process, which never performs the intent. The app sets it in `iOSApp.init()`,
/// alongside the Live Activity bridge, and only that closure touches Kotlin.
final class TimerIntentBridge: @unchecked Sendable {

    static let shared = TimerIntentBridge()

    private init() {}

    /// Awaits the write, so the card is not redrawn from state the repository has not caught up to.
    var handler: ((Bool) async -> Void)?

    func toggle(pause: Bool) async {
        await handler?(pause)
    }
}
