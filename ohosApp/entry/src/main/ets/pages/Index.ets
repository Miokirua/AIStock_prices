import { ErrorReason, KRRecord, Kuikly, KuiklyRenderBaseView } from '@kuikly-open/render';
import router from '@ohos.router';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import globalNativeManager from '../kuikly/MyNativeManager';
import { ThemeController } from '../kuikly/ThemeController';

const DEFAULT_PAGE_NAME: string = 'stock_list';

@Entry
@Component
struct Index {
  kuiklyViewDelegate = new KuiklyViewDelegate();
  pageName?: string;
  pageData?: KRRecord;
  @State exception?: string = "";
  @State alive: boolean = true;
  @StorageLink(ThemeController.KEY_VERSION) @Watch('onThemeVersionChanged') themeVersion: number = 0;

  aboutToAppear() {
    const uiContext: UIContext = this.getUIContext();
    AppStorage.setOrCreate<UIContext>("context", uiContext);
    ThemeController.init(uiContext);

    const params = router.getParams() as Record<string, Object>;
    this.pageName = params?.pageName as string;
    this.pageData = params?.pageData as KRRecord;
  }

  /** 主题切换：先销毁 Kuikly 实例再重建，等价于 Android 的 Activity recreate */
  onThemeVersionChanged(): void {
    this.alive = false;
    setTimeout(() => {
      this.alive = true;
    }, 50);
  }

  /** 注入主题状态（与 Android createPageData 一致：isNightMode / themeMode） */
  private buildPagerData(): KRRecord {
    const origin: Record<string, Object> = (this.pageData ?? {}) as Record<string, Object>;
    const data: Record<string, Object> = {};
    Object.keys(origin).forEach((key: string) => {
      data[key] = origin[key];
    });
    data['isNightMode'] = ThemeController.currentNight();
    data['themeMode'] = ThemeController.currentMode();
    return data as KRRecord;
  }

  build() {
    Stack() {
      if (this.alive) {
        Kuikly({
          pagerName: this.pageName ?? DEFAULT_PAGE_NAME,
          pagerData: this.buildPagerData(),
          delegate: this.kuiklyViewDelegate,
          nativeManager: globalNativeManager,
          onRenderException: (exception: Error, reason: ErrorReason) => {
            this.exception = `${exception.name}:\n${exception.message}`;
          },
        })
      }
      if (this.exception) {
        Text(this.exception)
      }
    }
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.KEYBOARD])
  }

  onPageShow(): void {
    this.kuiklyViewDelegate.pageDidAppear();
  }

  onPageHide(): void {
    this.kuiklyViewDelegate.pageDidDisappear();
  }
}