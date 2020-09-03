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

import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import com.gluonhq.charm.down.plugins.StatusBarService;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.paint.Color;
import javafxports.android.FXActivity;

/**
 * Note: Service supported only from Android API 21
 * TODO: Remove Android dependencies
 */
public class AndroidStatusBarService implements StatusBarService {

    private static final Logger LOG = Logger.getLogger(AndroidStatusBarService.class.getName());

    @Override
    public void setColor(Color color) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Logger.getLogger(AndroidStatusBarService.class.getName()).log(Level.WARNING, "StatusBar service is not supported for the current Android version");
            return;
        }
        final FXActivity activity = FXActivity.getInstance();

        if (activity == null) {
            LOG.log(Level.WARNING, "FXActivity not found. This service is not allowed when "
                            + "running in background mode or from wearable");
            return;
        }

        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();

            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            window.setStatusBarColor(getIntColor(color));
        });
    }

    private int getIntColor(Color color) {
        int intColor = (int) Math.round(color.getBlue() * 0xFF);
        intColor += (int) Math.round(color.getGreen() * 0xFF) << 8;
        intColor += (int) Math.round(color.getRed() * 0xFF) << 16;
        intColor += (int) Math.round(color.getOpacity() * 0xFF) << 24;

        return intColor;
    }
}