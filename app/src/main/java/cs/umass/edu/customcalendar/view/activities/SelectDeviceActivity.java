package cs.umass.edu.customcalendar.view.activities;

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
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.module.AccelerometerBosch;
import com.mbientlab.metawear.module.IBeacon;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.Timer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import bolts.Continuation;
import bolts.Task;
import cs.umass.edu.customcalendar.R;
import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.io.ApplicationPreferences;

/**
 * This UI allows the user to select a pill bottle to connect. Only one device may be selected.
 * All devices within range will be displayed along with their signal strength (RSSI) in order
 * to differentiate devices with the same name.
 *
 * @author Sean Noran
 * @affiliation University of Massachusetts Amherst
 *
 * @see <a href="https://github.com/mbientlab/Metawear-SampleAndroidApp/blob/master/app/src/main/java/com/mbientlab/metawear/app/ScannerActivity.java">ScannerActivity</a>
 * @see ScannerCommunicationBus
 * @see BtleService
 */
public class SelectDeviceActivity extends AppCompatActivity implements ScannerCommunicationBus, ServiceConnection {

    /** used for debugging purposes */
    @SuppressWarnings("unused")
    private static final String TAG = SelectDeviceActivity.class.getName();

    private int medicationIndex = 0;

    private ApplicationPreferences preferences;

    private List<Medication> medications;

    private TextView txtTitle;

    private final static UUID[] serviceUUIDs;

    private BtleService.LocalBinder serviceBinder;

    private MetaWearBoard mwBoard;

    private Led ledModule;

    private ListView lstDevices;

    private Map<Medication, String> addressMapping = new HashMap<>();

    static {
        serviceUUIDs = new UUID[] {
                MetaWearBoard.METAWEAR_GATT_SERVICE,
                MetaWearBoard.METABOOT_SERVICE
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_metawear);

        preferences = ApplicationPreferences.getInstance(this);
        medications = preferences.getMedications(this);

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
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

    private void configureDevice(final BluetoothDevice btDevice){
        mwBoard= serviceBinder.getMetaWearBoard(btDevice);
        final ProgressDialog connectDialog = new ProgressDialog(this);
        connectDialog.setTitle(getString(R.string.title_connecting));
        connectDialog.setMessage(getString(R.string.message_wait));
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
                .continueWith(task -> {
                    if (!task.isCancelled()) {
                        SelectDeviceActivity.this.runOnUiThread(connectDialog::dismiss);
                        SelectDeviceActivity.this.startMotionDetection();
                        // TODO configure device
                    }
                    return null;
                });
    }

    /**
     * Called when motion is detected on the Metawear board.
     */
    private void onMotionDetected(){
        ledModule.play();
//        iBeaconModule.configure().major((short) 1);
//        iBeaconModule.enable();
        // TODO : Enable then disable, e.g. after a couple of beacons
    }

    /** Module that handles motion events of the Metawear board. **/
    private AccelerometerBosch motionModule;

    /**
     * Configures motion detection on the Metawear board.
     */
    private void startMotionDetection(){
        ledModule = mwBoard.getModule(Led.class);
        motionModule = mwBoard.getModule(AccelerometerBosch.class); // TODO is this correct sensor?
        Timer timer = mwBoard.getModule(Timer.class);
        IBeacon iBeaconModule = mwBoard.getModule(IBeacon.class);

//        timer.scheduleAsync(30000, false, () -> iBeaconModule.configure().major((short) 0));
        timer.scheduleAsync(5000, false, () -> ledModule.stop(false));
        iBeaconModule.configure() // TODO
//                .uuid(uuid)
//                .major(major)
//                .minor(minor)
                .period((short)2000)
//                .rxPower(rxPower)
//                .txPower(txPower)
                .commit();

        ledModule.stop(true);
        ledModule.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID).commit();

        final AccelerometerBosch.AnyMotionDataProducer motionDetection = motionModule.motion(AccelerometerBosch.AnyMotionDataProducer.class);
        // https://mbientlab.com/androiddocs/latest/data_route.html#reaction
        motionDetection.addRouteAsync(source -> source.react(token -> onMotionDetected()))
                .continueWith((Continuation<Route, Void>) task -> {
                    motionDetection.start();
                    motionModule.start();
                    mwBoard.disconnectAsync();
                    return null;
                });
    }

    @Override
    public void onDeviceSelected(final BluetoothDevice device) {
        configureDevice(device);
        addressMapping.put(medications.get(medicationIndex), device.getAddress());
        refreshListView();
        medicationIndex++;
        if (medicationIndex >= medications.size()) {
            finish();
            preferences.setAddressMapping(this, addressMapping);
        } else {
            txtTitle.setText(String.format(Locale.getDefault(), "Select device for %s", medications.get(medicationIndex).getName()));
        }
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