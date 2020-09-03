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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings.Secure;
import com.gluonhq.charm.down.Platform;
import com.gluonhq.charm.down.plugins.DeviceService;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import java.util.UUID;

public class AndroidDeviceService implements DeviceService {
    
    private static final String PREFS_FILE = "device_id.xml";
    private static final String PREFS_DEVICE_ID = "device_id";
    
    @Override
    public String getModel() {
        return Build.MODEL + " ("+ Build.MANUFACTURER + ")";
    }

    @Override
    public String getUuid() {
        final Application application = getApplication();
        final SharedPreferences prefs = application.getSharedPreferences(PREFS_FILE, 0);
        String uuid = prefs.getString(PREFS_DEVICE_ID, null);
        if (uuid != null) {
            return uuid;
        }

        final String androidId = Secure.getString(application.getContentResolver(), Secure.ANDROID_ID);
        uuid = UUID.nameUUIDFromBytes(androidId.getBytes()).toString();
        prefs.edit().putString(PREFS_DEVICE_ID, uuid).commit();
        return uuid;
    }

    @Override
    public String getPlatform() {
        return Platform.getCurrent().name();
    }

    @Override
    public String getVersion() {
        return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
    }

    @Override
    public String getSerial() {
        return Build.SERIAL;
    }

    @Override
    public boolean isWearable() {
        return getApplication().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

}
