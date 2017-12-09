package com.tarcuri.til;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
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

    private Queue<LC1Packet> mPacketQueue = new LinkedList<>();

    private Queue<byte[]> mChunkQueue = new LinkedList<>();

    /** method for clients */
    public LC1Packet getPacket() {
        return mPacketQueue.remove();
    }

    public byte[] getChunk() {
        return mChunkQueue.remove();
    }

    public static final String ISP_SERVICE_CONNECTED = "com.tarcuri.til.ISP_SERVICE_CONNECTED";
    public static final String ISP_DATA_RECEIVED = "com.tarcuri.til.ISP_DATA_RECEIVED";
    public static final String ISP_LC1_RECEIVED = "com.tarcuri.til.ISP_LC1_RECEIVED";

    // LC1 length in words (not including 1 word header)
    private static int ISP_LC1_PACKET_LENGTH = 2;
    // bits 15, 13, 9, and 7 are always 1 in header
    private static int ISP_HEADER_MASK = 0xa280;
    private static short ISP_HIGH_BIT_LENGTH_MASK = 0x100;
    private static short ISP_LOW_LENGTH_MASK = 0x7f;

    private static UsbSerialPort sPort = null;

    private class ReadISP extends AsyncTask<UsbSerialPort, Long, Long> {
        private ByteBuffer mByteBuffer = ByteBuffer.allocate(1024);

        protected Long doInBackground(UsbSerialPort... ports) {
            long bytes_read = 0;
            for (UsbSerialPort port : ports) {
                boolean error = false;
                byte[] chunk;
                byte[] bbuf = new byte[32];
                short[] packet = null;
                int packet_words = 0;
                int words_read = 0;

                mByteBuffer.clear();

                // TODO: signal task to stop
                while (!error) try {
                    Log.d(TAG, "Waiting for read()");
                    int br = port.read(bbuf, 500);
                    bytes_read += br;

                    if (br == 0) {
                        Log.d(TAG, "read timed out");
                        continue;
                    }

                    chunk = Arrays.copyOfRange(bbuf, 0, br);
                    Log.d(TAG, "read " + br + " bytes: " + HexDump.dumpHexString(chunk) + "\n");

                    // chunk access for debug
                    //mChunkQueue.add(chunk);
                    //sendBroadcast(new Intent(ISPService.ISP_DATA_RECEIVED));

                    // now process the bytebuffer
                    mByteBuffer.put(chunk);

                    if (mByteBuffer.position() == 1) {
                        // only have 1 byte, no sense in going further
                        // just continue to read
                        Log.d(TAG, "only read 1 byte - continuing");
                        continue;
                    }

                    mByteBuffer.flip();

                    int len = mByteBuffer.limit() - mByteBuffer.position();
                    int num_words = len / 2;

                    Log.d(TAG, "checking " + num_words + " words for packets");

                    // packet length in words, not including header
                    for (int i = 0; i < num_words; i++) {
                        short word = mByteBuffer.getShort();
                        Log.d(TAG, "word: " + Integer.toHexString(word));
                        if ((word & ISP_HEADER_MASK) == ISP_HEADER_MASK) {
                            packet_words = (byte) (word & ISP_LOW_LENGTH_MASK);
                            if ((word & ISP_HIGH_BIT_LENGTH_MASK) == ISP_HIGH_BIT_LENGTH_MASK) {
                                packet_words |= 1 << 7;
                            }
                            Log.d(TAG, "found header, len = " + packet_words);
                            packet = new short[packet_words + 1];
                            packet[0] = word;
                            words_read = 0;
                        } else if (packet != null) {
                            if (words_read < packet_words) {
                                packet[++words_read] = word;
                                Log.d(TAG, "appended word to packet ["+ words_read + "/" + packet_words + "]");
                            }

                            if (words_read == packet_words) {
                                Log.d(TAG, "found full packet (" + packet_words + " words)");
                                if (packet_words == ISP_LC1_PACKET_LENGTH) {
                                    Log.d(TAG, "found LC1 packet");
                                    mPacketQueue.add(new LC1Packet(packet));
                                    sendBroadcast(new Intent(ISPService.ISP_LC1_RECEIVED));
                                }

                                packet = null;
                                packet_words = 0;
                                words_read = 0;
                            }
                        } else {
                            Log.d(TAG, "ignoring non-header word");
                        }
                    }

                    // clear buffer, but put any remaining bytes onto new buffer
                    int rem = mByteBuffer.remaining();
                    if (rem > 0) {
                        byte[] rem_bytes = new byte[rem];
                        mByteBuffer.get(rem_bytes, 0, rem);
                        mByteBuffer.clear();
                        mByteBuffer.put(rem_bytes);
                        Log.d(TAG, "prepended " + rem + " remaining bytes to next buffer");
                    } else {
                        mByteBuffer.clear();
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
