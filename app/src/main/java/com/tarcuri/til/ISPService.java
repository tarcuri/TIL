package com.tarcuri.til;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;

/**
 * Created by tarcuri on 10/20/17.
 */

public class ISPService extends Service {
    static final String ISP_SERVICE_CONNECTED = "com.tarcuri.til.ISP_SERVICE_CONNECTED";

    private static final int IPS_BAUD_RATE = 19200;

    private static UsbSerialPort sPort = null;

    private static Context sInstance = null;

    public static boolean isConnected = false;

    @Override
    public void onCreate() {
        // The service is being created
        sInstance = this;
        Toast.makeText(this, "onCreate", Toast.LENGTH_SHORT).show();
        isConnected = true;
        sendBroadcast(new Intent(ISPService.ISP_SERVICE_CONNECTED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        Toast.makeText(this, "onStartCommand", Toast.LENGTH_SHORT).show();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // client binding with bindService
        return null;
    }

    @Override
    public void onDestroy() {

    }

    static Context getInstance() {
        return sInstance;
    }

//    private void findSerialPortDevice() {
//        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
//        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
//        if (!usbDevices.isEmpty()) {
//            boolean keep = true;
//            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
//                usbDevice = entry.getValue();
//                int deviceVID = usbDevice.getVendorId();
//                int devicePID = usbDevice.getProductId();
//
//                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003)) {
//                    // There is a device connected to our Android device. Try to open it as a Serial Port.
//                    requestUserPermission();
//                    keep = false;
//                } else {
//                    usbDeviceConnection = null;
//                    usbDevice = null;
//                }
//
//                if (!keep)
//                    break;
//            }
//            if (!keep) {
//                // There is no USB devices connected (but usb host were listed). Send an intent to MainActivity.
////                Intent intent = new Intent(ACTION_NO_USB);
////                sendBroadcast(intent);
//            }
//        } else {
//            // There is no USB devices connected. Send an intent to MainActivity
////            Intent intent = new Intent(ACTION_NO_USB);
////            sendBroadcast(intent);
//        }
//    }

    static void connectISPService(Context context, UsbSerialPort port) {
        sPort = port;
        Intent isp_intent = new Intent(context, ISPService.class);
        context.startService(isp_intent);
        Toast.makeText(context, "launched ISP service", Toast.LENGTH_SHORT).show();
    }
}
