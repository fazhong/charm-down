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

import android.content.Context;
import android.content.SharedPreferences;
import com.gluonhq.charm.down.plugins.SettingsService;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of the
 * {@link SettingsService SettingsService} for the
 * Android platform. It makes use of the application's SharedPreferences to
 * manage the settings.
 */
public class AndroidSettingsService implements SettingsService {

    private static final Logger LOG = Logger.getLogger(AndroidSettingsService.class.getName());

    private final SharedPreferences settings;

    public AndroidSettingsService() {
        settings = getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    @Override
    public void store(String key, String value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(key, value);
        editor.commit();

        LOG.log(Level.INFO, String.format("Updated setting %s = \"%s\"", key, value));
    }

    @Override
    public void remove(String key) {
        String value = retrieve(key);

        SharedPreferences.Editor editor = settings.edit();
        editor.remove(key);
        editor.commit();

        LOG.log(Level.INFO, String.format("Removed setting %s = \"%s\"", key, value));
    }

    @Override
    public String retrieve(String key) {
        return settings.getString(key, null);
    }

}
