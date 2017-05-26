package cs.umass.edu.customcalendar.constants;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Maintains various global constants.
 */
public class Constants {

    /** The interval of each minute picker (both AM and PM). **/
    public static final int TIME_PICKER_INTERVAL = 15;

    public interface DATE_FORMAT {
        SimpleDateFormat AM_PM = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat _24_HR = new SimpleDateFormat("H:mm", Locale.getDefault());
        SimpleDateFormat MM_YYYY = new SimpleDateFormat("MM yyyy", Locale.getDefault());
        SimpleDateFormat MMM_YY = new SimpleDateFormat("MMM yy", Locale.getDefault());
        SimpleDateFormat MMM_YYYY = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        SimpleDateFormat MONTH_DAY = new SimpleDateFormat("M/dd", Locale.getDefault());
    }

    /** Intent actions used to communicate between the main UI and the sensor service
     * @see android.content.Intent */
    public interface ACTION {
        String BROADCAST_MESSAGE = "edu.umass.cs.my-activities-toolkit.action.broadcast-message";
        String NAVIGATE_TO_APP = "edu.umass.cs.my-activities-toolkit.action.navigate-to-app";
        String START_SERVICE = "edu.umass.cs.prepare.action.start-service";
        String STOP_SERVICE = "edu.umass.cs.prepare.action.stop-service";
        String QUERY_BATTERY_LEVEL = "edu.umass.cs.prepare.action.query-battery-level";
        String QUERY_CONNECTION_STATE = "edu.umass.cs.prepare.action.query-connection-state";
    }

    public interface MESSAGES {
        int BEACON_WITHIN_RANGE = 0;
        int METAWEAR_CONNECTED = 1;
        int METAWEAR_CONNECTING = 2;
        int METAWEAR_DISCONNECTED = 3;
        int BAND_SERVICE_STARTED = 4;
        int BAND_SERVICE_STOPPED = 5;
        int RECORDING_SERVICE_STARTED = 6;
        int RECORDING_SERVICE_STOPPED = 7;
        int INVALID_ADDRESS = 8;
        int BLUETOOTH_DISABLED = 9;
        int BLUETOOTH_UNSUPPORTED = 10;
        int NO_MOTION_DETECTED = 11;
        int METAWEAR_SERVICE_STOPPED = 12;
        int SERVER_CONNECTION_FAILED = 13;
        int WEARABLE_SERVICE_STARTED = 14;
        int WEARABLE_SERVICE_STOPPED = 15;
        int WEARABLE_CONNECTED = 16;
        int WEARABLE_DISCONNECTED = 17;
        int SERVER_CONNECTION_SUCCEEDED = 18;
        int SERVER_DISCONNECTED =
                19;
        int WEARABLE_CONNECTION_FAILED = 20;
    }

    /**
     * Keys used to identify data instances from data packages sent over Bluetooth to the mobile device.
     */
    public interface KEY {
        String UUID = "edu.umass.cs.prepare.key.uuid";
        String TIMESTAMPS = "edu.umass.cs.prepare.key.timestamps";
        String VALUES = "edu.umass.cs.prepare.key.sensor-values";
        String MESSAGE = "edu.umass.cs.prepare.key.message";

        String RECORD_AUDIO = "edu.umass.cs.prepare.key.record-audio";
        String SURFACE_WIDTH = "edu.umass.cs.prepare.key.surface-width";
        String SURFACE_HEIGHT = "edu.umass.cs.prepare.key.surface-height";
        String SURFACE_X = "edu.umass.cs.prepare.key.surface-x";
        String SURFACE_Y = "edu.umass.cs.prepare.key.surface-y";
        String SENSOR_DATA = "edu.umass.cs.prepare.key.sensor-data";
        String SENSOR_TYPE = "edu.umass.cs.prepare.key.sensor-type";
        String TIMESTAMP = "edu.umass.cs.prepare.key.timestamp";
        String PAGE_INDEX = "edu.umass.cs.prepare.key.page-index";
        String NOTIFICATION_ID = "edu.umass.cs.prepare.key.notification-id";
    }

    public interface NOTIFICATION_ID {
        int BAND_SERVICE = 101;
        int METAWEAR_SENSOR_SERVICE = 102;
        int DATA_WRITER_SERVICE = 103;
        int RECORDING_SERVICE = 104;
    }

    // TODO: Only for debugging purposes
    public static boolean GENERATE_DUMMY_DATA = true;
}
