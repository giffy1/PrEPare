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

package cs.umass.edu.prepare.view.custom;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;


import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import cs.umass.edu.prepare.data.Adherence;
import cs.umass.edu.prepare.data.Medication;
import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.util.Utils;

/**
 * A custom adapter intended to populate the gridview calendar with custom views. This
 * allows each date cell to contain a daily adherence overview in the form of color-coded
 * squares, giving the user a meaningful yet intuitive overview of monthly adherence.
 */
public class CalendarAdapter extends BaseAdapter {

    /** used for debugging purposes */
    @SuppressWarnings("unused")
    private static final String TAG = CalendarAdapter.class.getName();

    /**
     * Indicates the calendar type, either {@link #BASIC} for a traditional calendar
     * or {@link #DETAILED} if each date should also display adherence overview.
     */
    public enum DisplayType {
        BASIC,
        DETAILED
    }

    /** Indicates whether calendar cells should display adherence details. By default show details in each date cell. **/
    private DisplayType displayType = DisplayType.DETAILED;

    /** A constant indicating the first day of a week. */
	private static final int FIRST_DAY_OF_WEEK = 0; // 0=Sunday, 1=Monday etc.

    /** A reference to the context, used for inflating views, etc. */
	private final Context context;

    /** The date strings, including empty cells preceding the first day of the month. */
    private String[] dateStrings;

    /** The selected month. */
    private final Calendar month;

    /** The selected date. */
    private Calendar selectedDate;

    /** The list of medications. */
    private ArrayList<Medication> medications;

    /** The adherence adherenceData, mapping dates to medications to adherence objects. Because dates are easily
     * comparable, it is preferable to use a tree map. */
    private Map<Calendar, Map<Medication, Adherence[]>> adherenceData = new TreeMap<>();

    /** The text displayed in empty cells preceding the first day of the month. */
    private static final String EMPTY_CELL_TEXT = "";

    /**
     * Instantiates the calendar adapter, given a context, the selected month and the selected date.
     * @param context the context, e.g. the parent activity of the calendar view
     * @param monthCalendar the selected month, as a {@link Calendar} object. Only year and month fields are relevant.
     * @param selectedDate the selected date, as a {@link Calendar} object. Only year, month and date fields are relevant.
     */
    public CalendarAdapter(Context context, Calendar monthCalendar, Calendar selectedDate) {
    	month = monthCalendar;
        this.selectedDate=selectedDate;
    	this.context = context;
        month.set(Calendar.DAY_OF_MONTH, 1);
        refresh();
    }

    /**
     * Sets the selected date.
     * @param selectedDate a {@link Calendar} object corresponding to the selected date.
     */
    public void setSelectedDate(Calendar selectedDate){
        this.selectedDate = selectedDate;
    }

    /**
     * Sets the medications.
     * @param medications a list of medications.
     */
    public void setMedicationInfo(ArrayList<Medication> medications){
        this.medications = medications;
    }

    /**
     * Sets the adherence data.
     * @param adherenceData a mapping from date keys to medications to adherence objects.
     */
    public void setAdherenceData(Map<Calendar, Map<Medication, Adherence[]>> adherenceData) {
        this.adherenceData = adherenceData;
    }

    /**
     * Sets the form in which the calendar should be displayed.
     * @param displayType {@link DisplayType#BASIC} indicates that a traditional calendar should
     *        be displayed. {@link DisplayType#DETAILED} indicates that each cell should contain
     *        an overview of adherence details.
     */
    public void setDisplayType(DisplayType displayType){
        this.displayType = displayType;
    }

    /**
     * Returns the number of days to display. Note this includes the empty cells preceding the first day of the month.
     * @return the number of days in the month plus the number of empty cells preceding the first day.
     */
    public int getCount() {
        return dateStrings.length;
    }

    /**
     * Gets the item at the given index in the gridview. Note that this includes empty cells! So
     * don't rely on this method for obtaining date references. This is only used because it
     * must overwrite its superclass method in BaseAdapter.
     * @param position the index of the date.
     * @return the date text at the given index.
     */
    public Object getItem(int position) {
        return dateStrings[position];
    }

    /**
     * Returns 0. This method must overwrite its superclass method in BaseAdapter, but has
     * no functionality in this application.
     * @param position ignore.
     * @return 0
     */
    public long getItemId(int position) {
        return 0;
    }

    // create a new view for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
    	TextView dayView;
        if (convertView == null) {  // if it's not recycled, initialize some attributes
            v = View.inflate(context, R.layout.calendar_item, null);
        }

        ViewGroup insertPoint = (ViewGroup) v.findViewById(R.id.layout_calendar_item);
        insertPoint.removeAllViews();

        dayView = (TextView) View.inflate(context, R.layout.textview_date, null);
        insertPoint.addView(dayView);

        // disable empty days from the beginning
        if(dateStrings[position].equals("")) {
        	dayView.setClickable(false);
        	dayView.setFocusable(false);
        }
        else {
            v.setBackgroundResource(R.drawable.list_item_background);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, month.get(Calendar.YEAR));
            cal.set(Calendar.MONTH, month.get(Calendar.MONTH));
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateStrings[position]));
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            if(month.get(Calendar.YEAR)== selectedDate.get(Calendar.YEAR) && month.get(Calendar.MONTH)== selectedDate.get(Calendar.MONTH) && dateStrings[position].equals(""+ selectedDate.get(Calendar.DAY_OF_MONTH))) {
                int selectedColor = ContextCompat.getColor(context, R.color.color_calendar_item_background_selected);
                v.setBackgroundColor(selectedColor);
            } else if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY){
                dayView.setTextColor(Color.GRAY);
            }
        }
        dayView.setText(dateStrings[position]);
        
        // create date string for comparison
        String dateStr = dateStrings[position];

        if (displayType==DisplayType.BASIC){
            v.setMinimumHeight(0);
            return v; // do not populate cells
        }
        v.setMinimumHeight(325); // TODO: not device independent

        if (medications == null || adherenceData == null){
            Log.w(TAG, "Warning : No adherenceData found.");
            return v; // do not populate cells
        }

        if (dateStr.equals(""))
            return v;

        Calendar dateKey = Utils.getDateKey(month.get(Calendar.YEAR), month.get(Calendar.MONTH), Integer.parseInt(dateStr));
        if (adherenceData.containsKey(dateKey)) {
            populateCell(dateKey, insertPoint);
        }

        return v;
    }

    /**
     * Populates the calendar cell for the given date key with the adherence data for that date.
     * @param dateKey a {@link Calendar} object encoding the date. Only month, year and date fields are relevant.
     * @param insertPoint the {@link ViewGroup} parent to which to append the views.
     */
    private void populateCell(Calendar dateKey, ViewGroup insertPoint){
        Map<Medication, Adherence[]> adherenceMap = adherenceData.get(dateKey);
        for (Medication medication : medications) {
            View calendarItem = View.inflate(context, R.layout.view_adherence_details_simple, null);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            View pillAdherenceView1 = calendarItem.findViewById(R.id.pill_adherence1);
            View pillAdherenceView2 = calendarItem.findViewById(R.id.pill_adherence2);

            Adherence[] adherence = adherenceMap.get(medication);
            if (adherence[0].getAdherenceType() == Adherence.AdherenceType.NONE) {
                pillAdherenceView1.setVisibility(View.INVISIBLE);
            } else {
                pillAdherenceView1.setBackground(Utils.getDrawableForAdherence(context, adherence[0].getAdherenceType()));
            }
            if (adherence[1].getAdherenceType() == Adherence.AdherenceType.NONE) {
                pillAdherenceView2.setVisibility(View.INVISIBLE);
            } else {
                pillAdherenceView2.setBackground(Utils.getDrawableForAdherence(context, adherence[1].getAdherenceType()));
            }

            layoutParams.setMargins(0, 5, 0, 5);
            insertPoint.addView(calendarItem, layoutParams); //, 1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * Refreshes the view for each calendar date in the selected month.
     */
    public void refresh() {
        int firstDay = month.get(Calendar.DAY_OF_WEEK); // the day of the week of the first day of the selected month
    	int lastDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);

        dateStrings = new String[lastDay+firstDay-1];

        for (int j=0; j < firstDay; j++)
            dateStrings[j] = EMPTY_CELL_TEXT;

        int date = 1;
        for (int j = firstDay-1; j < dateStrings.length; j++)
            dateStrings[j] = "" + date++;
    }
}