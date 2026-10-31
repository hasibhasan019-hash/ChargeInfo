package com.hb.chargeinfo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

public class AlertService extends Service {

    static final String ACT_STOP_BEEP = "com.hb.chargeinfo.STOP_BEEP";
    static final int THRESHOLD = 80;

    private static final String CH_RUN = "run";
    private static final String CH_ALERT = "alert";
    private static final int ID_RUN = 1;
    private static final int ID_ALERT = 2;
    private static final long BEEP_MS = 10000;

    private final Handler h = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;
    private PowerManager.WakeLock wl;
    private boolean alerted = false;
    private long beepEnd = 0;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            check(i);
        }
    };

    private final Runnable beeper = new Runnable() {
        @Override
        public void run() {
            if (tone == null) return;
            if (SystemClock.uptimeMillis() >= beepEnd) {
                stopBeep();
                return;
            }
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400);
            h.postDelayed(this, 800);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CH_RUN, "Battery alarm", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CH_ALERT, "Battery alert", NotificationManager.IMPORTANCE_HIGH));
        startForeground(ID_RUN, runNotification());
        Intent sticky = registerReceiver(rx,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) check(sticky);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID_RUN, runNotification());
        if (intent != null && ACT_STOP_BEEP.equals(intent.getAction())) {
            stopBeep();
        }
        return START_STICKY;
    }

    private Notification runNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_RUN)
                .setContentTitle("Charge Info")
                .setContentText("Battery alarm is on (beeps at " + THRESHOLD + "%)")
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    private void check(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? level * 100 / scale : -1;
        boolean pluggedIn = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

        if (!pluggedIn) {
            alerted = false;
            stopBeep();
            getSystemService(NotificationManager.class).cancel(ID_ALERT);
            return;
        }
        if (pct >= THRESHOLD) {
            if (!alerted) {
                alerted = true;
                startBeep(pct);
            }
        } else {
            alerted = false;
        }
    }

    private void startBeep(int pct) {
        stopBeep();
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (RuntimeException e) {
            tone = null;
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hbcharge:beep");
        wl.acquire(BEEP_MS + 2000);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AlertService.class).setAction(ACT_STOP_BEEP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setContentTitle("Battery at " + pct + "%")
                .setContentText("Unplug the charger to protect your battery.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode),
                        "Stop beep", stop).build())
                .build();
        getSystemService(NotificationManager.class).notify(ID_ALERT, n);

        if (tone != null) {
            beepEnd = SystemClock.uptimeMillis() + BEEP_MS;
            h.post(beeper);
        }
    }

    private void stopBeep() {
        h.removeCallbacks(beeper);
        if (tone != null) {
            try { tone.release(); } catch (Exception ignored) { }
            tone = null;
        }
        if (wl != null && wl.isHeld()) {
            try { wl.release(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(rx); } catch (Exception ignored) { }
        stopBeep();
        getSystemService(NotificationManager.class).cancel(ID_ALERT);
        super.onDestroy();
    }
}
