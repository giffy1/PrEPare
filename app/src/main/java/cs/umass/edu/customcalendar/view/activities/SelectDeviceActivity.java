package cs.umass.edu.customcalendar.view.activities;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment.ScannerCommunicationBus;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.android.BtleService;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        medications = preferences.getMedications();

        txtTitle = (TextView) findViewById(R.id.ble_scan_title);
        txtTitle.setText(String.format(Locale.getDefault(), "Select device for %s", medications.get(medicationIndex).getName()));

        getApplicationContext().bindService(new Intent(this, BtleService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onDeviceSelected(final BluetoothDevice device) {
        Map<Medication, String> addressMapping = preferences.getAddressMapping();
        if (addressMapping == null){
            addressMapping = new HashMap<>();
        }
        addressMapping.put(medications.get(medicationIndex), device.getAddress());
        preferences.setAddressMapping(addressMapping);
        medicationIndex++;
        if (medicationIndex >= medications.size()) {
            finish();
        } else {
            txtTitle.setText(String.format(Locale.getDefault(), "Select device for %s", medications.get(medicationIndex).getName()));
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        BtleService.LocalBinder serviceBinder = (BtleService.LocalBinder) iBinder;
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