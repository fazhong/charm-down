/*
 * Copyright (c) 2016, 2017 Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.charm.down.plugins.android;

import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import com.gluonhq.charm.down.plugins.Notification;
import com.gluonhq.impl.charm.down.plugins.android.AlarmReceiver;
import com.gluonhq.impl.charm.down.plugins.android.NotificationActivity;
import com.gluonhq.impl.charm.down.plugins.LocalNotificationsServiceBase;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;

/**
 * Android implementation of LocalNotificationService
 * 
 */
public class AndroidLocalNotificationsService extends LocalNotificationsServiceBase {
    
    private static final Application APPLICATION = getApplication();
    private static final String CHANNEL_ID = "charm_down_channel";

    @Override
    protected void scheduleNotification(Notification notification) {
        PendingIntent pendingIntent = getPendingIntent(notification);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) APPLICATION.getSystemService(Context.ALARM_SERVICE);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, notification.getDateTime().toEpochSecond() * 1000, pendingIntent);
        }
    }
    
    @Override
    protected void unscheduleNotification(Notification notification) {
        PendingIntent pendingIntent = getPendingIntent(notification);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) APPLICATION.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
        }
    }
    
    private PendingIntent getPendingIntent(Notification notification) {
        // only process valid notifications
        if (notification == null || notification.getId() == null) {
            return null;
        }
        
        Intent notificationIntent = new Intent(APPLICATION, AlarmReceiver.class);
        // use the data field to define a unique intent, even if request code is the same
        notificationIntent.setData(getData(notification.getId()));
        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION_ID, notification.getId());
        notificationIntent.putExtra(AlarmReceiver.NOTIFICATION, getNotification(notification));
        return PendingIntent.getBroadcast(APPLICATION, AlarmReceiver.REQUEST_CODE, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    }
    
    private android.app.Notification getNotification(Notification notification) {
        
        Intent resultIntent = new Intent(APPLICATION, NotificationActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resultIntent.setData(getData(notification.getId()));
        resultIntent.putExtra(NotificationActivity.ID, notification.getId());
        resultIntent.putExtra(NotificationActivity.PACKAGE_NAME, APPLICATION.getPackageName());
        
        PendingIntent resultPendingIntent = PendingIntent.getActivity(APPLICATION, AlarmReceiver.REQUEST_CODE, 
                resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        android.app.Notification.Builder builder = new android.app.Notification.Builder(APPLICATION);
        if (notification.getTitle() != null) {
            builder.setContentTitle(notification.getTitle());
        }
        builder.setContentText(notification.getText());
        builder.setSmallIcon(APPLICATION.getApplicationInfo().icon);
        builder.setPriority(android.app.Notification.PRIORITY_MAX); 
        builder.setContentIntent(resultPendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(android.app.Notification.CATEGORY_EVENT);
            builder.setVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        }
        builder.setDefaults(android.app.Notification.DEFAULT_VIBRATE);
        if (notification.getImageInputStream() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setLargeIcon(BitmapFactory.decodeStream(notification.getImageInputStream()));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannelId();
            builder.setChannelId(CHANNEL_ID);
        }
        builder.setAutoCancel(true);
        return builder.build();
    }

    // Provides unique Uri based on the id of the notification
    private Uri getData(String id) {
       return Uri.withAppendedPath(Uri.parse("charm://down/Id/#"), id);
    }
    
    private void createChannelId() {
        NotificationManager mNotificationManager = (NotificationManager) APPLICATION.getSystemService(Context.NOTIFICATION_SERVICE);
        // The id of the channel
        ApplicationInfo applicationInfo = APPLICATION.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        // The user-visible name of the channel.
        CharSequence name = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : APPLICATION.getString(stringId);
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);

        // Configure the notification channel.
        mChannel.setShowBadge(true);
        mNotificationManager.createNotificationChannel(mChannel);
    }
}
