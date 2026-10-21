import ComposeApp
import FirebaseAnalytics
import Foundation

/// The Swift half of analytics: Kotlin decides *what* to log and maps it to Firebase's names, this
/// hands it to the Firebase SDK that Xcode links through Swift Package Manager.
///
/// Kotlin's `Long` and `Double` params arrive as `NSNumber` subclasses and `String` as `NSString`,
/// which `Analytics` takes as they are.
final class FirebaseAnalyticsBackend: NSObject, AnalyticsBackend {

    func logEvent(name: String, params: [String: Any]) {
        Analytics.logEvent(name, parameters: params)
    }

    func setUserProperty(name: String, value: String) {
        Analytics.setUserProperty(value, forName: name)
    }

    func setUserId(id: String?) {
        Analytics.setUserID(id)
    }
}
