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
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Dashboard extends AppCompatActivity {
    private final String TAG = Dashboard.class.getSimpleName();

    private static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static PendingIntent mPermissionIntent = null;

    private TextView mLogView;
    private ScrollView mScrollView;

    private static UsbSerialPort sPort = null;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // need to bind to ISP service
    private boolean mBound;
    private ISPService mISPService;

    private class IspUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ISPService.ISP_SERVICE_CONNECTED)) {
                // Do stuff - maybe update my view based on the changed DB contents
                Log.i(TAG, "ISP_SERVICE_CONNECTED");
                Toast.makeText(context, "ISP Connection received", Toast.LENGTH_SHORT).show();
            } else if (intent.getAction().equals(ISPService.ISP_LC1_RECEIVED)) {
                Log.i(TAG, "ISP_LC1_RECEIVED");
                LC1Packet packet = mISPService.getPacket();
                TextView tv = (TextView) findViewById(R.id.lamba_text);
                tv.setText("0.0");
                byte[] buf = packet.getPacketBytes();
                updateReceivedData(buf);
            }
        }
    }

    private IspUpdateReceiver mIspUpdateReceiver = new IspUpdateReceiver();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                    mLogView.append("RUNNER STOPPED\n");
                }

                @Override
                public void onNewData(final byte[] data) {
                    mLogView.append("onNewData\n");
                    Dashboard.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLogView.append("receieved data\n");
                            Dashboard.this.updateReceivedData(data);
                        }
                    });
                }
            };

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ISPService.ISP_SERVICE_CONNECTED);
        filter.addAction(ISPService.ISP_LC1_RECEIVED);
        registerReceiver(mIspUpdateReceiver, filter);
    }

//    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            CharSequence usb_perms_text = "USB permissions granted";
//            CharSequence usb_noperms_text = "USB permissions NOT granted";
//            CharSequence usb_attached_text = "USB attached";
//            CharSequence usb_detached_text = "USB detatched";
//            int duration = Toast.LENGTH_SHORT;
//
//            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
//                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
//                if (granted) {
//                    // user granted permission
//                    Toast toast = Toast.makeText(context, usb_perms_text, duration);
//                    toast.show();
//                } else {
//                    // user did not grant permissions
//                    Toast toast = Toast.makeText(context, usb_noperms_text, duration);
//                    toast.show();
//                }
//            } else if (intent.getAction().equals(ACTION_USB_ATTACHED)) {
//                Toast toast = Toast.makeText(context, usb_attached_text, duration);
//                toast.show();
//            } else if (intent.getAction().equals(ACTION_USB_DETACHED)) {
//                Toast toast = Toast.makeText(context, usb_detached_text, duration);
//                toast.show();
//            }
//        }
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        setFilter();

        mLogView = (TextView) findViewById(R.id.isplog_textview);
        mScrollView = (ScrollView) findViewById(R.id.log_view);

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
    }

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mLogView.append("No serial device\n");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                mLogView.append("Opening device failed\n");
                return;
            }

            try {
                mLogView.append("opening connection\n");
                sPort.open(connection);
                mLogView.append("setting parameters\n");
                sPort.setParameters(19200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                ISPService.startISPService(this, sPort, mConnection);

//                showStatus(mLogView, "CD  - Carrier Detect", sPort.getCD());
//                showStatus(mLogView, "CTS - Clear To Send", sPort.getCTS());
//                showStatus(mLogView, "DSR - Data Set Ready", sPort.getDSR());
//                showStatus(mLogView, "DTR - Data Terminal Ready", sPort.getDTR());
//                showStatus(mLogView, "DSR - Data Set Ready", sPort.getDSR());
//                showStatus(mLogView, "RI  - Ring Indicator", sPort.getRI());
//                showStatus(mLogView, "RTS - Request To Send", sPort.getRTS());
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mLogView.append("Error opening device: " + e.getMessage() + "\n");
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            } catch (Exception e) {
                mLogView.append("received unhandled exception\n");
            }
            mLogView.append("Serial device: " + sPort.getClass().getSimpleName() + "\n");
        }
//        onDeviceStateChange();
    }

    public void startLog() {
        Toast.makeText(this, "startLog", Toast.LENGTH_SHORT).show();
        // TODO: launch ISP logger service
    }

    public void stopLog() {
        Toast.makeText(this, "stopLog()", Toast.LENGTH_SHORT).show();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mLogView.append("stopping IO manager...\n");
            mScrollView.smoothScrollTo(0, mLogView.getBottom());
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mLogView.append("starting IO manager...\n");
            mScrollView.smoothScrollTo(0, mLogView.getBottom());
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n\n";
        mLogView.append(message);
        mScrollView.smoothScrollTo(0, mLogView.getBottom());
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
