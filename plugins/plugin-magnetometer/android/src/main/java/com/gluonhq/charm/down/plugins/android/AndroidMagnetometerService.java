/*
 * Copyright (c) 2016, Gluon
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

import com.gluonhq.charm.down.Services;
import com.gluonhq.charm.down.plugins.LifecycleService;
import com.gluonhq.charm.down.plugins.MagnetometerReading;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import com.gluonhq.charm.down.plugins.MagnetometerService;
import com.gluonhq.charm.down.plugins.LifecycleEvent;
import com.gluonhq.impl.charm.down.plugins.android.Magnetometer;

public class AndroidMagnetometerService implements MagnetometerService {

    private final Magnetometer magnetometer;
    private final ReadOnlyObjectWrapper<MagnetometerReading> reading;

    public AndroidMagnetometerService() {
        this.magnetometer = new Magnetometer(FREQUENCY);
        reading = new ReadOnlyObjectWrapper<>();
        reading.bind(magnetometer.readingProperty());
        
        Services.get(LifecycleService.class).ifPresent(l -> {
            l.addListener(LifecycleEvent.PAUSE, magnetometer::stop);
            l.addListener(LifecycleEvent.RESUME, magnetometer::start);
        });
        magnetometer.start();
    }
    
    @Override
    public ReadOnlyObjectProperty<MagnetometerReading> readingProperty() {
        return reading.getReadOnlyProperty();
    }

    @Override
    public MagnetometerReading getReading() {
        return reading.get();
    }
    
}
