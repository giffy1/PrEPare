package cs.umass.edu.customcalendar.view.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.androidplot.ui.SeriesRenderer;
import com.androidplot.ui.Size;
import com.androidplot.ui.SizeMetric;
import com.androidplot.ui.SizeMode;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cs.umass.edu.customcalendar.data.Adherence;
import cs.umass.edu.customcalendar.io.ApplicationPreferences;
import cs.umass.edu.customcalendar.constants.Constants;
import cs.umass.edu.customcalendar.data.Medication;
import cs.umass.edu.customcalendar.view.custom.MedicationCheckboxAdapter;
import cs.umass.edu.customcalendar.R;
import cs.umass.edu.customcalendar.util.Utils;

/**
 * This view visualizes pill adherence data using a bar graph.
 */
public class ProgressActivity extends BaseActivity {

    /** The bar graph handle. **/
    private XYPlot plot;

    /** The number of data points (months or weeks) to display. **/
    private final int numberOfDataPoints = 6;

    /** The adherence data, i.e. a mapping from dates to a mapping from medication to adherence. **/
    private Map<Calendar, Map<Medication, Adherence[]>> adherenceData;

    /** The list of medications **/
    private ArrayList<Medication> medications;

    /** A mapping from medications to boolean flags, indicating whether that medication is selected for display. **/
    private Map<Medication, Boolean> medicationCheckedMapping;

    private SimpleDateFormat monthFormat = Constants.DATE_FORMAT.MMM_YY;

    private SimpleDateFormat weekFormat = Constants.DATE_FORMAT.MONTH_DAY;

    /** Indicates whether aggregate data is displayed by month (true) or by week (false). */
    private boolean byMonth = true;

    public void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_progress);
        super.onCreate(savedInstanceState);

        plot = (XYPlot) findViewById(R.id.progress_chart);

        // Add a null series to make sure the renderer is initiated (otherwise NullPointerException is thrown)
        plot.addSeries(null, new MyBarFormatter(Color.BLACK, Color.BLACK));

        // format range and domain
        plot.setRangeBoundaries(0, BoundaryMode.FIXED, 100, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 50);
        plot.setDomainBoundaries(-1, BoundaryMode.FIXED, numberOfDataPoints, BoundaryMode.FIXED); // TODO: categorical domain
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 1);

        // hide grid lines, graph border and graph background
        plot.getGraph().getDomainOriginLinePaint().setColor(Color.TRANSPARENT);
        plot.getGraph().getDomainGridLinePaint().setColor(Color.TRANSPARENT);
        plot.getGraph().getRangeGridLinePaint().setColor(Color.TRANSPARENT);
        plot.getGraph().getBackgroundPaint().setColor(Color.TRANSPARENT);
        plot.getBackgroundPaint().setColor(Color.WHITE);
        plot.setPlotMargins(0, 0, 0, 0);

        // TODO: sizes not device independent
        plot.getLegend().setSize(new Size(new SizeMetric(200, SizeMode.ABSOLUTE), new SizeMetric(1000, SizeMode.ABSOLUTE)));

        // format domain to display date in format MM YYYY
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
            private Calendar date;
            @Override
            public StringBuffer format(Object obj, @NonNull StringBuffer toAppendTo, @NonNull FieldPosition pos) {
                int index = (int) Float.parseFloat(obj.toString());
                if (index == 0) {
                    date = Calendar.getInstance(); // reset
                    if (byMonth)
                        date.add(Calendar.MONTH, 1-numberOfDataPoints);
                    else
                        date.add(Calendar.WEEK_OF_YEAR, 1-numberOfDataPoints);
                }
                if (index >= 0 && index < numberOfDataPoints) { // -1 and numberOfDataPoints are included to ensure there is a margin
                    if (byMonth) {
                        String monthStr = monthFormat.format(date.getTime());
                        date.add(Calendar.MONTH, 1); // next date
                        return toAppendTo.append(monthStr);
                    } else {
                        date.set(Calendar.DAY_OF_WEEK, date.getFirstDayOfWeek());
                        String weekStr = weekFormat.format(date.getTime());
                        date.add(Calendar.DATE, 7); // next week
                        return toAppendTo.append(weekStr);
                    }
                } else {
                    return toAppendTo.append("");
                }
            }
            @Override
            public Object parseObject(String source, @NonNull ParsePosition pos) {
                return null;
            }
        });

        MyBarRenderer renderer = ((MyBarRenderer)plot.getRenderer(MyBarRenderer.class));
        renderer.setBarWidthStyle(BarRenderer.BarWidthStyle.FIXED_WIDTH);
        renderer.setBarWidth(150f);
        plot.getLegend().setMarginTop(75);
        plot.redraw();

        // populate data:
        ApplicationPreferences preferences = ApplicationPreferences.getInstance (this);
        medications = preferences.getMedications(this);
        adherenceData = preferences.getAdherenceData(this);
        medicationCheckedMapping = new HashMap<>();
        if (medications == null){
            Toast.makeText(this, "No medications found. Please contact your primary care provider.", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Medication medication : medications){
            medicationCheckedMapping.put(medication, true); // default
        }
        ListView lvMedications = (ListView) findViewById(R.id.lv_choose_medications);
        MedicationCheckboxAdapter listAdapter = new MedicationCheckboxAdapter(this, medications);
        lvMedications.setAdapter(listAdapter);
        listAdapter.notifyDataSetChanged();

        listAdapter.setOnCheckedChangeListener((position, view, checked) -> {
            medicationCheckedMapping.put(medications.get(position), checked);
            updatePlot();
        });

        RadioGroup radioPlotType = (RadioGroup) findViewById(R.id.radio_plot_type);
        // TODO : For some reason, using lambda expression here causes exception:
        radioPlotType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, @IdRes int id) {
                byMonth = (id==R.id.radio_by_month);
                updatePlot();
            }
        });

        updatePlot();
    }

    /**
     * Updates the bar graph.
     */
    private void updatePlot(){
        plot.clear();

        String[] labels = new String[]{"On time", "Late", "Missed"};
        int[] colors = new int[]{ContextCompat.getColor(this, R.color.color_dose_taken),
                ContextCompat.getColor(this, R.color.color_dose_late),
                ContextCompat.getColor(this, R.color.color_dose_missed)};
        int[][] adherence = new int[labels.length][numberOfDataPoints];

        Calendar date = Calendar.getInstance();
        if (byMonth)
            date.add(Calendar.MONTH, -numberOfDataPoints);
        else
            date.add(Calendar.WEEK_OF_YEAR, -numberOfDataPoints);

        int index = 0;
        for (int i = 0; i < numberOfDataPoints; i++){
            if (byMonth) {
                date.add(Calendar.MONTH, 1);
                date.set(Calendar.DATE, 1);
            } else {
                date.add(Calendar.WEEK_OF_YEAR, 1);
                date.set(Calendar.DAY_OF_WEEK, date.getFirstDayOfWeek());
            }
            int countTaken = 0;
            int countLate = 0;
            int countMissed = 0;
            int numberOfDays;
            if (byMonth)
                numberOfDays = date.getActualMaximum(Calendar.DAY_OF_MONTH);
            else
                numberOfDays = 7;
            for (int day = 0; day < numberOfDays; day++){
                Map<Medication, Adherence[]> dailyAdherence = adherenceData.get(Utils.getDateKey(date));
                if (day < numberOfDays-1)
                    date.add(Calendar.DATE, 1);
                if (dailyAdherence != null) {
                    for (Medication medication : medications) {
                        if (medicationCheckedMapping.get(medication)) {
                            Adherence[] medicationAdherence = dailyAdherence.get(medication);
                            for (Adherence aMedicationAdherence : medicationAdherence) {
                                switch (aMedicationAdherence.getAdherenceType()) {
                                    case TAKEN:
                                        countTaken++;
                                        break;
                                    case MISSED:
                                        countMissed++;
                                        break;
                                    case TAKEN_EARLY_OR_LATE:
                                        countLate++;
                                        break;
                                    case TAKEN_CLARIFY_TIME:
                                        countTaken++; // TODO : Don't know if late or on-time
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    }
                }
            }
            int total = countTaken + countMissed + countLate;
            adherence[0][index] = (int) (100 * ((float)countTaken) / total);
            adherence[1][index] = adherence[0][index] + (int) (100 * ((float)countLate) / total);
            if (total == 0)
                adherence[2][index]=0;
            else
                adherence[2][index] = 100; // always sum to 100%
            index++;
        }

        for (int i=labels.length-1; i >= 0; i--) {
            List<Number> values = new ArrayList<>();

            for (int j=0; j < adherence[i].length; j++) {
                values.add(adherence[i][j]);
            }

            XYSeries series = new SimpleXYSeries(values, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, labels[i]);
            plot.addSeries(series, new MyBarFormatter(colors[i], Color.BLACK));
        }

        plot.redraw();
    }

    private class MyBarFormatter extends BarFormatter {
        MyBarFormatter(int fillColor, int borderColor) {
            super(fillColor, borderColor);
        }

        @Override
        public Class<? extends SeriesRenderer> getRendererClass() {
            return MyBarRenderer.class;
        }

        @Override
        public SeriesRenderer getRendererInstance(XYPlot plot) {
            return new MyBarRenderer(plot);
        }
    }

    private class MyBarRenderer extends BarRenderer<MyBarFormatter> {

        MyBarRenderer(XYPlot plot) {
            super(plot);
        }

        @Override
        public MyBarFormatter getFormatter(int index, XYSeries series) {
            return getFormatter(series);
        }
    }

}
