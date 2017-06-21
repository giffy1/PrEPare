package cs.umass.edu.prepare.services;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.AccelerometerBosch;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Settings;

import java.util.List;
import java.util.Map;

import bolts.Continuation;
import bolts.Task;
import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.constants.Constants;
import cs.umass.edu.prepare.data.Medication;

/**
 * Service that handles the data collection from the Metawear board on the pill bottle cap.
 *
 * @author Sean Noran
 */
public class MetawearService extends SensorService implements ServiceConnection {

    /** used for debugging purposes */
    @SuppressWarnings("unused")
    private static final String TAG = MetawearService.class.getName();

    /** The Bluetooth device handle of the <a href="https://mbientlab.com/metawearc/">MetaWear C</a> board. **/
    private BluetoothDevice btDevice;

    /** Indicates whether the sensor service is bound to the {@link BtleService} **/
    private boolean mIsBound = false;

    /** A handle to the Metawear board **/
    private MetaWearBoard mwBoard;

    /** Module that handles streaming accelerometer data from the Metawear board. **/
    private Accelerometer accModule;

    /** Module that handles LED state for on-board notifications. **/
    private Led ledModule;

    /** Module that handles the disconnection events on the Metawear board. **/
    private Settings settingsModule;

    /** Module that handles motion events of the Metawear **/
    private AccelerometerBosch motionModule;

    @Override
    protected void registerSensors() {
        if (mIsBound) return;
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        List<Medication> medications = dataIO.getMedications(this);
        Map<Medication, String> addressMapping = dataIO.getAddressMapping(this);

        try {
            btDevice = btManager.getAdapter().getRemoteDevice(addressMapping.get(medications.get(0)));
        }catch(IllegalArgumentException e){
            e.printStackTrace();
            Log.i(TAG, "Invalid Bluetooth address");
//            if (broadcaster != null)
//                broadcaster.broadcastMessage(Constants.MESSAGES.INVALID_ADDRESS);
            return;
        }
        mIsBound = getApplicationContext().bindService(new Intent(MetawearService.this, BtleService.class),
                MetawearService.this, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "Bound to Metawear service: " + mIsBound);
    }

    @Override
    protected void unregisterSensors() {
        stopSensors();
        if (mwBoard != null && mwBoard.isConnected()) {
            mwBoard.disconnectAsync();
        } else {
            onMetawearDisconnected();
        }
    }

    @Override
    protected int getNotificationID() {
        return Constants.NOTIFICATION_ID.METAWEAR_SENSOR_SERVICE;
    }

    @Override
    protected String getNotificationContentText() {
        return "Collecting Pill Bottle Data...";
    }

    @Override
    protected int getNotificationIconResourceID() {
        return R.drawable.ic_pill_white_24dp;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        stopForeground(true);
        stopSelf();
    }

    private void doUnbind(){
        if (mIsBound) {
            getApplicationContext().unbindService(MetawearService.this);
            mIsBound = false;
        } else {
            onServiceDisconnected(null); // ensures service is stopped
        }
    }

    /**
     * Called when the application disconnects from the Metawear board.
     */
    private void onMetawearDisconnected(){
//        if (broadcaster != null)
//            broadcaster.broadcastMessage(Constants.MESSAGES.METAWEAR_DISCONNECTED);
        doUnbind();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mwBoard = ((BtleService.LocalBinder) service).getMetaWearBoard(btDevice);

//        Log.i(TAG, getString(R.string.notify_service_connected));
        connect();
    }

    private static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        return reconnect(board);
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                });
    }

    /**
     * Connect to the Metawear board.
     */
    private void connect(){
        mwBoard.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        Log.d(TAG, "Could not connect to board.");
                        return reconnect(mwBoard);
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                }).continueWith(task -> {
                    onMetawearConnected();
                    return null;
                });
    }

    /**
     * Called once the Metawear board is connected
     */
    private void onMetawearConnected(){
        Log.d(TAG, "OnMetawearConnected()");
        getModules();
        if (accelRoute != null)
            accelRoute.remove();
        handleBoardDisconnectionEvent();
        stopSensors();
        setSamplingRates();
        startSensors();
//        if (broadcaster != null)
//            broadcaster.broadcastMessage(Constants.MESSAGES.METAWEAR_CONNECTED);
    }

    /**
     * Requests the relevant modules from the Metawear board.
     */
    private void getModules(){
        accModule = mwBoard.getModule(Accelerometer.class);
        ledModule = mwBoard.getModule(Led.class);
        settingsModule = mwBoard.getModule(Settings.class);
        motionModule = mwBoard.getModule(AccelerometerBosch.class);
    }

    /**
     * Prepares the Metawear board for sensor data collection.
     */
    private void setSamplingRates() {
        Accelerometer.ConfigEditor<?> accelEditor = accModule.configure();
        accelEditor.range(2.f);
        accelEditor.odr(25.f); // TODO
//        accelEditor.odr((float) dataIO.getAccelerometerSamplingRate());
        accelEditor.commit();
    }

    /**
     * Sends commands to the board upon disconnection. When the board is disconnected, we specifically
     * want to start low power motion detection, stop all other sensors and stop advertisements.
     */
    private void handleBoardDisconnectionEvent(){
        //ensures that if the disconnect monitor event does not properly get called, then we can reconnect to the board
        //settingsModule.configure().setAdInterval((short) 1000, (byte) 0).commit();
        mwBoard.onUnexpectedDisconnect(status -> {
            Log.i("MainActivity", "Unexpectedly lost connection: " + status);
            onMetawearDisconnected();
        });
//        mwBoard.disconnectAsync().continueWith(new Continuation<Void, Void>() {
//            @Override
//            public Void then(Task<Void> task) throws Exception {
//                Log.i("MainActivity", "Disconnected");
//                return null;
//            }
//        });
        // TODO: isn't really working
        settingsModule.onDisconnectAsync(() -> {
            ledModule.editPattern(Led.Color.RED, Led.PatternPreset.SOLID)
                    .repeatCount((byte) 2)
                    .commit();
            ledModule.play();
        });
//        settingsModule.onDisconnectAsync(()->{
//            stopSensors();
//            onMetawearDisconnected();
//        });

        settingsModule.onDisconnectAsync(() -> {
            settingsModule.editBleAdConfig().timeout((byte) 1); // stop BLE advertising
            startMotionDetection();
        });
    }

    /**
     * Starts all enabled sensors and blinks LED on the Metawear board.
     */
    private void startSensors() {
//        if (dataIO.blinkLedWhileRunning()) // TODO
        turnOnLed(Led.Color.GREEN, true);
        startAccelerometer();
        startMotionDetection();
    }

    private void onMotionDetected(){
//        settingsModule.startBleAdvertising();
        ledModule.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID);
        ledModule.play();
    }

    private void startMotionDetection(){
        final AccelerometerBosch.AnyMotionDataProducer motionDetection = motionModule.motion(AccelerometerBosch.AnyMotionDataProducer.class);

        // configure motion detection
        // difference > 0.1g for 5 consecutive samples
        motionDetection.configure()
                .count(5) // TODO
                .threshold(0.1f)
                .commit();
//        motionDetection.addRouteAsync(source -> source.stream((Subscriber) (data, env) -> onMotionDetected()))
//            .continueWith((Continuation<Route, Void>) task -> {
//                motionDetection.start();
//                motionModule.start();
//                return null;
//            });


        // https://mbientlab.com/androiddocs/latest/data_route.html#reaction
        motionDetection.addRouteAsync(source -> source.react(token -> onMotionDetected()))
                .continueWith((Continuation<Route, Void>) task -> {
            motionDetection.start();
            motionModule.start();
            return null;
        });
    }

    /**
     * Turns on the LED on the Metawear device.
     */
    private void turnOnLed(Led.Color color, boolean ongoing){
        ledModule.stop(true);
        byte repeat = -1;
        if (!ongoing)
            repeat = 1;
        ledModule.editPattern(color).highIntensity((byte) 15).lowIntensity((byte) 0)
                .riseTime((short) 125).fallTime((short) 125)
                .highTime((short) 250).pulseDuration((short) 1000)
                .repeatCount(repeat).commit();
        ledModule.play();
    }

    private Route accelRoute;

    /**
     * Starts collecting accelerometer data from the Metawear board until a no motion event has
     * been detected.
     */
    private void startAccelerometer(){
//        Log.i(TAG, getString(R.string.routing_accelerometer));
        accModule.acceleration().addRouteAsync(source -> source.stream((data, env) -> {
            Log.i(TAG, "got data");
            final Acceleration value = data.value(Acceleration.class);
            float x = value.x();
            float y = value.y();
            float z = value.z();
            long timestamp = System.currentTimeMillis();
            // TODO
//            if (broadcaster != null)
//                broadcaster.broadcastSensorReading(Constants.SENSOR_TYPE.ACCELEROMETER_METAWEAR,
//                        timestamp, x, y, z);
        })).continueWith(task -> {
            accelRoute = task.getResult();
            accModule.acceleration().start();
            accModule.start();

            return null;
        });
    }
    /**
     * Stops sensors/LED on the Metawear board.
     */
    private void stopSensors(){
        if (ledModule != null) {
            ledModule.stop(true);
        }
        if (accModule != null) {
            accModule.stop();
        }
    }

}
