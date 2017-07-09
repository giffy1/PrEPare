package cs.umass.edu.prepare.data;

import android.content.Context;
import android.os.AsyncTask;

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

/**
 * This class is responsible for reading and writing data to disk and managing
 * data shared across application components.
 */
public class DataIO {

    /** The list of medications. **/
    private ArrayList<Medication> medications;

    /** The entire adherence data. Date is stored in a tree map because dates are naturally ordered. **/
    private Map<Calendar, Map<Medication, Adherence[]>> adherenceData;

    /** Maps a medication to a dosage **/
    private Map<Medication, Integer> dosageMapping; // in mg

    /** Maps a medication to a schedule (a list of times to take the medication). **/
    private Map<Medication, Calendar[]> schedule;

    /** Maps a medication to a unique Mac Address. **/
    private Map<String, Medication> addressMapping;

    private TreeSet<Integer> reminders;

    private static DataIO instance;

    private interface FILENAME {
        String MEDICATIONS = "medications";
        String DOSAGE_MAPPING = "dosage_mapping";
        String SCHEDULE = "daily_schedule";
        String ADHERENCE_DATA = "adherence_data";
        String ADDRESS_MAPPING = "address_mapping";
        String REMINDERS = "reminders";
    }

    private static final String DIRECTORY = "data";

    public interface OnDataChangedListener {
        void onDataChanged();
    }

    private final List<OnDataChangedListener> onDataChangedListeners = new ArrayList<>();

    public void addOnDataChangedListener(OnDataChangedListener onDataChangedListener){
        this.onDataChangedListeners.add(onDataChangedListener);
    }

    public static DataIO getInstance(Context context){
        if (instance == null)
            instance = new DataIO(context);
        return instance;
    }

    private DataIO(Context context){
        loadPreferences(context);
    }

    @SuppressWarnings("unchecked")
    private void loadPreferences(Context context){
        medications = (ArrayList<Medication>) readObject(context, FILENAME.MEDICATIONS);
        dosageMapping = (Map<Medication, Integer>) readObject(context, FILENAME.DOSAGE_MAPPING);
        schedule = (Map<Medication, Calendar[]>) readObject(context, FILENAME.SCHEDULE);
        adherenceData = (Map<Calendar, Map<Medication, Adherence[]>>) readObject(context, FILENAME.ADHERENCE_DATA);
        addressMapping = (Map<String, Medication>) readObject(context, FILENAME.ADDRESS_MAPPING);
        reminders = (TreeSet<Integer>) readObject(context, FILENAME.REMINDERS);
    }

    /**
     * Reads an object from disk. TODO : Should this be done on a background thread?
     * @param context a context required to access storage.
     * @param filename the file from which to read the data.
     * @return an object containing the data read from the specified file.
     */
    private Object readObject(Context context, String filename) {
        Object object = null;
        File file = new File(context.getDir(DIRECTORY, Context.MODE_PRIVATE), filename);
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
     * An asynchronous task for writing data to disk.
     */
    private class WriteOperation extends AsyncTask<Object, Void, Void> {

        private final Context context;

        WriteOperation(Context context){
            super();
            this.context = context;
        }

        @Override
        protected Void doInBackground(Object... params) {
            Object object = params[0];
            String filename = (String) params[1];

            File file = new File(context.getDir(DIRECTORY, Context.MODE_PRIVATE), filename);
            try {
                ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
                outputStream.writeObject(object);
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {}

        @Override
        protected void onPreExecute() {}

        @Override
        protected void onProgressUpdate(Void... values) {}
    }

    /**
     * Writes an object to disk in a background thread.
     * @param context a context required to access storage.
     * @param object the data to write to disk.
     * @param filename the file to which to write the data.
     */
    private void writeObject(Context context, Object object, String filename){
        new WriteOperation(context).doInBackground(object, filename);
        this.onDataChangedListeners.forEach(OnDataChangedListener::onDataChanged);
    }

    public void setMedications(Context context, ArrayList<Medication> medications){
        this.medications = medications;
        writeObject(context, medications, FILENAME.MEDICATIONS);
    }

    public void setAdherenceData(Context context, Map<Calendar, Map<Medication, Adherence[]>> adherenceData){
        this.adherenceData = adherenceData;
        writeObject(context, adherenceData, FILENAME.ADHERENCE_DATA);
    }

    public void setSchedule(Context context, Map<Medication, Calendar[]> schedule){
        this.schedule = schedule;
        writeObject(context, schedule, FILENAME.SCHEDULE);
    }

    public void setDosageMapping(Context context, Map<Medication, Integer> dosageMapping){
        this.dosageMapping = dosageMapping;
        writeObject(context, dosageMapping, FILENAME.DOSAGE_MAPPING);
    }

    public void setAddressMapping(Context context, Map<String, Medication> addressMapping){
        this.addressMapping = addressMapping;
        writeObject(context, addressMapping, FILENAME.ADDRESS_MAPPING);
    }

    public void setReminders(Context context, TreeSet<Integer> reminders) {
        this.reminders = reminders;
        writeObject(context, reminders, FILENAME.REMINDERS);
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

    public Map<String, Medication> getAddressMapping(Context context) {
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
