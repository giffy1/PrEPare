package cs.umass.edu.customcalendar.view.custom;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.R;

public class MedicationCheckboxAdapter extends BaseAdapter {

    public interface OnCheckedChangeListener {
        void onCheckedChange(int position, View view, boolean checked);
    }

    private final ArrayList<Medication> medications;
    private OnCheckedChangeListener onCheckedChangeListener;
    private final Context context;

    private LayoutInflater inflater=null;

    public MedicationCheckboxAdapter(Context context, final ArrayList<Medication> medications) {
        this.medications = medications;
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

    public void setOnCheckedChangeListener(OnCheckedChangeListener onCheckedChangeListener){
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

    public class Holder
    {
        CheckBox chkMedication;
        ImageView imgMedication;
        TextView txtMedication;
    }

    private View[] rowView;

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder holder=new Holder();

        if (rowView[position] == null) {
            rowView[position] = inflater.inflate(R.layout.progress_list_item_medication, parent, false);
        }

        holder.chkMedication = (CheckBox) rowView[position].findViewById(R.id.chkMedication);
        holder.imgMedication = (ImageView) rowView[position].findViewById(R.id.imgMed);
        holder.txtMedication = (TextView) rowView[position].findViewById(R.id.txtMed);

        holder.txtMedication.setText(medications.get(position).getName());
        holder.chkMedication.setChecked(true);
        holder.chkMedication.setOnCheckedChangeListener((compoundButton, b) -> {
            if (onCheckedChangeListener != null){
                onCheckedChangeListener.onCheckedChange(position, rowView[position], b);
            }
        });
        BitmapDrawable medicationDrawable = new BitmapDrawable(context.getResources(), medications.get(position).getImage());
        holder.imgMedication.setImageDrawable(medicationDrawable); //TODO
        return rowView[position];
    }

}