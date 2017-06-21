package cs.umass.edu.prepare.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.constants.Constants;
import cs.umass.edu.prepare.data.Adherence;
import cs.umass.edu.prepare.data.Medication;
import cs.umass.edu.prepare.io.DataIO;
import cs.umass.edu.prepare.reminders.NotificationPublisher;
import cs.umass.edu.prepare.util.Utils;
import cs.umass.edu.prepare.view.activities.CalendarActivity;
import edu.umass.cs.MHLClient.client.MessageReceiver;
import edu.umass.cs.MHLClient.client.MobileIOClient;

import static android.support.v4.app.NotificationCompat.CATEGORY_CALL;

public class DataService extends Service {

    /** used for debugging purposes */
    private static final String TAG = DataService.class.getName();

    /** The list of medications. **/
    private ArrayList<Medication> medications = new ArrayList<>();

    /** The entire adherence data. Date is stored in a tree map because dates are naturally ordered. **/
    private Map<Calendar, Map<Medication, Adherence[]>> adherenceData = new TreeMap<>();

    /** Maps a medication to a schedule (a list of times to take the medication). **/
    private Map<Medication, Calendar[]> schedule = new HashMap<>();

    /** Maps a medication to a unique Mac Address. **/
    private Map<Medication, String> addressMapping = new HashMap<>();

    private Map<Medication, Integer> dosageMapping = new HashMap<>();

    private TreeSet<Integer> reminders = new TreeSet<>();

    private DataIO dataIO;

    private MobileIOClient mClient;

    private Handler handler;

    /**
     * Loads the data from disk.
     */
    private void loadData(){
        medications = dataIO.getMedications(this);
        schedule = dataIO.getSchedule(this);
        adherenceData = dataIO.getAdherenceData(this);
        addressMapping = dataIO.getAddressMapping(this);
        dosageMapping = dataIO.getDosageMapping(this);
        reminders = dataIO.getReminders(this);
    }

    /**
     * Updates the list of medications, given a new list of medications, and saves it to disk.
     * @param newMedications a JSON array of medications
     */
    private void updateMedications(JSONArray newMedications) throws JSONException {
        medications.clear();
        for (int i = 0; i < newMedications.length(); i++){
            String medicationName = newMedications.getString(i);
            Medication medication = new Medication(medicationName);
            Bitmap medImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pill);
            medication.setImage(medImage);
            medication.setDefaultImage(medImage);
            medications.add(medication);
        }
        dataIO.setMedications(this, medications);
    }

    /**
     * Updates the medication schedule and saves it to disk.
     * @param newSchedule a JSON object mapping medication names to a list of times encoded in Strings.
     */
    private void updateSchedule(JSONObject newSchedule) throws JSONException, ParseException {
        schedule = new HashMap<>();
        // assuming medications are updated first
        for (Medication medication : medications){
            JSONArray medicationSchedule = newSchedule.getJSONArray(medication.getName());
            String timeAM = medicationSchedule.getString(0);
            String timePM = medicationSchedule.getString(1);
            // TODO : should time be AM/PM or 24HR?
            Calendar calendarAM, calendarPM;
            if (timeAM.equals("null")){
                calendarAM = null;
            } else {
                Date dateAM = Constants.DATE_FORMAT.AM_PM.parse(timeAM + " AM");
                calendarAM = Calendar.getInstance();
                calendarAM.setTime(dateAM);
            }

            if (timePM.equals("null")){
                calendarPM = null;
            } else {
                Date datePM = Constants.DATE_FORMAT.AM_PM.parse(timePM + " PM");
                calendarPM = Calendar.getInstance();
                calendarPM.setTime(datePM);
            }
            schedule.put(medication, new Calendar[]{calendarAM, calendarPM});
        }
        dataIO.setSchedule(this, schedule);
    }

    /**
     * Updates the adherence data and saves it to disk.
     * @param newAdherenceData the new adherence data.
     */
    private void updateAdherenceData(JSONObject newAdherenceData) throws JSONException, ParseException {
        adherenceData.clear();
        // assuming medication list and schedule has already been updated
        Iterator<String> dateIter = newAdherenceData.keys();
        while (dateIter.hasNext()) {
            String dateKeyStr = dateIter.next();
            Map<Medication, Adherence[]> medicationAdherence = new HashMap<>();

            JSONObject medicationAdherenceJSON = newAdherenceData.getJSONObject(dateKeyStr);
            for (Medication medication : medications) {
                Log.i(TAG, medication.getName());
                String medicationKey = medication.getName();
                JSONArray adherence = medicationAdherenceJSON.getJSONArray(medicationKey);
                JSONArray adherenceAM_JSON = adherence.getJSONArray(0);
                JSONArray adherencePM_JSON = adherence.getJSONArray(1);

                String adherenceType_AM = adherenceAM_JSON.getString(0);
                String time_AM = adherenceAM_JSON.getString(1);
                String adherenceType_PM = adherencePM_JSON.getString(0);
                String time_PM = adherencePM_JSON.getString(1);

                Adherence adherenceAM = null;
                if (adherenceType_AM.equals("FUTURE")) {
                    Date dateTimeAM = Constants.DATE_FORMAT.AM_PM.parse(time_AM + " AM");
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(dateTimeAM);
                    adherenceAM = new Adherence(Adherence.AdherenceType.FUTURE, cal);

                } else if (adherenceType_AM.equals("NONE")) { // assume NONE or FUTURE
                    adherenceAM = new Adherence(Adherence.AdherenceType.NONE, null);
                }

                Adherence adherencePM = null;
                if (adherenceType_PM.equals("FUTURE")) {
                    Date dateTimePM = Constants.DATE_FORMAT.AM_PM.parse(time_PM + " PM");
                    Calendar cal2 = Calendar.getInstance();
                    cal2.setTime(dateTimePM);
                    adherencePM = new Adherence(Adherence.AdherenceType.FUTURE, cal2);
                } else if (adherenceType_PM.equals("NONE")) { // assume NONE or FUTURE
                    adherencePM = new Adherence(Adherence.AdherenceType.NONE, null);
                }

                medicationAdherence.put(medication, new Adherence[]{adherenceAM, adherencePM});
            }
            Calendar dateInfo = Calendar.getInstance();
            // TODO : other dateKey method doesn't work
            dateInfo.setTime(Constants.DATE_FORMAT.YEAR_MONTH_DAY.parse(dateKeyStr));
            Calendar dateKey = Utils.getDateKey(dateInfo.get(Calendar.YEAR), dateInfo.get(Calendar.MONTH), dateInfo.get(Calendar.DATE));
            adherenceData.put(dateKey, medicationAdherence);
        }
        Log.i(TAG, adherenceData.toString());
        dataIO.setAdherenceData(this, adherenceData);
    }

    /**
     * Returns a medication given its UUID.
     * @param bottleUUID a unique mac address of the Metawear.
     * @return the medication associated with that device, null if not found.
     */
    private Medication getMedicationByUUID(String bottleUUID){
        if (true) // TODO just for testing
            return medications.get(0);
        if (addressMapping == null){
            Log.w(TAG, "No address mapping found. Cannot get medication by UUID.");
            return null;
        }
        for (Medication medication : medications){
            if (addressMapping.get(medication).equals(bottleUUID)){
                return medication;
            }
        }
        return null;
    }

    public static class NotificationResponseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null){
                Intent forward = new Intent();
                forward.putExtra(Constants.KEY.MEDICATION, intent.getSerializableExtra(Constants.KEY.MEDICATION));
                forward.putExtra(Constants.KEY.TIME_TAKEN, intent.getSerializableExtra(Constants.KEY.TIME_TAKEN));
                if (action.equals(Constants.ACTION.PILL_INTAKE_RESPONSE_YES)){
                    forward.putExtra(Constants.KEY.MESSAGE, Constants.MESSAGES.PILL_INTAKE_GESTURE_CONFIRMED);
                    Log.i(TAG, "YES");
                } else if (action.equals(Constants.ACTION.PILL_INTAKE_RESPONSE_NO)){
                    forward.putExtra(Constants.KEY.MESSAGE, Constants.MESSAGES.PILL_INTAKE_GESTURE_DENIED);
                    Log.i(TAG, "NO");
                }
                forward.setAction(Constants.ACTION.BROADCAST_MESSAGE);
                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);
                manager.sendBroadcast(forward);
            }
        }
    }

    private void requestUserConfirmation(Medication medication, Calendar timeTaken){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setVibrate(Constants.NOTIFICATION_PATTERN);
        // TODO: uncomment for sound, also let user customize
//        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//        builder.setSound(alarmSound);

        String timeTakenStr = Constants.DATE_FORMAT.FULL_AM_PM.format(timeTaken.getTime());
        String prompt = getString(R.string.notification_gesture_detected_text, medication.getName(), timeTakenStr);

        builder.setContentTitle(getString(R.string.notification_gesture_detected_title));
        builder.setContentText(prompt);
        builder.setLargeIcon(medication.getImage());
        // TODO : after API 23, you can use the medication image as the small icon; before that, use the pill icon
        // see https://stackoverflow.com/questions/23836920/how-to-set-bitmap-as-notification-icon-in-android
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            builder.setSmallIcon(Icon.createWithBitmap(medication.getImage()));
//        } else {
//            builder.setSmallIcon(R.drawable.ic_pill_white_24dp);
//        }
        // TODO with NotificationCompat, the above doesn't work, only with Notification which has other issues of deprecation for pending intents
        builder.setSmallIcon(R.drawable.ic_pill_white_24dp);

        builder.setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }
        builder.setVibrate(Constants.NOTIFICATION_PATTERN);
//        builder.setDefaults(Notification.DEFAULT_ALL);
        builder.setOngoing(true);
        builder.setCategory(CATEGORY_CALL);
        builder.setAutoCancel(false);
        builder.setLights(0xff00ff00, 300, 100);

        Intent intent = new Intent(this, CalendarActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 113, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setFullScreenIntent(pendingIntent, true);

        //Yes intent
        Intent yesReceive = new Intent(DataService.this, NotificationResponseReceiver.class);
        yesReceive.putExtra(Constants.KEY.MEDICATION, medication);
        yesReceive.putExtra(Constants.KEY.TIME_TAKEN, timeTaken);
        yesReceive.setAction(Constants.ACTION.PILL_INTAKE_RESPONSE_YES);
        PendingIntent pendingIntentYes = PendingIntent.getBroadcast(this, 12345, yesReceive, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(R.drawable.ic_check_white_24dp, "Yes", pendingIntentYes);

        //No intent
        Intent noReceive = new Intent(DataService.this, NotificationResponseReceiver.class);
        noReceive.putExtra(Constants.KEY.MEDICATION, medication);
        noReceive.putExtra(Constants.KEY.TIME_TAKEN, timeTaken);
        noReceive.setAction(Constants.ACTION.PILL_INTAKE_RESPONSE_NO);
        PendingIntent pendingIntentNo = PendingIntent.getBroadcast(this, 12345, noReceive, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(R.drawable.ic_highlight_off_white_24dp, "No", pendingIntentNo);

        Notification notification = builder.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationID, notification);
    }

    // TODO: notification ID should allow for multiple intakes (id based on timestamp)
    private final int notificationID = 2000;

    private void cancelPillIntakeNotification(Context context){
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationID);
    }

    private void onPillIntakeGestureConfirmed(Medication medication, Calendar timeTaken){
        Calendar dateKey = Utils.getDateKey(timeTaken);
        Map<Medication, Adherence[]> singleDayAdherenceData = adherenceData.get(dateKey);
        if (singleDayAdherenceData == null) {
            Log.w(TAG, "Warning : No adherence data found for " + Constants.DATE_FORMAT.MONTH_DAY_YEAR.format(dateKey.getTime()));
            singleDayAdherenceData = new HashMap<>();
        }
        Adherence[] medicationAdherence = singleDayAdherenceData.get(medication); // TODO: check for null pointer here
        if (medicationAdherence == null)
            medicationAdherence = new Adherence[2];
        Calendar[] medicationSchedule = schedule.get(medication);
        if (medicationSchedule != null) {
            int threshold = 90; // 2 hr before, 4 hr after
            // TODO : Must decide if first or second pill (may be tricky if taken sometime in between)
            int differenceInMin = medicationSchedule[0].get(Calendar.MINUTE) + 60 * medicationSchedule[0].get(Calendar.HOUR_OF_DAY); // TODO hour or hour of day?
            if (differenceInMin <= threshold) {
                medicationAdherence[0] = new Adherence(Adherence.AdherenceType.TAKEN, timeTaken);
            } else {
                medicationAdherence[0] = new Adherence(Adherence.AdherenceType.TAKEN_EARLY_OR_LATE, timeTaken);
            }
            dataIO.setAdherenceData(this, adherenceData);
        }
        cancelPillIntakeNotification(this);
    }

    /**
     * Callback method when a pill intake gesture is detected.
     * @param timeTaken the timestamp, wrapped in a {@link Calendar} object, at which the event occurred.
     * @param bottleUUID the ID of the medication bottle from which the pill was likely taken.
     */
    private void onPillIntakeGestureDetected(final Calendar timeTaken, String bottleUUID){
        final Medication medication = getMedicationByUUID(bottleUUID);
        // TODO : if address mapping is null or no medication has the UUID, the user should still
        // be notified but informed that it should be properly mapped (by contacting researcher/PCP)
        if (medication == null) {
            Log.w(TAG, "No medication with that UUID");
            return;
        }

        userResponded = false; // indicates whether user responded (either positively or negatively)
        broadcastPillIntakeGestureDetected(medication, timeTaken);
        requestUserConfirmation(medication, timeTaken);

        handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (!userResponded){ // assume it is correct
                onPillIntakeGestureConfirmed(medication, timeTaken);
            }
        }, 30000);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(Constants.ACTION.BROADCAST_MESSAGE)){
                    int message = intent.getIntExtra(Constants.KEY.MESSAGE, -1);
                    switch (message){
                        case Constants.MESSAGES.PILL_INTAKE_GESTURE_CONFIRMED:
                            userResponded = true;
                            Medication medication = (Medication) intent.getSerializableExtra(Constants.KEY.MEDICATION);
                            Calendar timeTaken = (Calendar) intent.getSerializableExtra(Constants.KEY.TIME_TAKEN);
                            onPillIntakeGestureConfirmed(medication, timeTaken);
                            break;
                        case Constants.MESSAGES.PILL_INTAKE_GESTURE_DENIED:
                            userResponded = true;
                            // TODO send message to server
                            cancelPillIntakeNotification(context);
                            if (handler != null)
                                handler.removeCallbacksAndMessages(null);
                            break;
                    }
                }
            }
        }
    };

    private boolean userResponded = false;

    /**
     * Broadcasts a message to other application components indicating that a
     * pill intake gesture has occurred.
     * @param medication The medication which was concluded to have been taken.
     * @param timeTaken The time at which the medication was taken, wrapped in a {@link Calendar} object.
     */
    private void broadcastPillIntakeGestureDetected(Medication medication, Calendar timeTaken){
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.MESSAGE, Constants.MESSAGES.PILL_INTAKE_GESTURE_DETECTED);
        intent.putExtra(Constants.KEY.MEDICATION, medication);
        intent.putExtra(Constants.KEY.TIME_TAKEN, timeTaken);
        intent.setAction(Constants.ACTION.BROADCAST_MESSAGE);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    private void connect() {
        mClient = MobileIOClient.getInstance(this, getString(R.string.mobile_health_client_user_id));
        mClient.connect();
        mClient.registerMessageReceiver(new MessageReceiver() {
            @Override
            protected void onMessageReceived(JSONObject json) {
                try {
                    String message = json.getString("message");
                    if (message.equals(Constants.SERVER_MESSAGE.PILL_INTAKE_GESTURE)){
                        JSONObject data = json.getJSONObject("data");
                        int timestamp_in_seconds = data.getInt("timestamp");
                        Calendar timeTaken = Utils.timestampToCalendar(timestamp_in_seconds);
                        String bottleUUID = data.getString("bottle_uuid");
                        onPillIntakeGestureDetected(timeTaken, bottleUUID);
                    } else if (message.equals(Constants.SERVER_MESSAGE.UPDATE_DATA)){
                        Log.i(TAG, "Data update received from server.");
                        JSONObject data = json.getJSONObject("data");

                        JSONObject newAdherenceData = data.getJSONObject("adherence_data");
                        JSONObject newSchedule = data.getJSONObject("schedule");
                        JSONArray newMedications = data.getJSONArray("medications");

                        try {
                            updateMedications(newMedications);
                            updateSchedule(newSchedule);
                            updateAdherenceData(newAdherenceData);
                        } catch (JSONException | ParseException e){
                            e.printStackTrace(); // TODO revert on error? Currently not respecting ACID properties
                        }


                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null){
            if (intent.getAction().equals(Constants.ACTION.START_SERVICE)) {
                start();
            } else if (intent.getAction().equals(Constants.ACTION.STOP_SERVICE)) {
                stop();
            } else if (intent.getAction().equals(Constants.ACTION.SCHEDULE_REMINDERS)) {
                scheduleReminders();
            }
        } else {
            Log.d(TAG, "Service restarted after killed by OS.");
            start(); // TODO restart
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void start() {
        Log.d(TAG, "start()");
        if (dataIO == null){
            dataIO = DataIO.getInstance(this);
            dataIO.addOnDataChangedListener(this::loadData); // reload data when changed
        }
        loadData();

        broadcastMessage(Constants.MESSAGES.WEARABLE_SERVICE_STARTED);
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        //the intent filter specifies the messages we are interested in receiving
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION.BROADCAST_MESSAGE);
        broadcastManager.registerReceiver(receiver, filter);

        connect();
    }

    private void stop() {
        Log.i(TAG, "stop()");
        // broadcastMessage(Constants.MESSAGES.WEARABLE_SERVICE_STOPPED); // TODO
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        try {
            broadcastManager.unregisterReceiver(receiver);
        }catch (IllegalArgumentException e){
            e.printStackTrace();
        }

        mClient.disconnect(); // TODO check this works
    }

    /**
     * Broadcasts a message to other application components.
     * @param message a message, as defined in {@link Constants.MESSAGES}
     */
    private void broadcastMessage(int message) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.MESSAGE, message);
        intent.setAction(Constants.ACTION.BROADCAST_MESSAGE);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Notifies the data service to schedule reminders according to the medication schedule.
     * @param context A context used to initiate the service, e.g. an activity.
     */
    public static void scheduleReminders(Context context){
        Intent intent = new Intent(context, DataService.class);
        intent.setAction(Constants.ACTION.SCHEDULE_REMINDERS);
        context.startService(intent);
    }

    /**
     * Schedules reminders for all medications.
     *
     * Note that when setting reminders using the {@link AlarmManager}, notifications are not
     * delivered at the exact time, since the OS batches notifications for efficient delivery.
     * See Asaf Gamliel's answer at https://stackoverflow.com/questions/27676553/android-alarm-manager-working-but-delayed
     * for more details. To handle this, this method sets the reminder time to be 1 minute before the
     * time selected by the user. Early delivers are acceptable (as are late within reason).
     *
     * Notification IDs for reminders start at 1001, so don't fire notifications with IDs in the range 1001-1010.
     */
    private void scheduleReminders(){
        int notificationID = 1001; // TODO : cancel alarms before rescheduling, not just when rescheduling a particular notification
        if (reminders == null) return; // TODO : initialize? Also, always set reminder 3 hr 55 min after ideal time
        for (Medication medication : medications){
            Calendar[] medicationSchedule = schedule.get(medication);
            for (Calendar time : medicationSchedule){
                if (time != null) {
                    for (int numberOfMinutesPrior : reminders) {
                        Calendar alarmTime = (Calendar) time.clone();
                        alarmTime.add(Calendar.MINUTE, -numberOfMinutesPrior);
                        if (alarmTime.before(Calendar.getInstance())) {
                            alarmTime.add(Calendar.DATE, 1); // start tomorrow if the alarm should have happened already today
                        }
                        scheduleDailyNotification(getNotification(time, medication), notificationID++, alarmTime);
                    }
                }
            }
        }
    }

    /**
     * Schedules a notification to appear at a specific time each day.
     * @param notification The notification to appear.
     * @param notificationID The unique ID of the notification.
     * @param alarmTime The time at which the notification should be fired.
     */
    private void scheduleDailyNotification(Notification notification, int notificationID, Calendar alarmTime) {
        Intent notificationIntent = new Intent(this, NotificationPublisher.class);
        notificationIntent.setAction("reminder");
        notificationIntent.putExtra(NotificationPublisher.NOTIFICATION_ID, notificationID);
        notificationIntent.putExtra(NotificationPublisher.NOTIFICATION, notification);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, notificationID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), Constants.MILLISECONDS_PER_DAY, pendingIntent);
    }

    /**
     * Creates a reminder according to a medication.
     * @param medication The medication that must be taken.
     * @return a {@link Notification} object.
     */
    private Notification getNotification(Calendar time, Medication medication) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }
        builder.setDefaults(Notification.DEFAULT_ALL);
        int volumeOption = preferences.getInt(getString(R.string.pref_volume_key), getResources().getInteger(R.integer.pref_volume_index_default));
        if (volumeOption > 0){
            builder.setVibrate(Constants.NOTIFICATION_PATTERN);
        }
        if (volumeOption == 2){
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            builder.setSound(alarmSound);
        }
        String timeStr = Constants.DATE_FORMAT.AM_PM.format(time.getTime());
        builder.setContentTitle(getString(R.string.notification_reminder_title, timeStr));
        String notificationText = preferences.getString(
                getString(R.string.pref_notification_text_key),
                getString(R.string.default_notification_text));
        builder.setContentText(String.format(notificationText, medication.getName(), dosageMapping.get(medication)));
        builder.setLargeIcon(medication.getImage());
        // TODO : after API 23, you can use the medication image as the small icon; before that, use the pill icon
        // see https://stackoverflow.com/questions/23836920/how-to-set-bitmap-as-notification-icon-in-android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setSmallIcon(Icon.createWithBitmap(medication.getImage()));
        } else {
            builder.setSmallIcon(R.drawable.ic_pill_white_24dp);
        }

        return builder.build();
    }


}