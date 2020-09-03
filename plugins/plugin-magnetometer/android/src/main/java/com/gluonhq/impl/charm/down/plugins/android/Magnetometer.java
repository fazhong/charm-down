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

import static android.content.Context.SENSOR_SERVICE;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.gluonhq.charm.down.plugins.MagnetometerReading;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;


public class Magnetometer implements SensorEventListener {

    private final int frequency;
    private final ObjectProperty<MagnetometerReading> reading;

    private final SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private boolean sensorReady = false;

    private boolean initialized = false;

    public Magnetometer(int frequency) {
        this.frequency = frequency;
        reading = new SimpleObjectProperty<>();
        sensorManager = (SensorManager) getApplication().getSystemService(SENSOR_SERVICE);
    }
    
    public final void start() {
        if (!initialized) {
            int rateInMillis = frequency > 0 ? (int) (1000d / (double) frequency) : 100;
            if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                sensorManager.registerListener(this, 
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
                    rateInMillis);
            } else {
                Logger.getLogger(Accelerometer.class.getName()).log(Level.WARNING, "No accelerometer available");
            }
            
            if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
                sensorManager.registerListener(this, 
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), 
                    rateInMillis);
            } else {
                Logger.getLogger(Magnetometer.class.getName()).log(Level.WARNING, "No magnetometer available");
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
        if (se.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(se.values, 0, accelerometerReading, 0, accelerometerReading.length);
        } else if (se.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(se.values, 0, magnetometerReading, 0, magnetometerReading.length);
            sensorReady = true;
        }
        
        if (sensorReady) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles);

                MagnetometerReading r = new MagnetometerReading(magnetometerReading[0], magnetometerReading[1], magnetometerReading[2], getMagnitude(), 
                        orientationAngles[0], orientationAngles[1], orientationAngles[2]);
                Platform.runLater(() -> reading.setValue(r));
            }
            sensorReady = false;
        }
    }
    
    private double getMagnitude() {
        return Math.sqrt(magnetometerReading[0] * magnetometerReading[0] +
               magnetometerReading[1] * magnetometerReading[1] +
               magnetometerReading[2] * magnetometerReading[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }
    
    public final ObjectProperty<MagnetometerReading> readingProperty() {
        return reading;
    }
    
}
