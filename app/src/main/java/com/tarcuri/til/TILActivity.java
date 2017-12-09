package com.tarcuri.til;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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
import android.widget.Toast;
import android.widget.TwoLineListItem;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.sleep;

public class TILActivity extends AppCompatActivity {
    private final String TAG = TILActivity.class.getSimpleName();

    private static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private static PendingIntent mPermissionIntent = null;

    private static boolean mUsbPermission = false;

    private static UsbSerialPort sPort = null;
    private static int mLambdaMultiplier = 147;

    private UsbManager mUsbManager;
    private ListView mListView;

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
                    Button connect_button = (Button) findViewById(R.id.connect_lc1_button);
                    connect_button.getBackground().setColorFilter(null);
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
        setContentView(R.layout.activity_til);

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

        Button connect_button = (Button) findViewById(R.id.connect_lc1_button);
        connect_button.getBackground().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);

        refreshDeviceList(null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mUsbPermission) {
            Button log_button = (Button) findViewById(R.id.connect_lc1_button);
            log_button.setVisibility(Button.VISIBLE);
        }

        registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mUsbReceiver != null) {
            unregisterReceiver(mUsbReceiver);
        }
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
        Log.d(TAG, "Starting Dashboard...");
        if (sPort != null) {
            Dashboard.launch(this, sPort);
        } else {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();
        }
    }

    public void showSettings(View view) {
        Log.d(TAG, "showing settings");
        Intent intent = new Intent(this, Settings.class);
        startActivity(intent);
    }
}
