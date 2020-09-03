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
package com.gluonhq.impl.charm.down.plugins.android;

import static android.content.Context.SENSOR_SERVICE;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.gluonhq.charm.down.plugins.Acceleration;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class Accelerometer implements SensorEventListener {

    private final boolean filterGravity;
    private final int frequency;
    private final ObjectProperty<Acceleration> acceleration;

    private final SensorManager sensorManager;
    
    private boolean initialized = false;
    private final double alpha = 0.8;
    private final double[] gravity = new double[3];

    public Accelerometer(boolean filterGravity, int frequency) {
        this.filterGravity = filterGravity;
        this.frequency = frequency;
        acceleration = new SimpleObjectProperty<>();
        sensorManager = (SensorManager) getApplication().getSystemService(SENSOR_SERVICE);
    }
    
    public final void start() {
        if (!initialized) {
            if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                int rateInMillis = frequency > 0 ? (int) (1000d / (double) frequency) : 10;
                sensorManager.registerListener(this, 
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
                    rateInMillis);
            } else {
                Logger.getLogger(Accelerometer.class.getName()).log(Level.WARNING, "No accelerometer available");
            }
            initialized = true;
        }
    }
    
    public final void stop() {
        if (initialized) {
            sensorManager.unregisterListener(this);
            initialized = false;
        }
    }
    
    @Override
    public void onSensorChanged(SensorEvent se) {
        if (se.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            if (filterGravity) {
                // filter to remove gravity
                gravity[0] = alpha * gravity[0] + (1 - alpha) * se.values[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * se.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * se.values[2];
            }
            double x = se.values[0] - gravity[0];
            double y = se.values[1] - gravity[1];
            double z = se.values[2] - gravity[2];
            Acceleration a = new Acceleration(x, y, z, LocalDateTime.now());
            Platform.runLater(() -> acceleration.setValue(a));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }
    
    public final ObjectProperty<Acceleration> accelerationProperty() {
        return acceleration;
    }
    
}
