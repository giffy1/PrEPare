package cs.umass.edu.prepare.view.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import cs.umass.edu.prepare.R;

/**
 * The base activity class wraps action bar functionality among various application UIs, including
 * the progress and reminders activities. The calendar activity does not extend the base
 * activity, because (1) it should not call finish() when switching to another activity, as that
 * would remove the main UI from the stack and (2) it contains an additional menu item, the
 * today icon, which refreshes the calendar view. The settings activity is also not a base
 * activity, as it has no action bar.
 */
public abstract class BaseActivity extends AppCompatActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_action_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_chart:
                Intent progressIntent = new Intent(this, ProgressActivity.class);
                finish();
                startActivity(progressIntent);
                return true;

            case R.id.action_reminders:
                Intent reminderIntent = new Intent(this, ReminderActivity.class);
                finish();
                startActivity(reminderIntent);
                return true;

            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }
}
