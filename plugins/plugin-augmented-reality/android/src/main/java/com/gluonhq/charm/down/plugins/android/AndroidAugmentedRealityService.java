/*
 * Copyright (c) 2018, Gluon
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

import android.content.Intent;
import android.net.Uri;
import com.gluonhq.charm.down.plugins.ar.ARModel;
import com.gluonhq.impl.charm.down.plugins.DefaultAugmentedRealityService;
import com.gluonhq.impl.charm.down.plugins.android.AndroidBridge;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafxports.android.FXActivity;

public class AndroidAugmentedRealityService extends DefaultAugmentedRealityService {

    private static final Logger LOG = Logger.getLogger(AndroidAugmentedRealityService.class.getName());
    private static final String ARCORE_APK = "com.google.ar.core";
    private static final int REQUEST_CODE = 123456;
    
    private final ReadOnlyBooleanWrapper cancelWrapper = new ReadOnlyBooleanWrapper();
    
    private final AndroidBridge bridge;
    
    public AndroidAugmentedRealityService() {
        this.bridge = new AndroidBridge(this, debug);
    }

    @Override
    public Availability checkAR(Runnable afterInstall) {
        final Availability availability = bridge.checkAvailability();
        if (availability == Availability.ARCORE_NOT_INSTALLED) {
            LOG.log(Level.INFO, "ARCore not installed but supported. Intent to install ARCore");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ARCORE_APK));
            FXActivity.getInstance().setOnActivityResultHandler((r, c, d) -> {
                if (REQUEST_CODE == r) {
                    if (afterInstall != null && bridge.checkAvailability() == Availability.AR_SUPPORTED) {
                        if (debug) LOG.log(Level.INFO, "Run after ARCore was installed");
                        Platform.runLater(afterInstall);
                    }
                }
            });
            FXActivity.getInstance().startActivityForResult(intent, REQUEST_CODE);
        }
        return availability;
    }

    @Override
    public void setModel(ARModel model) {
        bridge.setModel(model);
    }

    @Override
    public void showAR() {
        if (debug) LOG.log(Level.INFO, "Show AR");
        cancelWrapper.set(false);
        if (bridge.show(() -> cancelWrapper.set(true))) {
            if (debug) LOG.log(Level.INFO, "Error starting AR session");
        }
    }

    @Override
    public void debugAR(boolean enable) {
        bridge.enableDebug(enable);
    }
    
    @Override
    public ReadOnlyBooleanProperty cancelled() {
        return cancelWrapper.getReadOnlyProperty();
    }
    
}
