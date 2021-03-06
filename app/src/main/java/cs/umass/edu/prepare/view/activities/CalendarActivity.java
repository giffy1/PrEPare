/*
* Copyright 2011 Lauri Nevala.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package cs.umass.edu.prepare.view.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;


import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import cs.umass.edu.prepare.data.Adherence;
import cs.umass.edu.prepare.data.DataIO;
import cs.umass.edu.prepare.main.CheckForUpdatesTask;
import cs.umass.edu.prepare.services.DataService;
import cs.umass.edu.prepare.services.WearableService;
import cs.umass.edu.prepare.view.custom.CalendarAdapter;
import cs.umass.edu.prepare.constants.Constants;
import cs.umass.edu.prepare.view.gestures.CustomMotionEventListener;
import cs.umass.edu.prepare.data.Medication;
import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.util.Utils;

public class CalendarActivity extends AppCompatActivity {

	/** used for debugging purposes */
	@SuppressWarnings("unused")
	private static final String TAG = CalendarActivity.class.getName();

	/** Today's date. The final attribute assumes that the application is re-opened each day.
	 *  TODO: It may be good to add a mechanism to update today each day. **/
	private final Calendar today;

	/** The activity_calendar's date, indicating the month and year being viewed. Day is irrelevant. **/
	private final Calendar month;

	/** The date the user selected, initially the current date. **/
	private final Calendar selectedDate;

	/** The adapter for displaying the custom activity_calendar. **/
	private CalendarAdapter adapter;

	/** The handler is used to update the activity_calendar view asynchronously. **/
	private Handler handler;



	/** The list of medications. **/
	private ArrayList<Medication> medications;

	/** The entire adherence data. Date is stored in a tree map because dates are naturally ordered. **/
	private Map<Calendar, Map<Medication, Adherence[]>> adherenceData;

	/** Maps a medication to a dosage **/
	private Map<Medication, Integer> dosageMapping; // in mg

	/** Maps a medication to a schedule (a list of times to take the medication). **/
	private Map<Medication, Calendar[]> dailySchedule;

	/** Maps a medication to a unique Mac Address. **/
	private Map<String, Medication> addressMapping;

	/** Indicates whether the schedule details should be displayed, i.e. whether {@link #detailsView} is visible. **/
	private boolean displayDetailsView = false;

	/** Displays more detailed information for the {@link #selectedDate selected date}. Only visible if {@link #displayDetailsView}=true.**/
	private View detailsView;

	/** Used for formatting time of day. **/
	private SimpleDateFormat timeFormat = Constants.DATE_FORMAT.AM_PM;

	/** Used for formatting dates in mm/dd format. **/
	private final SimpleDateFormat dayFormat = Constants.DATE_FORMAT.MONTH_DAY;

	private DataIO preferences;

	private CustomMotionEventListener.OnSwipeListener onDetailsSwiped = new CustomMotionEventListener.OnSwipeListener() {
		@Override
		public void onSwipe(CustomMotionEventListener.OnSwipeListener.Direction direction) {
			if (direction == CustomMotionEventListener.OnSwipeListener.Direction.LEFT) {
				selectedDate.set(Calendar.DATE, selectedDate.get(Calendar.DATE)+1);
			} else {
				selectedDate.set(Calendar.DATE, selectedDate.get(Calendar.DATE)-1);
			}
			refresh();
			transition();
		}
	};

	private final CustomMotionEventListener motionEventListener = new CustomMotionEventListener();

	public CalendarActivity(){
		today = Calendar.getInstance();
		month = Calendar.getInstance();
		selectedDate = Calendar.getInstance();
	}

	/**
	 * Loads the medication, adherence, dosage and schedule data from disk.
	 */
	private void loadData(){
		medications = preferences.getMedications(this);
		dosageMapping = preferences.getDosageMapping(this);
		dailySchedule = preferences.getSchedule(this);
		adherenceData = preferences.getAdherenceData(this);
		addressMapping = preferences.getAddressMapping(this);
	}

	@Override
	protected void onStart() {
		super.onStart();

		LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
		//the intent filter specifies the messages we are interested in receiving
		IntentFilter filter = new IntentFilter();
		filter.addAction(Constants.ACTION.BROADCAST_MESSAGE);
		broadcastManager.registerReceiver(receiver, filter);
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		int timeFormatOption = preferences.getInt(getString(R.string.pref_time_type_key), getResources().getInteger(R.integer.pref_time_type_index_default));
		if (timeFormatOption == 0) {
			timeFormat = Constants.DATE_FORMAT.AM_PM;
		} else {
			timeFormat = Constants.DATE_FORMAT._24_HR;
		}
	}

	@Override
	protected void onStop() {
		LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
		try {
			broadcastManager.unregisterReceiver(receiver);
		}catch (IllegalArgumentException e){
			e.printStackTrace();
		}
		super.onStop();
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_calendar);

		// on first run, create dummy data: // TODO : get rid of this when deploying
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
		boolean firstRun = p.getBoolean("PREFERENCE_FIRST_RUN", true);
		if (firstRun) {
			Log.i("PrEPare", "Initial run... Creating dummy data...");
			Utils.setDummyAdherenceData(this);
			p.edit().putBoolean("PREFERENCE_FIRST_RUN", false).apply();
		}
		if (preferences == null){
			preferences = DataIO.getInstance(this);
			preferences.addOnDataChangedListener(() -> CalendarActivity.this.runOnUiThread(CalendarActivity.this::refresh));
		}
		loadData();

		Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
		setSupportActionBar(myToolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowTitleEnabled(false);
			actionBar.setDisplayShowCustomEnabled(true);
		}

		final View v = View.inflate(this, R.layout.menu_item_today, null);

		TextView txtDate = (TextView) v.findViewById(R.id.txt_today);
		txtDate.setText(String.valueOf(today.get(Calendar.DATE)));

		v.setOnClickListener(view -> {
            finish();
            startActivity(getIntent()); // refresh activity
        });

		getSupportActionBar().setCustomView(v);

		adapter = new CalendarAdapter(this, month, selectedDate);
		GridView gridview = (GridView) findViewById(R.id.gridview);
		gridview.setAdapter(adapter);

		handler = new Handler();
		handler.post(calendarUpdater);

		GridView headers = (GridView) findViewById(R.id.gvHeaders);

		ArrayAdapter<String> headersAdapter = new ArrayAdapter<>(this,
				android.R.layout.simple_list_item_1, new String[]{"S", "M", "T", "W", "T", "F", "S"});
		headers.setAdapter(headersAdapter);

		TextView title  = (TextView) findViewById(R.id.title);
		title.setText(android.text.format.DateFormat.format("MMMM yyyy", month));
		final TextView previous  = (TextView) findViewById(R.id.previous);
		previous.setOnClickListener(v13 -> previousMonth());

		TextView next  = (TextView) findViewById(R.id.next);
		next.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v1) {
				CalendarActivity.this.nextMonth();
			}
		});

		detailsView = findViewById(R.id.details);

		gridview.setOnItemClickListener((parent, v12, position, id) -> {
            TextView date = (TextView) v12.findViewById(R.id.date);
            if(date != null && !date.getText().equals("")) {
                if (date.getText().toString().equals(String.valueOf(selectedDate.get(Calendar.DATE)))){
                    displayDetailsView = !displayDetailsView;
                } else {
                    displayDetailsView = true;
                }
                selectedDate.set(Calendar.MONTH, month.get(Calendar.MONTH));
                selectedDate.set(Calendar.YEAR, month.get(Calendar.YEAR));
                selectedDate.set(Calendar.DATE, Integer.valueOf(date.getText().toString()));
                refresh();
            }
        });

		motionEventListener.setOnSwipeListener(gridview, direction -> {
            if (direction== CustomMotionEventListener.OnSwipeListener.Direction.LEFT){
                nextMonth();
            } else if (direction == CustomMotionEventListener.OnSwipeListener.Direction.RIGHT) {
                previousMonth();
            }
        });

//		motionEventListener.setOnSwipeListener(detailsView, onDetailsSwiped);
//		motionEventListener.setOnSwipeListener(this, onDetailsSwiped);

		if (addressMapping == null){ // TODO
			Intent selectDeviceIntent = new Intent(this, SelectDeviceActivity.class);
			startActivity(selectDeviceIntent);
		}

		new CheckForUpdatesTask(this).execute(true);

		Intent wearableServiceIntent = new Intent(this, WearableService.class);
		wearableServiceIntent.setAction(Constants.ACTION.START_SERVICE);
		startService(wearableServiceIntent);

		Intent dataServiceIntent = new Intent(this, DataService.class);
		dataServiceIntent.setAction(Constants.ACTION.START_SERVICE);
		startService(dataServiceIntent);
	}

	@Override
	protected void onResume() {
		refresh();
		super.onResume();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		motionEventListener.onTouch(this, event);
		return super.onTouchEvent(event);
	}

	/**
	 * Changes the activity_calendar to display the next month.
	 */
	private void nextMonth(){
		int month = this.month.get(Calendar.MONTH);
		int year = this.month.get(Calendar.YEAR);
		if(month == Calendar.DECEMBER) {
			this.month.set(year+1,Calendar.JANUARY,1);
		} else {
			this.month.set(Calendar.MONTH, month+1);
		}
		refreshCalendar();
	}

	/**
	 * Changes the activity_calendar to show the previous month.
	 */
	private void previousMonth(){
		int month = this.month.get(Calendar.MONTH);
		int year = this.month.get(Calendar.YEAR);
		if(month == Calendar.JANUARY) {
			this.month.set(year-1,Calendar.DECEMBER,1);
		} else {
			this.month.set(Calendar.MONTH, month-1);
		}
		refreshCalendar();
	}

	/**
	 * Refreshes the view, including the adherence view and the calendar.
	 */
	private void refresh(){
		// if the details view below the calendar is visible, then the calendar should NOT show its adherence details in each cell
		if (displayDetailsView) {
			adapter.setDisplayType(CalendarAdapter.DisplayType.BASIC);
		} else {
			adapter.setDisplayType(CalendarAdapter.DisplayType.DETAILED);
		}
		adapter.setSelectedDate(selectedDate);
		refreshCalendar();
		if (displayDetailsView)
			updateDetails(Utils.getDateKey(selectedDate));
		TextView txtDate = (TextView) findViewById(R.id.txtDate);
		if (displayDetailsView){
			txtDate.setVisibility(View.VISIBLE);
			detailsView.setVisibility(View.VISIBLE);
		} else {
			txtDate.setVisibility(View.INVISIBLE);
			detailsView.setVisibility(View.INVISIBLE);
		}
	}

	private void transition(){
		int transitionColor = ContextCompat.getColor(this, R.color.color_transition_details_swipe);
		ValueAnimator colorAnim = ObjectAnimator.ofInt(detailsView, "backgroundColor", transitionColor, Color.TRANSPARENT);
		colorAnim.setDuration(250);
		colorAnim.setEvaluator(new ArgbEvaluator());
		colorAnim.setRepeatCount(0);
		colorAnim.setRepeatMode(ValueAnimator.REVERSE);
		colorAnim.start();
	}

	@Override
	public void onBackPressed() {
		// if the user presses the back button on the phone and the details view is visible, hide the details view.
		if (displayDetailsView){
			displayDetailsView = false;
			refresh();
		} else {
			super.onBackPressed();
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
				startActivity(progressIntent);
				return true;

			case R.id.action_reminders:
				Intent reminderIntent = new Intent(this, ReminderActivity.class);
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

	/**
	 * Allows the user to edit adherence.
	 * TODO: This is only available for easy debugging and data manipulation.
	 * @param medication the medication for which the adherence is being edited.
	 * @param dateKey the date for which the adherence is being edited.
	 * @param index the index into the adherence array, that is either AM or PM.
	 */
	private void editAdherence(final Medication medication, final Calendar dateKey, final int index){
		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle("Select Adherence:");
		final String[] adherenceChoices = new String[Adherence.AdherenceType.values().length];
		for (int i = 0; i < adherenceChoices.length; i++){
			adherenceChoices[i] = Adherence.AdherenceType.values()[i].toString();
		}
		b.setItems(adherenceChoices, (dialog, which) -> {
            dialog.dismiss();
            Map<Medication, Adherence[]> selectedAdherence = adherenceData.get(dateKey);
            Adherence[] dailyAdherence = selectedAdherence.get(medication);
            dailyAdherence[index].setAdherenceType(Adherence.AdherenceType.values()[which]);
            if (Adherence.AdherenceType.values()[which] == Adherence.AdherenceType.TAKEN){
                dailyAdherence[index].setTimeTaken(dailySchedule.get(medication)[index]);
            } else if (Adherence.AdherenceType.values()[which] == Adherence.AdherenceType.TAKEN_EARLY_OR_LATE){
                Calendar timeToTake = (Calendar) dailySchedule.get(medication)[index].clone();
                // random time if early or late:
                int sign = (Math.random()>0.5 ? 1 : -1);
                timeToTake.add(Calendar.HOUR, (int)(sign * (1 + 3*Math.random())));
                timeToTake.add(Calendar.MINUTE, (int)(sign * (15 + 45*Math.random())));
                dailyAdherence[index].setTimeTaken(timeToTake);
            }
            refresh();
            DataIO preferences = DataIO.getInstance(CalendarActivity.this);
            preferences.setAdherenceData(this, adherenceData);
        });
		b.show();
	}

	/**
	 * Allows the user to set time for adherence data where the time is unknown.
	 * @param medication the medication for which the adherence is being modified.
	 * @param index the index of the adherence being modified, i.e. AM or PM.
	 */
	private void setTimeTaken(final Medication medication, final Calendar dateKey, final int index){
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.dialog_set_time);

		final TimePicker timePicker = (TimePicker) dialog.findViewById(R.id.time_picker);

		Button cancelButton = (Button) dialog.findViewById(R.id.btn_time_cancel);
		cancelButton.setOnClickListener(v -> dialog.dismiss());

		Button saveButton = (Button) dialog.findViewById(R.id.btn_time_save);
		saveButton.setOnClickListener(v -> {
            Calendar time = Utils.getTimeNoInterval(timePicker); // TODO: Store times taken
            Map<Medication, Adherence[]> dailyAdherence = adherenceData.get(dateKey);
            Adherence[] adherence = dailyAdherence.get(medication);
            adherence[index].setTimeTaken(time);

            Calendar[] schedule = dailySchedule.get(medication);
            Calendar timeToTake = (Calendar) time.clone();
            timeToTake.set(Calendar.HOUR_OF_DAY, schedule[index].get(Calendar.HOUR_OF_DAY));
            timeToTake.set(Calendar.MINUTE, schedule[index].get(Calendar.MINUTE));
            Calendar upperBound = (Calendar) timeToTake.clone();
            upperBound.add(Calendar.HOUR_OF_DAY, 1);
            Calendar lowerBound = (Calendar) timeToTake.clone();
            lowerBound.add(Calendar.HOUR_OF_DAY, -1);

            if (time.after(upperBound) || time.before(lowerBound)) {
                adherence[index].setAdherenceType(Adherence.AdherenceType.TAKEN_EARLY_OR_LATE);
            } else {
                adherence[index].setAdherenceType(Adherence.AdherenceType.TAKEN);
            }

            dialog.dismiss();
            refresh();

            DataIO preferences = DataIO.getInstance(CalendarActivity.this);
            preferences.setAdherenceData(this, adherenceData);
        });

		dialog.show();
	}

	/**
	 * Populates the details view with adherence data for a particular date.
	 * @param dateKey corresponds to the selected date. Note that the date key must have zeroed
	 *                out all fields excluding month, year and date.
	 * @param insertPoint the parent to which the adherence views are to be added.
	 */
	private void insertDetailsForDate (final Calendar dateKey, ViewGroup insertPoint) {
		final Map<Medication, Adherence[]> adherenceMap = adherenceData.get(dateKey);
		for (final Medication medication : medications) {
			View details = View.inflate(this, R.layout.view_adherence_details_full, null);
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

			View[] adherenceViews = new View[2];
			int[] viewIDs = new int[]{R.id.adherence_details1, R.id.adherence_details2};
			int[] timeTakenIDs = new int[]{R.id.txtTimeTaken1, R.id.txtTimeTaken2};
			int[] medicationImgIDs = new int[]{R.id.imgMedication1, R.id.imgMedication2};
			final Adherence[] adherence = adherenceMap.get(medication);
			int dosage = dosageMapping.get(medication);
			Calendar[] schedule = dailySchedule.get(medication);

			for (int i = 0; i < viewIDs.length; i++){
				final int index = i;
				adherenceViews[index] = details.findViewById(viewIDs[index]);
				// TODO : Swiping on details doesn't work as expected (only works for adherence view):
//				motionEventListener.setOnSwipeListener(adherenceViews[index], onDetailsSwiped);
				adherenceViews[index].setOnLongClickListener(view -> {
                    editAdherence(medication, dateKey, index);
                    return false;
                });
				adherenceViews[index].setOnClickListener(view -> {
                    if (adherence[index].getAdherenceType() == Adherence.AdherenceType.TAKEN_CLARIFY_TIME) {
                        setTimeTaken(medication, dateKey, index);
                    }
                });
				adherenceViews[index].setBackground(Utils.getDrawableForAdherence(this, adherence[index].getAdherenceType()));
				TextView timeTaken = (TextView) details.findViewById(timeTakenIDs[index]);
				TextView imgMedication = (TextView) details.findViewById(medicationImgIDs[index]);
				BitmapDrawable medicationDrawable = new BitmapDrawable(getResources(), medication.getImage());
				imgMedication.setCompoundDrawablesWithIntrinsicBounds(null, medicationDrawable, null, null);
				imgMedication.setText(String.format(Locale.getDefault(), "%d mg", dosage));
				adherenceViews[index].setVisibility(View.VISIBLE);
				switch (adherence[index].getAdherenceType()) {
					case NONE:
						adherenceViews[index].setVisibility(View.INVISIBLE);
						break;
					case MISSED:
						timeTaken.setText(R.string.adherence_text_missed);
						break;
					case TAKEN_CLARIFY_TIME:
						timeTaken.setText(R.string.adherence_text_unknown);
						break;
					case FUTURE:
						timeTaken.setText(timeFormat.format(schedule[index].getTime()));
						break;
					default: // taken on-time or taken late/early
						Calendar time = adherence[index].getTimeTaken();
						if (time != null)
							timeTaken.setText(timeFormat.format(time.getTime()));
						break;
				}
			}
			// set margins and add to parent
			layoutParams.setMargins(0, 15, 0, 15);
			insertPoint.addView(details, layoutParams);
		}
	}

	/**
	 * Updates the details view for a particular date.
	 * @param dateKey corresponds to the selected date. Note that the date key must have zeroed
	 *                out all fields excluding month, year and date.
	 */
	private void updateDetails(Calendar dateKey){
		TextView txtDate = (TextView) findViewById(R.id.txtDate);
		txtDate.setText(dayFormat.format(selectedDate.getTime()) + "\n" + selectedDate.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()));

		ViewGroup insertPoint = (ViewGroup) findViewById(R.id.details);
		insertPoint.removeAllViews();

		if (adherenceData == null) {
			Log.w(TAG, "Warning : No adherence data found.");
		} else {
			if (adherenceData.containsKey(dateKey))
				insertDetailsForDate(dateKey, insertPoint);
		}
	}

	/**
	 * Refreshes the view asynchronously and displays the selected month and year.
	 */
	private void refreshCalendar() {
		TextView title  = (TextView) findViewById(R.id.title);

		adapter.refresh();
		adapter.notifyDataSetChanged();
		handler.post(calendarUpdater);

		title.setText(Constants.DATE_FORMAT.MMM_YYYY.format(month.getTime()));
	}

	/**
	 * Asynchronously updates the data displayed in the activity_calendar view.
	 */
	private final Runnable calendarUpdater = new Runnable() {
		@Override
		public void run() {
			adapter.setAdherenceData(adherenceData);
			adapter.setMedicationInfo(medications);
			adapter.notifyDataSetChanged();
		}
	};

	/**
	 * Shows a removable status message at the bottom of the application.
	 * @param message the status message shown
	 */
	public void showStatus(final String message){
		runOnUiThread(() -> Toast.makeText(CalendarActivity.this, message, Toast.LENGTH_LONG).show());
	}

	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction() != null) {
				if (intent.getAction().equals(Constants.ACTION.BROADCAST_MESSAGE)){
					int message = intent.getIntExtra(Constants.KEY.MESSAGE, -1);
					switch (message){
						case Constants.MESSAGES.METAWEAR_CONNECTING:
//							showStatus(getString(R.string.status_connection_attempt));
							break;
						case Constants.MESSAGES.METAWEAR_SERVICE_STOPPED:
//							showStatus(getString(R.string.status_service_stopped));
							break;
						case Constants.MESSAGES.METAWEAR_CONNECTED:
//							showStatus(getString(R.string.status_connected));
							break;
						case Constants.MESSAGES.METAWEAR_DISCONNECTED:
							break;
						case Constants.MESSAGES.WEARABLE_SERVICE_STARTED:
							break;
						case Constants.MESSAGES.WEARABLE_SERVICE_STOPPED:
							break;
						case Constants.MESSAGES.WEARABLE_CONNECTION_FAILED:
							break;
						case Constants.MESSAGES.WEARABLE_DISCONNECTED:
							break;
						case Constants.MESSAGES.NO_MOTION_DETECTED:
//							showStatus(getString(R.string.status_no_motion));
							break;
						case Constants.MESSAGES.INVALID_ADDRESS:
//							showStatus(getString(R.string.status_invalid_address));
//							startActivityForResult(new Intent(MainActivity.this, SelectDeviceActivity.class), REQUEST_CODE.SELECT_DEVICE);
							break;
						case Constants.MESSAGES.BLUETOOTH_UNSUPPORTED:
//							showStatus(getString(R.string.status_bluetooth_unsupported));
							break;
						case Constants.MESSAGES.BLUETOOTH_DISABLED:
							// showStatus(getString(R.string.status_bluetooth_disabled));
							Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
							startActivityForResult(enableBtIntent, Constants.REQUEST_CODE.ENABLE_BLUETOOTH);
							break;
						case Constants.MESSAGES.SERVER_CONNECTION_FAILED:
							break;
						case Constants.MESSAGES.SERVER_CONNECTION_SUCCEEDED:
							break;
						case Constants.MESSAGES.SERVER_DISCONNECTED:
							break;
						case Constants.MESSAGES.PILL_INTAKE_GESTURE_DETECTED:
//							Medication medication = (Medication) intent.getSerializableExtra(Constants.KEY.MEDICATION);
//							Calendar timeTaken = (Calendar) intent.getSerializableExtra(Constants.KEY.TIME_TAKEN);
//							// TODO
							break;
					}
				}
			}
		}
	};



}
