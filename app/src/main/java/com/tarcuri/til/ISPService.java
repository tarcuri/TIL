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
                int read_mark = 0;
                byte[] bbuf = new byte[32];

                mByteBuffer.clear();

                // TODO: signal task to stop
                while (!error) try {
                    Log.d(TAG, "Waiting for read()");
                    int br = port.read(bbuf, 500);
                    bytes_read += br;

                    if (br == 0) {
                        Log.d(TAG, "read timed out");
                        continue;
                    } else {
                        Log.d(TAG, "read " + br + "bytes: " + HexDump.dumpHexString(bbuf) + "\n");
                        // chunk access for debug
                        mChunkQueue.add(bbuf);
                        sendBroadcast(new Intent(ISPService.ISP_DATA_RECEIVED));
                    }

                    // now process the bytebuffer
                    mByteBuffer.put(bbuf);

                    // mark this location to continue reading
                    mByteBuffer.mark();

                    // now flip for reading
                    mByteBuffer.limit(mByteBuffer.position());
                    mByteBuffer.position(read_mark);

                    // convert to short buffer to check for packets
                    int limit = mByteBuffer.limit() - mByteBuffer.position(); // num bytes in buffer
                    int num_words = limit / 2;
                    if (num_words == 0) {
                        Log.d(TAG, "no words available (" + br + " bytes)");
                        // need to get more bytes, set limit to capacity
                        mByteBuffer.limit(mByteBuffer.capacity());
                        continue;
                    }

                    Log.d(TAG, "checking " + num_words + " words for packets");

                    // now see if we can find a packet in the bytebuffer
                    short[] words = new short[num_words];
                    mByteBuffer.order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(words);

                    // packet headers include packet length (in words)
                    int packet_words_total = 0;
                    int packet_words_read = 0;
                    int words_consumed = 0;
                    short[] packet = null;
                    for (short word : words) {
                        if ((word & ISP_HEADER_MASK) == ISP_HEADER_MASK) {
                            // found a header, get the packet length (in words)
                            packet_words_total = (byte) (word & ISP_LOW_LENGTH_MASK);
                            if ((word & ISP_HIGH_BIT_LENGTH_MASK) != 0) {
                                packet_words_total |= (1 << 7);
                            }
                            Log.d(TAG, "found header, len = " + packet_words_total);
                            packet = new short[packet_words_total + 1];
                            packet_words_read = 0;
                            packet[0] = word;

                            words_consumed++;
                        } else if (packet != null) {
                            if (packet_words_read < packet_words_total) {
                                packet[++packet_words_read] = word;

                                words_consumed++;
                                Log.d(TAG, "appended word to packet ["+ packet_words_read + "/" + packet_words_total + "]");
                            }

                            if (packet_words_read == packet_words_total) {
                                Log.d(TAG, "queueing LC1 packet");
                                mPacketQueue.add(new LC1Packet(packet));
                                sendBroadcast(new Intent(ISPService.ISP_LC1_RECEIVED));

                                packet = null;
                                packet_words_read = 0;
                                packet_words_total = 0;

                                // increment position to consume bytes
                                mByteBuffer.position(mByteBuffer.position() + words_consumed*2);
                            }
                        } else {
                            Log.d(TAG, "ignoring non-header word");

                            words_consumed++;
                        }
                    }

                    read_mark = mByteBuffer.position();

                    // did we read up to the mark?
                    if (mByteBuffer.hasRemaining()) {
                        // more to read to reset limit to capacity
                        Log.d(TAG, "did not consume all available bytes");
                        mByteBuffer.limit(mByteBuffer.capacity());
                    } else {
                        Log.d(TAG, "consumed all available bytes");
                    }

                    // now reset and check for remaining data
                    mByteBuffer.reset();
                    if (!mByteBuffer.hasRemaining()) {
                        // if no remaining unconsumed bytes then clear the buffer
                        // NOTE: this will potentially discard fragments of a packet
                        mByteBuffer.clear();
                        read_mark = mByteBuffer.position();
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
