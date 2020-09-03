#include "BackgroundPlayer.h"

extern JNIEnv *jEnv;
#define GET_MAIN_JENV \
if (jEnv == NULL) NSLog(@"ERROR: Java has been detached already, but someone is still trying to use it at %s:%s:%d\n", __FUNCTION__, __FILE__, __LINE__);\
JNIEnv *env = jEnv;

JNIEXPORT jint JNICALL
JNI_OnLoad_BackgroundPlayer(JavaVM *vm, void *reserved)
{
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

// BackgroundPlayer
BackgroundPlayer *_audioBackground;

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSBackgroundPlayerService_play
(JNIEnv *env, jclass jClass, jstring jTitle)
{
    NSLog(@"Play audio");
    const jchar *charsTitle = (*env)->GetStringChars(env, jTitle, NULL);
    NSString *name = [NSString stringWithCharacters:(UniChar *)charsTitle length:(*env)->GetStringLength(env, jTitle)];
    (*env)->ReleaseStringChars(env, jTitle, charsTitle);

    _audioBackground = [[BackgroundPlayer alloc] init];
    [_audioBackground playAudio:name];
    return;
}

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSBackgroundPlayerService_pause
(JNIEnv *env, jclass jClass)
{
    if (_audioBackground)
    {
        [_audioBackground pauseAudio];
    }
    return;
}

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSBackgroundPlayerService_resume
(JNIEnv *env, jclass jClass)
{
    if (_audioBackground)
    {
        [_audioBackground resumeAudio];
    }
    return;
}

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSBackgroundPlayerService_stop
(JNIEnv *env, jclass jClass)
{
    if (_audioBackground)
    {
        [_audioBackground stopAudio];
    }
    return;
}


@implementation BackgroundPlayer

AVAudioPlayer* playerBackground;

- (void)playAudio:(NSString *) audioName
{
    /*
    NSString* fileName = [audioName stringByDeletingPathExtension];
    NSString* extension = [audioName pathExtension];
    NSURL* url = [[NSBundle mainBundle] URLForResource:[NSString stringWithFormat:@"%@",fileName] withExtension:[NSString stringWithFormat:@"%@",extension]];
    */

    NSURL* url = [NSURL fileURLWithPath:audioName];
    NSError* error = nil;

    if(playerBackground)
    {
        [playerBackground stop];
        playerBackground = nil;
    }
    playerBackground = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:&error];
    if(!playerBackground)
    {
        NSLog(@"Error creating playerBackground: %@", error);
        return;
    }
    playerBackground.delegate = self;
    [playerBackground prepareToPlay];
    [playerBackground play];

}

- (void)pauseAudio
{
    if(!playerBackground)
    {
        return;
    }
    [playerBackground pause];
}

- (void)resumeAudio
{
    if(!playerBackground)
    {
        return;
    }
    [playerBackground play];
}

- (void)stopAudio
{
    if(!playerBackground)
    {
        return;
    }
    [playerBackground stop];
    playerBackground = nil;
}

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)playerBackground successfully:(BOOL)flag
{
    NSLog(@"%s successfully=%@", __PRETTY_FUNCTION__, flag ? @"YES"  : @"NO");
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)playerBackground error:(NSError *)error
{
    NSLog(@"%s error=%@", __PRETTY_FUNCTION__, error);
}

@end