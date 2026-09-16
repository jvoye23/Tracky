import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

/// The running timer, on the Lock Screen and in the Dynamic Island.
///
/// The widget never reaches Kotlin — see `TrackyTimerAttributes`. Everything it draws comes from
/// the activity's content state, and the button hands back a `TimerToggleIntent` that iOS performs
/// in the app.
struct TrackyTimerLiveActivity: Widget {

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: TrackyTimerAttributes.self) { context in
            LockScreenView(attributes: context.attributes, state: context.state)
                .activityBackgroundTint(nil)
        } dynamicIsland: { context in
            let accent = ProjectAccent.color(argb: context.attributes.accentArgb)

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.attributes.projectTitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        Text(context.state.taskTitle)
                            .font(.headline)
                            .lineLimit(1)
                        if let subTaskTitle = context.state.subTaskTitle {
                            Text(subTaskTitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ToggleButton(state: context.state, attributes: context.attributes)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ClockView(state: context.state)
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .foregroundStyle(accent)
                }
            } compactLeading: {
                Image(systemName: context.state.isRunning ? "stopwatch" : "pause.circle")
                    .foregroundStyle(accent)
            } compactTrailing: {
                ClockView(state: context.state)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(accent)
            } minimal: {
                Image(systemName: context.state.isRunning ? "stopwatch" : "pause.circle")
                    .foregroundStyle(accent)
            }
        }
    }
}

/// The card in `Requirements/DesignRequirements/tracky_notification.png`: project line, task line,
/// optional subtask line, then the clock and the button side by side.
private struct LockScreenView: View {

    let attributes: TrackyTimerAttributes
    let state: TrackyTimerAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(attributes.projectTitle)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)

            VStack(alignment: .leading, spacing: 2) {
                Text(state.taskTitle)
                    .font(.title3.weight(.bold))
                    .lineLimit(1)
                if let subTaskTitle = state.subTaskTitle {
                    Text(subTaskTitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            HStack(alignment: .center) {
                ClockView(state: state)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(ProjectAccent.color(argb: attributes.accentArgb))
                    // Timer text is given the width before the button and the spacer, so nothing can
                    // squeeze it. Also not the cause of the simulator's "--" seconds, but it does
                    // rule the layout out as one.
                    .layoutPriority(1)

                Spacer(minLength: 8)

                ToggleButton(state: state, attributes: attributes)
            }
        }
        .padding(16)
    }
}

/// The clock.
///
/// While running this is `Text(timerInterval:)`, which counts up by itself from the start date the
/// Kotlin side derived — so a running card is drawn once and never updated again. Frozen, it is a
/// plain string, because a paused clock must not tick.
private struct ClockView: View {

    /// The range's end is what iOS sizes the text against, not the value on screen, so it is bounded
    /// rather than left at `distantFuture` - a day is far past the ~8 hours iOS lets a Live Activity
    /// survive, and holds the reservation at h:mm:ss.
    ///
    /// This did **not** turn out to be why the simulator renders the seconds as "--"; bounding it
    /// changed nothing there. It is kept because reserving width for a duration no timer can reach
    /// is wrong on its own terms, not because it fixes that.
    private static let longestPlausibleRun: TimeInterval = 24 * 60 * 60

    let state: TrackyTimerAttributes.ContentState

    var body: some View {
        Group {
            if state.isRunning {
                Text(
                    timerInterval: state.startedAt...state.startedAt.addingTimeInterval(Self.longestPlausibleRun),
                    countsDown: false
                )
            } else {
                Text(Duration.trackyClock(seconds: state.elapsedSeconds))
            }
        }
        .monospacedDigit()
    }
}

/// Pause while running, Play while frozen — the same pill as the Android notification's button.
private struct ToggleButton: View {

    let state: TrackyTimerAttributes.ContentState
    let attributes: TrackyTimerAttributes

    var body: some View {
        Button(intent: TimerToggleIntent(pause: state.isRunning)) {
            Label(
                state.isRunning ? "Pause" : "Play",
                systemImage: state.isRunning ? "pause.fill" : "play.fill"
            )
            .font(.subheadline.weight(.bold))
            .foregroundStyle(ProjectAccent.onAccent(useLightTextColor: attributes.useLightTextColor))
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
        }
        .background(ProjectAccent.color(argb: attributes.accentArgb), in: Capsule())
        .buttonStyle(.plain)
    }
}
