package cs.umass.edu.customcalendar.view.activities;

import android.app.Activity;
import android.app.ActivityManager;
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
import android.view.View;
import android.widget.Button;
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
import java.util.Locale;
import java.util.Map;

import cs.umass.edu.customcalendar.R;
import cs.umass.edu.customcalendar.constants.Constants;
import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.io.ApplicationPreferences;
import cs.umass.edu.customcalendar.util.Utils;
import cs.umass.edu.customcalendar.view.custom.MedicationArrayAdapter;

/**
 * This activity allows the user to adjust medication reminder times and
 * dosages, as well as upload a picture of each medication.
 */
public class SettingsActivity extends BaseActivity {

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

    /** The number picker for the medication dosage. **/
    private NumberPicker dosagePicker;

    /** The position of the selected medication. **/
    private int selectedPosition=0;

    private Bitmap medicationImage;

    private TextView imgMedication;

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
        dosagePicker = (NumberPicker) dialog.findViewById(R.id.dosagePicker);

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

        int minValue = 50;
        int maxValue = 500;
        int increment = 25;
        int numberOfValues = 1 + (maxValue - minValue) / increment;
        final String[] minuteValues = new String[numberOfValues];

        int index = 0;
        for (int i = minValue; i <= maxValue; i+=increment) {
            minuteValues[index++] = String.valueOf(i) + " mg";
        }
        dosagePicker.setMaxValue(numberOfValues-1);
        dosagePicker.setDisplayedValues(minuteValues);
        dosagePicker.setValue((dosageMapping.get(medication) - minValue) / increment);
        // TODO: Why doesn't this work??
//        dosagePicker.setFormatter(new NumberPicker.Formatter() {
//            @Override
//            public String format(int i) {
//                return i + " mg";
//            }
//        });
//        dosagePicker.invalidate();

        Button cancelButton = (Button) dialog.findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        Button saveButton = (Button) dialog.findViewById(R.id.btn_save);
        saveButton.setOnClickListener(v -> {
            String dosageStr = minuteValues[dosagePicker.getValue()];
            int dosage = Integer.parseInt(dosageStr.split(" ")[0]);
            dosageMapping.put(medication, dosage);

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
            ApplicationPreferences preferences = ApplicationPreferences.getInstance(SettingsActivity.this);
            preferences.setMedications(medications);
            preferences.setDosageMapping(dosageMapping);
            preferences.setDailySchedule(dailySchedule);
            preferences.scheduleReminders();
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

    /**
     * Loads the data from disk. This includes the list of medications, the corresponding dosages
     * and the daily schedule. Adherence data is not needed here.
     */
    private void loadData(){
        ApplicationPreferences preferences = ApplicationPreferences.getInstance(this);

        medications = preferences.getMedications();
        dosageMapping = preferences.getDosageMapping();
        dailySchedule = preferences.getDailySchedule();
    }

    public void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings);
        super.onCreate(savedInstanceState);

        loadData();

        Button cancelButton = (Button) findViewById(R.id.btn_cancel_settings);
        cancelButton.setOnClickListener(view -> finish());

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

        Button adjustButton = (Button) findViewById(R.id.btn_adjust);
        adjustButton.setOnClickListener(view -> {
            if (selectedPosition >= 0) {
                showMedicationAdjustmentDialog();
            }
        });
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
