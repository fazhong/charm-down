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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Intent;
import com.gluonhq.charm.down.plugins.ConnectivityService;
import com.gluonhq.charm.down.Services;

import com.gluonhq.charm.down.plugins.LifecycleEvent;
import com.gluonhq.charm.down.plugins.LifecycleService;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/**
 * Note: Requires this permission:
 * {@code &lt;uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/&gt;}
 */

public class AndroidConnectivityService implements ConnectivityService {

    private final static Application APPLICATION = getApplication();
            
    private final ReadOnlyBooleanWrapper connected;
    private final ConnectivityManager connectivityManager;

    private final ConnectivityReceiver connectivityReceiver;
    private final IntentFilter intentFilter;
    private boolean isRegistered = false;

    public AndroidConnectivityService() {
        connected = new ReadOnlyBooleanWrapper();
        connectivityManager = (ConnectivityManager) APPLICATION.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        connectivityReceiver = new ConnectivityReceiver();
        intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        
        Services.get(LifecycleService.class).ifPresent(l -> {
            l.addListener(LifecycleEvent.PAUSE, () -> unregisterReceiver());
            l.addListener(LifecycleEvent.RESUME, () -> {
                checkConnection();
                registerReceiver();
            });
        });

        // initial status
        checkConnection();
        registerReceiver();
    }
            
    @Override
    public ReadOnlyBooleanProperty connectedProperty() {
        return connected.getReadOnlyProperty();
    }

    @Override
    public boolean isConnected() {
        return checkConnection();
    }
    
    private boolean checkConnection() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        final boolean c = activeNetwork != null ? activeNetwork.isConnectedOrConnecting() : false;
        if (c != connected.get()) {
            Platform.runLater(() -> connected.setValue(c));
        }
        return c;
    }
    
    private void registerReceiver() {
        if (!isRegistered) {
            APPLICATION.registerReceiver(connectivityReceiver, intentFilter);
            isRegistered = true;
        }
    }
    
    private void unregisterReceiver() {
        if (isRegistered) {
            APPLICATION.unregisterReceiver(connectivityReceiver);
            isRegistered = false;
        }
    }

    private class ConnectivityReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context cntxt, Intent intent) {
            checkConnection();
        }
    }
}