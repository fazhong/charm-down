/*
 * Copyright (c) 2017, Gluon
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import com.gluonhq.impl.charm.down.plugins.NestedEventLoopInvoker;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafxports.android.FXActivity;

/**
 * If targetSdkVersion &gt;= 23, and any of the plugin requires a dangerous 
 * permission, this activity must be added to the AndroidManifest.xml:
 * 
 * <pre>
 * {@code
 * <activity android:name="com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity" />
 * }
 * </pre>
 * 
 * See list of dangerous permission here: 
 * https://developer.android.com/guide/topics/permissions/requesting.html#normal-dangerous
 */
public class PermissionRequestActivity extends Activity {
    private static final Logger LOG = Logger.getLogger(PermissionRequestActivity.class.getName());

    private static final String KEY_PERMISSIONS = "permissions";
    private static final String KEY_REQUEST_CODE = "requestCode";
    private static final int PERMISSION_REQUEST_CODE = 10010;
    
    private static String[] permissions;
    private static int requestCode;
    private static final BooleanProperty VERIFIED = new SimpleBooleanProperty();
    
    @Override
    protected void onStart() {
        super.onStart();
        permissions = this.getIntent().getStringArrayExtra(KEY_PERMISSIONS);
        requestCode = this.getIntent().getIntExtra(KEY_REQUEST_CODE, 0);

        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }
    
    // Requires SDK 23+
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (PermissionRequestActivity.requestCode == requestCode) {
            VERIFIED.set(verify(permissions));
            Platform.runLater(() -> {
                try {
                    NestedEventLoopInvoker.exitNestedEventLoop(VERIFIED, null);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "PermissionRequestActivity: exitNestedEventLoop failed", e);
                }
            });
        }
        finish();
    }

    private static void requestPermission(String[] permissionsName) {
        Intent intent = new Intent(FXActivity.getInstance(), PermissionRequestActivity.class);
        intent.putExtra(KEY_PERMISSIONS, permissionsName);
        intent.putExtra(KEY_REQUEST_CODE, PERMISSION_REQUEST_CODE);
        FXActivity.getInstance().startActivityForResult(intent, PERMISSION_REQUEST_CODE);
    }
    
    private static boolean verify(String[] permissionsName) {
        for (String permission : permissionsName) {
            int result = ContextCompat.checkSelfPermission(FXActivity.getInstance(), permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                LOG.log(Level.WARNING, String.format("Permission %s not granted", permission));
                return false;
            }
        }
        return true;
    }
    
    private static void checkPermissions(String... permissionsName) {
        requestPermission(permissionsName);
        try {
            NestedEventLoopInvoker.enterNestedEventLoop(VERIFIED);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "PermissionRequestActivity: enterNestedEventLoop failed", e);
        }
    }
    
    public static boolean verifyPermissions(String... permissionsName) {
        VERIFIED.set(verify(permissionsName));
        if (! VERIFIED.get()) {
            if (Platform.isFxApplicationThread()) {
                checkPermissions(permissionsName);
             } else {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    checkPermissions(permissionsName);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                } catch (InterruptedException ex) {
                    LOG.log(Level.WARNING, "Error count down latch", ex);
                }
            }
        }
        return VERIFIED.get();
    }
}
