package com.tarcuri.til;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        List<String> fileNames = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            fileNames.add(files[i].getName().toString());
        }

        // This is the array adapter, it takes the context of the activity as a
        // first parameter, the type of list view as a second parameter and your
        // array as a third parameter.
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                fileNames );

        mListView.setAdapter(arrayAdapter);

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
