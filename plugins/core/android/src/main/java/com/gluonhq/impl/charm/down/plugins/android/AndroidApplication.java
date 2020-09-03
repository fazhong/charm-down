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
import android.app.Application;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafxports.android.FXDalvikEntity;

/**
 * Util class to retrieve the current android application launched either from 
 * an activity or from a background service.
 * 
 * There are three possible scenarios:
 * 
 * - A regular activity (FXActivity) on mobile, initially running on foreground (or in background if the app was paused)
 * - A wearable activity (FXWearableActivity), running on a wearable device on the foreground
 * - An android service on mobile, running in background, without activity and without UI
 * 
 * Note that all the plugins will run in the first scenario, but not all of them will run in the other two.
 */
public class AndroidApplication {

    private static final Logger LOG = Logger.getLogger(AndroidApplication.class.getName());

    private static Activity activity;
    private static Application application;
    
    /**
     * Returns the Activity's Application (FXActivity or FXWearableActivity) if not null, 
     * else tries to get the Android Application, else throws a runtime exception
     * 
     * @return the Android Application
     */
    public static Application getApplication() {
        if (activity == null) {
            activity = FXDalvikEntity.getActivity();
        }
        
        if (activity != null) {
            return activity.getApplication();
        } 
        
        if (application == null) {
            application = findApplication();
        }
        
        if (application != null) {
            return application;
        } 
        throw new RuntimeException("The service can't access the Application: no Activity or Application were found");
    }
    
    private static Application findApplication() {
        try {
            final Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            final Method method = activityThreadClass.getMethod("currentApplication");
            return (Application) method.invoke(null, (Object[]) null);
        } catch (final ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Error retrieving application", e);
        } catch (final NoSuchMethodException e) {
            LOG.log(Level.SEVERE, "Error retrieving application", e);
        } catch (final IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "Error retrieving application", e);
        } catch (final IllegalAccessException e) {
            LOG.log(Level.SEVERE, "Error retrieving application", e);
        } catch (final InvocationTargetException e) {
            LOG.log(Level.SEVERE, "Error retrieving application", e);
        }
        return null;
    }

}
