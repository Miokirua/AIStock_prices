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
    // 键盘避让：Kuikly iOS 渲染层只回传 keyboardHeightChange、不自动避开键盘，
    // 统一在宿主侧监听键盘 frame 变化，把被键盘遮挡的输入框上移露出。
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(p_keyboardWillChangeFrame:)
                                                 name:UIKeyboardWillChangeFrameNotification
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
    // 离场时还原键盘上移位移，避免侧滑返回/ push 新页时残留偏移
    self.view.transform = CGAffineTransformIdentity;
    [_delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_delegator viewDidDisappear];
}

#pragma mark - private

#pragma mark 键盘避让（iOS 渲染层不自动避让，宿主侧统一处理）

// 键盘 frame 变化（弹出 / 收起 / 切换键盘高度）统一入口
- (void)p_keyboardWillChangeFrame:(NSNotification *)notify {
    NSDictionary *info = notify.userInfo;
    CGRect kbEndFrame = [info[UIKeyboardFrameEndUserInfoKey] CGRectValue];
    NSTimeInterval duration = [info[UIKeyboardAnimationDurationUserInfoKey] doubleValue];
    UIViewAnimationCurve curve = [info[UIKeyboardAnimationCurveUserInfoKey] integerValue];

    // 先还原，确保下面的坐标换算基于「未上移」的原始坐标系（transform 只做视觉位移，不改布局）
    self.view.transform = CGAffineTransformIdentity;

    CGFloat offset = 0;
    CGFloat screenH = UIScreen.mainScreen.bounds.size.height;
    BOOL keyboardVisible = kbEndFrame.origin.y < screenH;
    if (keyboardVisible) {
        UIView *responder = [self p_findFirstResponder:self.view];
        if (responder) {
            // 键盘顶部在 self.view 坐标系里的 y
            CGRect viewInScreen = [self.view convertRect:self.view.bounds toView:nil];
            CGFloat keyboardTopInView = kbEndFrame.origin.y - viewInScreen.origin.y;
            // 输入框底部在 self.view 坐标系里的 y
            CGRect responderFrame = [responder convertRect:responder.bounds toView:self.view];
            CGFloat responderBottom = CGRectGetMaxY(responderFrame);
            static CGFloat const kGap = 12.0; // 输入框与键盘顶部的间距
            if (responderBottom + kGap > keyboardTopInView) {
                offset = responderBottom + kGap - keyboardTopInView;
            }
        }
    }

    UIViewAnimationOptions options = ((UIViewAnimationOptions)curve << 16) | UIViewAnimationOptionBeginFromCurrentState;
    [UIView animateWithDuration:duration
                          delay:0
                        options:options
                     animations:^{
        self.view.transform = CGAffineTransformMakeTranslation(0, -offset);
    } completion:nil];
}

// 递归查找当前 firstResponder（Kuikly 渲染层级较深，需遍历整棵视图树）
- (UIView *)p_findFirstResponder:(UIView *)view {
    if (view.isFirstResponder) {
        return view;
    }
    for (UIView *subview in view.subviews) {
        UIView *found = [self p_findFirstResponder:subview];
        if (found) {
            return found;
        }
    }
    return nil;
}

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