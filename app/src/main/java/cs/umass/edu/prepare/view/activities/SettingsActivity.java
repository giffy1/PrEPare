package cs.umass.edu.prepare.view.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.services.DataService;

/**
 * This activity allows the user to specify various preferences, including the reminder text
 * and whether the phone should vibrate or ring when a notification is fired.
 */
public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        private EditTextPreference prefNotificationText;

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            prefNotificationText = (EditTextPreference) findPreference(getString(R.string.pref_notification_text_key));
            prefNotificationText.setSummary(prefNotificationText.getText());
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }


        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (isAdded()) { // make sure fragment is attached to activity (user is using it)
                if (key.equals(getString(R.string.pref_notification_text_key))) {
                    prefNotificationText.setSummary(prefNotificationText.getText());
                }
            }
            DataService.scheduleReminders(getActivity());
        }
    }
}