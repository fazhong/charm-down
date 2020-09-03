/*
 * Copyright (c) 2016, 2017, Gluon
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import static android.os.BatteryManager.BATTERY_PLUGGED_AC;
import static android.os.BatteryManager.BATTERY_PLUGGED_USB;

import com.gluonhq.charm.down.plugins.LifecycleService;
import com.gluonhq.charm.down.Services;
import com.gluonhq.charm.down.plugins.BatteryService;
import com.gluonhq.charm.down.plugins.LifecycleEvent;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyFloatProperty;
import javafx.beans.property.ReadOnlyFloatWrapper;
import javafxports.android.FXDalvikEntity;

public class AndroidBatteryService implements BatteryService {

    private static final Logger LOG = Logger.getLogger(AndroidBatteryService.class.getName());

    private static final Application APPLICATION = getApplication();

    private final ReadOnlyBooleanWrapper pluggedIn;
    private final ReadOnlyFloatWrapper batteryLevel;

    private BatteryReceiver batteryReceiver;
    private IntentFilter intentFilter;
    private boolean isRegistered = false;
    
    public AndroidBatteryService() {
        
        if (FXDalvikEntity.getActivity() != null) {
        
            batteryReceiver = new BatteryReceiver();

            // the IntentFilter requires an Activity
            intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = APPLICATION.registerReceiver(null, intentFilter);

            pluggedIn = new ReadOnlyBooleanWrapper(isPluggedIn(batteryStatus));
            batteryLevel = new ReadOnlyFloatWrapper(batteryLevel(batteryStatus));

            Services.get(LifecycleService.class).ifPresent(l -> {
                l.addListener(LifecycleEvent.PAUSE, () -> unregisterReceiver());
                l.addListener(LifecycleEvent.RESUME, () -> registerReceiver());
            });
            registerReceiver();
        } else {
            pluggedIn = new ReadOnlyBooleanWrapper(false);
            batteryLevel = new ReadOnlyFloatWrapper(-1f);
            
            LOG.log(Level.WARNING, "Activity not found. This service is not allowed when "
                    + "running in background mode ");
        }
    }
    
    @Override
    public float getBatteryLevel() {
        return batteryLevel.get();
    }

    @Override
    public ReadOnlyFloatProperty batteryLevelProperty() {
        return batteryLevel.getReadOnlyProperty();
    }

    @Override
    public boolean isPluggedIn() {
        return pluggedIn.get();
    }

    @Override
    public ReadOnlyBooleanProperty pluggedInProperty() {
        return pluggedIn.getReadOnlyProperty();
    }
    
    private boolean isPluggedIn(Intent batteryIntent) {
        int chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BATTERY_PLUGGED_AC;
        return usbCharge || acCharge;
    }
    
    private float batteryLevel(Intent batteryIntent) {
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (float) level / (float) scale;
    }
    
    private void registerReceiver() {
        if (!isRegistered) {
            APPLICATION.registerReceiver(batteryReceiver, intentFilter);
            isRegistered = true;
        }
    }
    
    private void unregisterReceiver() {
        if (isRegistered) {
            APPLICATION.unregisterReceiver(batteryReceiver);
            isRegistered = false;
        }
    }
    
    public class BatteryReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            float batteryPct = batteryLevel(intent);
            if (batteryLevel.get() != batteryPct) {
                Platform.runLater(() -> batteryLevel.set(batteryPct));
            }
            boolean charging = isPluggedIn(intent);
            if (pluggedIn.get() != charging) {
                Platform.runLater(() -> pluggedIn.set(charging));
            }
        }
    }

}
