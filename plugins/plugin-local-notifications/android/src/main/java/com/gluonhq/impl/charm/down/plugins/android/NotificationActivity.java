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
import static com.gluonhq.charm.down.plugins.RuntimeArgsService.LAUNCH_LOCAL_NOTIFICATION_KEY;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafxports.android.FXDalvikEntity;

public class NotificationActivity extends Activity {
    
    private static final Logger LOG = Logger.getLogger(NotificationActivity.class.getName());

    public static final String ID = "id";
    public static final String PACKAGE_NAME = "packageName";
    private String id = "";
    private String packageName;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        id = getIntent().getStringExtra(ID);
        packageName = getIntent().getStringExtra(PACKAGE_NAME);
        
        // required to keep the ANDROID platform when the app is resumed later on
        if (System.getProperty("javafx.platform") == null) {
            System.setProperty("javafx.platform", "android");
        }
        final Activity activity = FXDalvikEntity.getActivity();
        if (activity != null && 
            activity.getPackageName().equals(packageName)) {
                // if we have an app running on front or in the background
                Services.get(RuntimeArgsService.class)
                        .ifPresent(s -> s.fire(LAUNCH_LOCAL_NOTIFICATION_KEY, id));
        } else {
            // we can open the application otherwise
            if (!openApp(this, packageName)) {
                LOG.log(Level.SEVERE, String.format("[Notifications] - Error opening app %s", packageName));
            }
        }
        finish();
    }

    public boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent intent = manager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                LOG.log(Level.SEVERE, String.format("[Notifications] - App error in package: %s", packageName));
                return false;
            }
            System.setProperty(LAUNCH_LOCAL_NOTIFICATION_KEY, id);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            LOG.log(Level.INFO, String.format("[Notifications] - Starting App for package: %s", packageName));
            startActivity(intent);
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("[Notifications] - App error: ", e.getMessage()), e);
            return false;
        }
    }
    
}


