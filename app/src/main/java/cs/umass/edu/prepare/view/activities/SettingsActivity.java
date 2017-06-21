package cs.umass.edu.prepare.view.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.main.CheckForUpdatesTask;
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

            initializeCheckForUpdatesPreference();
            initializeAboutPreference();
        }

        private void initializeCheckForUpdatesPreference(){
            findPreference(getString(R.string.pref_check_for_updates_key)).setOnPreferenceClickListener(preference -> {
                new CheckForUpdatesTask(getActivity()).execute();
                return true;
            });
        }

        private void initializeAboutPreference(){
            findPreference(getString(R.string.pref_about_key)).setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), AboutActivity.class);
                startActivity(intent);
                return true;
            });
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
