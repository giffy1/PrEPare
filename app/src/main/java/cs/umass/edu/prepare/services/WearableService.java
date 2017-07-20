package cs.umass.edu.prepare.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    /** The object which receives sensor data from the Microsoft Band. */
    private BandClient bandClient = null;

    /** Responsible for communicating with the data collection server. */
    protected MobileIOClient mClient;

    /** The user ID required to authenticate the server connection. */
    protected String mUserID;

    /** Gives access to persisted and in-memory data (adherence, medications, etc.) */
    protected DataIO dataIO;

    /** Used for starting the service in the foreground. */
    private static final int SERVICE_ID = 4001;

    private boolean bandConnectionAtemptCancelled = false;

    private enum BAND_STATUS {
        CONNECTING,
        CONNECTED,
        CONNECTION_FAILED,
        BLUETOOTH_DISABLED,
        BLUETOOTH_NOT_SUPPORTED,
        COLLECTING_SENSOR_DATA
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
        dataIO = DataIO.getInstance(this);
        IntentFilter bluetoothStateFiler = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, bluetoothStateFiler);
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
                connect(new BandConnectionCallback() {
                    @Override
                    public void onBandConnected() {
                        registerSensors();
                    }
                });
            } else if (intent.getAction().equals(Constants.ACTION.STOP_SENSORS)) {
                unregisterSensors();
                startForeground(SERVICE_ID, getNotification(BAND_STATUS.CONNECTED));
            } else if (intent.getAction().equals(Constants.ACTION.ATTEMPT_BAND_RECONNECT)){
                connect(null);
            } else if (intent.getAction().equals(Constants.ACTION.CANCEL)){
                bandConnectionAtemptCancelled = true;
                stop();
            } else if (intent.getAction().equals(Constants.ACTION.REQUEST_BLUETOOTH)){
                requestBluetooth();
            }
        } else {
            Log.i(TAG, "Service restarted after killed by OS.");
            start();
        }
        return START_STICKY;
    }

    /**
     * Requests the user to enable Bluetooth on the phone.
     */
    private void requestBluetooth(){
        Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(btIntent);
    }

    /**
     * Starts the service in the foreground and connects to the MS Band and the data collection server.
     */
    protected void start(){
        Log.d(TAG, "Service started");
        connect(null);
    }

    /**
     * Stops the sensor service.
     */
    protected void stop(){
        Log.d(TAG, "Service stopped");
        stopForeground(true);
        unregisterSensors();
//        if (mClient != null) // TODO : Can't disconnect here because the DataService may still be connected.
//            mClient.disconnect();
        stopSelf();
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
     * Used to specify execution after successfully connecting to the MS Band.
     */
    private interface BandConnectionCallback {
        /** Called after successfully connecting to the MS Band. */
        void onBandConnected();
    }

    /**
     * Asynchronous task for connecting to the Microsoft Band over Bluetooth. When executing, pass
     * in any number of {@link BandConnectionCallback} instances, specifying what should occur after
     * a successful connection. Note these callbacks will not be called if the connection attempt
     * fails. If the band is already connected, this will successfully execute and the callbacks
     * will be called as well.
     * Errors may arise if the Band does not support the Band SDK version or the Microsoft Health
     * application is not installed on the mobile device.
     **
     * @see com.microsoft.band.BandErrorType#UNSUPPORTED_SDK_VERSION_ERROR
     * @see com.microsoft.band.BandErrorType#SERVICE_ERROR
     * @see BandClient#getSensorManager()
     * @see com.microsoft.band.sensors.BandSensorManager
     */
    private class BandConnectionTask extends AsyncTask<BandConnectionCallback, Void, Void> {
        @Override
        protected Void doInBackground(BandConnectionCallback... callbacks) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                startForeground(SERVICE_ID, getNotification(BAND_STATUS.BLUETOOTH_NOT_SUPPORTED));
                return null;
            } else {
                if (!mBluetoothAdapter.isEnabled()) {
                    startForeground(SERVICE_ID, getNotification(BAND_STATUS.BLUETOOTH_DISABLED));
                    return null;
                }
            }

            try {
                boolean connectionSucceeded = getConnectedBandClient();
                if (bandConnectionAtemptCancelled){
                    return null;
                }
                if (connectionSucceeded) {
                    Log.i(TAG, getString(R.string.status_connected));
                    broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTED);
                    startForeground(SERVICE_ID, getNotification(BAND_STATUS.CONNECTED));
                    if (callbacks != null) { // success
                        for (BandConnectionCallback callback : callbacks) {
                            if (callback != null)
                                callback.onBandConnected();
                        }
                    }
                } else {
                    broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTION_FAILED);
                    startForeground(SERVICE_ID, getNotification(BAND_STATUS.CONNECTION_FAILED));
                    Log.e(TAG, getString(R.string.status_not_connected));
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
                startForeground(SERVICE_ID, getNotification(BAND_STATUS.CONNECTION_FAILED));
                broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTION_FAILED);
            } catch (InterruptedException e) {
                Log.e(TAG, getString(R.string.err_default) + e.getMessage());
                startForeground(SERVICE_ID, getNotification(BAND_STATUS.CONNECTION_FAILED));
                broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTION_FAILED);
            }
            return null;
        }
    }

    /**
     * Connects the mobile device to the Microsoft Band. This cannot be called from the main thread.
     * @return True if successful, False otherwise.
     * @throws InterruptedException if the connection is interrupted.
     * @throws BandException if the band SDK version is not compatible or the Microsoft Health band is not installed.
     */
    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (bandClient == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                broadcastMessage(Constants.MESSAGES.WEARABLE_NOT_PAIRED);
                Log.e(TAG, getString(R.string.status_not_paired));
                return false;
            }
            bandClient = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == bandClient.getConnectionState()) {
            return true;
        }

        broadcastMessage(Constants.MESSAGES.WEARABLE_CONNECTING);
        Log.i(TAG, getString(R.string.status_connecting));
        startForeground(SERVICE_ID, getNotification(BAND_STATUS.CONNECTING));

        return ConnectionState.CONNECTED == bandClient.connect().await();
    }

    /**
     * Registers the accelerometer and gyroscope sensors.
     */
    protected void registerSensors() {
        try {
            // TODO
            bandClient.getSensorManager().registerGyroscopeEventListener(this, SampleRate.MS16);
        } catch (BandIOException e) {
            e.printStackTrace();
            return;
        }
        connectToServer();
        startForeground(SERVICE_ID, getNotification(BAND_STATUS.COLLECTING_SENSOR_DATA));
    }

    /**
     * Unregisters the sensors from the sensor service.
     */
    public void unregisterSensors() {
        if (bandClient != null) {
            try {
                bandClient.getSensorManager().unregisterAllListeners();
                disconnect();
            } catch (BandIOException e) {
                Log.w(TAG, getString(R.string.err_default) + e.getMessage());
                broadcastMessage(Constants.MESSAGES.WEARABLE_UNREGISTER_SENSORS_FAILED);
            }
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
     * Connects to the Microsoft Band.
     */
    private void connect(BandConnectionCallback callback){
        bandConnectionAtemptCancelled = false;
        new BandConnectionTask().execute(callback);
    }

    /**
     * Disconnects the sensor service from the Microsoft Band
     */
    private void disconnect() {
        if (bandClient != null) {
            try {
                bandClient.disconnect().await();
            } catch (InterruptedException | BandException e) {
                // Do nothing as this is happening during disconnection
            }
        }
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

    /**
     * Sends a pill intake notification to the MS Band. If the band is not connected, this method
     * will first attempt to connect to the band and if successful, the notification will be
     * forwarded to the band.
     * @param medication the medication that was likely taken.
     */
    public void sendNotificationToWearable(Medication medication){
        connect(new BandConnectionCallback() {
            @Override
            public void onBandConnected() {
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
                    Log.w(TAG, "Warning : Failed to send notification to MS Band.");
                    broadcastBandException(e);
                }
            }
        });
    }

    /**
     * Returns the notification displayed during background recording.
     * @param status the band connectivity status, on which the notification content and design depends.
     * @return the notification handle.
     */
    protected Notification getNotification(BAND_STATUS status){
        Intent notificationIntent = new Intent(this, CalendarActivity.class); //open main activity when user clicks on notification
        notificationIntent.setAction(Constants.ACTION.NAVIGATE_TO_APP);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra(Constants.KEY.NOTIFICATION_ID, Constants.NOTIFICATION_ID.BAND_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, this.getClass());
        stopIntent.setAction(Constants.ACTION.STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

        Intent cancelIntent = new Intent(this, this.getClass());
        cancelIntent.setAction(Constants.ACTION.CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, 0);

        Intent reconnectIntent = new Intent(this, this.getClass());
        reconnectIntent.setAction(Constants.ACTION.ATTEMPT_BAND_RECONNECT);
        PendingIntent reconnectPendingIntent = PendingIntent.getService(this, 0, reconnectIntent, 0);

        Intent requestBLEIntent = new Intent(this, this.getClass());
        requestBLEIntent.setAction(Constants.ACTION.REQUEST_BLUETOOTH);
        PendingIntent requestBLEPendingIntent = PendingIntent.getService(this, 0, requestBLEIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        final String contentText;
        int notificationIcon = R.drawable.ic_watch_white_24dp;
        String actionText;
        int actionIcon;
        switch (status){
            case CONNECTING:
                contentText = getString(R.string.wearable_connecting_notification);
                notificationIcon = R.drawable.ic_watch_white_24dp;

                actionText = getString(R.string.notification_action_cancel);
                actionIcon = R.drawable.ic_stop_white_24dp;
                builder.addAction(actionIcon, actionText, cancelPendingIntent);
                break;
            case COLLECTING_SENSOR_DATA:
                contentText = getString(R.string.wearable_sensors_notification);
                notificationIcon = R.drawable.ic_watch_white_24dp;

                actionText = getString(R.string.notification_action_stop_service);
                actionIcon = R.drawable.ic_stop_white_24dp;
                builder.addAction(actionIcon, actionText, stopPendingIntent);
                break;
            case CONNECTED:
                contentText = getString(R.string.wearable_service_notification);
                notificationIcon = R.drawable.ic_watch_white_24dp;

                // TODO : Should there be a stop service action when connected to the band?
//                actionText = getString(R.string.notification_action_stop_service);
//                actionIcon = R.drawable.ic_stop_white_24dp;
//                builder.addAction(actionIcon, actionText, stopPendingIntent);
                break;
            case CONNECTION_FAILED:
                contentText = getString(R.string.wearable_connection_failed_notification);
                notificationIcon = R.drawable.ic_watch_error_white_24dp;

                actionText = getString(R.string.notification_action_try_again);
                actionIcon = R.drawable.ic_refresh_white_24dp;
                builder.addAction(actionIcon, actionText, reconnectPendingIntent);
                break;
            case BLUETOOTH_DISABLED:
                contentText = getString(R.string.wearable_bluetooth_disabled_notification);
                notificationIcon = R.drawable.ic_watch_error_white_24dp;

                actionText = getString(R.string.notification_action_enable_bluetooth);
                actionIcon = R.drawable.ic_bluetooth_white_24dp;
                builder.addAction(actionIcon, actionText, requestBLEPendingIntent);
                break;
            case BLUETOOTH_NOT_SUPPORTED:
                contentText = getString(R.string.wearable_bluetooth_not_supported_notification);
                notificationIcon = R.drawable.ic_watch_error_white_24dp;
                break;
            default:
                contentText = "";
                break;
        }

        builder = builder.setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(notificationIcon)
                .setOngoing(true)
                .setVibrate(new long[]{0, 50, 150, 200})
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pendingIntent);

        if (status == BAND_STATUS.CONNECTING){
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_OFF:
                        startForeground(SERVICE_ID, getNotification(BAND_STATUS.BLUETOOTH_DISABLED));
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        connect(null);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }

            }
        }
    };

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

    /**
     * Broadcasts a band exception to other application components.
     * @param exception the exception that occurs.
     */
    protected void broadcastBandException(Exception exception) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.EXCEPTION, exception);
        intent.setAction(Constants.ACTION.BROADCAST_EXCEPTION);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(bluetoothStateReceiver);
    }
}