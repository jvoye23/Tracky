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
            // Liquid Glass, and only here — the Island draws itself on the hardware's own black pill
            // and must not have a material laid over it.
            //
            // The tint is `.clear` rather than `nil`: `nil` asks for ActivityKit's *default*
            // material, which is the flat dark card this replaces. `.clear` keeps the system from
            // stacking anything opaque underneath.
            //
            // The glass sits in `.background` rather than modifying the card directly. Applied to
            // the card, `.glassEffect` rendered its material but swallowed the card — a widget is
            // drawn out of process, and the effect does not composite content there the way it does
            // in an app. Behind the card it cannot eat what it is meant to sit under, and if the
            // effect no-ops entirely the system's own glass container still shows through the
            // `.clear` tint. `ContainerRelativeShape` inherits the container's corner radius rather
            // than guessing at one.
            TimerCard(attributes: context.attributes, state: context.state)
                .background {
                    Color.clear.glassEffect(.clear, in: ContainerRelativeShape())
                }
                .activityBackgroundTint(.clear)
                .widgetURL(TimerDeepLink.url(projectId: context.attributes.projectId))
        } dynamicIsland: { context in
            let accent = ProjectAccent.color(argb: context.attributes.accentArgb)

            return DynamicIsland {
                // Everything goes in .bottom, which is the only full-width region. Splitting the
                // header across .leading/.trailing looks closer to the mock on paper, but those two
                // flank the camera cutout and are barely 100pt wide — the project title truncated to
                // "Track…" there. .center stays unused.
                DynamicIslandExpandedRegion(.bottom) {
                    TimerCard(attributes: context.attributes, state: context.state, compact: true)
                }
            } compactLeading: {
                // Unpadded, because the pill has no width to spend on leading zeros: `14:54`, not
                // `00:14:54`. `ClockView` clamps itself to exactly those glyphs, which is what stops
                // the greedy `Text(timerInterval:)` from stretching the pill out.
                ClockView(state: context.state, fontSize: 13, weight: .semibold, padded: false)
                    .foregroundStyle(accent)
            } compactTrailing: {
                // The same accent glyph `minimal` uses — the project's colour is the only thing
                // identifying whose timer this is at a glance.
                Image(systemName: context.state.isRunning ? "stopwatch" : "pause.circle")
                    .foregroundStyle(accent)
            } minimal: {
                Image(systemName: context.state.isRunning ? "stopwatch" : "pause.circle")
                    .foregroundStyle(accent)
            }
            // Only the *inner* edges lose their inset — the ones facing the camera cutout, where the
            // padding does nothing but widen the pill. The outer edges keep the system's margin on
            // purpose: zeroing `.horizontal` here put the clock and the stopwatch hard against the
            // pill's rounded ends, where its mask clipped both of them in half.
            .contentMargins(.trailing, 0, for: .compactLeading)
            .contentMargins(.leading, 0, for: .compactTrailing)
            .widgetURL(TimerDeepLink.url(projectId: context.attributes.projectId))
        }
    }
}

/// The card in `Requirements/DesignRequirements/DynamicIsland_expanded.png`, which the Lock Screen
/// and the expanded Dynamic Island now share: the header row, then the clock and the toggle centred
/// beneath it. The card draws no background of its own: the Island supplies the hardware's black
/// pill, and the Lock Screen lays it over Liquid Glass — see `TrackyTimerLiveActivity.body`.
private struct TimerCard: View {

    let attributes: TrackyTimerAttributes
    let state: TrackyTimerAttributes.ContentState
    /// The Island has far less height to spend than the Lock Screen, so it draws the same card a
    /// size down rather than a different card.
    var compact: Bool = false

    private var accent: Color { ProjectAccent.color(argb: attributes.accentArgb) }
    private var clockSize: CGFloat { compact ? 30 : 34 }

    var body: some View {
        VStack(spacing: compact ? 0 : 6) {
            HStack(alignment: .top) {
                TimerHeader(attributes: attributes, state: state, compact: compact)
                Spacer(minLength: 8)
                AccentStopwatch(accent: accent)
            }

            ClockView(state: state, fontSize: clockSize)
                .foregroundStyle(accent)
                // `ClockView` has already clamped itself to its glyphs; this outer frame is only
                // what centres that clamped box in the card.
                .frame(maxWidth: .infinity)

            // Pause is stop-then-start, so pausing a timer another device is running would stop
            // it globally. The app refuses that either way; hiding the button is how the user
            // finds out, rather than tapping something that does nothing. One conditional covers
            // both surfaces, because this card is the Lock Screen and the expanded Island alike.
            if !state.isForeign {
                ToggleGlyph(state: state, size: compact ? 28 : 34)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(compact ? 0 : 16)
    }
}

/// The design's left-hand block: app icon, then the project over whatever is being timed.
///
/// The project is the bold line and the task the quiet one — the reverse of the older
/// `tracky_notification.png`. Colours are semantic rather than the mock's literal white and grey, so
/// the same view stays readable on the Lock Screen's light material as well as the always-dark Island.
private struct TimerHeader: View {

    let attributes: TrackyTimerAttributes
    let state: TrackyTimerAttributes.ContentState
    var compact: Bool = false

    private var side: CGFloat { compact ? 28 : 34 }

    var body: some View {
        HStack(spacing: 8) {
            Image("TrackyIcon")
                .resizable()
                .frame(width: side, height: side)
                // The source art is a full-bleed square, so the rounding has to happen here.
                .clipShape(RoundedRectangle(cornerRadius: side / 4.25, style: .continuous))

            VStack(alignment: .leading, spacing: 0) {
                Text(attributes.projectTitle)
                    .font(compact ? .subheadline.weight(.semibold) : .headline)
                    .lineLimit(1)
                // Whatever is actually being timed. A subtask wins the row outright — the parent
                // task's name is not shown while one of its subtasks runs, which keeps the header at
                // two rows on both surfaces and leaves the Island the height for a larger glyph.
                Text(state.subTaskTitle ?? state.taskTitle)
                    .font(compact ? .caption : .subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }
}

/// The accent stopwatch the design pins to the top-right corner.
private struct AccentStopwatch: View {

    let accent: Color

    var body: some View {
        Image(systemName: "stopwatch")
            .font(.title2)
            .foregroundStyle(accent)
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

    /// The measured advance of one rounded monospaced-digit character, as a fraction of the font
    /// size. It is `4.9 / 8` — the old card clamped its eight padded characters at `fontSize * 4.9`,
    /// so per-character sizing reproduces that exactly while also sizing the shorter compact forms.
    private static let characterWidth: CGFloat = 0.6125

    let state: TrackyTimerAttributes.ContentState
    let fontSize: CGFloat
    var weight: Font.Weight = .bold
    /// The card pads to a full `HH:mm:ss`; the compact pill cannot afford to — see `pad`.
    var padded: Bool = true

    /// What the clock reads *now*, not when the content state was last pushed.
    ///
    /// `pad` below depends on the magnitude, and the magnitude changes as the timer runs, so reading
    /// the frozen `elapsedSeconds` would pick a prefix that was right minutes ago. This is
    /// re-evaluated on every render — iOS redraws the card on lock, unlock and each Island
    /// expand/collapse — so the padding is correct whenever the card is actually drawn.
    private var liveElapsed: TimeInterval {
        state.isRunning ? Date().timeIntervalSince(state.startedAt) : state.elapsedSeconds
    }

    /// The design wants `02:14:54`, but `Text(timerInterval:)` drops leading zeros at every level and
    /// offers no padding option — `showsHours:` only decides whether the hours appear at all. So the
    /// zeros are drawn as their own `Text` and butted against the ticking one with zero spacing.
    ///
    /// How many are needed depends on the magnitude, all of it confirmed in the canvas:
    ///
    ///     < 10 min   iOS renders  1:33     + "00:0"  ->  00:01:33
    ///     < 1 h                  14:54     + "00:"   ->  00:14:54
    ///     < 10 h                2:16:09    + "0"     ->  02:16:09
    ///     otherwise            10:16:09    + nothing ->  10:16:09
    ///
    /// Padded, the result is always eight characters, which is what lets the clock be centred at all
    /// — see `body`. A card left on screen while the timer crosses 10 minutes or an hour keeps the
    /// old prefix until the next render; that is the accepted cost of never pushing an update.
    ///
    /// Unpadded — the compact pill — this is instead the prefix that is *dropped*, so the pill reads
    /// `14:54` and stays as narrow as the timer's magnitude allows.
    private var pad: String? {
        switch liveElapsed {
        case ..<600: "00:0"
        case ..<3600: "00:"
        case ..<36000: "0"
        default: nil
        }
    }

    /// How many glyphs are on screen: eight when padded, otherwise eight less whatever `pad` would
    /// have contributed. Exact, because `pad` is by construction the prefix the padded form carries.
    private var characterCount: Int {
        padded ? 8 : 8 - (pad?.count ?? 0)
    }

    /// `Duration.trackyClock` is always `%02d:%02d:%02d`, so a padded frozen clock needs no help.
    /// Unpadded, stripping `pad` from the front yields exactly the form the running clock would show
    /// at the same magnitude — which is what keeps the pill from resizing when the timer is paused.
    private var frozenText: String {
        let full = Duration.trackyClock(seconds: state.elapsedSeconds)
        return padded ? full : String(full.dropFirst(pad?.count ?? 0))
    }

    var body: some View {
        Group {
            if state.isRunning {
                HStack(spacing: 0) {
                    if padded, let pad {
                        Text(pad)
                    }
                    Text(
                        timerInterval: state.startedAt...state.startedAt.addingTimeInterval(Self.longestPlausibleRun),
                        countsDown: false,
                        showsHours: true
                    )
                }
            } else {
                Text(frozenText)
            }
        }
        .font(.system(size: fontSize, weight: weight, design: .rounded))
        .monospacedDigit()
        // Sizing the clock takes this clamp, and not for want of simpler attempts.
        // `Text(timerInterval:)` takes every point it is offered rather than wrapping, so left alone
        // it fills whatever it is put in and sits hard left; `.fixedSize` does stop that but blanks
        // the running card outright — do not reapply it. Instead, offer it only about as much width
        // as its glyphs need and let it fill that.
        .frame(maxWidth: fontSize * Self.characterWidth * CGFloat(characterCount))
    }
}

/// Pause while running, Play while frozen — a bare glyph, as the design asks: no capsule, no label.
///
/// Losing the label also retires the bug the previews caught, where the running clock's width
/// reservation squeezed the old pill until `Label` dropped its title and left an icon anyway.
/// The padding is hit target, not decoration: the glyph is small and the whole button must stay
/// comfortably tappable.
private struct ToggleGlyph: View {

    let state: TrackyTimerAttributes.ContentState
    let size: CGFloat

    var body: some View {
        Button(intent: TimerToggleIntent(pause: state.isRunning)) {
            Image(systemName: state.isRunning ? "pause.fill" : "play.fill")
                .font(.system(size: size))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(state.isRunning ? "Pause" : "Play")
    }
}

// MARK: - Previews

// The canvas is the only fast way to tune this layout: the alternative is rebuild, reinstall, start
// a timer, look, repeat. They live in this file rather than beside it because the three views above
// are private — from anywhere else only the whole widget could be previewed.
#if DEBUG

private extension TrackyTimerAttributes {

    /// Smart Stepper's real colour from the database. Kotlin packs ARGB into an `Int`, so an opaque
    /// colour arrives with the alpha byte set and therefore negative — these are the literal values
    /// the bridge hands over, not tidied-up ones, so the preview exercises the same unpacking.
    /// 0xFF165DDF: dark enough that the pill wants light text.
    static let previewCool = TrackyTimerAttributes(
        projectId: "preview-project",
        projectTitle: "Tracky App Redesign",
        accentArgb: -15311393,
        useLightTextColor: true
    )

    /// The mock's orange, 0xFFF3A023 — the accent from `tracky_notification.png`. Kept so a warm and
    /// a cool project can be compared side by side in the canvas, since the accent now tints the
    /// clock and the stopwatch rather than filling a pill.
    static let previewWarm = TrackyTimerAttributes(
        projectId: "preview-project",
        projectTitle: "Tracky App Redesign",
        accentArgb: -810973,
        useLightTextColor: false
    )
}

private extension TrackyTimerAttributes.ContentState {

    /// 02:16:09 in, the reading the design mock shows.
    private static let mockElapsed: TimeInterval = 2 * 3600 + 16 * 60 + 9

    static let running = TrackyTimerAttributes.ContentState(
        taskTitle: "Token refresh",
        subTaskTitle: "Auth endpoints",
        startedAt: .now.addingTimeInterval(-mockElapsed),
        elapsedSeconds: mockElapsed,
        isRunning: true
    )

    /// The only state that renders `HH:mm:ss` — frozen, the clock is a plain string, so this is
    /// where the zero-padded format and the Play label can be checked.
    static let paused = TrackyTimerAttributes.ContentState(
        taskTitle: "Token refresh",
        subTaskTitle: "Auth endpoints",
        startedAt: .now.addingTimeInterval(-mockElapsed),
        elapsedSeconds: mockElapsed,
        isRunning: false
    )

    /// A task timed directly drops the subtask line, which changes the card's height.
    static let noSubtask = TrackyTimerAttributes.ContentState(
        taskTitle: "Token refresh",
        subTaskTitle: nil,
        startedAt: .now.addingTimeInterval(-mockElapsed),
        elapsedSeconds: mockElapsed,
        isRunning: true
    )

    /// Under ten minutes, where iOS drops a digit from the minutes too. Must read 00:01:33 — this is
    /// the state that proves the `"00:0"` branch rather than the `"00:"` one.
    static let veryShortRun = TrackyTimerAttributes.ContentState(
        taskTitle: "Token refresh",
        subTaskTitle: "Auth endpoints",
        startedAt: .now.addingTimeInterval(-93),
        elapsedSeconds: 93,
        isRunning: true
    )

    /// Under an hour, which is the case the zero-padding has to get right: this must read 00:14:54,
    /// not 14:54 and not 0:14:54.
    static let shortRun = TrackyTimerAttributes.ContentState(
        taskTitle: "Token refresh",
        subTaskTitle: "Auth endpoints",
        startedAt: .now.addingTimeInterval(-894),
        elapsedSeconds: 894,
        isRunning: true
    )

    /// Real titles are longer than the mock's. This is what the `lineLimit(1)` truncation actually
    /// looks like, and how little room the clock leaves the Dynamic Island's leading region.
    static let longTitles = TrackyTimerAttributes.ContentState(
        taskTitle: "Refresh the OAuth access token before every request",
        subTaskTitle: "Authentication endpoints and the retry interceptor",
        startedAt: .now.addingTimeInterval(-mockElapsed),
        elapsedSeconds: mockElapsed,
        isRunning: true
    )
}

#Preview("Lock Screen", as: .content, using: TrackyTimerAttributes.previewCool) {
    TrackyTimerLiveActivity()
} contentStates: {
    TrackyTimerAttributes.ContentState.running
    TrackyTimerAttributes.ContentState.veryShortRun
    TrackyTimerAttributes.ContentState.shortRun
    TrackyTimerAttributes.ContentState.paused
    TrackyTimerAttributes.ContentState.noSubtask
    TrackyTimerAttributes.ContentState.longTitles
}

#Preview("Lock Screen — warm accent", as: .content, using: TrackyTimerAttributes.previewWarm) {
    TrackyTimerLiveActivity()
} contentStates: {
    TrackyTimerAttributes.ContentState.running
    TrackyTimerAttributes.ContentState.paused
}

#Preview("Island — expanded", as: .dynamicIsland(.expanded), using: TrackyTimerAttributes.previewCool) {
    TrackyTimerLiveActivity()
} contentStates: {
    TrackyTimerAttributes.ContentState.running
    TrackyTimerAttributes.ContentState.veryShortRun
    TrackyTimerAttributes.ContentState.shortRun
    TrackyTimerAttributes.ContentState.paused
    TrackyTimerAttributes.ContentState.noSubtask
    TrackyTimerAttributes.ContentState.longTitles
}

#Preview("Island — compact", as: .dynamicIsland(.compact), using: TrackyTimerAttributes.previewCool) {
    TrackyTimerLiveActivity()
} contentStates: {
    TrackyTimerAttributes.ContentState.running
    TrackyTimerAttributes.ContentState.veryShortRun
    TrackyTimerAttributes.ContentState.shortRun
    TrackyTimerAttributes.ContentState.paused
}

#Preview("Island — minimal", as: .dynamicIsland(.minimal), using: TrackyTimerAttributes.previewCool) {
    TrackyTimerLiveActivity()
} contentStates: {
    TrackyTimerAttributes.ContentState.running
    TrackyTimerAttributes.ContentState.paused
}

#endif
