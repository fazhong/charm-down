#include "AudioPlayer.h"

extern JNIEnv *jEnv;
#define GET_MAIN_JENV \
if (jEnv == NULL) NSLog(@"ERROR: Java has been detached already, but someone is still trying to use it at %s:%s:%d\n", __FUNCTION__, __FILE__, __LINE__);\
JNIEnv *env = jEnv;

JNIEXPORT jint JNICALL
JNI_OnLoad_AudioPlayer(JavaVM *vm, void *reserved)
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

// AudioPlayer
AudioPlayer *_audio;

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSAudioPlayerService_play
(JNIEnv *env, jclass jClass, jstring jTitle)
{
    NSLog(@"Play audio");
    const jchar *charsTitle = (*env)->GetStringChars(env, jTitle, NULL);
    NSString *name = [NSString stringWithCharacters:(UniChar *)charsTitle length:(*env)->GetStringLength(env, jTitle)];
    (*env)->ReleaseStringChars(env, jTitle, charsTitle);

    _audio = [[AudioPlayer alloc] init];
    [_audio playAudio:name];
    return;
}

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSAudioPlayerService_pause
(JNIEnv *env, jclass jClass)
{
    if (_audio)
    {
        [_audio pauseAudio];
    }
    return;
}

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSAudioPlayerService_resume
(JNIEnv *env, jclass jClass)
{
    if (_audio)
    {
        [_audio resumeAudio];
    }
    return;
}

JNIEXPORT void JNICALL Java_com_gluonhq_charm_down_plugins_ios_IOSAudioPlayerService_stop
(JNIEnv *env, jclass jClass)
{
    if (_audio)
    {
        [_audio stopAudio];
    }
    return;
}


@implementation AudioPlayer

AVAudioPlayer* player;

- (void)playAudio:(NSString *) audioName
{
    /*
    NSString* fileName = [audioName stringByDeletingPathExtension];
    NSString* extension = [audioName pathExtension];
    NSURL* url = [[NSBundle mainBundle] URLForResource:[NSString stringWithFormat:@"%@",fileName] withExtension:[NSString stringWithFormat:@"%@",extension]];
    */

    NSURL* url = [NSURL fileURLWithPath:audioName];
    NSError* error = nil;

    if(player)
    {
        [player stop];
        player = nil;
    }
    player = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:&error];
    if(!player)
    {
        NSLog(@"Error creating player: %@", error);
        return;
    }
    player.delegate = self;
    [player prepareToPlay];
    [player play];

}

- (void)pauseAudio
{
    if(!player)
    {
        return;
    }
    [player pause];
}

- (void)resumeAudio
{
    if(!player)
    {
        return;
    }
    [player play];
}

- (void)stopAudio
{
    if(!player)
    {
        return;
    }
    [player stop];
    player = nil;
}

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag
{
    NSLog(@"%s successfully=%@", __PRETTY_FUNCTION__, flag ? @"YES"  : @"NO");
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error
{
    NSLog(@"%s error=%@", __PRETTY_FUNCTION__, error);
}

@end