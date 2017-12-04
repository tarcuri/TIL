package com.tarcuri.til;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialPort;

public class ISPLogger extends AppCompatActivity {
    private static UsbSerialPort sPort = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        TextView tv = (TextView) findViewById(R.id.lamba_text);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/DSEG7Modern-Regular.ttf");
        tv.setTypeface(tf);
    }

    static void launch(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, ISPLogger.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }
}
