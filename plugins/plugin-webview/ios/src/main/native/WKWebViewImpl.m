#import "WKWebViewImpl.h"

#define JAR_PREFIX              @"jar:"
#define JAR_PATH_DELIMITER      @"!"
#define JAR_DIR_PREFIX          @"_"
#define JAR_DIR_SUFFIX          @"_Resources"
#define JAVA_CALL_PREFIX        @"javacall:"

#define PATH_DELIMITER          '/'

jint JNI_OnLoad_WKWebView(JavaVM* vm, void * reserved) {
#ifdef JNI_VERSION_1_8
    //min. returned JNI_VERSION required by JDK8 for builtin libraries
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_VERSION_1_4;
    }
    return JNI_VERSION_1_8;
#else
    return JNI_VERSION_1_4;
#endif
}

jstring createJStringWKWebView(JNIEnv *env, NSString *nsStr) {
    if (nsStr == nil) {
        return NULL;
    }
    jsize resLength = [nsStr length];
    jchar resBuffer[resLength];
    [nsStr getCharacters:(unichar *)resBuffer];
    return (*env)->NewString(env, resBuffer, resLength);
}


@implementation WKWebViewImpl

@synthesize window;     //known as masterWindow in glass
@synthesize windowView; //known as masterWindowHost in glass

- (void)setWidth:(CGFloat)value {
    width = value;
    [self updateWebView];
}

- (void)setHeight:(CGFloat)value {
    height = value;
    [self updateWebView];
}

- (void)loadUrl:(NSString *)value {
    NSURL *homeURL = [NSURL URLWithString:value];
    NSURLRequest *request = [[NSURLRequest alloc] initWithURL:homeURL];

    loadingLabel.text = [NSString stringWithFormat:@"Loading %@",
                        [[request URL] absoluteString]];

    loadingLabel.hidden = YES;

    [self updateWebView];
    [self updateTransform];

    [webView loadRequest:request];
    [request release];
}

- (void)loadContent:(NSString *)content {
    [webView loadHTMLString:content baseURL:nil];
}

- (void)executeScript:(NSString *) script {
    jsResult = [webView stringByEvaluatingJavaScriptFromString:script];
}

- (NSString *)getScriptResult {
    return jsResult;
}

- (WKWebViewImpl *)create:(JNIEnv *)env :(jobject)object {
    self = [super init];
    transform = CATransform3DIdentity;
    (*env)->GetJavaVM(env, &jvm);
    jObject = (*env)->NewGlobalRef(env, object);
    jclass cls = (*env)->GetObjectClass(env, object);
    jmidLoadStarted = (*env)->GetMethodID(env, cls, "notifyLoadStarted", "()V");
    // jmidLoadFinished = (*env)->GetMethodID(env, cls, "notifyLoadFinished", "()V");
    jmidLoadFinished = (*env)->GetMethodID(env, cls, "notifyLoadFinished", "(Ljava/lang/String;Ljava/lang/String;)V");
    jmidLoadFailed = (*env)->GetMethodID(env, cls, "notifyLoadFailed", "()V");
    jmidJavaCall = (*env)->GetMethodID(env, cls, "notifyJavaCall", "(Ljava/lang/String;)V");
    if (jmidLoadStarted == 0 || jmidLoadFinished == 0 || jmidLoadFailed == 0 || jmidJavaCall == 0) {
        NSLog(@"ERROR: could not get jmethodIDs: %d, %d, %d, %d",
                jmidLoadStarted, jmidLoadFinished, jmidLoadFailed, jmidJavaCall);
    }
    return self;
}

- (void) initWebViewImpl {
    CGRect screenBounds = [[UIScreen mainScreen] bounds];

    if (width <= 0) {
        width = screenBounds.size.width;
    }

    if (height <= 0) {
        height = screenBounds.size.height;
    }

    webView = [[WKWebView alloc] initWithFrame:CGRectMake(0, 0, width, height)];
    webView.userInteractionEnabled = YES;
    webView.UIDelegate = self;
    webView.navigationDelegate = self;
    webView.allowsBackForwardNavigationGestures = YES;
    //[webView setDelegate:self];
    //[webView.layer setAnchorPoint:CGPointMake(0.0f, 0.0f)];

    loadingLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, height/2, width, 40)];
    loadingLabel.textAlignment = UITextAlignmentCenter;
    //[loadingLabel.layer setAnchorPoint:CGPointMake(0.0f, 0.0f)];

    window = [self getWindow];                          //known as masterWindow in glass
    windowView = [[window rootViewController] view];    //known as masterWindowHost in glass
}

- (JNIEnv *)getJNIEnv {
    JNIEnv *env = NULL;
    if ((*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        NSLog(@"ERROR: Cannot get JNIEnv on the thread!");
    }
    return env;
}

- (void)releaseJNIEnv:(JNIEnv *)env {
}

- (WKWebView *)getWebView {
    return webView;
}

- (UILabel *)getLoadingLabel {
    return loadingLabel;
}

- (UIWindow *)getWindow {
    if (!window) {
        UIApplication *app = [UIApplication sharedApplication];
        return [app keyWindow];
    }

    return window;
}

- (void) dealloc {
    //webView.delegate = nil;
    webView.UIDelegate = nil;
    webView.navigationDelegate = nil;
    [webView release];
    [loadingLabel release];
    JNIEnv *env = [self getJNIEnv];
    if (env != NULL) {
        (*env)->DeleteGlobalRef(env, jObject);
        [self releaseJNIEnv:env];
    }
    [super dealloc];
}

- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    NSLog(@"URL：%@", navigationAction.request.URL.absoluteString);
    NSString *url = [navigationAction.request.URL query];
    if ([url hasPrefix:JAVA_CALL_PREFIX]) {
        JNIEnv *env = [self getJNIEnv];
        if (env != NULL) {
            jstring jUrl = createJStringWKWebView(env, url);
            (*env)->CallVoidMethod(env, jObject, jmidJavaCall, jUrl);
            (*env)->DeleteLocalRef(env, jUrl);
            [self releaseJNIEnv:env];
        }
    }
    decisionHandler(WKNavigationActionPolicyAllow);
}

- (void)webView:(WKWebView *)webView didStartProvisionalNavigation:(WKNavigation *)navigation {
    [windowView addSubview:loadingLabel];
    loadingLabel.hidden = hidden;
    [UIApplication sharedApplication].networkActivityIndicatorVisible = YES;

    JNIEnv *env = [self getJNIEnv];
    if (env != NULL) {
        (*env)->CallVoidMethod(env, jObject, jmidLoadStarted);
        [self releaseJNIEnv:env];
    }
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
    [webView evaluateJavaScript:@"document.documentElement.innerHTML" completionHandler:^(id _Nullable obj, NSError * _Nullable error) {
        //NSLog(@"evaluateJavaScript, obj = %@, error = %@", obj, error);
        if (error == nil) {
            if (obj != nil) {
                NSString *inner = [NSString stringWithFormat:@"%@", obj];
                NSString *currentUrl = webView.URL.absoluteString;
                loadingLabel.hidden = YES;
                [UIApplication sharedApplication].networkActivityIndicatorVisible = NO;
                if (windowView) {
                    [windowView addSubview:webView];
                } else {
                    NSLog(@"WebViewImpl ERROR: main Window is NIL");
                }

                JNIEnv *env = [self getJNIEnv];
                if (env != NULL) {
                    jstring jInner = createJStringWKWebView(env, inner);
                    jstring jUrl = createJStringWKWebView(env, currentUrl);
                    (*env)->CallVoidMethod(env, jObject, jmidLoadFinished, jUrl, jInner);
                    [self releaseJNIEnv:env];
                }
            }
        } else {
            NSLog(@"evaluateJavaScript error : %@", error.localizedDescription);
        }
    }];

}

- (void)webView:(WKWebView *)webView didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error {
    NSLog(@"WebViewImpl ERROR: didFailNavigation");
    NSLog(@" this error => %@ ", [error userInfo] );
    JNIEnv *env = [self getJNIEnv];
    if (env != NULL) {
        (*env)->CallVoidMethod(env, jObject, jmidLoadFailed);
        [self releaseJNIEnv:env];
    }
}

- (WKWebView *)webView:(WKWebView *)webView createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration forNavigationAction:(WKNavigationAction *)navigationAction windowFeatures:(WKWindowFeatures *)windowFeatures {
    if (navigationAction.targetFrame == nil) {
        NSURL *tempURL = navigationAction.request.URL;
        NSURLComponents *URLComponents = [[NSURLComponents alloc] init];
        URLComponents.scheme = [tempURL scheme];
        URLComponents.host = [tempURL host];
        URLComponents.path = [tempURL path];
        [webView loadRequest:navigationAction.request];
    }
    return nil;
}

- (void) updateWebView {
    CGRect bounds = webView.bounds;
    bounds.size.width = width;
    bounds.size.height = height;
    CGPoint center = CGPointMake(/*transform.m41 +*/ width/2, /* transform.m42 +*/ height/2);
    [webView setCenter:center];
    [webView setBounds:bounds];
    [loadingLabel setCenter:center];
    [loadingLabel setBounds:bounds];
}

- (void) updateTransform {
    [CATransaction begin];
    [CATransaction setAnimationDuration: 0];
    [CATransaction setDisableActions: YES];
    [webView.layer setTransform: transform];
    [loadingLabel.layer setTransform: transform];
    [CATransaction commit];
}

- (void) setFXTransform
        :(CGFloat) mxx :(CGFloat) mxy :(CGFloat) mxz :(CGFloat) mxt
        :(CGFloat) myx :(CGFloat) myy :(CGFloat) myz :(CGFloat) myt
        :(CGFloat) mzx :(CGFloat) mzy :(CGFloat) mzz :(CGFloat) mzt {

    transform.m11 = mxx;
    transform.m21 = mxy;
    transform.m31 = mxz;
    transform.m41 = mxt;

    transform.m12 = myx;
    transform.m22 = myy;
    transform.m32 = myz;
    transform.m42 = myt;

    transform.m13 = mzx;
    transform.m23 = mzy;
    transform.m33 = mzz;
    transform.m43 = mzt;

    if (webView) {
        [self performSelectorOnMainThread:@selector(updateTransform) withObject:nil waitUntilDone:NO];
    }
}

- (void) setHidden:(BOOL)value {
    hidden = value;
    [self performSelectorOnMainThread:@selector(applyHidden) withObject:nil waitUntilDone:NO];
}

- (void) applyHidden {
    loadingLabel.hidden = hidden;
    webView.hidden = hidden;
}

@end

unsigned int lastIndexOfWKWebView(char searchChar,NSString * string) {
    NSRange searchRange;
    searchRange.location = (unsigned int) searchChar;
    searchRange.length = 1;

    NSRange foundRange = [string rangeOfCharacterFromSet:
                            [NSCharacterSet characterSetWithRange: searchRange]
                            options: NSBackwardsSearch];
    return foundRange.location;
}

NSString* bundleUrlFromJarUrlWKWebView(NSString* jarUrlString) {
    NSString *bundlePath = @"";
    NSArray *jarUrlComponents = [jarUrlString componentsSeparatedByString: JAR_PATH_DELIMITER];

    // In the URL there must be exactly 1 exclamation mark, so after split there must be 2 components
    if ([jarUrlComponents count] == 2) {
        NSString *filePath = (NSString *) [jarUrlComponents lastObject];
        NSString *jarPath = (NSString *) [jarUrlComponents objectAtIndex: 0];

        unsigned int lastPathDelimiter = lastIndexOfWKWebView(PATH_DELIMITER, jarPath);

        NSString *jarFileName = [jarPath substringFromIndex: lastPathDelimiter + 1];
        NSString *jarDirName = [JAR_DIR_PREFIX stringByAppendingString: jarFileName];
        jarDirName = [jarDirName stringByAppendingString: JAR_DIR_SUFFIX];

        filePath = [jarDirName stringByAppendingString: filePath];

        bundlePath = [[[NSBundle mainBundle] resourcePath]
                      stringByAppendingPathComponent: filePath];
    }

    return bundlePath;
}


#ifdef __cplusplus
extern "C" {
#endif
    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _initWebView
     * Signature: ([J)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1initWebView(JNIEnv *env, jobject obj, jlongArray nativeHandle) {
        WKWebViewImpl *wvi = [[WKWebViewImpl alloc] create: env : obj];
        [wvi performSelectorOnMainThread:@selector(initWebViewImpl) withObject:nil waitUntilDone:NO];

        jlong handle = ptr_to_jlong(wvi);
        (*env)->SetLongArrayRegion(env, nativeHandle, 0, 1, &handle);
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _setWidth
     * Signature: (JD)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1setWidth(JNIEnv *env, jobject cl, jlong handle, jdouble w) {
        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi setWidth:w];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _setHeight
     * Signature: (JD)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1setHeight(JNIEnv *env, jobject cl, jlong handle, jdouble h) {
        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi setHeight:h];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _setVisible
     * Signature: (JZ)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1setVisible(JNIEnv *env, jobject cl, jlong handle, jboolean v) {
        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi setHidden:(v ? NO : YES)];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _setTransform
     * Signature: (JDDDDDDDDDDDD)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1setTransform(JNIEnv *env, jobject cl, jlong handle,
                                                 jdouble mxx, jdouble mxy, jdouble mxz, jdouble mxt,
                                                 jdouble myx, jdouble myy, jdouble myz, jdouble myt,
                                                 jdouble mzx, jdouble mzy, jdouble mzz, jdouble mzt) {
        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi setFXTransform
                 :(CGFloat) mxx :(CGFloat) mxy :(CGFloat) mxz :(CGFloat) mxt
                :(CGFloat) myx :(CGFloat) myy :(CGFloat) myz :(CGFloat) myt
                :(CGFloat) mzx :(CGFloat) mzy :(CGFloat) mzz :(CGFloat) mzt];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _removeWebView
     * Signature: (J)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1removeWebView(JNIEnv *env, jobject cl, jlong handle) {
        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            UIView *view = [wvi getWebView];
            if (view)
                [view performSelectorOnMainThread:@selector(removeFromSuperview) withObject:nil waitUntilDone:NO];
            view = [wvi getLoadingLabel];
            if (view)
                [view performSelectorOnMainThread:@selector(removeFromSuperview) withObject:nil waitUntilDone:NO];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _loadUrl
     * Signature: (JLjava/lang/String;)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1loadUrl(JNIEnv *env, jobject cl, jlong handle, jstring str) {
        NSString *string = @"";
        if (str!= NULL)
        {
            const jchar* jstrChars = (*env)->GetStringChars(env, str, NULL);
            string = [[[NSString alloc] initWithCharacters: jstrChars length: (*env)->GetStringLength(env, str)] autorelease];
            (*env)->ReleaseStringChars(env, str, jstrChars);
        }

        if ([string hasPrefix:JAR_PREFIX]) {
            string = bundleUrlFromJarUrlWKWebView(string);
        }

        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi performSelectorOnMainThread:@selector(loadUrl:) withObject:string waitUntilDone:NO];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _loadContent
     * Signature: (JLjava/lang/String;)V
     */
    JNIEXPORT void JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1loadContent(JNIEnv *env, jobject cl, jlong handle, jstring content) {
        NSString *string = @"";
        if (content!= NULL)
        {
            const jchar* jstrChars = (*env)->GetStringChars(env, content, NULL);
            string = [[[NSString alloc] initWithCharacters: jstrChars length: (*env)->GetStringLength(env, content)] autorelease];
            (*env)->ReleaseStringChars(env, content, jstrChars);
        }

        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi performSelectorOnMainThread:@selector(loadContent:) withObject:string waitUntilDone:NO];
        }
    }

    /*
     * Class:     com_gluonhq_charm_down_plugins_ios_WKWebViewImpl
     * Method:    _executeScript
     * Signature: (JLjava/lang/String;)Ljava/lang/String;
     */
    JNIEXPORT jstring JNICALL
    Java_com_gluonhq_charm_down_plugins_ios_WKWebViewImpl__1executeScript(JNIEnv *env, jobject cl, jlong handle, jstring script) {
        NSString *string = @"";
        if (script!= NULL)
        {
            const jchar* jstrChars = (*env)->GetStringChars(env, script, NULL);
            string = [[[NSString alloc] initWithCharacters: jstrChars length: (*env)->GetStringLength(env, script)] autorelease];
            (*env)->ReleaseStringChars(env, script, jstrChars);
        }

        WKWebViewImpl *wvi = jlong_to_ptr(handle);
        if (wvi) {
            [wvi performSelectorOnMainThread:@selector(executeScript:) withObject:string waitUntilDone:YES];

            NSString *result = [wvi getScriptResult];

            if (result != nil) {
                jsize resLength = [result length];
                jchar resBuffer[resLength];
                [result getCharacters:(unichar *)resBuffer];
                return (*env)->NewString(env, resBuffer, resLength);
            }
        }

        return NULL;
    }

#ifdef __cplusplus
}
#endif
