package com.tarcuri.til;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ManageLogs extends AppCompatActivity {
    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_logs);

        mListView = (ListView) findViewById(R.id.log_listview);

        String path = getExternalFilesDir(null).toString();
        File dir = new File(path);
        File[] files = dir.listFiles();
        final List<String> fileNames = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            fileNames.add(files[i].getName().toString());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_expandable_list_item_2, fileNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TwoLineListItem row;
                if (convertView == null){
                    final LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    row = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = (TwoLineListItem) convertView;
                }

                final String log_filename = fileNames.get(position);

                // get file size, date
                final File file = new File(getExternalFilesDir(null), log_filename);
                final long length = file.length();
                final Date modified = new Date(file.lastModified());

                row.getText1().setText(log_filename);
                row.getText1().setTypeface(null, Typeface.BOLD);

                final String subtitle = String.format(Locale.US,
                        "%s\n%d bytes",
                        modified.toString(), length);

                row.getText2().setText(subtitle);

                return row;
            }

        };

        mListView.setAdapter(adapter);

        mListView.setOnItemClickListener(mLogSelectListener);
    }

    private AdapterView.OnItemClickListener mLogSelectListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String fileName = (String) parent.getItemAtPosition(position);

            Intent intent = new Intent(view.getContext(), ViewLog.class);
            intent.putExtra("logfile", fileName);
            startActivity(intent);
            Toast.makeText(view.getContext(), "selected: " + fileName, Toast.LENGTH_SHORT).show();
        }
    };

    public void listLogFiles(View view) {
        String path = getExternalFilesDir(null).toString();
        File dir = new File(path);
        File[] files = dir.listFiles();
    }
}
