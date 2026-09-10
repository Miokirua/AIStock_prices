#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/KRBaseModule.h>
NS_ASSUME_NONNULL_BEGIN

@interface HRBridgeModule : KRBaseModule

/// toast：{ content: "提示文案" }
- (void)toast:(NSDictionary *)args;
/// 当前毫秒时间戳（同步返回字符串）
- (NSString *)currentTimestamp:(NSDictionary *)args;
/// 日期格式化：{ timeStamp: 毫秒, format: "HH:mm:ss" }
- (NSString *)dateFormatter:(NSDictionary *)args;
/// 切换主题：{ mode: 0跟随系统/1浅色/2深色 }
- (void)setThemeMode:(NSDictionary *)args;
/// 关闭当前页面
- (void)closePage:(NSDictionary *)args;

@end

NS_ASSUME_NONNULL_END