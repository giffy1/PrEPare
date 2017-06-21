package cs.umass.edu.prepare.view.custom;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import cs.umass.edu.prepare.data.Medication;
import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.constants.Constants;

public class MedicationArrayAdapter extends BaseAdapter {

    private final ArrayList<Medication> medications;
    private final Map<Medication, Integer> dosageMapping;
    private final Map<Medication, Calendar[]> dailySchedule;
    private final Context context;
    private final SimpleDateFormat dateFormat = Constants.DATE_FORMAT.AM_PM;

    private LayoutInflater inflater=null;
    private TextView dataView, imageView;

    public MedicationArrayAdapter(Context context, final ArrayList<Medication> medications, final Map<Medication, Integer> dosageMapping,
                                  final Map<Medication, Calendar[]> dailySchedule) {
        this.medications = medications;
        this.dosageMapping = dosageMapping;
        this.dailySchedule = dailySchedule;
        this.context = context;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        rowView = new View[medications.size()];
    }
    @Override
    public int getCount() {
        return medications.size();
    }

    @Override
    public Object getItem(int position) {
        return medications.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private class Holder
    {
        TextView txtSchedule, txtMedication, txtDosage;
    }

    private final View[] rowView;

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder holder=new Holder();

        if (rowView[position] == null) {
            rowView[position] = inflater.inflate(R.layout.settings_list_item_medication, parent, false);
        }

        holder.txtSchedule = (TextView) rowView[position].findViewById(R.id.txtSchedule);
        holder.txtDosage = (TextView) rowView[position].findViewById(R.id.txtDosage);
        holder.txtMedication = (TextView) rowView[position].findViewById(R.id.txtMedication);

        if (imageView == null && position == 0)
            imageView = holder.txtDosage;

        if (dataView == null && position == 0)
            dataView = holder.txtMedication;

        holder.txtMedication.setText(medications.get(position).getName());
        BitmapDrawable medicationDrawable = new BitmapDrawable(context.getResources(), medications.get(position).getImage());
        holder.txtDosage.setCompoundDrawablesWithIntrinsicBounds(null, medicationDrawable, null, null);
        holder.txtDosage.setText(String.format(Locale.getDefault(), "%d mg", dosageMapping.get(medications.get(position))));
        String output = "";
        if (dailySchedule.containsKey(medications.get(position))) {
            for (Calendar time : dailySchedule.get(medications.get(position))) {
                if (time != null) {
                    output += dateFormat.format(time.getTime()) + "\n";
                }
            }
        }
        holder.txtSchedule.setText(output);
        return rowView[position];
    }

}