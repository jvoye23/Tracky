import SwiftUI

/// Turns the project's stored colour into something SwiftUI can paint with.
///
/// Kotlin keeps colours as packed ARGB `Int`s, which reach Swift as `Int32` — and every opaque
/// colour has the alpha byte set, so the value arrives **negative**. Going through
/// `UInt32(bitPattern:)` before unpacking is what keeps that from turning into nonsense.
///
/// The two on-accent values mirror the Android notification factory exactly, so the same project
/// looks the same on both platforms.
enum ProjectAccent {

    private static let onAccentLight = Color.white
    private static let onAccentDark = Color(red: 0x1A / 255, green: 0x1C / 255, blue: 0x1E / 255)

    static func color(argb: Int32) -> Color {
        let bits = UInt32(bitPattern: argb)
        return Color(
            .sRGB,
            red: Double((bits >> 16) & 0xFF) / 255,
            green: Double((bits >> 8) & 0xFF) / 255,
            blue: Double(bits & 0xFF) / 255,
            opacity: Double((bits >> 24) & 0xFF) / 255
        )
    }

    /// The project already knows whether its own colour needs light text on top — the same flag the
    /// task cards read. No luminance maths here on purpose.
    static func onAccent(useLightTextColor: Bool) -> Color {
        useLightTextColor ? onAccentLight : onAccentDark
    }
}

extension Duration {

    /// `HH:mm:ss`, the format the frozen card shows and the one the design asks for. Hours are not
    /// wrapped at a day, matching the app's own duration formatting.
    static func trackyClock(seconds: Double) -> String {
        let total = Int(seconds.rounded(.down))
        return String(format: "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }
}
