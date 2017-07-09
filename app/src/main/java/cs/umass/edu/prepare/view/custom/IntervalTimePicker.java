package cs.umass.edu.prepare.view.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.NumberPicker;
import android.widget.TimePicker;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.constants.Constants;

/**
 * @author Sean Noran
 */
public class IntervalTimePicker extends TimePicker {

    /** Used for debugging purposes. */
    @SuppressWarnings("unused")
    private static final String TAG = IntervalTimePicker.class.getName();

    /** The list of options in the segmented radio group. */
    private int interval;

    public IntervalTimePicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.IntervalTimePicker, defStyle, 0);
        try {
            interval = a.getInteger(R.styleable.IntervalTimePicker_interval, 1);
            setTimePickerInterval();
        }
        finally {
            a.recycle();
        }
    }

    public IntervalTimePicker(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.IntervalTimePicker, 0, 0);
        try {
            interval = a.getInteger(R.styleable.IntervalTimePicker_interval, 0);
            setTimePickerInterval();
        }
        finally {
            a.recycle();
        }
    }

    private void setTimePickerInterval() {
        Class<?> classForID;
        Field field;
        NumberPicker minutePicker;
        // a bit of a hack, we can get and modify the time picker's number picker view
        try {
            classForID = Class.forName("com.android.internal.R$id");
            field = classForID.getField("minute");
            minutePicker = (NumberPicker) findViewById(field.getInt(null));
        }catch(ClassNotFoundException | NoSuchFieldException | IllegalAccessException e){
            e.printStackTrace();
            return;
        }

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(60 / interval - 1);
        ArrayList<String> displayedValues = new ArrayList<>();
        for (int i = 0; i < 60; i += interval) {
            displayedValues.add(String.format(Locale.getDefault(), "%02d", i));
        }
        minutePicker.setDisplayedValues(displayedValues.toArray(new String[0]));
        minutePicker.setWrapSelectorWheel(true);
    }

    @Override
    public int getMinute() {
        return Constants.TIME_PICKER_INTERVAL * super.getMinute();
    }

    @NonNull
    @Override
    @SuppressWarnings("deprecation")
    public Integer getCurrentMinute() {
        return Constants.TIME_PICKER_INTERVAL * super.getCurrentMinute();
    }

    /**
     * Acquires the time from the given time picker. Note the time picker must have a
     * minute interval of {@link Constants#TIME_PICKER_INTERVAL}.
     * @return a {@link Calendar} object encoding the time. Only the hour and minute fields are relevant.
     * All other fields refer to the current date.
     */
    @SuppressWarnings("deprecation")
    public Calendar getTime(){
        Calendar time = Calendar.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            time.set(Calendar.HOUR_OF_DAY, getHour());
            time.set(Calendar.MINUTE, getMinute());
        }else {
            time.set(Calendar.HOUR_OF_DAY, getCurrentHour());
            time.set(Calendar.MINUTE, getCurrentMinute());
        }
        return time;
    }

    /**
     * Sets the time of the given time picker to the specified time.
     * @param time a time encoded in a {@link Calendar} object. Only the hour and minute fields are relevant.
     */
    @SuppressWarnings("deprecation")
    public void setTime(Calendar time){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setHour(time.get(Calendar.HOUR_OF_DAY));
            setMinute(time.get(Calendar.MINUTE) / interval);
        } else {
            setCurrentHour(time.get(Calendar.HOUR_OF_DAY));
            setCurrentMinute(time.get(Calendar.MINUTE) / interval);
        }
    }

}