package com.tarcuri.til;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tarcuri on 10/20/17.
 */

public class ISPService extends Service {
    private final String TAG = ISPService.class.getSimpleName();

    static final String ISP_SERVICE_CONNECTED = "com.tarcuri.til.ISP_SERVICE_CONNECTED";
    static final String ISP_DATA_RECEIVED = "com.tarcuri.til.ISP_DATA_RECEIVED";

    // bits 15, 13, 9, and 7 are always 1 in header
    private static int ISP_HEADER_MASK = 0xa280;
    private static short ISP_HIGH_BIT_LENGTH_MASK = 0x100;
    private static short ISP_LOW_LENGTH_MASK = 0x7f;

    private static UsbSerialPort sPort = null;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private static Context sInstance = null;

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
                    int br = port.read(bbuf, 20);
                    bytes_read += br;

                    System.arraycopy(bbuf, 0, pbuf, packet_bytes, br);
                    packet_bytes += br;

                    // all packets are made of words
                    if (packet_bytes > 2 && packet_bytes % 2 == 0) {
                        // should have at least a header
                        short[] words = new short[pbuf.length / 2];
                        ByteBuffer.wrap(pbuf).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(words);

                        byte packet_words_total = 0;
                        byte packet_words = 0;
                        short[] packet = null;
                        for (short word : words) {
                            if ((word & ISP_HEADER_MASK) == ISP_HEADER_MASK) {
                                // found a header, get the packet length (in words)
                                packet_words_total = (byte) (word & ISP_LOW_LENGTH_MASK);
                                if ((word & ISP_HIGH_BIT_LENGTH_MASK) != 0) {
                                    packet_words_total |= (1 << 7);
                                    packet = new short[packet_words_total + 1];
                                    packet[0] = word;
                                }
                            } else if (packet != null) {
                                if (packet_words < packet_words_total) {
                                    packet[++packet_words] = word;
                                }
                            } else {
                                Log.i(TAG, "no valid packet");
                            }

                            if (packet_words == packet_words_total) {
                                // complete packet

                                packet = null;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return bytes_read;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Integer result) {

        }

    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        // The service is being created

        sInstance = this;

//        Toast.makeText(this, "onCreate", Toast.LENGTH_SHORT).show();
//        isConnected = true;
//        sendBroadcast(new Intent(ISPService.ISP_SERVICE_CONNECTED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        // The service is starting, due to a call to startService()
//        Toast.makeText(this, "onStartCommand", Toast.LENGTH_SHORT).show();
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

    static void connectISPService(Context context, UsbSerialPort port) {
        sPort = port;
        Intent isp_intent = new Intent(context, ISPService.class);
        context.startService(isp_intent);
        Toast.makeText(context, "launched ISP service", Toast.LENGTH_SHORT).show();
    }
}
