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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.gluonhq.charm.down.Services;
import com.gluonhq.charm.down.plugins.RuntimeArgsService;
import static com.gluonhq.charm.down.plugins.RuntimeArgsService.LAUNCH_PUSH_NOTIFICATION_KEY;
import static com.gluonhq.charm.down.plugins.android.AndroidPushNotificationsService.debug;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafxports.android.FXDalvikEntity;

public class PushNotificationActivity extends Activity {
    
    private static final Logger LOG = Logger.getLogger(PushNotificationActivity.class.getName());

    public static final String MESSAGE = "message";
    public static final String PACKAGE_NAME = "packageName";
    private String message="";
    private String packageName;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        message = getIntent().getStringExtra(MESSAGE);
        packageName = getIntent().getStringExtra(PACKAGE_NAME);
        final Activity activity = FXDalvikEntity.getActivity();
        if (activity != null && activity.getPackageName().equals(packageName)) {
                // if we have an app running on front or in the background
                if (debug) {
                    LOG.log(Level.INFO, String.format("[Push Notifications] - Fire MESSAGE: %s", message));
                }
                Services.get(RuntimeArgsService.class)
                        .ifPresent(s -> s.fire(LAUNCH_PUSH_NOTIFICATION_KEY, message));
        } else {
            // we can open the application otherwise
            if (debug) {
                LOG.log(Level.INFO, "[Push Notifications] - Open App");
            }
            if (!openApp(this, packageName)) {
                LOG.log(Level.WARNING, String.format("[Push Notifications] - Error opening app: %s", packageName));
            }
        }
        finish();
    }

    public boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent intent = manager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                LOG.log(Level.WARNING, String.format("[Push Notifications] - App error in package: %s", packageName));
                return false;
            }
            if (debug) {
                LOG.log(Level.INFO, String.format("[Push Notifications] - Set property %s with value: %s", LAUNCH_PUSH_NOTIFICATION_KEY, message));
            }
            System.setProperty(LAUNCH_PUSH_NOTIFICATION_KEY, message);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("[Push Notifications] - App error: %s", e.getMessage()));
            return false;
        }
    }
    
}


