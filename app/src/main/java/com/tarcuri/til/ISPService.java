package com.tarcuri.til;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

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

    private long mLogStartTime;
    private long mStartTime;

    private static Context mContext;

    private File mLogFile = null;
    private FileOutputStream mLogOut = null;

    private AsyncTask<UsbSerialPort, Long, Long> mIspParser = null;

    private class TILUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Dashboard.TIL_START_LOGGING)) {
                startLogging();
            } else if (intent.getAction().equals(Dashboard.TIL_STOP_LOGGING)) {
                stopLogging();
            }
        }
    }

    private TILUpdateReceiver mTILUpdateReceiver = new TILUpdateReceiver();

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Dashboard.TIL_START_LOGGING);
        filter.addAction(Dashboard.TIL_STOP_LOGGING);
        registerReceiver(mTILUpdateReceiver, filter);
    }

    private class ISPParser extends AsyncTask<UsbSerialPort, Long, Long> {
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
                while (!isCancelled()) try {
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
                                    LC1Packet lc1 = new LC1Packet(packet);

                                    // queue for dashboard display
                                    mPacketQueue.add(lc1);

                                    // log to file
                                    if (mLogOut != null) {
                                        logPacket(mLogOut, lc1);
                                    }

                                    Intent intent = new Intent(ISPService.ISP_LC1_RECEIVED);
                                    // elapsed time since service started (diff. from elasped log time)
                                    float elapsed = (float) (System.nanoTime() - mStartTime) / (float) 1000000;
                                    intent.putExtra("time", String.format("%f", elapsed));
                                    sendBroadcast(intent);
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
        setFilter();
        sendBroadcast(new Intent(ISPService.ISP_SERVICE_CONNECTED));

        mStartTime = System.nanoTime();
        mIspParser = new ISPParser();
        mIspParser.execute(sPort);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // client binding with bindService
        return mBinder;
    }

    @Override
    public void onDestroy() {
        mIspParser.cancel(true);
        if (mTILUpdateReceiver != null) {
            unregisterReceiver(mTILUpdateReceiver);
        }
        Log.d(TAG, String.format("onDestroy() - %s", mIspParser.getStatus().toString()));
    }

    private File createLogFile() {
        SimpleDateFormat timeStamp = new SimpleDateFormat("YYYY-MM-dd-HHmmss", Locale.US);
        String filename = "lc1_" + timeStamp.format(new Date()) + ".csv";
        File file = new File(getExternalFilesDir(null), filename);
        return file;
    }

    private void logPacket(FileOutputStream out, LC1Packet p) {
        float elapsed = (float) (System.nanoTime() - mLogStartTime) / (float) 1000000;
        // elasped,afr,lamda,multiplier
        String line = String.format(Locale.US,"%.3f,%f,%d,%d\n",
                elapsed, p.getAFR(), p.getLambdaWord(), p.getMultiplier());
        Log.d("LOGGING: ", line);

        try {
            out.write(line.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startLogging() {
        mLogFile = createLogFile();
        mLogStartTime = System.nanoTime();
        try {
            mLogOut = new FileOutputStream(mLogFile);
        } catch (IOException ioe) {
            Log.e(TAG, "ERROR: couldn't create log file");
            ioe.printStackTrace();
        }

        Bitmap largeIcon = BitmapFactory.decodeResource(
                this.getResources(),
                R.mipmap.ic_launcher
        );

        Notification noti = new Notification.Builder(mContext)
                .setContentTitle("TIL: Logging LC-1")
                .setContentText(mLogFile.getName())
                .setSmallIcon(R.drawable.ic_lamba)
                .setLargeIcon(largeIcon)
                .build();

        startForeground(1, noti);
    }

    private void stopLogging() {
        if (mLogOut != null) {
            try {
                mLogOut.close();
                mLogOut = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        stopForeground(true);
    }

    static void startISPService(Context context,
                                UsbSerialPort port,
                                ServiceConnection conn) {
        sPort = port;
        Intent isp_intent = new Intent(context, ISPService.class);
        context.bindService(isp_intent, conn, context.BIND_AUTO_CREATE);

        mContext = context;
    }
}
