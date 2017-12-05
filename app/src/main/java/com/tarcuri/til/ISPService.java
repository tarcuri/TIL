package com.tarcuri.til;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tarcuri on 10/20/17.
 */

public class ISPService extends Service {
    private final String TAG = ISPService.class.getSimpleName();

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        ISPService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ISPService.this;
        }
    }

    private Queue<LC1Packet> mLC1Packets;

    /** method for clients */
    public LC1Packet getPacket() {
        return mLC1Packets.remove();
    }

    public static final String ISP_SERVICE_CONNECTED = "com.tarcuri.til.ISP_SERVICE_CONNECTED";
    public static final String ISP_DATA_RECEIVED = "com.tarcuri.til.ISP_DATA_RECEIVED";
    public static final String ISP_LC1_RECEIVED = "com.tarcuri.til.ISP_LC1_RECEIVED";

    // bits 15, 13, 9, and 7 are always 1 in header
    private static int ISP_HEADER_MASK = 0xa280;
    private static short ISP_HIGH_BIT_LENGTH_MASK = 0x100;
    private static short ISP_LOW_LENGTH_MASK = 0x7f;

    private static UsbSerialPort sPort = null;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public static boolean isConnected = false;

    private class ReadISP extends AsyncTask<UsbSerialPort, Long, Long> {
        protected Long doInBackground(UsbSerialPort... ports) {
            int count = ports.length;
            long bytes_read = 0;
            for (UsbSerialPort port : ports) {
                boolean error = false;
                byte[] pbuf = new byte[32];
                byte[] bbuf = new byte[32];


                int packet_bytes = 0;

                // TODO: signal task to stop
                while (!error) try {
                    Log.d(TAG, "Waiting for read()");
                    int br = port.read(bbuf, 500);
                    bytes_read += br;

                    if (br == 0) {
                        Log.d(TAG, "read timed out");
                        continue;
                    }

                    System.arraycopy(bbuf, 0, pbuf, packet_bytes, br);
                    packet_bytes += br;

                    // all packets are made of words
                    int num_words = packet_bytes / 2;
                    if (num_words >= 1) {
                        // should have at least a header
                        short[] words = new short[num_words];
                        ByteBuffer.wrap(pbuf).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(words);

                        short[] packet = null;
                        byte packet_words_total = 0;
                        byte packet_words = 0;
                        for (short word : words) {
                            if ((word & ISP_HEADER_MASK) == ISP_HEADER_MASK) {
                                // found a header, get the packet length (in words)
                                packet_words_total = (byte) (word & ISP_LOW_LENGTH_MASK);
                                if ((word & ISP_HIGH_BIT_LENGTH_MASK) != 0) {
                                    packet_words_total |= (1 << 7);
                                }
                                packet = new short[packet_words_total + 1];
                                packet[0] = word;
                            } else if (packet != null) {
                                if (packet_words < packet_words_total) {
                                    packet[++packet_words] = word;
                                }
                            } else {
                                Log.i(TAG, "no valid packet");
                            }

                            if (packet_words == packet_words_total) {
                                if (packet_words == 3) {
                                    Log.i(TAG, "found packet (3 bytes) - LC1");
                                    LC1Packet lc1 = new LC1Packet(packet);
                                    mLC1Packets.add(lc1);
                                    sendBroadcast(new Intent(ISPService.ISP_LC1_RECEIVED));
                                }
                                // complete packet
                                packet = null;
                                packet_words = 0;
                                packet_bytes = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return bytes_read;
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        // The service is being created
        sendBroadcast(new Intent(ISPService.ISP_SERVICE_CONNECTED));
        new ReadISP().execute(sPort);
//        Toast.makeText(this, "onCreate", Toast.LENGTH_SHORT).show();
//        isConnected = true;
//
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        // The service is starting, due to a call to startService()
//        Toast.makeText(this, "onStartCommand", Toast.LENGTH_SHORT).show();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // client binding with bindService
        return mBinder;
    }

    @Override
    public void onDestroy() {

    }

    static void startISPService(Context context,
                                UsbSerialPort port,
                                ServiceConnection conn) {
        sPort = port;
        Intent isp_intent = new Intent(context, ISPService.class);
        context.bindService(isp_intent, conn, context.BIND_AUTO_CREATE);
    }
}
