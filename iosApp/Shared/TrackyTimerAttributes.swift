import ActivityKit
import Foundation

/// The shape of the running-timer Live Activity.
///
/// This file belongs to **both** the app and the widget targets: the app requests and updates the
/// activity, the widget draws it, and ActivityKit matches the two by this type. Neither half may
/// import ComposeApp — the Kotlin framework is static, and linking it into an extension would copy
/// the whole Kotlin runtime into a binary with a far smaller memory budget than the app's.
///
/// So the Kotlin `LiveActivityState` is translated into this on the app side, and the widget never
/// learns that Kotlin exists.
/// Equatable so the app can tell "same timer, new reading" from "different project": attributes are
/// fixed for an activity's life, so the second case has to end the activity and request another.
struct TrackyTimerAttributes: ActivityAttributes, Equatable {

    /// What does not change while one timer runs. A different project or task ends the activity and
    /// starts a new one.
    let projectId: String
    let projectTitle: String
    /// The project's own colour, ARGB, as Kotlin stores it.
    let accentArgb: Int32
    /// Whether that colour needs light text on top. The project already knows; see the task cards.
    let useLightTextColor: Bool

    struct ContentState: Codable, Hashable {
        let taskTitle: String
        /// Nil when the task itself is being timed rather than one of its subtasks.
        let subTaskTitle: String?
        /// When the clock would have started to read `elapsedSeconds` now. `Text(timerInterval:)`
        /// counts up from this on its own, so a running card needs no further updates.
        let startedAt: Date
        /// What a frozen card reads: the whole task or subtask lifetime, banked intervals included.
        let elapsedSeconds: Double
        /// False after Pause — the clock stops where it is and the button offers Play.
        let isRunning: Bool
    }
}
