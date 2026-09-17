import ActivityKit
import ComposeApp
import Foundation

/// The Swift half of the timer notification: the Kotlin controller decides *what* to show, this
/// puts it on the Lock Screen and in the Dynamic Island.
///
/// Kotlin calls in on the main thread — `IosTimerNotificationController` guarantees it, because
/// ActivityKit is main-thread only.
final class LiveActivityBridgeImpl: NSObject, LiveActivityBridge {

    private var activity: Activity<TrackyTimerAttributes>?

    func show(state: LiveActivityState) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        let content = ActivityContent(
            state: state.contentState,
            // The clock ticks locally, so nothing here goes stale in the way a pushed score would.
            staleDate: nil
        )

        let attributes = state.attributes

        // Two reasons the activity in hand may be unusable. iOS ends one on its own after about
        // eight hours and the user can swipe it away, which leaves a handle that silently swallows
        // updates; and attributes are fixed for an activity's life, so moving to another project
        // cannot be an update either. Both mean: end it and request a fresh one.
        if let live = activity,
           live.activityState == .active || live.activityState == .stale,
           live.attributes == attributes {
            Task { await live.update(content) }
            return
        }

        endCurrentActivity()
        activity = try? Activity.request(attributes: attributes, content: content, pushType: nil)
    }

    func dismiss() {
        endCurrentActivity()
    }

    private func endCurrentActivity() {
        guard let live = activity else { return }
        activity = nil
        Task { await live.end(nil, dismissalPolicy: .immediate) }
    }
}

private extension LiveActivityState {

    var attributes: TrackyTimerAttributes {
        TrackyTimerAttributes(
            projectId: projectId,
            projectTitle: projectTitle,
            accentArgb: accentArgb,
            useLightTextColor: useLightTextColor
        )
    }

    var contentState: TrackyTimerAttributes.ContentState {
        TrackyTimerAttributes.ContentState(
            taskTitle: taskTitle,
            subTaskTitle: subTaskTitle,
            startedAt: Date(timeIntervalSince1970: startedAtEpochSeconds),
            elapsedSeconds: elapsedSeconds,
            isRunning: isRunning
        )
    }
}
