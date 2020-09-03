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
package com.gluonhq.charm.down.plugins.android;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import com.gluonhq.impl.charm.down.plugins.Constants;
import com.gluonhq.impl.charm.down.plugins.DefaultAudioRecordingService;
import com.gluonhq.impl.charm.down.plugins.android.PermissionRequestActivity;
import com.gluonhq.impl.charm.down.plugins.android.WavHeader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;

public class AndroidAudioRecordingService extends DefaultAudioRecordingService {

    private static final Logger LOG = Logger.getLogger(AndroidAudioRecordingService.class.getName());
    private boolean debug;
    
    // record duration for a chunk, in seconds
    private long CHUNK_RECORD_TIME = 60;  // 1 minute
    
    private static final int DEFAULT_BUFFER_INCREASE_FACTOR = 2;

    private AudioRecord audioRecord = null;
    
    private RecorderTask recorderTask;

    private ExecutorService recordingExecutor;
    private Function<String, Boolean> addChunk;

    public AndroidAudioRecordingService() {
        if ("true".equals(System.getProperty(Constants.DOWN_DEBUG))) {
            debug = true;
        }
    }
    
    @Override
    protected void start(float sampleRate, int sampleSizeInBits, int channels, int chunkRecordTime, Function<String, Boolean> addChunk) {
        boolean audioPermission = PermissionRequestActivity.verifyPermissions(Manifest.permission.RECORD_AUDIO);
        if (! audioPermission) {
            LOG.log(Level.WARNING, "Audio recording disabled");
            return;
        }
        
        CHUNK_RECORD_TIME = chunkRecordTime;
        recorderTask = new RecorderTask((int) sampleRate, sampleSizeInBits, channels);
        recordingExecutor = createExecutor("AudioRecording");
        recordingExecutor.execute(recorderTask);
        this.addChunk = addChunk;
    }

    @Override
    protected void stop() {
        if (recorderTask != null) {
            recorderTask.stop();
        }
        
        if (recordingExecutor != null) {
            recordingExecutor.shutdown();
            try {
                recordingExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) { }
        }
    }
    
    private class RecorderTask extends Task<Void> {

        private TimedAudioCapture timedAudioCapture;
        private ScheduledExecutorService scheduler;
        private CountDownLatch latch;
        private final int sampleRate;
        private final int sampleSizeInBits;
        private final int channels;

        public RecorderTask(int sampleRate, int sampleSizeInBits, int channels) {
            this.sampleRate = sampleRate;
            this.sampleSizeInBits = sampleSizeInBits;
            this.channels = channels;
        }
        
        @Override
        protected Void call() {
            
            final int audioFormat = sampleSizeInBits == 16 ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
            final int channelConfig = channels == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            int increasedBufferSize =  bufferSize * DEFAULT_BUFFER_INCREASE_FACTOR;
            
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, increasedBufferSize);
            
            audioRecord.startRecording();
            latch = new CountDownLatch(1);
            timedAudioCapture = new TimedAudioCapture(sampleRate, sampleSizeInBits, channels, bufferSize);
                    
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                    if (timedAudioCapture != null) {
                        timedAudioCapture.update();
                    }
            }, CHUNK_RECORD_TIME, CHUNK_RECORD_TIME, TimeUnit.SECONDS);
            timedAudioCapture.start();
            
            try {
                latch.await();
            } catch (InterruptedException ex) {
                LOG.log(Level.WARNING, "Error count down latch", ex);
            }
            return null;
        }

        @Override
        protected void cancelled() {
            super.cancelled(); 
            LOG.log(Level.WARNING, "Recording task was cancelled");
            updateRecordingStatus(false);
            stop();
        }

        @Override
        protected void failed() {
            super.cancelled(); 
            LOG.log(Level.WARNING, "Recording task failed");
            updateRecordingStatus(false);
            stop();
        }

        @Override
        protected void succeeded() {
            super.succeeded(); 
            LOG.log(Level.INFO, "Recording task was succeeded"); 
            closeLine();
        }
        
        private void closeLine() {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    scheduler.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) { }
            }
            if (debug) {
                LOG.log(Level.INFO, "Finished recording");
            }
            updateRecordingStatus(false);
        }
        
        public void stop() {
            if (debug) {
                LOG.log(Level.INFO, "Stop recording");
            }
            
            if (timedAudioCapture != null) {
                timedAudioCapture.stop();
            } 
            if (latch != null) {
                latch.countDown();
            }
        }
    }
    
    /**
    * Reads data from the input channel and writes to the output stream, with a 
    * maximum time of CHUNK_RECORD_TIME
    */
    private class TimedAudioCapture {

        private final DateTimeFormatter pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd.HH-mm-ss.SSS");
        private volatile boolean isRunning;
        private volatile boolean update;

        private final Thread thread;
        private final int channels;
        private final int sampleRate;
        private final int sampleSizeInBits;
        private int chunk  = 0;

        public TimedAudioCapture(int sampleRate, int sampleSizeInBits, int channels, int bufferSize) {
            this.sampleRate = sampleRate;
            this.sampleSizeInBits = sampleSizeInBits;
            this.channels = channels;
            thread = new Thread(() -> {
                isRunning = audioRecord != null;
                if (debug) {
                    LOG.log(Level.INFO, "Start recording chunk 0");
                }
                final short[] readBuffer = new short[bufferSize];
                
                int outputSize = (int) (CHUNK_RECORD_TIME * sampleRate * sampleSizeInBits * channels / 8);

                if (debug) {
                    LOG.log(Level.INFO, String.format("reserved initial size %d bytes", outputSize));
                }
                ByteArrayOutputStream recordBytes = new ByteArrayOutputStream(outputSize);
                while (isRunning && audioRecord != null) {
                    if (isRunning && audioRecord != null) {
                        int bufferResult = audioRecord.read(readBuffer, 0, bufferSize);
                        if (bufferResult != AudioRecord.ERROR_INVALID_OPERATION && bufferResult != AudioRecord.ERROR_BAD_VALUE) {
                            byte[] buffer = short2byte(readBuffer);
                            recordBytes.write(buffer, 0, buffer.length);
                        }
                        if (update) {
                            byte[] data = recordBytes.toByteArray();
                            recordBytes.reset();
                            save(data);
                            update = false;
                        }
                    }
                }
                // final chunk
                save(recordBytes.toByteArray());
            });
            
            thread.setName("TimedAudioCapture");
        }

        public void update() {
            update = true;
            if (debug) {
                LOG.log(Level.INFO, String.format("Start recording chunk %d", chunk));
            }
        }
        
        public void stop() {
            if (debug) {
                LOG.log(Level.INFO, "Stop TimedAudioCapture");
            }
            isRunning = false;
        }
        
        public void start() {
            thread.start();
        }
    
        private void save(byte[] audioData) {
            if (debug) {
                LOG.log(Level.INFO, String.format("Start saving chunk %d", chunk));
            }
            final String fileName = String.format("audioFile-%03d-%s", chunk++, LocalDateTime.now().format(pattern));
            Thread threadSave = new Thread(() -> {
                final File pcmFile = new File(getAudioFolder(), fileName + ".wav");
                try (FileOutputStream output = new FileOutputStream(pcmFile)) {
                    output.write(new WavHeader(sampleRate, sampleSizeInBits, channels, audioData.length).toBytes());
                    output.write(audioData);
                    if (debug) {
                        LOG.log(Level.INFO, String.format("File %s.wav added to %s", fileName, getAudioFolder()));
                    }
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error saving file", ex);
                }
                addChunk.apply(fileName + ".wav");
            });
            threadSave.setPriority(Thread.MIN_PRIORITY);
            threadSave.start();
        } 
        
    }
    
    private ExecutorService createExecutor(final String name) {       
        ThreadFactory factory = r -> {
            Thread t = new Thread(r);
            t.setName(name);
            t.setDaemon(true);
            return t;
        };
        return Executors.newSingleThreadExecutor(factory);
    }  
    
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }
    
}
