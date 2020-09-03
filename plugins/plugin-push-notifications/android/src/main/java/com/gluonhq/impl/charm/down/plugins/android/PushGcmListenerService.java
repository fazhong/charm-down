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
package com.gluonhq.impl.charm.down.plugins.android;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.gluonhq.charm.down.Services;
import com.gluonhq.charm.down.plugins.RuntimeArgsService;
import static com.gluonhq.charm.down.plugins.android.AndroidPushNotificationsService.debug;
import com.google.android.gms.gcm.GcmListenerService;
import java.util.UUID;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PushGcmListenerService extends GcmListenerService {

    public static final int REQUEST_CODE = 123456;
    private static final String CHANNEL_ID = "charm_down_channel";
    private static final Logger LOG = Logger.getLogger(PushGcmListenerService.class.getName());

    @Override
    public void onMessageReceived(String from, Bundle bundle) {
        if (debug) {
            LOG.log(Level.INFO, String.format("Message received from %s with bundle %s", from, bundle));
        }

        String id = "";
        String body = "";
        String title  = "";
        boolean silent = false;
        try {
            id = bundle.getString("id");
            body = bundle.getString("body");
            title = bundle.getString("title");
            if (id == null) {
                id = "";
            } 
            if (body == null) {
                body = "";
            } 
            if (title == null) {
                title = "";
            }
            silent = Boolean.parseBoolean(bundle.getString("silent"));
        } catch (Exception e) {
            LOG.log(Level.WARNING, String.format("Error parsing bundle %s", bundle), e);
        }
        
        // required to keep the ANDROID platform when the app is resumed later on
        if (System.getProperty("javafx.platform") == null) {
            System.setProperty("javafx.platform", "android");
        }
            
        if (silent) {
            // invisible notification. Don't show, directly call into RAS
            if (debug) {
                LOG.log(Level.INFO, "Message will be processed through RAS");
            }
            
            String rasMessage = "{\"id\":\"" + id + "\", \"body\":\"" + body + "\", \"title\":\"" + title + "\"}";

            // set the message as system property in case app was closed, so later on, 
            // when resuming the app, RAS can handle it
            System.setProperty(RuntimeArgsService.LAUNCH_PUSH_NOTIFICATION_KEY, rasMessage);
            final String finalRasMessage = rasMessage;
            // fire will work if the key is available, only when the app is running:
            Services.get(RuntimeArgsService.class)
                    .ifPresent(ras -> ras.fire(RuntimeArgsService.LAUNCH_PUSH_NOTIFICATION_KEY, finalRasMessage));
        } else {
            if (debug) {
                LOG.log(Level.INFO, "Message will be processed through a PushNotificationActivity");
            }
            sendNotification(id, title, body);
        }
    }
 
    private void sendNotification(String id, String title, String body) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Activity.NOTIFICATION_SERVICE);
        if (debug) {
            LOG.log(Level.INFO, String.format("Sending push notification with id: %s, title: %s, body: %s", id, title, body));
        }
        notificationManager.notify(REQUEST_CODE, getNotification(title, body, 
                id.isEmpty() ? UUID.randomUUID().toString() : id));
    }
    
    private Notification getNotification(String title, String body, String id) {
        final Application application = getApplication();
        
        Intent resultIntent = new Intent(application, PushNotificationActivity.class);
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resultIntent.setData(getData(id));
        resultIntent.putExtra(PushNotificationActivity.MESSAGE, "{\"id\":\"" + id + "\", \"body\":\"" + body + "\", \"title\":\"" + title + "\"}");
        resultIntent.putExtra(PushNotificationActivity.PACKAGE_NAME, application.getPackageName());
        
        PendingIntent resultPendingIntent = PendingIntent.getActivity(application, REQUEST_CODE, 
                resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        android.app.Notification.Builder builder = new android.app.Notification.Builder(application);
        if (title != null) {
            builder.setContentTitle(title);
        }
        builder.setContentText(body);
        builder.setSmallIcon(application.getApplicationInfo().icon);
        builder.setPriority(android.app.Notification.PRIORITY_MAX); 
        builder.setContentIntent(resultPendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(android.app.Notification.CATEGORY_EVENT);
            builder.setVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        }
        builder.setDefaults(android.app.Notification.DEFAULT_VIBRATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //builder.setLargeIcon(BitmapFactory.decodeStream(notification.getImageInputStream()));
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
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final Application application = getApplication();
        // The id of the channel
        ApplicationInfo applicationInfo = application.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        // The user-visible name of the channel.
        CharSequence name = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : application.getString(stringId);
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);

        // Configure the notification channel.
        mChannel.setShowBadge(true);
        mNotificationManager.createNotificationChannel(mChannel);
    }
}
