package com.tarcuri.til;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.hoho.android.usbserial.driver.UsbSerialPort;

public class ISPLogger extends AppCompatActivity {
    private static UsbSerialPort sPort = null;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        ToggleButton connect_toggle = (ToggleButton) findViewById(R.id.connect_button);
        connect_toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // connect
                    connect();
                } else {
                    // disconnect
                    disconnect();
                }
            }
        });

        ToggleButton log_toggle = (ToggleButton) findViewById(R.id.log_button);
        log_toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // connect
                    startLog();
                } else {
                    // disconnect
                    stopLog();
                }
            }
        });

        TextView tv = (TextView) findViewById(R.id.lamba_text);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/DSEG7Modern-Regular.ttf");
        tv.setTypeface(tf);
    }

    public void connect() {
        Toast.makeText(this, "connecting", Toast.LENGTH_SHORT).show();
    }

    public void disconnect() {
        Toast.makeText(this, "disconnecting", Toast.LENGTH_SHORT).show();
    }

    public void startLog() {
        Toast.makeText(this, "startLog", Toast.LENGTH_SHORT).show();
    }

    public void stopLog() {
        Toast.makeText(this, "stopLog()", Toast.LENGTH_SHORT).show();
    }

    static void launch(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, ISPLogger.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }
}
