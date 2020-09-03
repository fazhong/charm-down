/*
 * Copyright (c) 2017, 2018 Gluon
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

import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import com.gluonhq.charm.down.plugins.ShareService;
import com.gluonhq.impl.charm.down.plugins.Constants;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafxports.android.FXActivity;


public class AndroidShareService implements ShareService {

    private static final Logger LOG = Logger.getLogger(AndroidShareService.class.getName());
    private static final FXActivity ACTIVITY = FXActivity.getInstance();
    private boolean debug;
    
    private final String authority;

    public AndroidShareService() {
        if ("true".equals(System.getProperty(Constants.DOWN_DEBUG))) {
            debug = true;
        }
        authority = ACTIVITY.getPackageName() + ".fileprovider";
    }
    
    @Override
    public void share(String contentText) {
        share(null, contentText);
    }

    @Override
    public void share(String subject, String contentText) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND); 
        sharingIntent.setType("text/plain");
        if (subject != null && ! subject.isEmpty()) {
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        if (contentText != null && ! contentText.isEmpty()) {
            sharingIntent.putExtra(Intent.EXTRA_TEXT, contentText);
        } else {
            LOG.log(Level.WARNING, "Error: A non empty contentText is required");
            return;
        }
        if (debug) {
            LOG.log(Level.INFO, "Start text sharing intent");
        }
        ACTIVITY.startActivity(Intent.createChooser(sharingIntent, null));
    }

    @Override
    public void share(String type, File file) {
        share(null, null, type, file);
    }

    @Override
    public void share(String subject, String contentText, String type, File file) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND); 
        if (subject != null && ! subject.isEmpty()) {
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        if (contentText != null && ! contentText.isEmpty()) {
            sharingIntent.putExtra(Intent.EXTRA_TEXT, contentText);
        }
        if (type != null && ! type.isEmpty()) {
            sharingIntent.setType(type);
        } else {
            LOG.log(Level.WARNING, "Error: A non empty type is required");
            return;
        }
        if (file != null && file.exists()) {
            if (debug) {
                LOG.log(Level.INFO, String.format("File to share: %s", file));
                LOG.log(Level.INFO, "Application name provider: " + authority);
            }
            final Uri uriFile = FileProvider.getUriForFile(ACTIVITY, authority, file);
            if (debug) {
                LOG.log(Level.INFO, String.format("Shared file URI: %s", file));
            }
            sharingIntent.putExtra(Intent.EXTRA_STREAM, uriFile);
            sharingIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } else {
            LOG.log(Level.WARNING, "Error: A non empty file is required");
            return;
        }
        if (debug) {
            LOG.log(Level.INFO, "Start file sharing intent");
        }
        ACTIVITY.startActivity(Intent.createChooser(sharingIntent, null));
    }

}