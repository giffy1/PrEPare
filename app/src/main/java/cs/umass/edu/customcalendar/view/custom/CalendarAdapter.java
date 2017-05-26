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

package cs.umass.edu.customcalendar.view.custom;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;


import android.content.Context;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import cs.umass.edu.customcalendar.data.Adherence;
import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.R;
import cs.umass.edu.customcalendar.util.Utils;

public class CalendarAdapter extends BaseAdapter {
	private static final int FIRST_DAY_OF_WEEK = 0; // 0=Sunday, 1=Monday etc.
	
	private Context mContext;

    private String[] days;

    private Calendar month;
    private Calendar selectedDate;
    private Map<Calendar, Map<Medication, Adherence[]>> data = new TreeMap<>();
    private ArrayList<Medication> medications;

    private boolean styleBasic = false; // by default show details in each date cell

    public CalendarAdapter(Context c, Calendar monthCalendar, Calendar selectedDate) {
    	month = monthCalendar;
        this.selectedDate=selectedDate;
    	mContext = c;
        month.set(Calendar.DAY_OF_MONTH, 1);
        refreshDays();
    }

    public void setSelectedDate(Calendar selectedDate){
        this.selectedDate = selectedDate;
    }

    public void setMedicationInfo(ArrayList<Medication> medications){
        this.medications = medications;
    }
    
    public void setData(Map<Calendar, Map<Medication, Adherence[]>> data) {
        this.data = data;
    }

    public void setStyleBasics(boolean styleBasic){
        this.styleBasic = styleBasic;
    }

    public int getCount() {
        return days.length;
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }

    // create a new view for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
    	TextView dayView;
        if (convertView == null) {  // if it's not recycled, initialize some attributes
        	LayoutInflater vi = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.calendar_item, null);
        	
        }

        LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup insertPoint = (ViewGroup) v.findViewById(R.id.layout_calendar_item);
        insertPoint.removeAllViews();

        dayView = (TextView) vi.inflate(R.layout.textview_date, null);
        insertPoint.addView(dayView);

        // disable empty days from the beginning
        if(days[position].equals("")) {
        	dayView.setClickable(false);
        	dayView.setFocusable(false);
        }
        else {
            v.setBackgroundResource(R.drawable.list_item_background);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, month.get(Calendar.YEAR));
            cal.set(Calendar.MONTH, month.get(Calendar.MONTH));
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(days[position]));
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        	// mark current day as focused
//        	if(activity_calendar.get(Calendar.YEAR)== today.get(Calendar.YEAR) && activity_calendar.get(Calendar.MONTH)== today.get(Calendar.MONTH) && days[position].equals(""+ today.get(Calendar.DAY_OF_MONTH))) {
//                dayView.setBackgroundColor(Color.BLUE);
//                dayView.setTextColor(Color.WHITE);
//        	} else
            if(month.get(Calendar.YEAR)== selectedDate.get(Calendar.YEAR) && month.get(Calendar.MONTH)== selectedDate.get(Calendar.MONTH) && days[position].equals(""+ selectedDate.get(Calendar.DAY_OF_MONTH))) {
                int selectedColor = ContextCompat.getColor(mContext, R.color.color_calendar_item_background_selected);
                v.setBackgroundColor(selectedColor);
            } else if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY){
                dayView.setTextColor(Color.GRAY);
            }
        }
        dayView.setText(days[position]);
        
        // create date string for comparison
        String date = days[position];
    	
        if(date.length()==1) {
    		date = "0"+date;
    	}

        if (styleBasic){
            v.setMinimumHeight(0);
            return v;
        }
        v.setMinimumHeight(325); // TODO: not device independent

        if (!date.equals("") && medications != null) {
            Calendar dateKey = Utils.getDateKey(month.get(Calendar.YEAR), month.get(Calendar.MONTH), Integer.parseInt(date));
            Log.i("DATEKEY", dateKey.toString());
            if (data.containsKey(dateKey)) {
                Map<Medication, Adherence[]> adherenceMap = data.get(dateKey);
                for (Medication medication : medications) {
                    View calendarItem = vi.inflate(R.layout.view_adherence_details_simple, null);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

                    View pillAdherenceView1 = calendarItem.findViewById(R.id.pill_adherence1);
                    View pillAdherenceView2 = calendarItem.findViewById(R.id.pill_adherence2);

                    Adherence[] adherence = adherenceMap.get(medication);
                    if (adherence[0].getAdherenceType() == Adherence.AdherenceType.NONE) {
                        pillAdherenceView1.setVisibility(View.INVISIBLE);
                    } else {
                        pillAdherenceView1.setBackground(Utils.getDrawableForAdherence(mContext, adherence[0].getAdherenceType()));
                    }
                    if (adherence[1].getAdherenceType() == Adherence.AdherenceType.NONE) {
                        pillAdherenceView2.setVisibility(View.INVISIBLE);
                    } else {
                        pillAdherenceView2.setBackground(Utils.getDrawableForAdherence(mContext, adherence[1].getAdherenceType()));
                    }

                    layoutParams.setMargins(0, 5, 0, 5);
                    insertPoint.addView(calendarItem, layoutParams); //, 1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
            }
        }

        return v;
    }

    public void refreshDays()
    {
    	int lastDay = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDay = month.get(Calendar.DAY_OF_WEEK);
        
        // figure size of the array
        if(firstDay==1){
        	days = new String[lastDay];
        }
        else {
        	days = new String[lastDay+firstDay-(FIRST_DAY_OF_WEEK+1)];
        }
        
        int j;
        
        // populate empty days before first real day
        if(firstDay>1) {
	        for(j=0;j<firstDay-FIRST_DAY_OF_WEEK;j++) {
	        	days[j] = "";
	        }
        }
	    else {
	    	j= 1; // sunday => 1, monday => 7
	    }
        
        // populate days
        int dayNumber = 1;
        for(int i=j-1;i<days.length;i++) {
        	days[i] = ""+dayNumber;
        	dayNumber++;
        }
    }
}