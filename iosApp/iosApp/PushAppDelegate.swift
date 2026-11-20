import FirebaseMessaging
import Foundation
import UIKit
import UserNotifications

/// The Swift half of console pushes: connects the device to APNs and hands FCM the result.
///
/// Registering needs no user permission, so this runs on every launch. Whether a push can actually
/// show is the permission `IosNotificationPermissionRequester` asks for.
final class PushAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Nothing else touches FCM, and this is what starts its auto-init.
        _ = Messaging.messaging()
        application.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Set by hand as well as by FCM's swizzling, which can miss SwiftUI's delegate adaptor.
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Usually the Push Notifications capability missing from the build.
        NSLog("APNs registration failed: %@", error.localizedDescription)
    }

    /// iOS hides a push that lands while the app is open unless told otherwise.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}
