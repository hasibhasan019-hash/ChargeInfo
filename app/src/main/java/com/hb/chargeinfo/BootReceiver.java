package com.hb.chargeinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
                && c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                        .getBoolean(MainActivity.KEY_ALARM, true)) {
            c.startForegroundService(new Intent(c, AlertService.class));
        }
    }
}
