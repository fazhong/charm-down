/*
 * Copyright (c) 2016, 2018 Gluon
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

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import com.gluonhq.charm.down.plugins.BarcodeScanService;
import com.gluonhq.impl.charm.down.plugins.NestedEventLoopInvoker;
import com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity;
import com.gluonhq.impl.charm.down.plugins.android.scan.zxing.Intents.Scan;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafxports.android.FXActivity;

/**
 * Requires this permission: android.permission.CAMERA
 * 
 * And this activity added to the AndroidManifest.xml:
 * 
 * <pre>
 * {@code
 *  <activity android:name="com.gluonhq.impl.charm.down.plugins.android.scan.zxing.CaptureActivity"
 *        android:screenOrientation="sensorLandscape"
 *        android:clearTaskOnLaunch="true"
 *       android:stateNotNeeded="true"
 *        android:windowSoftInputMode="stateAlwaysHidden">
 *         <intent-filter>
 *             <action android:name="com.gluonhq.charm.down.plugins.android.scan.SCAN"/>
 *            <category android:name="android.intent.category.DEFAULT"/>
 *          </intent-filter>
 *    </activity>
 *    <activity android:name="com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity" />
 *    }
 * </pre>
 */
public class AndroidBarcodeScanService implements BarcodeScanService {

    private static final Logger LOG = Logger.getLogger(AndroidBarcodeScanService.class.getName());

    private final Intent scanIntent;

    private static final int SCAN_CODE = 10002;
    public static final String SCAN_VIEW_TITLE = "ScanViewTitle";
    public static final String SCAN_VIEW_LEGEND = "ScanViewLegend";
    public static final String SCAN_VIEW_RESULT = "ScanViewResult";

    public AndroidBarcodeScanService() {
        scanIntent = new Intent(Scan.ACTION);
        scanIntent.addCategory(Intent.CATEGORY_DEFAULT);
    }

    @Override
    public Optional<String> scan() {
        return scan("", "", "Found plain text");
    }

    @Override
    public Optional<String> scan(String title, String legend, String resultText) {
        StringProperty result = new SimpleStringProperty();

        scanIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        scanIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        if (title != null && ! title.isEmpty()) {
            scanIntent.putExtra(SCAN_VIEW_TITLE, title);
        }
        
        if (legend != null && ! legend.isEmpty()) {
            scanIntent.putExtra(SCAN_VIEW_LEGEND, legend);
        }

        if (resultText != null && ! resultText.isEmpty()) {
            scanIntent.putExtra(SCAN_VIEW_RESULT, resultText);
        }

        if (FXActivity.getInstance() == null) {
            LOG.log(Level.WARNING, "FXActivity not found. This service is not allowed when "
                            + "running in background mode or from wearable");
            return Optional.empty();
        }
        
        boolean cameraEnabled = PermissionRequestActivity.verifyPermissions(Manifest.permission.CAMERA);
        if (! cameraEnabled) {
            LOG.log(Level.WARNING, "Camera disabled");
            return Optional.empty();
        }

        FXActivity.getInstance().setOnActivityResultHandler((requestCode, resultCode, data) -> {
            if (requestCode == SCAN_CODE) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // a barcode was scanned
                    result.setValue((String) data.getExtras().get("SCAN_RESULT"));
                }
                Platform.runLater(() -> {
                    try {
                        NestedEventLoopInvoker.exitNestedEventLoop(result, null);
                    } catch (Exception e) {
                        System.out.println("ScanActivity: exitNestedEventLoop failed: " + e);
                    }
                });
            }
        });
        
        FXActivity.getInstance().startActivityForResult(scanIntent, SCAN_CODE);
        try {
            NestedEventLoopInvoker.enterNestedEventLoop(result);
        } catch (Exception e) {
            System.out.println("ScanActivity: enterNestedEventLoop failed: " + e);
        } 
        
        return Optional.ofNullable(result.get());
    }
}
