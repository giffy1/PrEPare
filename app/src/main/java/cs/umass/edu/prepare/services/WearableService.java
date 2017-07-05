package cs.umass.edu.prepare.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
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

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.constants.Constants;
import cs.umass.edu.prepare.data.DataIO;
import cs.umass.edu.prepare.data.Medication;
import cs.umass.edu.prepare.view.activities.CalendarActivity;
import edu.umass.cs.MHLClient.client.ConnectionStateHandler;
import edu.umass.cs.MHLClient.client.MobileIOClient;
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
public class WearableService extends Service implements BandGyroscopeEventListener, ConnectionStateHandler {

    /** used for debugging purposes */
    private static final String TAG = WearableService.class.getName();

    /** The object which receives sensor data from the Microsoft Band */
    private BandClient bandClient = null;

    /** Responsible for communicating with the data collection server. */
    protected MobileIOClient mClient;

    /** The user ID required to authenticate the server connection. */
    protected String mUserID;

    /** Gives access to persisted and in-memory data (adherence, medications, etc.) */
    protected DataIO dataIO;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
        dataIO = DataIO.getInstance(this);
    }

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

    /**
     * Registers the accelerometer and gyroscope sensors.
     */
    protected void registerSensors() {
        try {
            bandClient.getSensorManager().registerGyroscopeEventListener(WearableService.this, SampleRate.MS16);
        } catch (BandIOException e) {
            e.printStackTrace();
        }
    }

    /**
     * unregisters the sensors from the sensor service
     */
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

    private int getNotificationID() {
        return Constants.NOTIFICATION_ID.BAND_SERVICE;
    }

    private String getNotificationContentText() {
        return getString(R.string.wearable_service_notification);
    }

    private int getNotificationIconResourceID() {
        return R.drawable.ic_watch_white_24dp;
    }

    /**
     * disconnects the sensor service from the Microsoft Band
     */
    private void disconnectBand() {
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

        mClient.sendSensorReading(new AccelerometerReading(mUserID, "WEARABLE", "", timestamp,
                accelerometerX, accelerometerY, accelerometerZ));
        mClient.sendSensorReading(new GyroscopeReading(mUserID, "WEARABLE", "", timestamp,
                gyroscopeX, gyroscopeY, gyroscopeZ));

        // to broadcast to other application components e.g. to UI
//        broadcaster.broadcastSensorReading(Constants.SENSOR_TYPE.ACCELEROMETER_WEARABLE, timestamp,
//                accelerometerX, accelerometerY, accelerometerZ);
//
//        broadcaster.broadcastSensorReading(Constants.SENSOR_TYPE.GYROSCOPE_WEARABLE, timestamp,
//                gyroscopeX, gyroscopeY, gyroscopeZ);

    }

    // TODO : call when pill intake gesture detected (happens in DataService, must notify this service from there).
    public void sendNotificationToWearable(Medication medication){
        try {
            if (bandClient == null){ // if this happens then notifications will not be sent to the MS Band
                Log.w(TAG, "Warning : Band Client is null.");
            }else {
                // send a dialog to the Band for one of our tiles
                bandClient.getNotificationManager().vibrate(VibrationType.NOTIFICATION_ALARM);
                bandClient.getNotificationManager().showDialog(UUID.randomUUID(),
                        "You've taken a dose of " + medication.getName(), "If this is incorrect, please correct it on your phone.").await();
            }
        } catch (BandException | InterruptedException e) {
            // handle BandException
        }
    }

    /**
     * Connects to the data collection server.
     */
    protected void connectToServer(){
        mUserID = getString(R.string.mobile_health_client_user_id);
        mClient = MobileIOClient.getInstance(this, mUserID);
        mClient.setConnectionStateHandler(this);
        mClient.connect();
    }

    /**
     * Starts the sensor service in the foreground.
     */
    protected void start(){
        Log.d(TAG, "Service started");
        connectToServer();
        new SensorSubscriptionTask().execute();
        startForeground(4001, getNotification());
    }

    /**
     * Stops the sensor service.
     */
    protected void stop(){
        Log.d(TAG, "Service stopped");
        unregisterSensors();
//        if (client != null)
//            client.disconnect(); //TODO
        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null){
            if (intent.getAction().equals(Constants.ACTION.PUSH_NOTIFICATION_TO_MSBAND)){
                Medication medication = (Medication) intent.getSerializableExtra(Constants.KEY.MEDICATION);
                sendNotificationToWearable(medication);
            } else if (intent.getAction().equals(Constants.ACTION.START_SERVICE)) {
                start();
            } else if (intent.getAction().equals(Constants.ACTION.STOP_SERVICE)) {
                stop();
            } else if (intent.getAction().equals(Constants.ACTION.START_SENSORS)) {
                registerSensors();
            } else if (intent.getAction().equals(Constants.ACTION.STOP_SENSORS)) {
                unregisterSensors();
            }
        } else {
            Log.d(TAG, "Service restarted after killed by OS.");
            start();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "Connected to server");
        broadcastMessage(Constants.MESSAGES.SERVER_CONNECTION_SUCCEEDED);
    }

    @Override
    public void onConnectionFailed(Exception e) {
        e.printStackTrace();
        Log.d(TAG, "Connection attempt failed.");
        broadcastMessage(Constants.MESSAGES.SERVER_CONNECTION_FAILED);
    }

    /**
     * Returns the notification displayed during background recording.
     * @return the notification handle.
     */
    protected Notification getNotification(){
        Intent notificationIntent = new Intent(this, CalendarActivity.class); //open main activity when user clicks on notification
        notificationIntent.setAction(Constants.ACTION.NAVIGATE_TO_APP); //TODO
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(Constants.KEY.NOTIFICATION_ID, getNotificationID());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, this.getClass());
        stopIntent.setAction(Constants.ACTION.STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

        // notify the user that the foreground service has started
        return new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_name))
                .setContentText(getNotificationContentText())
                .setSmallIcon(getNotificationIconResourceID())
                .setOngoing(true)
                .setVibrate(new long[]{0, 50, 150, 200})
                .setPriority(Notification.PRIORITY_MAX)
                .addAction(R.drawable.ic_stop_white_24dp, "Stop Service", stopPendingIntent)
                .setContentIntent(pendingIntent).build();
    }

    /**
     * Broadcasts a message to other application components.
     * @param message a message, as defined in {@link Constants.MESSAGES}
     */
    protected void broadcastMessage(int message) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.MESSAGE, message);
        intent.setAction(Constants.ACTION.BROADCAST_MESSAGE);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }
}