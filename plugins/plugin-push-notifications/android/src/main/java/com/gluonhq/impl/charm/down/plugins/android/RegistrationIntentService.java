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

import android.app.IntentService;
import android.content.Intent;
import static com.gluonhq.charm.down.plugins.android.AndroidPushNotificationsService.debug;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegistrationIntentService extends IntentService {

    private static final Logger LOG = Logger.getLogger(RegistrationIntentService.class.getName());

    public RegistrationIntentService() {
        super("RegistrationIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String authorizedEntity = PushRegistry.getInstance().getAuthorizedEntity();
        if (authorizedEntity == null || authorizedEntity.isEmpty()) {
            LOG.log(Level.WARNING, "Failed to register token: no authorizedEntity found");
            return;
        }
        try {
            InstanceID instanceID = InstanceID.getInstance(this);
            if (instanceID != null) {
                if (debug) {
                    LOG.log(Level.INFO, String.format("Registering authorized entity: %s", authorizedEntity));
                }
                final String token = instanceID.getToken(authorizedEntity, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                if (debug) {
                    LOG.log(Level.INFO, String.format("Got token: %s", token));
                }
                PushRegistry.getInstance().setToken(token);
            } else {
                LOG.log(Level.WARNING, "Failed to register token: no instanceID found");
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Failed to get token from InstanceID.", ex);
        }
    }
}
