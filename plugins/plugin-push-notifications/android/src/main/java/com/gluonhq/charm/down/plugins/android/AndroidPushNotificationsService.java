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

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.gluonhq.charm.down.plugins.PushNotificationsService;
import com.gluonhq.impl.charm.down.plugins.Constants;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;

import com.gluonhq.impl.charm.down.plugins.android.PushNotificationJobService;
import com.gluonhq.impl.charm.down.plugins.android.PushRegistry;
import com.gluonhq.impl.charm.down.plugins.android.RegistrationIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.control.Alert;

/**
 * Android implementation of PushNotificationService
 */
public class AndroidPushNotificationsService implements PushNotificationsService {
    
    private static final Logger LOG = Logger.getLogger(AndroidPushNotificationsService.class.getName());
    public static boolean debug;
    
    public AndroidPushNotificationsService() {
        if ("true".equals(System.getProperty(Constants.DOWN_DEBUG))) {
            debug = true;
        }
    }

    @Override
    public void register(String authorizedEntity) {
        PushRegistry.getInstance().setAuthorizedEntity(authorizedEntity);

        final Application application = getApplication();
        if (checkPlayServices(application)) {
            if (debug) {
                LOG.log(Level.INFO, "Registering entity for");
            }
            Intent intent = new Intent(application, RegistrationIntentService.class);
            application.startService(intent);

            // schedule a job that triggers every hour and only prints out a single line
            // this is a way to allow the app to keep receiving push notifications, even
            // in case the app was closed forcibly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                JobInfo jobInfo = new JobInfo.Builder(PushNotificationJobService.JOB_ID, new ComponentName(application, PushNotificationJobService.class))
                        .setPersisted(true)
                        .setPeriodic(1000 * 60 * 60) // every hour
                        .build();

                JobScheduler scheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                scheduler.schedule(jobInfo);
            }
        }
    }

    @Override
    public ReadOnlyStringProperty tokenProperty() {
        return PushRegistry.getInstance().tokenProperty();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog to alert the user that Google Play Services 
     * is not available in the device and push notifications won't work until this 
     * is fixed.
     */
    private boolean checkPlayServices(Context context) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            LOG.log(Level.SEVERE, String.format("Google Play Services Error: %s", apiAvailability.getErrorString(resultCode)));
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Google Play Services Error:\n" + 
                        apiAvailability.getErrorString(resultCode) + 
                        "\n\nPush notifications won't work until this error is fixed");
                alert.showAndWait();
            });
            return false;
        } else {
            if (debug) {
                LOG.log(Level.INFO, "Google Play Services found");
            }
        }
        return true;
    }

}
