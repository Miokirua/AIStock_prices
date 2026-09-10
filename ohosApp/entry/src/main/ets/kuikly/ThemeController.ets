import { common, ConfigurationConstant } from '@kit.AbilityKit';

/**
 * 主题控制器（与 Android ThemeController 对齐）
 *
 * 三态：AUTO=0 跟随系统 / LIGHT=1 / DARK=2，与共享层 ThemeMode 一致。
 * 持久化：由 EntryAbility 通过 PersistentStorage.persistProp 声明，读写走 AppStorage。
 * 重建：setMode 后自增 AppStorage 里的 kuikly_theme_version，Index 监听该值
 *       销毁并重建 Kuikly 实例，保证 body 构建期取到最新配色（与 Android recreate 等价）。
 *
 * 注意：本文件未经 DevEco 编译验证（开发环境暂无鸿蒙工具链），如遇 API 差异按注释意图微调。
 */
export class ThemeController {
  static readonly MODE_AUTO: number = 0;
  static readonly MODE_LIGHT: number = 1;
  static readonly MODE_DARK: number = 2;

  static readonly KEY_MODE: string = 'kuikly_theme_mode';
  static readonly KEY_VERSION: string = 'kuikly_theme_version';

  private static uiContext: UIContext | undefined = undefined;

  static init(uiContext: UIContext): void {
    ThemeController.uiContext = uiContext;
  }

  static currentMode(): number {
    const mode: number | undefined = AppStorage.get<number>(ThemeController.KEY_MODE);
    return mode === undefined ? ThemeController.MODE_AUTO : mode;
  }

  /** 当前是否夜间（实际配色依据） */
  static currentNight(): boolean {
    const mode: number = ThemeController.currentMode();
    if (mode === ThemeController.MODE_DARK) {
      return true;
    }
    if (mode === ThemeController.MODE_LIGHT) {
      return false;
    }
    try {
      const context: common.UIAbilityContext | undefined =
        ThemeController.uiContext?.getHostContext() as common.UIAbilityContext | undefined;
      return context?.config?.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
    } catch (e) {
      return false;
    }
  }

  /** 供 HRBridgeModule.setThemeMode 调用：持久化模式并触发全页面重建 */
  static setMode(mode: number): void {
    AppStorage.setOrCreate<number>(ThemeController.KEY_MODE, mode);
    const version: number = AppStorage.get<number>(ThemeController.KEY_VERSION) ?? 0;
    AppStorage.setOrCreate<number>(ThemeController.KEY_VERSION, version + 1);
  }
}
