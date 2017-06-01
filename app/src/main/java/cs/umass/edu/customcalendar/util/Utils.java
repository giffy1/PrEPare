package cs.umass.edu.customcalendar.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import cs.umass.edu.customcalendar.data.Adherence;
import cs.umass.edu.customcalendar.io.ApplicationPreferences;
import cs.umass.edu.customcalendar.constants.Constants;
import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.R;

/**
 * This class offers various utility functions.
 */
public class Utils {

    /**
     * Returns the drawable for a particular adherence type.
     * @param context A context is required to acquire the drawable from the project resources.
     * @param adherence The adherence type.
     * @return A drawable corresponding to the given adherence type.
     */
    public static GradientDrawable getDrawableForAdherence(Context context, Adherence.AdherenceType adherence){
        GradientDrawable drawable = new GradientDrawable();
        drawable.setStroke(2, ContextCompat.getColor(context, R.color.color_border));
        switch (adherence){
            case MISSED:
                drawable.setColor(ContextCompat.getColor(context, R.color.color_dose_missed));
                break;
            case TAKEN:
                drawable.setColor(ContextCompat.getColor(context, R.color.color_dose_taken));
                break;
            case TAKEN_CLARIFY_TIME:
                drawable.setColor(ContextCompat.getColor(context, R.color.color_dose_taken));
                drawable.setStroke(8, ContextCompat.getColor(context, R.color.color_border), 5, 5);
                break;
            case TAKEN_EARLY_OR_LATE:
                drawable.setColor(ContextCompat.getColor(context, R.color.color_dose_late));
                break;
            case FUTURE:
                drawable.setColor(ContextCompat.getColor(context, R.color.color_dose_future));
                break;
            default:
                break;
        }
        return drawable;
    }

    /**
     * Returns a {@link Calendar} object for the given year, month and day.
     * @param year an integer representing the year.
     * @param month a 0-index month representing the month.
     * @param day a 1-index day representing the day of the month.
     * @return a {@link Calendar} object.
     */
    public static Calendar getDateKey(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DATE, day);
        cal.clear(Calendar.HOUR);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        return cal;
    }

    /**
     * Returns a {@link Calendar} object corresponding to the specified date,
     * but with the hour, minute, seconds and millisecond fields set to 0 to
     * ensure successful date comparison.
     * @param date A {@link Calendar} object.
     * @return A new {@link Calendar} instance, with the same year, month and day as date
     */
    public static Calendar getDateKey(Calendar date) {
        Calendar cal = (Calendar) date.clone();
        cal.clear(Calendar.HOUR);
        cal.clear(Calendar.MINUTE);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        return cal;
    }

    /**
     * Sets the time of the given time picker to the specified time.
     * @param clock the time picker being modified.
     * @param time a time encoded in a {@link Calendar} object. Only the hour and minute fields are relevant.
     */
    @SuppressWarnings("deprecation")
    public static void setTime(TimePicker clock, Calendar time){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            clock.setHour(time.get(Calendar.HOUR));
            clock.setMinute(time.get(Calendar.MINUTE) / Constants.TIME_PICKER_INTERVAL);
        } else {
            clock.setCurrentHour(time.get(Calendar.HOUR_OF_DAY));
            clock.setCurrentMinute(time.get(Calendar.MINUTE) / Constants.TIME_PICKER_INTERVAL);
        }
    }

    /**
     * Acquires the time from the given time picker. Note the time picker must have a
     * minute interval of {@link Constants#TIME_PICKER_INTERVAL}.
     * @param clock the time picker.
     * @return a {@link Calendar} object encoding the time. Only the hour and minute fields are relevant.
     * All other fields refer to the current date.
     */
    @SuppressWarnings("deprecation")
    public static Calendar getTime(TimePicker clock){
        Calendar time = Calendar.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            time.set(Calendar.HOUR, clock.getHour() % 12);
            time.set(Calendar.MINUTE, Constants.TIME_PICKER_INTERVAL * clock.getMinute());
        }else {
            time.set(Calendar.HOUR, clock.getCurrentHour() % 12);
            time.set(Calendar.MINUTE, Constants.TIME_PICKER_INTERVAL * clock.getCurrentMinute());
        }
        return time;
    }

    /**
     * Acquires the time from the given unmodified time picker, i.e. its minute interval is 1.
     * @param clock the time picker.
     * @return a {@link Calendar} object encoding the time. Only the hour and minute fields are relevant.
     * All other fields refer to the current date.
     */
    @SuppressWarnings("deprecation")
    public static Calendar getTimeNoInterval(TimePicker clock){
        Calendar time = Calendar.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            time.set(Calendar.HOUR_OF_DAY, clock.getHour());
            time.set(Calendar.MINUTE, clock.getMinute());
        }else {
            time.set(Calendar.HOUR_OF_DAY, clock.getCurrentHour());
            time.set(Calendar.MINUTE, clock.getCurrentMinute());
        }
        return time;
    }

    /**
     * Used to generate and save dummy adherence data for testing purposes only.
     * @param context The context used to save the adherence data to disk.
     */
    public static void setDummyAdherenceData(Context context){
        ArrayList<Medication> medications = new ArrayList<>();
        Map<Calendar, Map<Medication, Adherence[]>> adherenceData = new TreeMap<>();
        Map<Medication, Integer> dosageMapping = new HashMap<>(); // in mg
        Map<Medication, Calendar[]> dailySchedule = new HashMap<>();

        medications.add(new Medication("Ritonavir"));
        Bitmap ritonavirImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.retonavir);
        medications.get(0).setImage(ritonavirImage);
        medications.get(0).setDefaultImage(ritonavirImage);

        medications.add(new Medication("Prezista"));
        Bitmap prezistaImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.prezista);
        medications.get(1).setImage(prezistaImage);
        medications.get(1).setDefaultImage(prezistaImage);

        medications.add(new Medication("Norvir"));
        Bitmap norvirImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.norvir);
        medications.get(2).setImage(norvirImage);
        medications.get(2).setDefaultImage(norvirImage);

        medications.add(new Medication("Descovy"));
        Bitmap descovyImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.descovy);
        medications.get(3).setImage(descovyImage);
        medications.get(3).setDefaultImage(descovyImage);

        dosageMapping.put(medications.get(0), 100);
        dosageMapping.put(medications.get(1), 200);
        dosageMapping.put(medications.get(2), 100);
        dosageMapping.put(medications.get(3), 50);

        // ----------- ADD SCHEDULE DATA --------------

        Calendar time1Med1 = Calendar.getInstance();
        time1Med1.set(Calendar.HOUR_OF_DAY, 7);
        time1Med1.set(Calendar.MINUTE, 0);
        Calendar time2Med1 = Calendar.getInstance();
        time2Med1.set(Calendar.HOUR_OF_DAY, 17);
        time2Med1.set(Calendar.MINUTE, 0);
        dailySchedule.put(medications.get(0), new Calendar[]{time1Med1, time2Med1});

        Calendar time1Med2 = Calendar.getInstance();
        time1Med2.set(Calendar.HOUR_OF_DAY, 10);
        time1Med2.set(Calendar.MINUTE, 0);
        Calendar time2Med2 = Calendar.getInstance();
        time2Med2.set(Calendar.HOUR_OF_DAY, 20);
        time2Med2.set(Calendar.MINUTE, 0);
        dailySchedule.put(medications.get(1), new Calendar[]{time1Med2, time2Med2});

        Calendar time2Med3 = Calendar.getInstance();
        time2Med3.set(Calendar.HOUR_OF_DAY, 17);
        time2Med3.set(Calendar.MINUTE, 0);
        dailySchedule.put(medications.get(2), new Calendar[]{null, time2Med3});

        Calendar time1Med4 = Calendar.getInstance();
        time1Med4.set(Calendar.HOUR_OF_DAY, 10);
        time1Med4.set(Calendar.MINUTE, 0);
        Calendar time2Med4 = Calendar.getInstance();
        time2Med4.set(Calendar.HOUR_OF_DAY, 20);
        time2Med4.set(Calendar.MINUTE, 0);
        dailySchedule.put(medications.get(3), new Calendar[]{time1Med4, time2Med4});

        //------------- ADD ADHERENCE DATA -----------------

        Calendar startDate = Calendar.getInstance();
        startDate.set(Calendar.MONTH, Calendar.APRIL);
        startDate.set(Calendar.DATE, 26);
        Calendar endDate = Calendar.getInstance();
        endDate.set(Calendar.MONTH, Calendar.AUGUST);
        endDate.set(Calendar.DATE, 15);
        while (startDate.before(endDate)){
            Map<Medication, Adherence[]> singleDayAdherenceData = new HashMap<>();
            for (Medication medication : medications){
                Adherence[] medicationAdherence = new Adherence[2];
                for (int j = 0; j < medicationAdherence.length; j++){
                    if (dailySchedule.get(medication)[j] == null)
                        medicationAdherence[j] = new Adherence(Adherence.AdherenceType.NONE, null);
                    else {
                        Calendar timeTaken = (Calendar) dailySchedule.get(medication)[j].clone();
                        medicationAdherence[j] = new Adherence(Adherence.AdherenceType.TAKEN, timeTaken);
                    }
                }
                singleDayAdherenceData.put(medication, medicationAdherence);
            }
            adherenceData.put(Utils.getDateKey(startDate), singleDayAdherenceData);
            startDate.add(Calendar.DATE, 1);
        }

        //------------------- SAVE TO DISK --------------------

        ApplicationPreferences preferences = ApplicationPreferences.getInstance(context);
        preferences.setAdherenceData(adherenceData);
        preferences.setDailySchedule(dailySchedule);
        preferences.setMedications(medications);
        preferences.setDosageMapping(dosageMapping);
        preferences.scheduleReminders();
    }
}
