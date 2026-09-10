#import "KuiklyRenderViewController.h"
#import "UINavigationController+FDFullscreenPopGesture.h"
#import <OpenKuiklyIOSRender/KuiklyRenderViewControllerBaseDelegator.h>
#import <OpenKuiklyIOSRender/KuiklyRenderContextProtocol.h>
#import "iosApp-Swift.h"

#define HRWeakSelf __weak typeof(self) weakSelf = self;
@interface KuiklyRenderViewController()<KuiklyRenderViewControllerBaseDelegatorDelegate>

@property (nonatomic, strong) KuiklyRenderViewControllerBaseDelegator *delegator;

@end

@implementation KuiklyRenderViewController {
    NSDictionary *_pageData;
}

- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData {
    if (self = [super init]) {
        pageData = [self p_mergeExtParamsWithOriditalParam:pageData];
        _pageData = pageData;
        _delegator = [[KuiklyRenderViewControllerBaseDelegator alloc] initWithPageName:pageName pageData:pageData];
        _delegator.delegate = self;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.fd_prefersNavigationBarHidden = YES;
    self.view.backgroundColor = [self p_themedBackgroundColor];
    // 主题切换后重建根页面（ContentView 通过 .id() 重建），此处同步刷新已存在页面的底色
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(p_themeDidChange)
                                                 name:ThemeController.themeChangedNotificationName
                                               object:nil];
    [_delegator viewDidLoadWithView:self.view];
    [self.navigationController setNavigationBarHidden:YES animated:NO];

}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [_delegator viewDidLayoutSubviews];

}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [_delegator viewWillAppear];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [_delegator viewDidAppear];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [_delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_delegator viewDidDisappear];
}

#pragma mark - private

- (NSDictionary *)p_mergeExtParamsWithOriditalParam:(NSDictionary *)pageParam {
    NSMutableDictionary *mParam = [(pageParam ?: @{}) mutableCopy];
    // 主题注入：所有页面（含 openPage 推入的子页面）都必须带上，否则子页面会固定按浅色渲染。
    // 键名须与 shared BasePager 一致：IS_NIGHT_MODE_KEY="isNightMode" / IS_THEME_MODE_KEY="themeMode"
    mParam[@"isNightMode"] = @([[ThemeController shared] currentNight]);
    mParam[@"themeMode"] = @([[ThemeController shared] mode]);
    return mParam;
}

#pragma mark - KuiklyRenderViewControllerDelegatorDelegate

- (void)p_themeDidChange {
    self.view.backgroundColor = [self p_themedBackgroundColor];
}

- (UIColor *)p_themedBackgroundColor {
    if (@available(iOS 13.0, *)) {
        return [UIColor systemBackgroundColor];
    }
    return [UIColor whiteColor];
}

- (UIView *)createLoadingView {
    UIView *loadingView = [[UIView alloc] init];
    loadingView.backgroundColor = [self p_themedBackgroundColor];
    return loadingView;
}

- (UIView *)createErrorView {
    UIView *errorView = [[UIView alloc] init];
    errorView.backgroundColor = [self p_themedBackgroundColor];
    return errorView;
}

- (void)fetchContextCodeWithPageName:(NSString *)pageName resultCallback:(KuiklyContextCodeCallback)callback {
    if (callback) {
        // 返回对应framework名字
        callback(@"shared", nil);
    }
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end