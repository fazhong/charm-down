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
package com.gluonhq.charm.down.plugins;

/**
 * Using the dialer service, you can initiate a call to the provided phone number. The implementation
 * will use the device's default dial application to make the phone call.
 *
 * <p><b>Example</b></p>
 * <pre>
 * {@code Services.get(DialerService.class).ifPresent(service -> {
 *      service.call("+32987123456");
 *  });}</pre>
 *
 * <p><b>Android Configuration</b></p>
 * <p>The permission <code>android.permission.CALL_PHONE</code> needs to be added.</p>
 * <pre>
 * {@code <manifest ...>
 *    <uses-permission android:name="android.permission.CALL_PHONE"/>
 *    ...
 *   <activity android:name="com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity" />
 *  </manifest>}</pre>
 *
 * <p><b>iOS Configuration</b>: none</p>
 *
 * @since 3.0.0
 */
public interface DialerService {

    /**
     * Starts a phone call to the given number with the native dial application.
     *
     * @param number A valid telephone number, without spaces, optionally including an international prefix.
     */
    void call(String number);
}
