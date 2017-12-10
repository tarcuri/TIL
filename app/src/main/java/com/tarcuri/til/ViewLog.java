package com.tarcuri.til;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class ViewLog extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_log);

        TableLayout tableLayout = (TableLayout) findViewById(R.id.log_tablelayout);

        Intent intent = getIntent();
        String filename = intent.getStringExtra("logfile");

        File file = new File(getExternalFilesDir(null), filename);
        try {
            BufferedReader br = new BufferedReader(new FileReader(file.getPath()));

            TableLayout.LayoutParams tlp = new TableLayout.LayoutParams();
            tableLayout.setStretchAllColumns(true);


            int i = 1;
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

                tableLayout.addView(row, i);
            }


        } catch (FileNotFoundException fnf) {
            fnf.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }



    }
}
