#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import "iosApp-Swift.h"

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

@implementation HRBridgeModule

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

#pragma mark - 业务使用的桥接

- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    if (content.length == 0) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        UIWindow *window = [self hr_keyWindow];
        if (!window) {
            return;
        }
        UILabel *tip = [[UILabel alloc] init];
        tip.text = content;
        tip.textColor = [UIColor whiteColor];
        tip.backgroundColor = [UIColor colorWithWhite:0 alpha:0.8];
        tip.textAlignment = NSTextAlignmentCenter;
        tip.font = [UIFont systemFontOfSize:14];
        tip.numberOfLines = 0;
        tip.layer.cornerRadius = 8;
        tip.layer.masksToBounds = YES;
        tip.alpha = 0;
        CGSize fit = [tip sizeThatFits:CGSizeMake(window.bounds.size.width - 80, CGFLOAT_MAX)];
        CGFloat w = MIN(fit.width + 32, window.bounds.size.width - 40);
        CGFloat h = fit.height + 20;
        tip.frame = CGRectMake((window.bounds.size.width - w) / 2, window.bounds.size.height * 0.75, w, h);
        [window addSubview:tip];
        [UIView animateWithDuration:0.2 animations:^{
            tip.alpha = 1;
        } completion:^(BOOL finished) {
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.6 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [UIView animateWithDuration:0.25 animations:^{
                    tip.alpha = 0;
                } completion:^(BOOL finished2) {
                    [tip removeFromSuperview];
                }];
            });
        }];
    });
}

- (NSString *)currentTimestamp:(NSDictionary *)args {
    long long ms = (long long)([[NSDate date] timeIntervalSince1970] * 1000.0);
    return [NSString stringWithFormat:@"%lld", ms];
}

- (NSString *)dateFormatter:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *format = params[@"format"] ?: @"HH:mm:ss";
    double ms = [params[@"timeStamp"] doubleValue];
    NSDate *date = [NSDate dateWithTimeIntervalSince1970:ms / 1000.0];
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = format;
    return [formatter stringFromDate:date] ?: @"";
}

- (void)setThemeMode:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSInteger mode = [params[@"mode"] integerValue];
    dispatch_async(dispatch_get_main_queue(), ^{
        [[ThemeController shared] setMode:mode];
    });
}

- (void)closePage:(NSDictionary *)args {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *root = [self hr_keyWindow].rootViewController;
        UINavigationController *nav =
            [root isKindOfClass:UINavigationController.class] ? (UINavigationController *)root : root.navigationController;
        [nav popViewControllerAnimated:YES];
    });
}

- (UIWindow *)hr_keyWindow {
    if (@available(iOS 13.0, *)) {
        for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
            if ([scene isKindOfClass:UIWindowScene.class]) {
                for (UIWindow *window in ((UIWindowScene *)scene).windows) {
                    if (window.isKeyWindow) {
                        return window;
                    }
                }
            }
        }
        return nil;
    }
    return UIApplication.sharedApplication.keyWindow;
}

@end