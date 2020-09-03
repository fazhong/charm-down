#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>
#import <QuartzCore/QuartzCore.h>
#import <WebKit/WebKit.h>​​​​​​​

#include <jni.h>

#ifdef  __LP64__
#define jlong_to_ptr(a) ((void*)(a))
#define ptr_to_jlong(a) ((jlong)(a))
#else
#define jlong_to_ptr(a) ((void*)(int)(a))
#define ptr_to_jlong(a) ((jlong)(int)(a))
#endif

@interface WKWebViewImpl : NSObject<UIWebViewDelegate> {
    WKWebView *webView;
    UILabel   *loadingLabel;
    CGFloat width;
    CGFloat height;
    CATransform3D transform;
    NSString *jsResult;
    BOOL hidden;

    JavaVM *jvm;
    jobject jObject;
    jmethodID jmidLoadStarted;
    jmethodID jmidLoadFinished;
    jmethodID jmidLoadFailed;
    jmethodID jmidJavaCall;
}

@property (readwrite, retain) UIWindow *window;
@property (readwrite, retain) UIView   *windowView;

- (WKWebViewImpl *)create:(JNIEnv *)env :(jobject)object;
- (void)initWebViewImpl;
- (JNIEnv *)getJNIEnv;
- (void)releaseJNIEnv:(JNIEnv *)env;
- (void)setWidth:(CGFloat)value;
- (void)setHeight:(CGFloat)value;
- (void)loadUrl:(NSString *)value;
- (void)loadContent:(NSString *)content;
- (void)executeScript:(NSString *)script;
- (NSString *)getScriptResult;
- (UIWebView *)getWebView;
- (UILabel *)getLoadingLabel;
- (UIWindow *)getWindow;
- (void) setFXTransform
        :(CGFloat) mxx :(CGFloat) mxy :(CGFloat) mxz :(CGFloat) mxt
        :(CGFloat) myx :(CGFloat) myy :(CGFloat) myz :(CGFloat) myt
        :(CGFloat) mzx :(CGFloat) mzy :(CGFloat) mzz :(CGFloat) mzt;
- (void) updateWebView;
- (void) updateTransform;
- (void) setHidden:(BOOL)value;

@end

