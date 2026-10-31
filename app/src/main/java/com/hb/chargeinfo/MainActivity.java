package com.hb.chargeinfo;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    static final String PREFS = "cfg";
    static final String KEY_ALARM = "alarm";

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView watt, speed, details;
    private Button alarmBtn, stopBtn;
    private BatteryManager bm;
    private SharedPreferences prefs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            h.postDelayed(this, 1000);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setTextSize(22);
        title.setText("Charge Info");

        watt = new TextView(this);
        watt.setTextSize(64);
        watt.setGravity(Gravity.CENTER);
        watt.setPadding(0, dp(24), 0, 0);

        speed = new TextView(this);
        speed.setTextSize(18);
        speed.setGravity(Gravity.CENTER);
        speed.setPadding(0, 0, 0, dp(16));

        details = new TextView(this);
        details.setTextSize(18);
        details.setLineSpacing(0, 1.3f);

        TextView note = new TextView(this);
        note.setTextSize(12);
        note.setPadding(0, dp(12), 0, dp(12));
        note.setText("Watts = battery voltage x battery current. This is the power going "
                + "into the battery. It is an estimate and is usually 10-25% lower than "
                + "the charger's rated output.");

        alarmBtn = new Button(this);
        alarmBtn.setOnClickListener(v -> toggleAlarm());

        stopBtn = new Button(this);
        stopBtn.setText("Stop beep");
        stopBtn.setOnClickListener(v -> {
            if (prefs.getBoolean(KEY_ALARM, true)) {
                startService(new Intent(this, AlertService.class)
                        .setAction(AlertService.ACT_STOP_BEEP));
            }
        });

        root.addView(title);
        root.addView(watt);
        root.addView(speed);
        root.addView(details);
        root.addView(note);
        root.addView(alarmBtn);
        root.addView(stopBtn);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        applyAlarm();
    }

    private void toggleAlarm() {
        prefs.edit().putBoolean(KEY_ALARM, !prefs.getBoolean(KEY_ALARM, true)).apply();
        applyAlarm();
    }

    private void applyAlarm() {
        boolean on = prefs.getBoolean(KEY_ALARM, true);
        if (on) {
            startForegroundService(new Intent(this, AlertService.class));
        } else {
            stopService(new Intent(this, AlertService.class));
        }
        alarmBtn.setText(on
                ? "Beep at " + AlertService.THRESHOLD + "%: ON (tap to turn off)"
                : "Beep at " + AlertService.THRESHOLD + "%: OFF (tap to turn on)");
        stopBtn.setEnabled(on);
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private void update() {
        Intent i = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return;

        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? level * 100 / scale : -1;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int mv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int tempTenth = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);

        // Some phones report microamps, some milliamps, sign also varies.
        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        double ma = Math.abs((double) raw);
        if (ma >= 20000) ma = ma / 1000.0;

        double volts = mv / 1000.0;
        double w = volts * (ma / 1000.0);

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        watt.setText(String.format(Locale.US, "%.1f W", w));
        if (charging) {
            if (status == BatteryManager.BATTERY_STATUS_FULL) {
                speed.setText("Battery full");
            } else if (w < 5) {
                speed.setText("Slow charging");
            } else if (w < 10) {
                speed.setText("Normal charging");
            } else {
                speed.setText("Fast charging");
            }
        } else {
            speed.setText("Not charging (battery is being used)");
        }

        details.setText(
                "Battery: " + pct + "%\n"
                + "Status: " + statusText(status) + "\n"
                + "Source: " + plugText(plugged) + "\n"
                + "Current: " + String.format(Locale.US, "%.0f mA", ma) + "\n"
                + "Voltage: " + String.format(Locale.US, "%.2f V", volts) + "\n"
                + "Temperature: " + String.format(Locale.US, "%.1f °C", tempTenth / 10.0) + "\n"
                + "Health: " + healthText(health));
    }

    private static String statusText(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    private static String plugText(int p) {
        if ((p & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "AC charger";
        if ((p & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((p & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "Wireless";
        return "Not plugged in";
    }

    private static String healthText(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }
}
