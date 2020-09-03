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
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import com.gluonhq.charm.down.plugins.OrientationService;
import com.gluonhq.charm.down.Services;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Orientation;

import com.gluonhq.charm.down.plugins.LifecycleEvent;
import com.gluonhq.charm.down.plugins.LifecycleService;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import java.util.Optional;
import javafx.application.Platform;

public class AndroidOrientationService implements OrientationService {

    private static final Application APPLICATION = getApplication();
    
    private final ReadOnlyObjectWrapper<Orientation> orientation;

    private final OrientationReceiver orientationReceiver;
    private final IntentFilter intentFilter;
    private boolean isRegistered = false;
    
    public AndroidOrientationService() {
        orientation = new ReadOnlyObjectWrapper<>();
        
        orientationReceiver = new OrientationReceiver();
        intentFilter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        
        Services.get(LifecycleService.class).ifPresent(l -> {
            l.addListener(LifecycleEvent.PAUSE, () -> unregisterReceiver());
            l.addListener(LifecycleEvent.RESUME, () -> {
                registerReceiver();
                getOrientation().ifPresent(orientation::setValue);
            });
        });
        registerReceiver();
        getOrientation().ifPresent(orientation::setValue);
    }
    
    @Override
    public ReadOnlyObjectProperty<Orientation> orientationProperty() {
        return orientation.getReadOnlyProperty();
    }

    @Override
    public final Optional<Orientation> getOrientation() {
        switch (APPLICATION.getResources().getConfiguration().orientation) {
            case Configuration.ORIENTATION_LANDSCAPE: return Optional.of(Orientation.HORIZONTAL);
            case Configuration.ORIENTATION_PORTRAIT: return Optional.of(Orientation.VERTICAL);
            default: return Optional.empty();
        }
    }
    
    private void registerReceiver() {
        if (!isRegistered) {
            APPLICATION.registerReceiver(orientationReceiver, intentFilter);
            isRegistered = true;
        }
    }
    
    private void unregisterReceiver() {
        if (isRegistered) {
            APPLICATION.unregisterReceiver(orientationReceiver);
            isRegistered = false;
        }
    }

    class OrientationReceiver extends BroadcastReceiver {
    
        @Override
        public void onReceive(Context cntxt, Intent intent) {
            getOrientation().ifPresent(o -> {
                if (orientation.get() == null || !orientation.get().equals(o)) {
                    Platform.runLater(() -> orientation.setValue(o));
                }
            });
        }
        
    } 
}
