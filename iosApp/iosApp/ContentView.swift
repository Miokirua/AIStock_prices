import SwiftUI
import shared

struct ContentView: View {

    /// 主题切换后自增，用于重建 Kuikly 页面（等价于 Android 的 Activity recreate）
    @State private var renderID = UUID()

    var body: some View {
        KuiklyRenderViewPage(pageName: "stock_list", data: ThemeController.shared.pageParams())
            .id(renderID)
            .ignoresSafeArea()
            .onReceive(NotificationCenter.default.publisher(for: .kuiklyThemeChanged)) { _ in
                renderID = UUID()
            }
    }
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
