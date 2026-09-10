import Foundation
import UIKit

/// 主题控制器（与 Android ThemeController 对齐）
///
/// 三态：AUTO=0 跟随系统 / LIGHT=1 / DARK=2，与共享层 ThemeMode 一致。
/// 持久化：UserDefaults（key: kuikly_theme_mode）。
/// 重建：切换后发通知，SwiftUI 侧通过 .id() 重建 Kuikly 页面，
///       保证 body 构建期取到最新配色（等价于 Android 的 Activity recreate）。
///
/// 注意：本文件未经 Xcode 编译验证（开发环境无 macOS），如遇 API/工程配置差异按注释意图微调。
extension Notification.Name {
    /// 注意：必须是 `static var`（计算属性），写成 `static let` 会编译报错
    /// "'let' declarations cannot be computed properties"
    static var kuiklyThemeChanged: Notification.Name {
        Notification.Name(ThemeController.themeChangedNotificationName)
    }
}

@objc class ThemeController: NSObject {

    @objc static let shared = ThemeController()

    /// ObjC 侧（HRBridgeModule / KuiklyRenderViewController）使用的通知名
    @objc static let themeChangedNotificationName: String = "kuiklyThemeChanged"

    @objc static let MODE_AUTO: Int = 0
    @objc static let MODE_LIGHT: Int = 1
    @objc static let MODE_DARK: Int = 2

    private static let key = "kuikly_theme_mode"

    private override init() {
        let stored = UserDefaults.standard.object(forKey: ThemeController.key) as? Int
        mode = stored ?? ThemeController.MODE_AUTO
        super.init()
    }

    /// 当前模式（0/1/2）
    @objc private(set) var mode: Int

    /// 当前是否夜间（实际配色依据）
    @objc func currentNight() -> Bool {
        if mode == ThemeController.MODE_DARK {
            return true
        }
        if mode == ThemeController.MODE_LIGHT {
            return false
        }
        if #available(iOS 13.0, *) {
            if let style = UIApplication.shared.currentKeyWindow?.traitCollection.userInterfaceStyle {
                return style == .dark
            }
            return UITraitCollection.current.userInterfaceStyle == .dark
        }
        return false
    }

    /// 供 HRBridgeModule setThemeMode 调用：持久化并触发全页面重建
    ///
    /// ⚠️ 方法名不能叫 `setMode(_:)`：`@objc` 属性 `mode` 会自动生成 `setMode:` 选择器，
    /// 与之冲突会编译报错 "method 'setMode' with Objective-C selector 'setMode:'
    /// conflicts with setter for 'mode' with the same Objective-C selector"。
    @objc func applyThemeMode(_ newMode: Int) {
        mode = newMode
        UserDefaults.standard.set(newMode, forKey: ThemeController.key)
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .kuiklyThemeChanged, object: nil)
        }
    }

    /// 注入给 Kuikly 页面的初始参数
    @objc func pageParams() -> [String: Any] {
        return [
            "isNightMode": currentNight(),
            "themeMode": mode
        ]
    }
}

private extension UIApplication {
    var currentKeyWindow: UIWindow? {
        if #available(iOS 13.0, *) {
            return connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }
        }
        return keyWindow
    }
}
