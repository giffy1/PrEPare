package cs.umass.edu.customcalendar.view.activities;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import cs.umass.edu.customcalendar.R;
import cs.umass.edu.customcalendar.constants.Constants;
import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.io.ApplicationPreferences;
import cs.umass.edu.customcalendar.services.DataService;
import cs.umass.edu.customcalendar.util.Utils;
import cs.umass.edu.customcalendar.view.custom.MedicationArrayAdapter;

/**
 * This activity allows users to set reminder times by specifying the number of minutes prior to
 * the ideal medication time that a notification alarm should fire. The user is welcome to
 * specify multiple notification times or none at all. By default, notifications will fire at
 * the ideal time. A notification will also fire 3:55 after the ideal time, just before the end
 * of the TAKEN period; note that this reminder cannot be disabled and also will not be shown in
 * this view.
 */
public class ReminderActivity extends BaseActivity {

    /** Request ID for receiving image data from the camera. **/
    private static final int CAMERA_REQUEST = 1888;

    /** Request ID for receiving image data from the gallery. **/
    private static final int IMAGE_REQUEST = 1889;

    /** The list of medications. **/
    private ArrayList<Medication> medications;

    /** A mapping from medication to dosage. **/
    private Map<Medication, Integer> dosageMapping;

    /** A mapping from medication to times of day when it should be taken. **/
    private Map<Medication, Calendar[]> dailySchedule;

    /** The custom adapter for displaying medication information in a list view. **/
    private MedicationArrayAdapter medicationAdapter;

    /** The time picker for the first pill (AM). **/
    private TimePicker timePickerAM;

    /** The time picker for the second pill (PM). **/
    private TimePicker timePickerPM;

    /** The position of the selected medication. **/
    private int selectedPosition=0;

    private Bitmap medicationImage;

    private TextView imgMedication;

    /** The ordered set of reminders specified by the user. */
    private TreeSet<Integer> reminders = new TreeSet<>();

    /** The list of reminders shown to the user, e.g. "On time", "10 min. before". */
    private ArrayList<String> reminderStrings = new ArrayList<>();

    /** Populates the list view with the reminder strings. */
    private ArrayAdapter<String> adapter;

    /** Indicates that the user is adding a reminder, not editing one. */
    private static final int APPEND_TO_LIST = -1;

    /** Indicates that the user is removing a reminder by selecting the "None" option. */
    private static final int REMOVE_REMINDER = -1;

    /** Specific reminder options to choose from, not including "custom". */
    private int[] options = {REMOVE_REMINDER, 0, 10, 30, 60};

    /** Human-readable reminder options to choose from, including "custom". */
    private String[] optionStrings = {"None", "On time", "10 min before", "30 min before", "1 hour before", "Custom"};

    /**
     * Loads the set of reminders from disk.
     */
    private void loadData(){
        ApplicationPreferences preferences = ApplicationPreferences.getInstance(this);
        medications = preferences.getMedications(this);
        dosageMapping = preferences.getDosageMapping(this);
        dailySchedule = preferences.getSchedule(this);

        reminders = preferences.getReminders(this);
        if (reminders == null){
            reminders = new TreeSet<>();
            reminders.add(0); // by default fire notification at ideal time
        }
    }

    /**
     * Updates the list of human-readable reminder strings from the set of selected reminders.
     */
    private void updateStringSet(){
        reminderStrings.clear();
        for (int numberOfMinutesPrior : reminders) {
            reminderStrings.add(getReminderString(numberOfMinutesPrior));
        }
    }

    /**
     * Maps a reminder to a human readable string. For instance, a value of 60 may map to
     * the string "1 hour before".
     * @param numberOfMinutesPrior the number of minutes prior to the ideal time.
     * @return a human-readable string indicating how many minutes prior to the ideal time the reminder will fire.
     */
    private String getReminderString(int numberOfMinutesPrior){
        if (numberOfMinutesPrior == 0) {
            return "On time";
        } else if (numberOfMinutesPrior < 60){
            return numberOfMinutesPrior + " min. before.";
        } else {
            int numberOfHoursPrior = numberOfMinutesPrior / 60;
            numberOfMinutesPrior = numberOfMinutesPrior % 60;
            if (numberOfMinutesPrior == 0){
                return numberOfHoursPrior + " hr. before.";
            } else {
                return numberOfHoursPrior + " hr. " + numberOfMinutesPrior + " min. before.";
            }
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_set_reminders);
        super.onCreate(savedInstanceState);

        loadData();
        updateStringSet();

        Button cancelButton = (Button) findViewById(R.id.btn_cancel_settings);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        if (medications == null){
            Toast.makeText(this, "No medications found. Please contact your primary care provider.", Toast.LENGTH_SHORT).show();
            return;
        }
        medicationAdapter = new MedicationArrayAdapter(this, medications, dosageMapping, dailySchedule);
        ListView medicationsList = (ListView) findViewById(R.id.lvMedications);
        medicationsList.setAdapter(medicationAdapter);
        medicationAdapter.notifyDataSetChanged();
        medicationsList.setOnItemClickListener((parent, view, position, arg3) -> {
            selectedPosition = position;
            view.setSelected(true);
        });
        medicationsList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                medicationsList.getChildAt(0).setSelected(true);
            }
        });

        Button adjustButton = (Button) findViewById(R.id.btn_adjust);
        adjustButton.setOnClickListener(view -> {
            if (selectedPosition >= 0) {
                showMedicationAdjustmentDialog();
            }
        });

        ListView lvReminders = (ListView) findViewById(R.id.lvReminders);
        lvReminders.setOnItemClickListener((adapterView, view, i, l) -> showReminderOptionDialog(i));
        adapter = new ArrayAdapter<> (this, android.R.layout.simple_list_item_1, android.R.id.text1, reminderStrings);
        lvReminders.setAdapter(adapter);

        TextView txtAddReminder = (TextView) findViewById(R.id.txtAddReminder);
        txtAddReminder.setOnClickListener(view -> showReminderOptionDialog(APPEND_TO_LIST));
    }

    /**
     * Saves the reminders to disk.
     */
    private void saveReminders(){
        ApplicationPreferences preferences = ApplicationPreferences.getInstance(this);
        preferences.setReminders(this, reminders);
    }

    /**
     * Returns the value of the reminder at the specified index. This has to be done by
     * iterating over the set, because the set isn't intended to have that functionality.
     * @param k the index
     * @return the value at index k
     */
    private int getReminderValueAtIndex(int k){
        Iterator<Integer> it = reminders.iterator();
        int i = 0;
        int current = 0;
        while(it.hasNext() && i <= k) {
            current = it.next();
            i++;
        }
        return current;
    }

    /**
     * Sets a reminder to the specified number of minutes prior to the ideal time.
     * @param selectedIndex the reminder chosen for editing, -1 if adding a new reminder.
     * @param numberOfMinutesPrior the number of minutes prior to the ideal time, selected/input by the user.
     */
    private void setReminder(int selectedIndex, int numberOfMinutesPrior){
        int selectedValue = getReminderValueAtIndex(selectedIndex);
        if (selectedIndex != APPEND_TO_LIST) {
            reminders.remove(selectedValue);
        }
        if (numberOfMinutesPrior != REMOVE_REMINDER){
            Log.i("WTF", "add");
            reminders.add(numberOfMinutesPrior);
        }
        Log.i("WTF", reminders.toString());
        updateStringSet();
        adapter.notifyDataSetChanged();
        saveReminders();
        DataService.scheduleReminders(this);
    }

    /**
     * Allows the user to select a reminder option from a list of options.
     * @param selectedIndex the reminder chosen for editing, -1 if adding a new reminder.
     */
    private void showReminderOptionDialog(int selectedIndex) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_add_alarm_black_24dp);
        builder.setTitle("Select Reminder Option");
        builder.setSingleChoiceItems(optionStrings, -1, (dialog, index) -> {
            if (index == optionStrings.length-1){ // CUSTOM
                showIntegerInput(selectedIndex);
            } else {
                setReminder(selectedIndex, options[index]);
            }
            dialog.dismiss();
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Allows the user to enter a custom reminder time, defined by the number of minutes prior to the ideal time.
     * @param selectedValue the reminder chosen for editing, -1 if adding a new reminder.
     */
    private void showIntegerInput(int selectedValue){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select number of minutes:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            int numberOfMinutesPrior = Integer.parseInt(input.getText().toString());
            setReminder(selectedValue, numberOfMinutesPrior);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * Loads and displays the dialog for editing the activity_settings for a particular medication.
     */
    private void showMedicationAdjustmentDialog(){
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_adjust_medication);

        final Medication medication = medications.get(selectedPosition);

        imgMedication = (TextView) dialog.findViewById(R.id.imgMedication);
        TextView txtMedicationName = (TextView) dialog.findViewById(R.id.txtMedicationName);

        BitmapDrawable medicationDrawable = new BitmapDrawable(getResources(), medication.getImage());
        imgMedication.setCompoundDrawablesWithIntrinsicBounds(null, medicationDrawable, null, null);
        imgMedication.setText(String.format(Locale.getDefault(), "%d mg", dosageMapping.get(medication)));
        txtMedicationName.setText(medication.getName());

        timePickerAM = (TimePicker) dialog.findViewById(R.id.timePickerAM);
        timePickerPM = (TimePicker) dialog.findViewById(R.id.timePickerPM);

        setTimePickerInterval(timePickerAM);
        setTimePickerInterval(timePickerPM);

        Calendar[] schedule = dailySchedule.get(medication);

        Calendar time = schedule[0];
        View labelAM = dialog.findViewById(R.id.label_AM);
        if (time == null) {
            timePickerAM.setVisibility(View.INVISIBLE);
            labelAM.setVisibility(View.INVISIBLE);
        } else {
            Utils.setTime(timePickerAM, time);
            timePickerAM.setVisibility(View.VISIBLE);
            labelAM.setVisibility(View.VISIBLE);
        }

        time = schedule[1];
        View labelPM = dialog.findViewById(R.id.label_PM);
        if (time == null) {
            timePickerPM.setVisibility(View.INVISIBLE);
            labelPM.setVisibility(View.INVISIBLE);
        } else {
            Utils.setTime(timePickerPM, time);
            timePickerPM.setVisibility(View.VISIBLE);
            labelPM.setVisibility(View.VISIBLE);
        }

        Button cancelButton = (Button) dialog.findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        Button saveButton = (Button) dialog.findViewById(R.id.btn_save);
        saveButton.setOnClickListener(v -> {

            Calendar timeAM = Utils.getTime(timePickerAM);
            Calendar timePM = Utils.getTime(timePickerPM);

            Calendar[] schedule1 = dailySchedule.get(medication);
            if (schedule1[0] != null) {
                schedule1[0] = timeAM;
                schedule1[0].set(Calendar.AM_PM, Calendar.AM);
            }
            if (schedule1[1] != null) {
                schedule1[1] = timePM;
                schedule1[1].set(Calendar.AM_PM, Calendar.PM);
            }

            if (medicationImage != null)
                medication.setImage(medicationImage);

            dialog.dismiss();
            medicationAdapter.notifyDataSetChanged();

            // persist to disk
            ApplicationPreferences preferences = ApplicationPreferences.getInstance(ReminderActivity.this);
            preferences.setMedications(this, medications);
            preferences.setSchedule(this, dailySchedule);

            DataService.scheduleReminders(this);
        });

        TextView takePhoto = (TextView) dialog.findViewById(R.id.take_photo);
        takePhoto.setOnClickListener(view -> {
            medicationImage = null; // reset
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(cameraIntent, CAMERA_REQUEST);
        });

        TextView chooseFromGallery = (TextView) dialog.findViewById(R.id.choose_from_gallery);
        chooseFromGallery.setOnClickListener(view -> {
            medicationImage = null; // reset
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), IMAGE_REQUEST);
        });

        TextView refreshImage = (TextView) dialog.findViewById(R.id.default_medication_icon);
        refreshImage.setOnClickListener(view -> {
            medicationImage = medication.getDefaultImage();
            BitmapDrawable medicationDrawable1 = new BitmapDrawable(getResources(), medicationImage);
            imgMedication.setCompoundDrawablesWithIntrinsicBounds(null, medicationDrawable1, null, null);
        });

        dialog.show();
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        if (parcelFileDescriptor == null) return null;
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            medicationImage = (Bitmap) data.getExtras().get("data");
            BitmapDrawable medicationDrawable = new BitmapDrawable(getResources(), medicationImage);
            imgMedication.setCompoundDrawablesWithIntrinsicBounds(null, medicationDrawable, null, null);
        } else if (requestCode == IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            Uri selectedImageUri = data.getData();
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            int iconSize = am.getLauncherLargeIconSize();
            try {
                Bitmap selectedImage = getBitmapFromUri(selectedImageUri);
                // resize to size of icon:
                medicationImage = Bitmap.createScaledBitmap(selectedImage, iconSize, iconSize, true);

                BitmapDrawable medicationDrawable = new BitmapDrawable(getResources(), medicationImage);
                imgMedication.setCompoundDrawablesWithIntrinsicBounds(null, medicationDrawable, null, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setTimePickerInterval(TimePicker timePicker) {
        Class<?> classForID;
        Field field;
        NumberPicker minutePicker;
        // a bit of a hack, we can get and modify the time picker's number picker view
        try {
            classForID = Class.forName("com.android.internal.R$id");
            field = classForID.getField("minute");
            minutePicker = (NumberPicker) timePicker.findViewById(field.getInt(null));
        }catch(ClassNotFoundException | NoSuchFieldException | IllegalAccessException e){
            e.printStackTrace();
            return;
        }

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(60 / Constants.TIME_PICKER_INTERVAL - 1);
        ArrayList<String> displayedValues = new ArrayList<>();
        for (int i = 0; i < 60; i += Constants.TIME_PICKER_INTERVAL) {
            displayedValues.add(String.format(Locale.getDefault(), "%02d", i));
        }
        minutePicker.setDisplayedValues(displayedValues.toArray(new String[0]));
        minutePicker.setWrapSelectorWheel(true);
    }

}
