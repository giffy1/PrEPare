package cs.umass.edu.prepare.view.activities;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment.ScannerCommunicationBus;
import com.mbientlab.bletoolbox.scanner.ScannedDeviceInfo;
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.module.Accelerometer;
import com.mbientlab.metawear.module.AccelerometerBosch;
import com.mbientlab.metawear.module.IBeacon;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Settings;
import com.mbientlab.metawear.module.Timer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import bolts.Continuation;
import bolts.Task;
import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.data.Medication;
import cs.umass.edu.prepare.data.DataIO;

/**
 * This UI allows the user to select a pill bottle to connect. Only one device may be selected.
 * All devices within range will be displayed along with their signal strength (RSSI) in order
 * to differentiate devices with the same name.
 *
 * @author Sean Noran
 *
 * @see <a href="https://github.com/mbientlab/Metawear-SampleAndroidApp/blob/master/app/src/main/java/com/mbientlab/metawear/app/ScannerActivity.java">ScannerActivity</a>
 * @see ScannerCommunicationBus
 * @see BtleService
 */
public class SelectDeviceActivity extends AppCompatActivity implements ScannerCommunicationBus, ServiceConnection {

    /** used for debugging purposes */
    @SuppressWarnings("unused")
    private static final String TAG = SelectDeviceActivity.class.getName();

    /** Metawear service UUIDs that are excluded from the list of devices. */
    private final static UUID[] serviceUUIDs;

    static {
        serviceUUIDs = new UUID[] {
                MetaWearBoard.METAWEAR_GATT_SERVICE,
                MetaWearBoard.METABOOT_SERVICE
        };
    }

    /** Used for binding to the Metawear BLE service. */
    private BtleService.LocalBinder serviceBinder;

    /** The list of medications. */
    private List<Medication> medications;

    /** Indicates the current medication for the medication-device mapping. */
    private int medicationIndex = 0;

    /** Used for accessing and updating the medication-device mapping, persisting it to disk. */
    private DataIO dataIO;

    /** The view's title, which should display the current medication. */
    private TextView txtTitle;

    /** A reference to the board for managing sensors. */
    private MetaWearBoard mwBoard;

    /** Module that configures and toggles the LEDs on the Metawear board. */
    private Led ledModule;

    /** Module that handles the beaconing on the Metawear board. */
    private IBeacon iBeaconModule;

    /** Module that handles motion events of the Metawear board. **/
    private AccelerometerBosch motionModule;

    /** The list view containing information for each device in range. */
    private ListView lstDevices;

    /** The mapping from medications to device UUIDs. */
    private final Map<Medication, String> addressMapping = new HashMap<>();

    /** The dialog shown when configuring the device. */
    private ProgressDialog configureDialog;

    /** Indicates how many milliseconds should elapse between each beacon. */
    private static final int BEACON_PERIOD = 2000;

    /** The threshold for detecting motion on the board. */
    private static final float MOTION_THRESHOLD = 0.2f;

    /** Indicates how frequently (in ms) beaconing should be disabled.
     * TODO : The timer task to disable beaconing should be scheduled when motion is detected. */
    private static final int DISABLE_BEACON_DELAY = 25000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_metawear);

        dataIO = DataIO.getInstance(this);
        medications = dataIO.getMedications(this);

        txtTitle = (TextView) findViewById(R.id.ble_scan_title);
        txtTitle.setText(String.format(Locale.getDefault(), "Select device for %s", medications.get(medicationIndex).getName()));

        lstDevices = (ListView) findViewById(R.id.blescan_devices);
//        ScannedDeviceInfoAdapter adapter = (ScannedDeviceInfoAdapter) lstDevices.getAdapter();
//        deviceName = (TextView)convertView.findViewById(com.mbientlab.bletoolbox.scanner.R.id.ble_device);
        lstDevices.getAdapter().registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                refreshListView();
                super.onChanged();
            }
        });

        getApplicationContext().bindService(new Intent(this, BtleService.class), this, BIND_AUTO_CREATE);
    }

    /**
     * Refreshes the list of available devices such that each device that is already mapped
     * to a medication cannot be re-selected. It is greyed out and its
     * {@link android.view.View.OnClickListener} is overwritten, such that clicking on
     * the item will generate a message indicating that it is already mapped to another medication.
     * TODO : How to handle mistakes then?
     */
    private void refreshListView(){
        for (int index = 0; index < lstDevices.getAdapter().getCount(); index++){
            ScannedDeviceInfo deviceInfo = (ScannedDeviceInfo) lstDevices.getAdapter().getItem(index);
            String address = deviceInfo.btDevice.getAddress();
            for (Medication medication : medications){
                if (addressMapping.containsKey(medication)){
                    if (addressMapping.get(medication).equals(address)){
                        if (lstDevices.getChildAt(index) != null) {
                            Log.i(TAG, address + ", " + index);
                            // setClickable(false) and setEnabled(false) do not work here:
                            View listViewItem = lstDevices.getChildAt(index);
                            listViewItem.setOnClickListener(view -> Toast.makeText(SelectDeviceActivity.this,
                                    "This device is already associated with " + medication.getName() +  ".",
                                    Toast.LENGTH_LONG).show());
                            listViewItem.setBackgroundColor(Color.rgb(235,235,228));
                            TextView deviceAddress = (TextView) listViewItem.findViewById(com.mbientlab.bletoolbox.scanner.R.id.ble_mac_address);
                            TextView deviceName = (TextView) listViewItem.findViewById(com.mbientlab.bletoolbox.scanner.R.id.ble_device);
                            TextView deviceRSSI = (TextView) listViewItem.findViewById(com.mbientlab.bletoolbox.scanner.R.id.ble_rssi_value);
                            deviceAddress.setTextColor(Color.rgb(155,155,140));
                            deviceName.setTextColor(Color.rgb(155,155,140));
                            deviceRSSI.setTextColor(Color.rgb(155,155,140));
                        }
                    }
                }
            }
        }
        // TODO : For some reason, not refreshing when selected (but will refresh if the user touches the screen).
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    /**
     * Reconnect to the board, e.g. on failure.
     * @param board the Metawear board
     * @return a task, indicating the result.
     */
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
     * Connects to the given device and then once connected, configures the device.
     * @param btDevice the selected device.
     */
    private void connect(final BluetoothDevice btDevice){
        mwBoard= serviceBinder.getMetaWearBoard(btDevice);
        final ProgressDialog connectDialog = new ProgressDialog(this);
        connectDialog.setTitle(getString(R.string.title_connecting));
        connectDialog.setMessage(getString(R.string.message_connect_wait));
        connectDialog.setCancelable(false);
        connectDialog.setCanceledOnTouchOutside(false);
        connectDialog.setIndeterminate(true);
        connectDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.label_cancel),
                (dialogInterface, i) -> mwBoard.disconnectAsync());
        connectDialog.show();

        mwBoard.connectAsync()
                .continueWithTask(task -> {
                    if (task.isCancelled()) {
                        return task;
                    }
                    return task.isFaulted() ? reconnect(mwBoard) : Task.forResult(null);
                })
                .continueWith(new Continuation<Void, Object>() {
                    @Override
                    public Object then(Task<Void> task) throws Exception {
                        if (!task.isCancelled()) {
                            SelectDeviceActivity.this.runOnUiThread(connectDialog::dismiss);
                            SelectDeviceActivity.this.configureDevice(btDevice.getAddress());
                        }
                        return null;
                    }
                });
    }

    /**
     * Configures motion detection on the Metawear board.
     */
    private void configureDevice(final String address){
        SelectDeviceActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                configureDialog = new ProgressDialog(SelectDeviceActivity.this);
                configureDialog.setTitle(getString(R.string.title_configuring));
                configureDialog.setMessage(getString(R.string.message_configure_wait));
                configureDialog.setCancelable(false);
                configureDialog.setCanceledOnTouchOutside(false);
                configureDialog.setIndeterminate(true);
                configureDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.label_cancel),
                        (dialogInterface, i) -> mwBoard.disconnectAsync()); // TODO : Should they be allowed to cancel this?? (though it's usually a lot faster than connecting)
                configureDialog.show();
            }
        });

        ledModule = mwBoard.getModule(Led.class);
        motionModule = mwBoard.getModule(AccelerometerBosch.class);
        iBeaconModule = mwBoard.getModule(IBeacon.class);

        // configure beaconing
        iBeaconModule.configure() // TODO
//                .uuid(uuid)
//                .major(major)
//                .minor(minor)
                .period((short) BEACON_PERIOD)
//                .rxPower(rxPower)
//                .txPower(txPower)
                .commit();

        // configure LED:
        ledModule.stop(true);
        ledModule.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID).commit();

        // configure motion detection:
        startMotionDetection(address);
    }

    /**
     * Configures motion detection using the accelerometer on the Metawear board. When motion is detected,
     * the {@link #onMotionDetected()} method will be called.
     * @param address the address of the Metawear board being configured.
     */
    private void startMotionDetection(final String address){
        final AccelerometerBosch.AnyMotionDataProducer motionDetection = motionModule.motion(AccelerometerBosch.AnyMotionDataProducer.class);
        motionDetection.configure().threshold(MOTION_THRESHOLD).commit();
        // https://mbientlab.com/androiddocs/latest/data_route.html#reaction
        motionDetection.addRouteAsync(source -> source.react(token -> onMotionDetected()))
                .continueWith((Continuation<Route, Void>) task -> {
                    motionDetection.start();
                    motionModule.start();
                    scheduleBeaconDisablingTask(address);
                    return null;
                });
    }

    /**
     * Called when motion is detected on the Metawear board.
     */
    private void onMotionDetected(){
        ledModule.play();
        iBeaconModule.enable();
    }

    /**
     * Disables BLE advertising on the Metawear. This method should be called when
     * connected to the board. If this method is called, subsequent connection attempts
     * will fail because the board does not advertise for connection. To send a single
     * BLE advertising packet, press on the button on the Metawear board.
     */
    private void disableBleAdvertising(){
        Settings settingsModule = mwBoard.getModule(Settings.class);
        settingsModule.editBleAdConfig().timeout((byte)5).commit();
    }

    /**
     * Schedules a task which disables beaconing on the Metawear board every 20s,
     * even when not connected to the phone.
     * @param address the address of the Metawear board being configured.
     */
    private void scheduleBeaconDisablingTask(final String address){
        Timer timer = mwBoard.getModule(Timer.class);

        timer.scheduleAsync(DISABLE_BEACON_DELAY, true, () -> {
                iBeaconModule.disable();
                ledModule.stop(false);
            })
            .continueWith(task -> {
                Timer.ScheduledTask mwTask = task.getResult();

                // lookup a task with id = 0
                if (mwTask != null) {
                    Log.i(TAG, "Starting beacon disabling task (executes every 20s).");
                    // start the task
                    mwTask.start();
                } else {
                    Log.e(TAG, "Error : Could not schedule beacon disabling task on board.");
                }
                onDeviceConfigured(address);
                mwBoard.disconnectAsync();
                return null;
            });
    }

    /**
     * Called after connecting to, configuring and disconnecting from the device.
     * @param address the address of the device
     */
    private void onDeviceConfigured(final String address){
        SelectDeviceActivity.this.runOnUiThread(configureDialog::dismiss);
        addressMapping.put(medications.get(medicationIndex), address);
        refreshListView();
        medicationIndex++;
        if (medicationIndex >= medications.size()) {
            finish();
            dataIO.setAddressMapping(this, addressMapping);
        } else {
            txtTitle.setText(String.format(Locale.getDefault(), "Select device for %s", medications.get(medicationIndex).getName()));
        }
    }

    @Override
    public void onDeviceSelected(final BluetoothDevice device) {
        connect(device);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (BtleService.LocalBinder) iBinder;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    @Override
    public UUID[] getFilterServiceUuids() {
        return serviceUUIDs;
    }

    @Override
    public long getScanDuration() {
        return 10000L;
    }
}