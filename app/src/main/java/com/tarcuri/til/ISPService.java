package com.tarcuri.til;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Created by tarcuri on 10/20/17.
 */

public class ISPService extends Service {

    private final IBinder binder = new UsbBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class UsbBinder extends Binder {
        public ISPService getService() {
            return ISPService.this;
        }
    }
}
