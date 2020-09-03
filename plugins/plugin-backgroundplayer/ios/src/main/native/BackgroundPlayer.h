#import <AVFoundation/AVFoundation.h>
#include "Charm.h"
#import <UIKit/UIKit.h>

@interface BackgroundPlayer :UIViewController <AVAudioPlayerDelegate>
{
}
    - (void) playAudio: (NSString *) audioName;
    - (void) pauseAudio;
    - (void) resumeAudio;
    - (void) stopAudio;
@end