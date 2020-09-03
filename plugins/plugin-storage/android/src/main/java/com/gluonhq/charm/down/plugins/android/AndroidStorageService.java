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

import android.Manifest;
import android.os.Environment;
import com.gluonhq.charm.down.plugins.StorageService;
import static com.gluonhq.impl.charm.down.plugins.android.AndroidApplication.getApplication;
import com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity;

import java.io.File;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AndroidStorageService implements StorageService {
    private static final Logger LOG = Logger.getLogger(AndroidStorageService.class.getName());

    @Override
    public Optional<File> getPrivateStorage() {
        return Optional.of(getApplication().getFilesDir());
    }
    
    /*
     * On Android, typical valid subdirectories are:
     * - Alarms
     * - DCIM
     * - Documents
     * - Download
     * - Movies
     * - Music
     * - Notifications
     * - Pictures
     * - Podcasts
     * - Ringtones
     * @param subdirectory
     * @return optional of a File of the subdirectory. The file may or may not exist
     */
    @Override
    public Optional<File> getPublicStorage(String subdirectory) {
        if (! verifyPermissions()) {
            LOG.log(Level.WARNING, "External Storage permissions disabled");
            return Optional.empty();
        }
        return Optional.of(Environment.getExternalStoragePublicDirectory(subdirectory));
    }

    @Override
    public boolean isExternalStorageWritable() {
        if (! verifyPermissions()) {
            LOG.log(Level.WARNING, "Not enough permissions to write to the External Storage");
            return false;
        }
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    @Override
    public boolean isExternalStorageReadable() {
        if (! verifyPermissions()) {
            LOG.log(Level.WARNING, "Not enough permissions to read the External Storage");
            return false;
        }
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
               Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }
    
    private boolean verifyPermissions() {
        return PermissionRequestActivity.verifyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, 
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
    
}
