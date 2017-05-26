package cs.umass.edu.customcalendar.io;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.data.Adherence;
import cs.umass.edu.customcalendar.reminders.NotificationPublisher;

/**
 * A simplified preference manager which exposes the relevant variables
 * and updates the variables automatically when the preferences are changed.
 */
public class ApplicationPreferences {

    /** The list of medications. **/
    private ArrayList<Medication> medications = new ArrayList<>();

    /** The entire adherence data. Date is stored in a tree map because dates are naturally ordered. **/
    private Map<Calendar, Map<Medication, Adherence[]>> adherenceData = new TreeMap<>();

    /** Maps a medication to a dosage **/
    private Map<Medication, Integer> dosageMapping = new HashMap<>(); // in mg

    /** Maps a medication to a schedule (a list of times to take the medication). **/
    private Map<Medication, Calendar[]> dailySchedule = new HashMap<>();

    private static ApplicationPreferences instance;

    private Context context;

    public static ApplicationPreferences getInstance(Context context){
        if (instance == null)
            instance = new ApplicationPreferences(context);
        return instance;
    }

    private ApplicationPreferences(Context context){
        this.context = context;
        medications = new ArrayList<>();
        adherenceData = new TreeMap<>();
        dosageMapping = new HashMap<>();
        dailySchedule = new HashMap<>();

        loadPreferences();
    }

    @SuppressWarnings("unchecked")
    private void loadPreferences(){
        medications = (ArrayList<Medication>) readObject("medications");
        dosageMapping = (Map<Medication, Integer>) readObject("dosage_mapping");
        dailySchedule = (Map<Medication, Calendar[]>) readObject("daily_schedule");
        adherenceData = (Map<Calendar, Map<Medication, Adherence[]>>) readObject("adherence_data");
    }

    private Object readObject(String filename) {
        Object object = null;
        File file = new File(context.getDir("data", Context.MODE_PRIVATE), filename);
        try {
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
            object = inputStream.readObject();
            inputStream.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return object;
    }

    private void writeObject(Object object, String filename){
        File file = new File(context.getDir("data", Context.MODE_PRIVATE), filename);
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(object);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setMedications(ArrayList<Medication> medications){
        this.medications = medications;
        writeObject(medications, "medications");
    }

    public void setAdherenceData(Map<Calendar, Map<Medication, Adherence[]>> adherenceData){
        this.adherenceData = adherenceData;
        writeObject(adherenceData, "adherence_data");
    }

    public void setDailySchedule(Map<Medication, Calendar[]> dailySchedule){
        this.dailySchedule = dailySchedule;
        writeObject(dailySchedule, "daily_schedule");
    }

    public void setDosageMapping(Map<Medication, Integer> dosageMapping){
        this.dosageMapping = dosageMapping;
        writeObject(dosageMapping, "dosage_mapping");
    }

    public ArrayList<Medication> getMedications(){
        return medications;
    }

    public Map<Calendar, Map<Medication, Adherence[]>> getAdherenceData(){
        return adherenceData;
    }

    public Map<Medication, Calendar[]> getDailySchedule(){
        return dailySchedule;
    }

    public Map<Medication, Integer> getDosageMapping(){
        return dosageMapping;
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
    public void scheduleReminders(){
        int notificationID = 1001;
        for (Medication medication : medications){
            Calendar[] schedule = dailySchedule.get(medication);
            for (Calendar time : schedule){
                if (time != null) {
                    Calendar alarmTime = (Calendar) time.clone();
                    alarmTime.add(Calendar.MINUTE, -1);
                    if (alarmTime.before(Calendar.getInstance())) {
                        alarmTime.add(Calendar.DATE, 1); // start tomorrow if the alarm should have happened already today
                    }
                    scheduleDailyNotification(getNotification(medication), notificationID++, alarmTime);
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
        Intent notificationIntent = new Intent(context, NotificationPublisher.class);
        notificationIntent.setAction("reminder");
        notificationIntent.putExtra(NotificationPublisher.NOTIFICATION_ID, notificationID);
        notificationIntent.putExtra(NotificationPublisher.NOTIFICATION, notification);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarmTime.getTimeInMillis(), 24*60*60*1000, pendingIntent);
    }

    /**
     * Creates a reminder according to a medication.
     * @param medication The medication that must be taken.
     * @return a {@link Notification} object.
     */
    private Notification getNotification(Medication medication) {
        Notification.Builder builder = new Notification.Builder(context);
        long[] pattern = {0, 600, 0};
        builder.setVibrate(pattern);
        // TODO: uncomment for sound, also let user customize
//        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//        builder.setSound(alarmSound);
        builder.setContentTitle("Take your pill!");
        builder.setContentText(String.format("Don't forget to take your %s", medication.getName()));
        builder.setLargeIcon(medication.getImage());
        // builder.setSmallIcon(medication.getImage()); // TODO What about small icon? see https://stackoverflow.com/questions/23836920/how-to-set-bitmap-as-notification-icon-in-android
        return builder.build();
    }

}
