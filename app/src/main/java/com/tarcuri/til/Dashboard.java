package com.tarcuri.til;

import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class Dashboard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        TextView tv = (TextView) findViewById(R.id.afr_dashboard);
        Typeface tf = Typeface.createFromAsset(getAssets(), getString(R.string.afr_font));
        tv.setTypeface(tf);
    }
}
