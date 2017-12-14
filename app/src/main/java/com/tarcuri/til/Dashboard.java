package com.tarcuri.til;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Dashboard extends AppCompatActivity {
    private final String TAG = Dashboard.class.getSimpleName();

    public static final String TIL_START_LOGGING = "com.arcuri.til.TIL_START_LOGGING";
    public static final String TIL_STOP_LOGGING = "com.arcuri.til.TIL_STOP_LOGGING";

    private TextView mLogView;

    private static UsbSerialPort sPort = null;

    // need to bind to ISP service
    private boolean mBound;
    private ISPService mISPService;
    private TextView mAFRView;

    private class IspUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ISPService.ISP_SERVICE_CONNECTED)) {
                Log.i(TAG, "ISP_SERVICE_CONNECTED");
            } else if (intent.getAction().equals(ISPService.ISP_DATA_RECEIVED)) {
                Log.i(TAG, "ISP_DATA_RECEIVED");
                byte[] chunk = mISPService.getChunk();
                updateReceivedData(chunk);
            } else if (intent.getAction().equals(ISPService.ISP_LC1_RECEIVED)) {
                LC1Packet packet = mISPService.getPacket();
                if (packet != null) {
                    float afr = packet.getAFR();
                    short L = packet.getLambdaWord();
                    byte m = packet.getMultiplier();
                    String byte_str = HexDump.dumpHexString(packet.getPacketBytes());
                    String afr_str = String.valueOf(afr);

                    // show some debug
                    mLogView.setText("ISP_LC1_RECEIVED: AFR = (" + L + " + 500) * "
                            + m + " / 10000 = " + afr_str + "\n");
                    Log.i(TAG, "ISP_LC1_RECEIVED: AFR = (" + L + " + 500) * "
                            + m + " / 10000 = " + afr_str);
                    Log.d(TAG, byte_str);

                    // update the gauge display
                    mAFRView.setText(String.format("%.2f", afr));

                    // update the graph
                    String elapsed = intent.getStringExtra("time");
                    DataPoint lc1_data = new DataPoint(Float.parseFloat(elapsed), afr);
                    mSeries.appendData(lc1_data, true, 10000);
                } else {
                    Log.d(TAG, "ISP_LC1_RECEIVED: null packet!\n");
                }
            }
        }
    }

    private IspUpdateReceiver mIspUpdateReceiver = new IspUpdateReceiver();

    private LineGraphSeries<DataPoint> mSeries;
    private GraphView mGraph;

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ISPService.ISP_SERVICE_CONNECTED);
        filter.addAction(ISPService.ISP_DATA_RECEIVED);
        filter.addAction(ISPService.ISP_LC1_RECEIVED);
        registerReceiver(mIspUpdateReceiver, filter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        setFilter();

        mLogView = (TextView) findViewById(R.id.status_textview);

        // start/stop logging
        ToggleButton log_toggle = (ToggleButton) findViewById(R.id.log_button);
        log_toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startLog();
                } else {
                    stopLog();
                }
            }
        });

        TextView tv = (TextView) findViewById(R.id.lamba_text);
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/DSEG7Modern-Regular.ttf");
        tv.setTypeface(tf);

        mAFRView = tv;

        mGraph = (GraphView) findViewById(R.id.graph);
        mSeries = new LineGraphSeries<>();

        // activate horizontal zooming and scrolling
        mGraph.getViewport().setScalable(true);

        // activate horizontal scrolling
        mGraph.getViewport().setScrollable(true);

        // activate horizontal and vertical zooming and scrolling
        mGraph.getViewport().setScalableY(true);

        // activate vertical scrolling
        mGraph.getViewport().setScrollableY(true);

        // add empty series
        mGraph.addSeries(mSeries);

        // manually set viewport
        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setMinX(0.0);
        mGraph.getViewport().setMaxX(10000);

        mGraph.getViewport().setYAxisBoundsManual(true);
        mGraph.getViewport().setMinY(5);
        mGraph.getViewport().setMaxY(25);
    }

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mIspUpdateReceiver != null) {
            unregisterReceiver(mIspUpdateReceiver);
        }

        // stop ISP service
        Intent intent = new Intent(this, ISPService.class);
        stopService(intent);

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mLogView.setText("No serial device\n");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                mLogView.setText("Opening device failed\n");
                return;
            }

            try {
                mLogView.setText("opening connection\n");
                sPort.open(connection);
                mLogView.setText("setting parameters\n");
                sPort.setParameters(19200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                ISPService.startISPService(this, sPort, mConnection);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mLogView.setText("Error opening device: " + e.getMessage() + "\n");
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            } catch (Exception e) {
                mLogView.setText("received unhandled exception\n");
            }
            mLogView.setText("Serial device: " + sPort.getClass().getSimpleName() + "\n");
        }
    }

    public void onGaugeButtonClick(View view) {
        ToggleButton log_toggle = (ToggleButton) findViewById(R.id.log_button);
        if (log_toggle.isChecked()) {
            stopLog();
            log_toggle.setChecked(false);
        } else {
            startLog();
            log_toggle.setChecked(true);
        }
    }

    public void startLog() {
        mLogView.setText("TIL_START_LOGGING\n");
        sendBroadcast(new Intent(Dashboard.TIL_START_LOGGING));
    }

    public void stopLog() {
        mLogView.setText("TIL_STOP_LOGGING\n");
        sendBroadcast(new Intent(Dashboard.TIL_STOP_LOGGING));
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n\n";
        mLogView.setText(message);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ISPService.LocalBinder binder = (ISPService.LocalBinder) service;
            mISPService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    static void launch(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, Dashboard.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }
}
