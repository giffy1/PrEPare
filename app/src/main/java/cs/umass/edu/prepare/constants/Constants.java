package cs.umass.edu.prepare.constants;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Maintains various global constants.
 */
public class Constants {

    /** The interval of each minute picker (both AM and PM). **/
    public static final int TIME_PICKER_INTERVAL = 15;

    public static final long[] NOTIFICATION_PATTERN = {0, 600, 0};

    public interface DATE_FORMAT {
        SimpleDateFormat AM_PM = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat _24_HR = new SimpleDateFormat("H:mm", Locale.getDefault());
        SimpleDateFormat MM_YYYY = new SimpleDateFormat("MM yyyy", Locale.getDefault());
        SimpleDateFormat MMM_YY = new SimpleDateFormat("MMM yy", Locale.getDefault());
        SimpleDateFormat MMM_YYYY = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        SimpleDateFormat MONTH_DAY = new SimpleDateFormat("M/dd", Locale.getDefault());
        SimpleDateFormat MONTH_DAY_YEAR = new SimpleDateFormat("MM-dd-yyyy", Locale.getDefault());
        SimpleDateFormat YEAR_MONTH_DAY = new SimpleDateFormat("yyy-MM-dd", Locale.getDefault());
        SimpleDateFormat MONTH_DAY_YEAR_SHORT = new SimpleDateFormat("MM.dd.yy", Locale.getDefault());
        SimpleDateFormat FULL_AM_PM = new SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.getDefault());
    }

    /** Intent actions used to communicate between the main UI and the sensor service
     * @see android.content.Intent */
    public interface ACTION {
        String REQUEST_BLUETOOTH = "edu.umass.cs.prepare.action.request-bluetooth";
        String ATTEMPT_BAND_RECONNECT = "edu.umass.cs.prepare.action.attempt-band-reconnect";
        String BROADCAST_EXCEPTION = "edu.umass.cs.prepare.action.broadcast-exception";
        String BROADCAST_MESSAGE = "edu.umass.cs.my-activities-toolkit.action.broadcast-message";
        String NAVIGATE_TO_APP = "edu.umass.cs.my-activities-toolkit.action.navigate-to-app";
        String CANCEL = "edu.umass.cs.prepare.action.cancel";
        String START_SERVICE = "edu.umass.cs.prepare.action.start-service";
        String STOP_SERVICE = "edu.umass.cs.prepare.action.stop-service";
        String START_SENSORS = "edu.umass.cs.prepare.action.start-sensors";
        String STOP_SENSORS = "edu.umass.cs.prepare.action.stop-sensors";
        String QUERY_BATTERY_LEVEL = "edu.umass.cs.prepare.action.query-battery-level";
        String QUERY_CONNECTION_STATE = "edu.umass.cs.prepare.action.query-connection-state";
        String PILL_INTAKE_RESPONSE_YES = "edu.umass.cs.prepare.action.pill-intake-response-yes";
        String PILL_INTAKE_RESPONSE_NO = "edu.umass.cs.prepare.action.pill-intake-response-no";
        String SCHEDULE_REMINDERS = "edu.umass.cs.prepare.action.schedule-reminders";
        String PUSH_NOTIFICATION_TO_MSBAND = "edu.umass.cs.prepare.action.push-notification";
    }

    public interface SERVER_MESSAGE {
        String PILL_INTAKE_GESTURE = "PILL_INTAKE_GESTURE";
        String UPDATE_DATA = "UPDATE_DATA";
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
        int SERVER_DISCONNECTED = 19;
        int WEARABLE_CONNECTION_FAILED = 20;
        int PILL_INTAKE_GESTURE_DETECTED = 21;
        int PILL_INTAKE_GESTURE_CONFIRMED = 22;
        int PILL_INTAKE_GESTURE_DENIED = 23;
        int WEARABLE_NOT_PAIRED = 24;
        int WEARABLE_CONNECTING = 25;
        int WEARABLE_UNREGISTER_SENSORS_FAILED = 26;
    }

    /**
     * Keys used to identify data instances from data packages sent over Bluetooth to the mobile device.
     */
    public interface KEY {
        String MESSAGE = "edu.umass.cs.prepare.key.message";
        String MEDICATION = "edu.umass.cs.prepare.key.medication";
        String TIME_TAKEN = "edu.umass.cs.prepare.key.time-taken";
        String NOTIFICATION_ID = "edu.umass.cs.prepare.key.notification-id";
        String TIMESTAMP = "edu.umass.cs.prepare.key.timestamp";
        String UUID = "edu.umass.cs.prepare.key.uuid";
        String EXCEPTION = "edu.umass.cs.prepare.key.exception";
    }

    public interface NOTIFICATION_ID {
        int BAND_SERVICE = 101;
        int METAWEAR_SENSOR_SERVICE = 102;
    }

    public interface REQUEST_CODE {
        int ENABLE_BLUETOOTH = 1;
    }

    public static final int MILLISECONDS_PER_DAY = 24*60*60*1000;
}
