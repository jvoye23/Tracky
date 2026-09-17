import SwiftUI
import WidgetKit

/// Tracky ships one widget, and it is not a home-screen widget: iOS has no surface there for a
/// running timer. The Live Activity covers the Lock Screen, the Dynamic Island and StandBy, which
/// is the whole of what the platform offers.
@main
struct TrackyTimerWidgetBundle: WidgetBundle {
    var body: some Widget {
        TrackyTimerLiveActivity()
    }
}
