package cs.umass.edu.customcalendar.view.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import cs.umass.edu.customcalendar.R;
import info.hoang8f.android.segmented.SegmentedGroup;

/**
 * @author Sean Noran
 */
public class SwitchPreference extends Preference {

    /**
     * the index of the default preference value in the case that it is not defined in the XML attributes.
     */
    private static final int DEFAULT_INDEX = 0;

    /** The index of the default value. **/
    private int defaultIndex;

    /** The list of options in the segmented radio group. */
    private CharSequence[] options;

    /**
     * Listener for handling selection events.
     */
    private SegmentedGroup.OnCheckedChangeListener onCheckedChangeListener;

    public SwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SwitchPreference, defStyle, 0);
        try {
            options = a.getTextArray(R.styleable.SwitchPreference_android_entries);
        }
        finally {
            a.recycle();
        }
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SwitchPreference, 0, 0);
        try {
            options = a.getTextArray(R.styleable.SwitchPreference_android_entries);
        }
        finally {
            a.recycle();
        }
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        setWidgetLayoutResource(R.layout.switch_multiway);
        return super.onCreateView(parent);
    }

    @Override
    protected void onBindView(View rootView) {
        super.onBindView(rootView);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        SegmentedGroup toggleSwitch = (SegmentedGroup) rootView.findViewById(R.id.segmented_radio_group);

        final int[] radioButtonIDs = new int[options.length];

        for (int i = toggleSwitch.getChildCount() - 1; i >= 0; i--){
            RadioButton button = (RadioButton) toggleSwitch.getChildAt(i);
            CharSequence option = options[i];
            button.setText(option);
            radioButtonIDs[i] = button.getId();
        }
        int selectedIndex = preferences.getInt(getKey(), defaultIndex);
        if (selectedIndex < radioButtonIDs.length)
            toggleSwitch.check(radioButtonIDs[selectedIndex]);

        toggleSwitch.setOnCheckedChangeListener((radioGroup, selectedID) -> {
            int index = 0;
            for (int ID : radioButtonIDs) {
                if (ID == selectedID) {
                    preferences.edit().putInt(getKey(), index).apply();
                }
                index++;
            }
            //forward to callback:
            if (onCheckedChangeListener != null)
                onCheckedChangeListener.onCheckedChanged(radioGroup, selectedID);
        });
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        defaultIndex = a.getInt(index, DEFAULT_INDEX);
        return defaultIndex;
    }

    /**
     * Sets the listener which handles the event that the user changed the preference.
     * @param onCheckedChangeListener the listener object
     */
    public void setOnCheckedChangeListener(SegmentedGroup.OnCheckedChangeListener onCheckedChangeListener){
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

}