import Foundation

/// The URL a tap on the Live Activity hands back to the app.
///
/// The widget builds it with `.widgetURL`, the app reads it in `.onOpenURL`, so both targets need
/// this. A custom scheme is enough — the link never leaves the device, and nothing outside the app
/// is meant to be able to forge it into a universal link.
enum TimerDeepLink {

    static let scheme = "tracky"
    private static let host = "project"

    static func url(projectId: String) -> URL? {
        var components = URLComponents()
        components.scheme = scheme
        components.host = host
        components.path = "/" + projectId
        return components.url
    }

    static func projectId(from url: URL) -> String? {
        guard url.host == host else { return nil }
        let id = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return id.isEmpty ? nil : id
    }
}
