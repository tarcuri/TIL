package com.tarcuri.til;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;


public class Dashboard extends AppCompatActivity {
    private final String TAG = Dashboard.class.getSimpleName();

    /**
     * Driver instance, passed in statically via

     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    Dashboard.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Dashboard.this.updateReceivedData(data);
                        }
                    });
                }
            };


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
//        Intent isp_intent = new Intent(this, ISPService.class);
//        startService(isp_intent);

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

//    // Our handler for received Intents. This will be called whenever an Intent
//// with an action named "custom-event-name" is broadcasted.
//    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            // Get extra data included in the Intent
//            String message = intent.getStringExtra("message");
////            Log.d("receiver", "Got message: " + message);
//
//        }
//    };

    private void updateReceivedData(byte[] data) {
//        final String message = "Read " + data.length + " bytes: \n"
//                + HexDump.dumpHexString(data) + "\n\n";
//        mDumpTextView.append(message);
//        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            //mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }
}
