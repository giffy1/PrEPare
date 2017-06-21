package cs.umass.edu.customcalendar.io;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import cs.umass.edu.customcalendar.data.Adherence;
import cs.umass.edu.customcalendar.data.Medication;

/**
 * A simplified preference manager which exposes the relevant variables
 * and updates the variables automatically when the preferences are changed.
 */
public class ApplicationPreferences {

    /** The list of medications. **/
    private ArrayList<Medication> medications;

    /** The entire adherence data. Date is stored in a tree map because dates are naturally ordered. **/
    private Map<Calendar, Map<Medication, Adherence[]>> adherenceData;

    /** Maps a medication to a dosage **/
    private Map<Medication, Integer> dosageMapping; // in mg

    /** Maps a medication to a schedule (a list of times to take the medication). **/
    private Map<Medication, Calendar[]> schedule;

    /** Maps a medication to a unique Mac Address. **/
    private Map<Medication, String> addressMapping;

    private TreeSet<Integer> reminders;

    private static ApplicationPreferences instance;

    public interface OnDataChangedListener {
        void onDataChanged();
    }

    private final List<OnDataChangedListener> onDataChangedListeners = new ArrayList<>();

    public void addOnDataChangedListener(OnDataChangedListener onDataChangedListener){
        this.onDataChangedListeners.add(onDataChangedListener);
    }

    public static ApplicationPreferences getInstance(Context context){
        if (instance == null)
            instance = new ApplicationPreferences(context);
        return instance;
    }

    private ApplicationPreferences(Context context){
        loadPreferences(context);
    }

    @SuppressWarnings("unchecked")
    private void loadPreferences(Context context){
        medications = (ArrayList<Medication>) readObject(context, "medications");
        dosageMapping = (Map<Medication, Integer>) readObject(context, "dosage_mapping");
        schedule = (Map<Medication, Calendar[]>) readObject(context, "daily_schedule");
        adherenceData = (Map<Calendar, Map<Medication, Adherence[]>>) readObject(context, "adherence_data");
        addressMapping = (Map<Medication, String>) readObject(context, "address_mapping");
        reminders = (TreeSet<Integer>) readObject(context, "reminders");
    }

    /**
     * Reads an object from disk. TODO : Should this be done on a background thread?
     * @param context a context required to access storage.
     * @param filename the file from which to read the data.
     * @return an object containing the data read from the specified file.
     */
    private Object readObject(Context context, String filename) {
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

    /**
     * Writes an object to disk in a background thread.
     * @param context a context required to access storage.
     * @param object the data to write to disk.
     * @param filename the file to which to write the data.
     */
    private void writeObject(Context context, Object object, String filename){
        this.onDataChangedListeners.forEach(OnDataChangedListener::onDataChanged);

        File file = new File(context.getDir("data", Context.MODE_PRIVATE), filename);
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(object);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        Runnable r = () -> {
//
//        };
//
//        Thread t = new Thread(r); // save to disk on background thread
//        t.start();
    }

    public void setMedications(Context context, ArrayList<Medication> medications){
        this.medications = medications;
        writeObject(context, medications, "medications");
    }

    public void setAdherenceData(Context context, Map<Calendar, Map<Medication, Adherence[]>> adherenceData){
        this.adherenceData = adherenceData;
        writeObject(context, adherenceData, "adherence_data");
    }

    public void setSchedule(Context context, Map<Medication, Calendar[]> schedule){
        this.schedule = schedule;
        writeObject(context, schedule, "daily_schedule");
    }

    public void setDosageMapping(Context context, Map<Medication, Integer> dosageMapping){
        this.dosageMapping = dosageMapping;
        writeObject(context, dosageMapping, "dosage_mapping");
    }

    public void setAddressMapping(Context context, Map<Medication, String> addressMapping){
        this.addressMapping = addressMapping;
        writeObject(context, addressMapping, "address_mapping");
    }

    public void setReminders(Context context, TreeSet<Integer> reminders) {
        this.reminders = reminders;
        writeObject(context, reminders, "reminders");
    }

    public ArrayList<Medication> getMedications(Context context){
        if (medications == null)
            loadPreferences(context);
        return medications;
    }

    public Map<Calendar, Map<Medication, Adherence[]>> getAdherenceData(Context context){
        if (adherenceData == null)
            loadPreferences(context);
        return adherenceData;
    }

    public Map<Medication, Calendar[]> getSchedule(Context context){
        if (schedule == null)
            loadPreferences(context);
        return schedule;
    }

    public Map<Medication, Integer> getDosageMapping(Context context){
        if (dosageMapping == null)
            loadPreferences(context);
        return dosageMapping;
    }

    public Map<Medication, String> getAddressMapping(Context context) {
        if (addressMapping == null)
            loadPreferences(context);
        return addressMapping;
    }

    public TreeSet<Integer> getReminders(Context context){
        if (reminders == null)
            loadPreferences(context);
        return reminders;
    }

}
