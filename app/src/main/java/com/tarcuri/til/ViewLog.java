package com.tarcuri.til;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class ViewLog extends AppCompatActivity {
    private String mFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_log);

        TableLayout tableLayout = (TableLayout) findViewById(R.id.log_tablelayout);

        Intent intent = getIntent();
        String filename = intent.getStringExtra("logfile");
        mFileName = filename;

        File file = new File(getExternalFilesDir(null), filename);
        try {
            BufferedReader br = new BufferedReader(new FileReader(file.getPath()));

            TableLayout.LayoutParams tlp = new TableLayout.LayoutParams();
            tableLayout.setStretchAllColumns(true);

            GraphView graph = (GraphView) findViewById(R.id.graph);
            LineGraphSeries<DataPoint> series = new LineGraphSeries<>();

            // activate horizontal zooming and scrolling
            graph.getViewport().setScalable(true);

            // activate horizontal scrolling
            graph.getViewport().setScrollable(true);

            // activate horizontal and vertical zooming and scrolling
            graph.getViewport().setScalableY(true);

            // activate vertical scrolling
            graph.getViewport().setScrollableY(true);

            int i = 0;
            for (String line; (line = br.readLine()) != null; i++) {
                TableRow row = new TableRow(this);
                TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(lp);
                String[] fields = line.split(",");
                for (int j = 0; j < fields.length; j++) {
                    TextView t = new TextView(this);
                    t.setText(fields[j]);
                    row.addView(t);
                }

                series.appendData(new DataPoint(Float.parseFloat(fields[0]), Float.parseFloat(fields[1])),
                        true, 10000);

                tableLayout.addView(row, i);
            }

            graph.addSeries(series);

            // manually set viewport
            graph.getViewport().setXAxisBoundsManual(true);
            graph.getViewport().setMinX(0.0);
            graph.getViewport().setMaxX(series.getHighestValueX());

            graph.getViewport().setYAxisBoundsManual(true);
            graph.getViewport().setMinY(5);
            graph.getViewport().setMaxY(27);

        } catch (FileNotFoundException fnf) {
            fnf.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void shareLog(View view) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        File f = new File(getExternalFilesDir(null), mFileName);

        if (f.exists()) {
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + f.getPath()));
            intent.putExtra(Intent.EXTRA_SUBJECT, "TIL LC-1 Logger Log File");
            intent.putExtra(Intent.EXTRA_TEXT, "TIL LC-1 Logger Log File attached. Format: time, afr, lambda, multiplier");
            startActivity(Intent.createChooser(intent, "Share Log"));
        }
    }
}
