import SwiftUI
import shared

@main
struct MiyoruApp: App {
    init() {
        // Koin 装配（数据库/插件/下载/设置/播放器），失败即 Early-crash 便于诊断。
        _ = IosKoinKt.doInitKoinIos(appVersion: "0.1.0")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all, edges: .bottom)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
