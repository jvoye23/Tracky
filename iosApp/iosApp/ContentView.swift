import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onOpenURL { url in
                // DeepLinkRouter holds the route until the nav host attaches a listener, so a tap
                // that cold-starts the app still lands on the project rather than the overview.
                guard url.scheme == TimerDeepLink.scheme,
                      let projectId = TimerDeepLink.projectId(from: url)
                else { return }
                KoinHelperKt.openProjectFromTimerNotification(projectId: projectId)
            }
    }
}



