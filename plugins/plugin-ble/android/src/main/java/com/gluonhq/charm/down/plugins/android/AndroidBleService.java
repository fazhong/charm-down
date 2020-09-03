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

import static android.app.Activity.RESULT_OK;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Build;
import com.gluonhq.charm.down.plugins.ble.Configuration;
import com.gluonhq.charm.down.plugins.BleService;
import com.gluonhq.charm.down.plugins.ble.Proximity;
import com.gluonhq.charm.down.plugins.ble.ScanDetection;
import java.util.Arrays;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;

import javafxports.android.FXActivity;

/**
 * Android implementation of BleService
 *
 * Android apps using BleService require the permissions BLUETOOTH and BLUETOOTH_ADMIN in the AndroidManifest file
 * 
 * Requires API &gt;= 21
 */
public class AndroidBleService implements BleService {
    
    private static final Logger LOG = Logger.getLogger(AndroidBleService.class.getName());

    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    private final List<String> uuids = new LinkedList<>();
    private final static int REQUEST_ENABLE_BT = 1001;

    /**
     * Check if Bluetooth is enabled and triggers a notification in case it is not, returning
     * to the application after the user enables it
     */
    public AndroidBleService() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            LOG.log(Level.SEVERE, "Device doesn't support Bluetooth");
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            LOG.log(Level.SEVERE, "Device doesn't support Bluetooth LE Scanner");
        } else {
            if (!adapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                FXActivity activity = FXActivity.getInstance();

                if (activity == null) {
                    LOG.log(Level.WARNING, "FXActivity not found. This service is not allowed when "
                            + "running in background mode or from wearable");
                    return;
                }
                
                activity.setOnActivityResultHandler((requestCode, resultCode, data) -> {
                    if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
                        scanner = adapter.getBluetoothLeScanner();
                    }
                });

                // A dialog will appear requesting user permission to enable Bluetooth
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

            } else {
                scanner = adapter.getBluetoothLeScanner();
            }
        }
    }

    /**
     * startScanning is called with a given uuid and a callback for the beacon found.
     * Android allows scanning for any beacon without knowing the uuid, so an empty
     * uuid can be set (this is not allowed in iOS implementation)
     *
     * @param region Containing the beacon uuid
     * @param callback Callback added to the beacon
     */
    @Override
    public void startScanning(Configuration region, Consumer<ScanDetection> callback) {
        List<String> requested =  region.getUuids();
        for (String candidate: requested) {
            uuids.add(candidate.toLowerCase());
        }
        if (scanner != null) {
            this.scanCallback = createCallback(callback);
            scanner.startScan(scanCallback);
        }
    }

    /**
     * stopScanning, if the scanner is initialized
     */
    @Override
    public void stopScanning() {
        if (scanner != null && scanCallback != null) {
            scanner.stopScan(scanCallback);
        }
    }

    private ScanCallback createCallback(Consumer<ScanDetection> callback) {
        ScanCallback answer = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                ScanRecord mScanRecord = result.getScanRecord();
                byte[] scanRecord = mScanRecord.getBytes();

                // https://www.pubnub.com/blog/2015-04-14-building-android-beacon-android-ibeacon-tutorial-overview/
                int index = 0;
                while (index < scanRecord.length) {
                    int length = scanRecord[index++];
                    if (length == 0) {
                        break;
                    }

                    int type = (scanRecord[index] & 0xff);

                    // process data
                    if (type == 0xff) {
                        ScanDetection detection = processAD(scanRecord, index + 1, result.getRssi());
                        if (detection != null) {
                            Platform.runLater(() -> callback.accept(detection));
                        }
                    }

                    index += length;
                }
            }
        };
        return answer;
    }

    private ScanDetection processAD(byte[] scanRecord, int init, int mRssi) {
        // AD: (Length FF) mID0 mID1 bID1 bID0 uuid15 ... uuid0 M1 M0 m1 m0 tx
        int startByte = init;
        // Manufacturer ID (little endian)
        // https://www.bluetooth.org/en-us/specification/assigned-numbers/company-identifiers
        int mID = ((scanRecord[startByte+1] & 0xff) << 8) | (scanRecord[startByte] & 0xff);

        startByte += 2;
        // Beacon ID (big endian)
        int beaconID = ((scanRecord[startByte] & 0xff) << 8) | (scanRecord[startByte+1] & 0xff);
        startByte += 2;
        // UUID (big endian)
        byte[] uuidBytes = Arrays.copyOfRange(scanRecord, startByte, startByte+16);
        String scannedUuid = ByteArrayToUUIDString(uuidBytes);

        if (uuids.isEmpty() || uuids.contains(scannedUuid.toLowerCase())) {
            startByte += 16;
            // major (big endian)
            int major = ((scanRecord[startByte] & 0xff) << 8) | (scanRecord[startByte+1] & 0xff);
            startByte += 2;
            // minor (big endian)
            int minor = ((scanRecord[startByte] & 0xff) << 8) | (scanRecord[startByte+1] & 0xff);
            startByte += 2;
            // power in dB
            int power = (scanRecord[startByte] & 0xff);
            power -= 256;
            Proximity distance = calculateProximity(power, mRssi);

//            System.out.println("Scan: mID: "+mID+", beaconID: "+beaconID+", uuid: "+scannedUuid+
//                    ", major: "+major+", minor: "+minor+", power: "+power+", distance: "+distance.name());

            ScanDetection detection = new ScanDetection();
            detection.setUuid(scannedUuid);
            detection.setMajor(major);
            detection.setMinor(minor);
            detection.setRssi(mRssi);
            detection.setProximity(distance.getProximity());
            return detection;
        }
        return null;
    }

    private static String ByteArrayToUUIDString(byte[] ba) {
        StringBuilder hex = new StringBuilder();
        for (byte b : ba) {
            hex.append(new Formatter().format("%02x", b));
        }
        return hex.toString().replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5" );
    }

    // https://github.com/RadiusNetworks/android-ibeacon-service/blob/tip/src/main/java/com/radiusnetworks/ibeacon/IBeacon.java
    private Proximity calculateProximity(int txPower, double rssi) {
        double accuracy = calculateAccuracy(txPower, rssi);
        if (accuracy < 0) {
            return Proximity.UNKNOWN;
        }
        if (accuracy < 0.5) {
            return Proximity.IMMEDIATE;
        }
        if (accuracy <= 4.0) {
            return Proximity.NEAR;
        }
        return Proximity.FAR;
    }

    private static double calculateAccuracy(int txPower, double rssi) {
        if (rssi == 0 || txPower == 0) {
            return -1.0; // if we cannot determine accuracy, return -1.
        }

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            double accuracy = 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
            return accuracy;
        }
    }
}