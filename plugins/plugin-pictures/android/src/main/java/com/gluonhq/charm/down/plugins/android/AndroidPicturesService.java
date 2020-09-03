/*
 * Copyright (c) 2016, 2018 Gluon
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
import static android.app.Activity.RESULT_OK;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.content.FileProvider;
import com.gluonhq.charm.down.plugins.PicturesService;
import com.gluonhq.impl.charm.down.plugins.Constants;
import com.gluonhq.impl.charm.down.plugins.NestedEventLoopInvoker;
import com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;
import javafxports.android.FXActivity;

/**
 * Android implementation of the PicturesService
 */
public class AndroidPicturesService implements PicturesService {

    private static final Logger LOG = Logger.getLogger(AndroidPicturesService.class.getName());

    private static final FXActivity ACTIVITY = FXActivity.getInstance();

    private static final int SELECT_PICTURE = 1;
    private static final int TAKE_PICTURE = 2;    

    private final String authority = ACTIVITY.getPackageName() + ".fileprovider";
    
    private static final ObjectProperty<File> imageFile = new SimpleObjectProperty<>();
    private static ObjectProperty<Image> result;
    
    private boolean debug;
    private String photoPath;

    public AndroidPicturesService() {
        if ("true".equals(System.getProperty(Constants.DOWN_DEBUG))) {
            debug = true;
        }
    }
    
    @Override
    public Optional<Image> takePhoto(boolean savePhoto) {
        if (! verifyPermissions()) {
            LOG.log(Level.WARNING, "Permission verification failed");
            return Optional.empty();
        }
        result = new SimpleObjectProperty<>();
        takePicture(savePhoto);
        return Optional.ofNullable(result.get());
    }
    
    @Override
    public Optional<Image> loadImageFromGallery() {
        if (! verifyPermissions()) {
            LOG.log(Level.WARNING, "Permission verification failed");
            return Optional.empty();
        }
        result = new SimpleObjectProperty<>();
        selectPicture();
        return Optional.ofNullable(result.get());
    }

    @Override
    public Optional<File> getImageFile() {
        return Optional.ofNullable(imageFile.get());
    }
    
    private void takePicture(boolean savePhoto) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        // Create the file where the photo should go
        File photo = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "IMG_"+ timeStamp + ".jpg");
        if (photo.exists()) {
            photo.delete();
        } else {
            photo.getParentFile().mkdirs();
        }
        photoPath = "file:" + photo.getAbsolutePath();
        
        if (debug) {
            LOG.log(Level.INFO, "Picture file: " + photoPath);
        }
        Uri photoUri = FileProvider.getUriForFile(ACTIVITY, authority, photo);
        
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        
        if (ACTIVITY == null) {
            LOG.log(Level.WARNING, "FXActivity not found. This service is not allowed when "
                            + "running in background mode or from wearable");
            return;
        }

        ACTIVITY.setOnActivityResultHandler((requestCode, resultCode, data) -> {
            if (requestCode == TAKE_PICTURE) {
                if (resultCode == RESULT_OK) {
                    if (debug) {
                        LOG.log(Level.INFO, "Picture successfully taken at " + photoPath);
                    }
                    Uri imageUri = Uri.parse(photoPath);
                    File photoFile = new File(imageUri.getPath());
                    if (photoFile.exists()) {
                        if (debug) {
                            LOG.log(Level.INFO, "Image file located at " + photoFile.getAbsolutePath());
                        }
                        try {
                            Image image = new Image(new FileInputStream(photoFile));
                        
                            if (!savePhoto) {
                                if (debug) {
                                    LOG.log(Level.INFO, "Removing photo: " + photoFile);
                                }
                                photoFile.delete();
                            } else {
                                if (debug) {
                                    LOG.log(Level.INFO, "Setting image file: " + photoFile);
                                }
                                imageFile.set(photoFile);
                            }
                            // media scanner to rescan DIRECTORY_PICTURES after an image is saved/deleted
                            MediaScannerConnection.scanFile(ACTIVITY, new String[] { photoFile.toString() }, null, null);
                            if (debug) {
                                LOG.log(Level.INFO, "Set image " + image);
                            }
                            // set image
                            result.set(image);
                        } catch (FileNotFoundException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    } else {
                        LOG.log(Level.WARNING, "Picture file doesn't exist");
                    }
                }
                Platform.runLater(() -> {
                    try {
                        NestedEventLoopInvoker.exitNestedEventLoop(result, null);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "GalleryActivity: exitNestedEventLoop failed: " + e);
                    }
                });
            }
        });

        // check for permissions
        if (intent.resolveActivity(ACTIVITY.getPackageManager()) != null) {
            ACTIVITY.startActivityForResult(intent, TAKE_PICTURE);
            try {
                NestedEventLoopInvoker.enterNestedEventLoop(result);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "GalleryActivity: enterNestedEventLoop failed: " + e);
            } 
        } else {
           LOG.log(Level.WARNING, "GalleryActivity: resolveActivity failed");
        }
    }
    
    private void selectPicture() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("image/*");
        
        if (ACTIVITY == null) {
            LOG.log(Level.WARNING, "FXActivity not found. This service is not allowed when "
                            + "running in background mode or from wearable");
            return;
        }

        ACTIVITY.setOnActivityResultHandler((requestCode, resultCode, data) -> {
            if (requestCode == SELECT_PICTURE) { 
                if (resultCode == RESULT_OK) {
                    if (debug) {
                        LOG.log(Level.INFO, "Picture successfully selected with: " + data);
                    }
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        try {
                            if (debug) {
                                LOG.log(Level.INFO, "Copy image file");
                            }
                            File cachePhotoFile = copyFile(selectedImageUri);
                            if (debug) {
                                LOG.log(Level.INFO, "Setting image file: " + cachePhotoFile.getAbsolutePath());
                            }
                            imageFile.set(cachePhotoFile);
                            
                            final Image image = new Image(ACTIVITY.getContentResolver().openInputStream(selectedImageUri));
                            if (debug) {
                                LOG.log(Level.INFO, "Set image " + image);
                            }
                            result.set(image);
                        } catch (FileNotFoundException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                }
                Platform.runLater(() -> {
                    try {
                        NestedEventLoopInvoker.exitNestedEventLoop(result, null);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "GalleryActivity: exitNestedEventLoop failed: " + e);
                    }
                });
            }
        });
        
        // check for permissions
        if (intent.resolveActivity(ACTIVITY.getPackageManager()) != null) {
            ACTIVITY.startActivityForResult(Intent.createChooser(intent,"Select Picture"), SELECT_PICTURE);
            try {
                NestedEventLoopInvoker.enterNestedEventLoop(result);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "GalleryActivity: enterNestedEventLoop failed: " + e);
            }
        } else {
            LOG.log(Level.WARNING, "GalleryActivity: resolveActivity failed");
        }
    }

    private boolean verifyPermissions() {
        boolean cameraEnabled = PermissionRequestActivity.verifyPermissions(Manifest.permission.CAMERA, 
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (! cameraEnabled) {
            LOG.log(Level.WARNING, "Camera disabled");
        }
        return cameraEnabled;
    }
    
    private String getImageName(Uri uri) {
        try (Cursor cursor = ACTIVITY.getContentResolver().query(uri, null, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        }
        return "image.jpg";
    }
    
    private File copyFile(Uri uri) {
        File selectedFile = new File(ACTIVITY.getCacheDir(), getImageName(uri));
        try (InputStream is = ACTIVITY.getContentResolver().openInputStream(uri);
                OutputStream os = new FileOutputStream(selectedFile)) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } catch (FileNotFoundException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return selectedFile;
    }

}
