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
package com.gluonhq.charm.down.plugins.ios;

import com.gluonhq.charm.down.plugins.LifecycleService;
import com.gluonhq.charm.down.plugins.LifecycleEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class IOSLifecycleService implements LifecycleService {

    static {
        IOSPlatform.init();
        System.loadLibrary("Lifecycle");
        initLifecycle();
    }

    private final static Map<LifecycleEvent, List<Runnable>> MAP_EVENTS = new HashMap<>();
    
    @Override
    public void addListener(LifecycleEvent eventType, Runnable eventHandler) {
        List<Runnable> list = MAP_EVENTS.get(eventType);
        if (list == null) {
            list = new ArrayList<>();
        } else {
            for (Runnable r : list) {
                if (r == eventHandler) {
                    return;
                }
            }
        }
        list.add(eventHandler);
        MAP_EVENTS.put(eventType, list);
    }

    @Override
    public void removeListener(LifecycleEvent eventType, Runnable eventHandler) {
        List<Runnable> list = MAP_EVENTS.get(eventType);
        if (list == null) {
            return;
        } 
        
        Iterator<Runnable> iterator = list.iterator();
        while (iterator.hasNext()) {
            Runnable next = iterator.next();
            if (next == eventHandler) {
                iterator.remove();
                break;
            }
        } 
    }

    @Override public void shutdown() {
        // no-op
    }
    
    // native
    private static native void initLifecycle(); // init IDs for java callbacks from native
    private static native void stopEvents();

    // callback
    public static void setEvent(String v) {
        if (v != null && !v.isEmpty()) {
            switch (v) {
                case "pause": 
                    doCheck(LifecycleEvent.PAUSE);
                    break;
                case "resume": 
                    doCheck(LifecycleEvent.RESUME);
                    break;
                default: break;
            }
        }
    }
    
    private static void doCheck(LifecycleEvent expected) {
        List<Runnable> list = MAP_EVENTS.get(expected);
        if (list == null) {
            return;
        }
        for (Runnable r : list) {
            run(r);
        }
    }
    
    private static void run(Runnable eventHandler) {
        if (eventHandler == null) {
            return;
        } 
        if (javafx.application.Platform.isFxApplicationThread()) {
            eventHandler.run();
        } else {
            javafx.application.Platform.runLater(eventHandler::run);
        }
    }
}
