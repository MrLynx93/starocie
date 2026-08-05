import SwiftUI
import UIKit
import Shared

/// The entire app is one Compose `UIViewController`; this is the only Swift that
/// knows anything about the screens.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose applies its own IME padding — matching what the buy form
            // relies on for its pinned buttons — so SwiftUI must not also shrink
            // the window, or the two corrections stack and the layout jumps.
            .ignoresSafeArea(.keyboard)
    }
}
