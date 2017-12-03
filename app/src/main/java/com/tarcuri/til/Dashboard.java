package com.tarcuri.til;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


public class Dashboard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        TextView tv = (TextView) findViewById(R.id.afr_dashboard);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/DSEG7Modern-Regular.ttf");
        tv.setTypeface(tf);

        TextView log = (TextView) findViewById(R.id.isplog_textview);
        log.setEnabled(false);
        log.append("starting ISPService...");

        // start the ISP service
        Intent isp_intent = new Intent(this, ISPService.class);
        startService(isp_intent);

        log.append("DONE.\n");

        for (int i = 0; i < 50; i++) {
            if (i % 3 == 0) {
                log.append("14.6\n");
            } else if (i % 7 == 0) {
                log.append("14.8\n");
                log.append("14.8\n");
            } else {
                log.append("14.7\n");
            }
        }
    }

    public void connectUSB(View view) {
        TextView tv = (TextView) findViewById(R.id.afr_dashboard);
        Typeface tf = Typeface.createFromAsset(getAssets(), getString(R.string.afr_font));
        tv.setTypeface(tf);
    }

    public void startLogging(View view) {

    }

    // Our handler for received Intents. This will be called whenever an Intent
// with an action named "custom-event-name" is broadcasted.
    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
//            Log.d("receiver", "Got message: " + message);

        }
    };
}
