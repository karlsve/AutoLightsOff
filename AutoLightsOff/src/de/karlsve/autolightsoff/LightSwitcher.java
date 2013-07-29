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
    
    private int delay = SensorManager.SENSOR_DELAY_NORMAL;
    
    private Notification note = null;

    private boolean magneticClosed = false;
    private boolean lightClosed = false;
    @SuppressWarnings("unused")
    private boolean proximityClosed = false;
    
    private enum ListState {
        BLOCKED, FREE
    }
    
    private ListState listState = ListState.FREE;

    private enum State {
        UNKNOWN, WOKEN, LOCKED
    }

    private State currentState = State.UNKNOWN;

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
        Intent notificationIntent = new Intent(this, LightSwitcherActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        
        note = new Notification.Builder(this)
                .setContentTitle(this.getText(R.string.notification_title))
                .setContentText(this.getText(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
        note.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        this.startForeground(25, note);
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        stopForeground(true);
    }

    @Override
    public void onSensorChanged(final SensorEvent se) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                switch (se.sensor.getType()) {
                case Sensor.TYPE_PROXIMITY:
                    proximityClosed = se.values[0] == 0.0;
                    break;
                case Sensor.TYPE_LIGHT:
                    lightClosed = se.values[0] == 0.0;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if(listState == ListState.FREE) {
                        listState = ListState.BLOCKED;
                        Float sum = Float.valueOf(0);
                        for(Float value : se.values) {
                            sum += value;
                        }
                        magneticsData.add(sum);
                        Collections.sort(magneticsData);
                        for(int i = 1; i < magneticsData.size() - 1; i++) {
                            magneticsData.remove(i);
                        }
                        
                        float threshold = Math.abs(magneticsData.get(0) - magneticsData.get(1)) / 4;
                        
                        boolean min = sum > (magneticsData.get(magneticsData.size() - 1) - threshold);
                        boolean max = sum < (magneticsData.get(magneticsData.size() -1) + threshold);
                        magneticClosed = min && max;
                        listState = ListState.FREE;
                    }
                    break;
                }
                toggleLock();
            }
        };
        new Thread(r).start();
    }

    protected void toggleLock() {
        if (currentState == State.UNKNOWN && magneticClosed && lightClosed) {
            lock();
        } else if (currentState == State.UNKNOWN
                && (!magneticClosed && !lightClosed)) {
            wake();
        } else if (currentState == State.WOKEN
                && (magneticClosed && lightClosed)) {
            lock();
        } else if (currentState == State.LOCKED && (!magneticClosed || !lightClosed)) {
            wake();
        }
    }

    private void lock() {
        if (isActiveAdmin()) {
            currentState = State.LOCKED;
            deviceManager.lockNow();
        }
    }

    @SuppressWarnings("deprecation")
    private void wake() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        WakeLock wake = manager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.FULL_WAKE_LOCK, this.getText(R.string.app_name).toString());
        wake.acquire();
        wake.release();
        currentState = State.WOKEN;
    }

    private boolean isActiveAdmin() {
        ComponentName adminReceiver = new LightSwitcherDeviceAdminReceiver()
                .getWho(this.getApplicationContext());
        return deviceManager.isAdminActive(adminReceiver);
    }
}
