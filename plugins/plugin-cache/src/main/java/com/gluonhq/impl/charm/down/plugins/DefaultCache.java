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
package com.gluonhq.impl.charm.down.plugins;

import com.gluonhq.charm.down.plugins.Cache;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @param <K> type for the key
 * @param <V> type for the value
 */
public class DefaultCache<K, V> implements Cache<K,V> {

    private Map<K, SoftReference<V>> map = new HashMap<>();

    public DefaultCache () {
    }
    
    @Override
    public V get(K key) {
        SoftReference<V> ref = map.get(key);
        if (ref != null) {
            return ref.get();
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        if (key == null) {
            throw new NullPointerException ("Cache key should not be null");
        }
        if (value == null) {
            throw new NullPointerException ("Cache value should not be null");
        }
        SoftReference<V> ref = new SoftReference<>(value);
        map.put(key, ref);
    }

    @Override
    public boolean remove(K key) {
        boolean answer = map.containsKey(key);
        map.remove(key);
        return answer;
    }

    @Override
    public void removeAll() {
        map.clear();
    }

}
