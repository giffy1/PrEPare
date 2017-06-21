package cs.umass.edu.customcalendar.services;

import android.app.Notification;
import android.app.Service;
import android.os.AsyncTask;
import android.util.Log;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.notifications.VibrationType;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandGyroscopeEvent;
import com.microsoft.band.sensors.BandGyroscopeEventListener;
import com.microsoft.band.sensors.SampleRate;

import java.util.UUID;

import cs.umass.edu.customcalendar.R;
import cs.umass.edu.customcalendar.constants.Constants;
import cs.umass.edu.customcalendar.data.Medication;
import edu.umass.cs.MHLClient.sensors.AccelerometerReading;
import edu.umass.cs.MHLClient.sensors.GyroscopeReading;

/**
 * The WearableService is responsible for starting and stopping the sensors on the Band and receiving
 * accelerometer and gyroscope data periodically. It is a foreground service, so that the user
 * can close the application on the phone and continue to receive data from the wearable device.
 * Because the {@link BandGyroscopeEvent} also receives accelerometer readings, we only need to
 * register a {@link BandGyroscopeEventListener} and no {@link BandAccelerometerEventListener}.
 * This should be compatible with both the Microsoft Band and Microsoft Band 2.
 *
 * @author Sean Noran
 *
 * @see Service#startForeground(int, Notification)
 * @see BandClient
 * @see BandGyroscopeEventListener
 */
public class WearableService extends SensorService implements BandGyroscopeEventListener {

    /** used for debugging purposes */
    private static final String TAG = WearableService.class.getName();

    /** The object which receives sensor data from the Microsoft Band */
    private BandClient bandClient = null;

    /**
     * Asynchronous task for connecting to the Microsoft Band accelerometer and gyroscope sensors.
     * Errors may arise if the Band does not support the Band SDK version or the Microsoft Health
     * application is not installed on the mobile device.
     **
     * @see com.microsoft.band.BandErrorType#UNSUPPORTED_SDK_VERSION_ERROR
     * @see com.microsoft.band.BandErrorType#SERVICE_ERROR
     * @see BandClient#getSensorManager()
     * @see com.microsoft.band.sensors.BandSensorManager
     */
    private class SensorSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    broadcastStatus(getString(R.string.status_connected));
                    broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTED);
                    bandClient.getSensorManager().registerGyroscopeEventListener(WearableService.this, SampleRate.MS16);
                    onSensorStarted();
                } else {
                    broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTION_FAILED);
                    broadcastStatus(getString(R.string.status_not_connected));
                }
            } catch (BandException e) {
                String exceptionMessage;
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = getString(R.string.err_unsupported_sdk_version);
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = getString(R.string.err_service);
                        break;
                    default:
                        exceptionMessage = getString(R.string.err_default) + e.getMessage();
                        break;
                }
                Log.e(TAG, exceptionMessage);
                broadcastStatus(exceptionMessage);
                broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTION_FAILED);
            } catch (Exception e) {
                broadcastStatus(getString(R.string.err_default) + e.getMessage());
                broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTION_FAILED);
            }
            return null;
        }
    }

    /**
     * Connects the mobile device to the Microsoft Band
     * @return True if successful, False otherwise
     * @throws InterruptedException if the connection is interrupted
     * @throws BandException if the band SDK version is not compatible or the Microsoft Health band is not installed
     */
    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (bandClient == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                broadcastStatus(getString(R.string.status_not_paired));
                return false;
            }
            bandClient = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == bandClient.getConnectionState()) {
            return true;
        }

        broadcastStatus(getString(R.string.status_connecting));
        return ConnectionState.CONNECTED == bandClient.connect().await();
    }

    @Override
    protected void registerSensors() {
        new SensorSubscriptionTask().execute();
    }

    /**
     * unregisters the sensors from the sensor service
     */
    @Override
    public void unregisterSensors() {
        if (bandClient != null) {
            try {
                bandClient.getSensorManager().unregisterAllListeners();
                disconnectBand();
            } catch (BandIOException e) {
                broadcastStatus(getString(R.string.err_default) + e.getMessage());
            }
        }
    }

    @Override
    protected int getNotificationID() {
        return Constants.NOTIFICATION_ID.BAND_SERVICE;
    }

    @Override
    protected String getNotificationContentText() {
        return "NOTIFICATION"; // TODO: // getString(R.string.wearable_service_notification);
    }

    @Override
    protected int getNotificationIconResourceID() {
        return R.drawable.ic_watch_white_24dp;
    }

    /**
     * disconnects the sensor service from the Microsoft Band
     */
    public void disconnectBand() {
        if (bandClient != null) {
            try {
                bandClient.disconnect().await();
            } catch (InterruptedException | BandException e) {
                // Do nothing as this is happening during destroy
            }
        }
    }

    private void broadcastStatus(String status){
        // TODO
        Log.i(TAG, status);
    }

    @Override
    public void onBandGyroscopeChanged(BandGyroscopeEvent event) {
        float accelerometerX = event.getAccelerationX();
        float accelerometerY = event.getAccelerationY();
        float accelerometerZ = event.getAccelerationZ();
        float gyroscopeX = event.getAngularVelocityX();
        float gyroscopeY = event.getAngularVelocityY();
        float gyroscopeZ = event.getAngularVelocityZ();

        long timestamp = System.currentTimeMillis(); //event.getTimestamp();

//        if (applicationPreferences.writeServer()) {
            mClient.sendSensorReading(new AccelerometerReading(mUserID, "WEARABLE", "", timestamp,
                    accelerometerX, accelerometerY, accelerometerZ));
            mClient.sendSensorReading(new GyroscopeReading(mUserID, "WEARABLE", "", timestamp,
                    gyroscopeX, gyroscopeY, gyroscopeZ));
//        }


//        broadcaster.broadcastSensorReading(Constants.SENSOR_TYPE.ACCELEROMETER_WEARABLE, timestamp,
//                accelerometerX, accelerometerY, accelerometerZ);
//
//        broadcaster.broadcastSensorReading(Constants.SENSOR_TYPE.GYROSCOPE_WEARABLE, timestamp,
//                gyroscopeX, gyroscopeY, gyroscopeZ);

    }

    // TODO : call when pill intake gesture detected (happens in DataService, must notify this service from there).
    public void sendNotificationToWearable(Medication medication){
        try {
            // send a dialog to the Band for one of our tiles
            bandClient.getNotificationManager().vibrate(VibrationType.NOTIFICATION_ALARM);
            bandClient.getNotificationManager().showDialog(UUID.randomUUID(),
                    "You've taken a dose of " + medication.getName(), "If this is incorrect, please correct it on your phone.").await();
        } catch (BandException | InterruptedException e) {
            // handle BandException
        }
    }
}