package de.karlsve.autolightsoff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class LightSwitcher extends Service implements SensorEventListener {

    private DevicePolicyManager deviceManager = null;
    private SensorManager sensorManager = null;
    private List<Sensor> registeredSensors = new ArrayList<Sensor>();

    private List<Float> magneticsData = new ArrayList<Float>();

    float threshold = 100;

    private int delay = SensorManager.SENSOR_DELAY_UI;

    private Notification note = null;

    @SuppressWarnings("unused")
    private boolean proximityClosed = false;

    private boolean isUpdating = false;

    private enum ListState {
        BLOCKED, FREE
    }

    private enum State {
        OPEN, CLOSED, UNKNOWN
    }

    private ListState magneticsDataListState = ListState.FREE;

    private State magneticsCaseState = State.UNKNOWN;

    private State lightCaseState = State.UNKNOWN;

    private State deviceState = State.OPEN;

    @Override
    public void onAccuracyChanged(Sensor s, int acc) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        deviceManager = (DevicePolicyManager) this
                .getSystemService(Context.DEVICE_POLICY_SERVICE);
        sensorManager = (SensorManager) this
                .getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, sensor, delay);
        registeredSensors.add(sensor);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, sensor, delay);
        registeredSensors.add(sensor);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this,
                LightSwitcherActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        note = new Notification.Builder(this)
                .setContentTitle(this.getText(R.string.notification_title))
                .setContentText(this.getText(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent).build();
        note.flags = Notification.FLAG_NO_CLEAR
                | Notification.FLAG_ONGOING_EVENT;
        this.startForeground(25, note);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
    }

    @Override
    public void onSensorChanged(final SensorEvent se) {
        new Thread(new UpdateSensorData(se)).run();
    }

    protected void toggleLock() {
        if (deviceState == State.OPEN && magneticsCaseState == State.CLOSED
                && lightCaseState == State.CLOSED) {
            lock();
        } else if (deviceState == State.CLOSED
                && (magneticsCaseState == State.OPEN || lightCaseState == State.OPEN)) {
            wake();
        }
    }

    private void lock() {
        if (isActiveAdmin()) {
            deviceManager.lockNow();
            deviceState = State.CLOSED;
        }
    }

    @SuppressWarnings("deprecation")
    private void wake() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        WakeLock wake = manager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.FULL_WAKE_LOCK, this.getText(R.string.app_name)
                .toString());
        wake.acquire();
        wake.release();
        deviceState = State.OPEN;
    }

    private boolean isActiveAdmin() {
        ComponentName adminReceiver = new LightSwitcherDeviceAdminReceiver()
                .getWho(this.getApplicationContext());
        return deviceManager.isAdminActive(adminReceiver);
    }
    
    private class UpdateSensorData implements Runnable {
        
        private SensorEvent se;
        
        public UpdateSensorData(SensorEvent se) {
            this.se = se;
        }

        @Override
        public void run() {
            if (!isUpdating) {
                isUpdating = true;
                switch (se.sensor.getType()) {
                case Sensor.TYPE_PROXIMITY:
                    proximityClosed = se.values[0] == 0.0;
                    break;
                case Sensor.TYPE_LIGHT:
                    lightCaseState = se.values[0] == Float.valueOf(0) ? State.CLOSED
                            : State.OPEN;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if (magneticsDataListState == ListState.FREE) {
                        magneticsDataListState = ListState.BLOCKED;
                        Float sum = Float.valueOf(0);
                        for (Float value : se.values) {
                            sum += value;
                        }
                        magneticsData.add(sum);
                        Collections.sort(magneticsData);
                        for (int i = 1; i < magneticsData.size() - 1; i++) {
                            magneticsData.remove(i);
                        }
                        boolean open = sum < (magneticsData.get(0) + threshold);
                        boolean closed = sum > (magneticsData.get(magneticsData
                                .size() - 1) - threshold);

                        magneticsCaseState = open ? State.OPEN
                                : closed ? State.CLOSED : State.UNKNOWN;

                        magneticsDataListState = ListState.FREE;
                    }
                    break;
                }
                toggleLock();
                isUpdating = false;
            }
        }
        
    }
}
