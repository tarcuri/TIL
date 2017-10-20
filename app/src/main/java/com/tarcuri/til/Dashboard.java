package com.tarcuri.til;

import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;

public class Dashboard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        TextView tv = (TextView) findViewById(R.id.afr_dashboard);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/DSEG7Modern-Regular.ttf");
        tv.setTypeface(tf);
    }

    public void connectUSB(View view) {
        TextView tv = (TextView) findViewById(R.id.afr_dashboard);
        Typeface tf = Typeface.createFromAsset(getAssets(), getString(R.string.afr_font));
        tv.setTypeface(tf);
    }
}
