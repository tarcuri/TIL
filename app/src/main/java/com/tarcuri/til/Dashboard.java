package com.tarcuri.til;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static java.lang.Thread.sleep;

public class Dashboard extends AppCompatActivity {
    private final String TAG = Dashboard.class.getSimpleName();

    private static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static PendingIntent mPermissionIntent = null;

    private static boolean mUsbPermission = false;

    private static UsbSerialPort sPort = null;

    private UsbManager mUsbManager;
    private ListView mListView;

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;



//    private final Handler mHandler = new Handler() {
//        @Override
//        public void handleMessage(Message msg) {
//            switch (msg.what) {
//                case MESSAGE_REFRESH:
//                    refreshDeviceList();
//                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
//                    break;
//                default:
//                    super.handleMessage(msg);
//                    break;
//            }
//        }
//
//    };
//
//    private class IspUpdateReceiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            if (intent.getAction().equals(ISPService.ISP_SERVICE_CONNECTED)) {
//                // Do stuff - maybe update my view based on the changed DB contents
//                Toast.makeText(context, "ISP Connection received", Toast.LENGTH_SHORT).show();
//            } else if (intent.getAction().equals(ISPService.ISP_DATA_RECEIVED)) {
//                Toast.makeText(context, "ISP DATA received", Toast.LENGTH_SHORT).show();
//                TextView tv = (TextView) findViewById(R.id.afr_dashboard);
//                tv.setText("0.0");
//            }
//        }
//    }

//    private IspUpdateReceiver mIspUpdateReceiver;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            CharSequence usb_perms_text = "USB permissions granted";
            CharSequence usb_noperms_text = "USB permissions NOT granted";
            CharSequence usb_attached_text = "USB attached";
            CharSequence usb_detached_text = "USB detatched";
            int duration = Toast.LENGTH_SHORT;

            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    // user granted permission
                    Toast toast = Toast.makeText(context, usb_perms_text, duration);
                    toast.show();

                    mUsbPermission = true;
                    Button log_button = (Button) findViewById(R.id.start_log_button);
                    log_button.setVisibility(Button.VISIBLE);
                } else {
                    // user did not grant permissions
                    Toast toast = Toast.makeText(context, usb_noperms_text, duration);
                    toast.show();

                    mUsbPermission = false;
                }
            } else if (intent.getAction().equals(ACTION_USB_ATTACHED)) {
                Toast toast = Toast.makeText(context, usb_attached_text, duration);
                toast.show();
            } else if (intent.getAction().equals(ACTION_USB_DETACHED)) {
                Toast toast = Toast.makeText(context, usb_detached_text, duration);
                toast.show();
            }
        }
    };

    private List<UsbSerialPort> mEntries = new ArrayList<UsbSerialPort>();
    private ArrayAdapter<UsbSerialPort> mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mListView = (ListView) findViewById(R.id.device_list);

        mAdapter = new ArrayAdapter<UsbSerialPort>(this,
                android.R.layout.simple_expandable_list_item_2, mEntries) {
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

                final UsbSerialPort port = mEntries.get(position);
                final UsbSerialDriver driver = port.getDriver();
                final UsbDevice device = driver.getDevice();

                final String title = String.format("Vendor %s Product %s",
                        HexDump.toHexString((short) device.getVendorId()),
                        HexDump.toHexString((short) device.getProductId()));
                row.getText1().setText(title);

                final String subtitle = driver.getClass().getSimpleName();
                row.getText2().setText(subtitle);

                return row;
            }

        };
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Pressed item " + position);
                if (position >= mEntries.size()) {
                    Log.w(TAG, "Illegal position.");
                    return;
                }

                final UsbSerialPort port = mEntries.get(position);
                useDevice(port);
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mUsbPermission) {
            Button log_button = (Button) findViewById(R.id.start_log_button);
            log_button.setVisibility(Button.VISIBLE);
        }
//        mHandler.sendEmptyMessage(MESSAGE_REFRESH);

//        Toast.makeText(this, "Dashboard::onResume", Toast.LENGTH_SHORT).show();
//        if (mIspUpdateReceiver == null) {
//            mIspUpdateReceiver = new IspUpdateReceiver();
//            IntentFilter intentFilter = new IntentFilter();
//            intentFilter.addAction(ISPService.ISP_SERVICE_CONNECTED);
//            intentFilter.addAction(ISPService.ISP_DATA_RECEIVED);
//            registerReceiver(mIspUpdateReceiver, intentFilter);
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mHandler.removeMessages(MESSAGE_REFRESH);

        Toast.makeText(this, "Dashboard::onPause", Toast.LENGTH_SHORT).show();
//        if (mIspUpdateReceiver != null) {
//            unregisterReceiver(mIspUpdateReceiver);
//        }
    }

    public void refreshDeviceList(View view) {
        Log.d(TAG, "Refreshing device list ...");

        final List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            Log.d(TAG, String.format("+ %s: %s port%s",
                    driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            result.addAll(ports);
        }

        mEntries.clear();
        mEntries.addAll(result);
        mAdapter.notifyDataSetChanged();
        Log.d(TAG, "Done refreshing, " + mEntries.size() + " entries found.");
    }

    public void useDevice(UsbSerialPort port) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        sPort = port;

        usbManager.requestPermission(port.getDriver().getDevice(), mPermissionIntent);
    }

    public void startLogger(View view) {
        Log.d(TAG, "Starting ISPLogger...");
        if (sPort != null) {
            ISPLogger.launch(this, sPort);
        } else {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();
        }
    }
}
