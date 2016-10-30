package com.smartcharger;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;


/**
 * Smart Charger charging service Created by Zim on 06/07/2015.
 */
public class ScChargingService extends Service {

    // Some constants
    protected static final String ACTION_CHARGE_PROCESS_CHECK = "Smart Charger battery check";
    protected static final String START_CHARGING_SIGNAL = "c";
    protected static final String STOP_CHARGING_SIGNAL = "s";
    protected static final String START_CHARGING_RESPONSE = "Charge";
    protected static final String STOP_CHARGING_RESPONSE = "Stop";
    protected static final int CODE_STOP_SERVICE = 100;
    protected static final int ERROR_BLUETOOTH = 1;
    protected static final int ERROR_POWER_DISCONNECTED = 2;
    protected static final int ERROR_INTENT_DATA = 3;
    protected static final int ERROR_DEVICE = 6;
    protected static final int ERROR_LOG_FILE = 7;
    protected static final int ALARM_REQUEST_CODE = 111;
    protected static final int NOTIFICATION_ID = 222;
    protected static final int NOTIFICATION_ID_RANGE = 1000000;
    protected static final int FIRST_CHARGING_CHECK_DELAY = 4000;


    // Some fields
    private int minCharge;
    private boolean firstCharge, firstAlarm, discharging, terminated, plugged, chargingStarted, chargingStopped;
    private BroadcastReceiver serviceReceiver;
    private AlarmManager alarmManager;
    private PendingIntent alarmIntent;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private ScBluetooth scBluetooth;
    private File logFile;
    private Handler mHandler;


    @Override
    public void onCreate() {
        super.onCreate();

        discharging = false;
        terminated = false;
        firstCharge = true;
        firstAlarm = true;

        mHandler = new Handler();

        // Init routines
        InitLog();
        initReceiver();
        initAlarms();

        Log.e(ScMain.DEBUG_TAG, "********** Charging service created! **********");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Get data from starting activity
        minCharge = intent.getIntExtra(ScMain.SET_CHARGE, ScMain.DEFAULT_CHARGE);

        // Start with a notification of the running service
        startForeground(NOTIFICATION_ID, getServiceNotification(minCharge));

        Log.e(ScMain.DEBUG_TAG, "********** Charging service started! **********");

        // Restart service with the same data if killed
        return Service.START_REDELIVER_INTENT;

    }

    @Override
    public IBinder onBind(Intent intent) {

        // Irrelevant
        return null;
    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        // Closing routines
        if (alarmManager != null)
            alarmManager.cancel(alarmIntent);

        if (serviceReceiver != null)
            unregisterReceiver(serviceReceiver);

        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();

        if(scBluetooth != null)
            scBluetooth.endConnection();

        Log.e(ScMain.DEBUG_TAG, "SERVICE END");

        // Dump log and clear log buffer on destroy
        try {

            String cmd = "logcat -v time -d -f " + logFile.getAbsolutePath() + " ***!~SMARTCHARGER~!***:E Smart Charger:W *:S";
            Runtime.getRuntime().exec(cmd);

        } catch (IOException e) {

            e.printStackTrace();
        }

    }

    private void initReceiver() {

        // Power manager to acquire partial wakelocks that are needed for charging checks.
        powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);

        // Wakelock for periodic charging checks during sleep
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, ScMain.DEBUG_TAG);

        // Alarm receiver for periodic wake up and check on the charging process.
        serviceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                // Periodic wakeup and check charging service
                if (intent.getAction().equals(ACTION_CHARGE_PROCESS_CHECK) && !terminated) {

                    if(firstAlarm) {

                        firstAlarm = false;

                        Thread delayThread = new Thread(new Runnable() {
                            @Override
                            public void run() {

                                try {
                                    Thread.sleep((long)FIRST_CHARGING_CHECK_DELAY);
                                } catch (InterruptedException e) {
                                    Log.e(ScMain.DEBUG_TAG, "ERROR: First charging check delay error...");
                                    e.printStackTrace();
                                }

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {

                                        checkCharging();
                                    }
                                });

                            }
                        });

                        delayThread.start();

                        return;
                    }

                    wakeLock.acquire();
                    Log.e(ScMain.DEBUG_TAG, "CHARGING CHECK WAKE UP ALARM!");
                    checkCharging();

                    // Respond to switching power on
                } else if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {

                    wakeLock.acquire();
                    Log.e(ScMain.DEBUG_TAG, "POWER CONNECTED!");

                    if(!chargingStarted) {
                        Log.e(ScMain.DEBUG_TAG, "TERMINATED: Device response != chargingStarted");
                        terminate(ERROR_DEVICE);
                    }

                    // If we are in the discharge phase and power is connected we assume device failure and terminate service
                    if (discharging) {
                        Log.e(ScMain.DEBUG_TAG, "TERMINATED: discharging power ON");
                        terminate(ERROR_DEVICE);
                    }

                    if (wakeLock != null && wakeLock.isHeld())
                        wakeLock.release();

                } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {

                    wakeLock.acquire();

                    Log.e(ScMain.DEBUG_TAG, "POWER DISCONNECTED!");

                    if(!chargingStopped) {
                        Log.e(ScMain.DEBUG_TAG, "TERMINATED: Device response != chargingStopped");
                        terminate(ERROR_DEVICE);
                    }

                    // If we are not in discharge phase and power is disconnected we assume service end
                    if (!discharging) {
                        Log.e(ScMain.DEBUG_TAG, "TERMINATED: charging power OFF");
                        terminate(ERROR_POWER_DISCONNECTED);
                    }

                    if (wakeLock != null && wakeLock.isHeld())
                        wakeLock.release();

                }
            }
        };

        // Don't forget to register the receiver for the relevant events
        IntentFilter scIntentFilter = new IntentFilter(ACTION_CHARGE_PROCESS_CHECK);
        scIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        scIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        registerReceiver(serviceReceiver, scIntentFilter);

    }

    // Create alarms for the receiver to pick up
    private void initAlarms() {

        alarmIntent = PendingIntent.getBroadcast(this,
                ALARM_REQUEST_CODE,
                new Intent(ACTION_CHARGE_PROCESS_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT);

        alarmManager = (AlarmManager) this.getSystemService(ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_HOUR, // First alarm time
                AlarmManager.INTERVAL_HOUR, // Repeat time
                alarmIntent);

    }

    // Logs for debugging during actual device operation
    private void InitLog() {

        // Create the log file
        try {

            // Create log file path
            logFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "SmartCharger" + File.separator + "log.txt");

            Log.e(ScMain.DEBUG_TAG, "Storage state: " + Environment.getExternalStorageState() + " Path: " + logFile.toString());

            // Create or validate directories
            logFile.getParentFile().mkdirs();

            // Create or replace log file
            if (!logFile.createNewFile()) {

                if (!logFile.exists() || (!logFile.delete() && !logFile.createNewFile()))
                    throw new IOException("ERROR: Unable to create log file.");

            }

            // Configure and filter logcat enteries to logfile
            String cmd = "logcat -v time -r 1000 -f " + logFile.getAbsolutePath() + " ***!~SMARTCHARGER~!***:E Smart Charger:W *:S";
            Runtime.getRuntime().exec(cmd);

        } catch (IOException e) {

            e.printStackTrace();
            Log.e(ScMain.DEBUG_TAG, "TERMINATED: Log File error");
            terminate(ERROR_LOG_FILE);

        }
    }

    private void checkCharging() {

        Log.e(ScMain.DEBUG_TAG, "********** Charging service check! **********");

        // Get sticky battery state intent from the last broadcast
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Terminate if unable to get data
        if (battery != null) {

            // Get power plug connection state
            int plug = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            // Check if power connected
            plugged = (plug == BatteryManager.BATTERY_PLUGGED_AC || plug == BatteryManager.BATTERY_PLUGGED_USB);

            Log.e(ScMain.DEBUG_TAG, "power connected: " + Boolean.toString(plugged));

            // Terminate if not connected with power connection and not discharging
            if (!plugged) {

                // Make sure we start charging on first charge
                if (firstCharge) {

                    firstCharge = false;
                    Log.e(ScMain.DEBUG_TAG, "Starting first charge to 100%");
                    startCharging(); // Wakelock is released here!
                    return;

                    // If we have no power and not discharging or in a first charge setup terminate
                } else if (!discharging) {

                    Log.e(ScMain.DEBUG_TAG, "TERMINATED: else if (!discharging)");
                    terminate(ERROR_POWER_DISCONNECTED);
                }
            }


            // Get battery current charge level
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

            // Terminate if we get bad values
            if (scale == -1 || level == -1) {
                Log.e(ScMain.DEBUG_TAG, "TERMINATED: Battery data: scale == -1 || level == -1");
                terminate(ERROR_INTENT_DATA);
            }

            // Calculate current battery charge level
            int batLevel = 100 * level / scale;

            Log.e(ScMain.DEBUG_TAG, "Current charge: " + Integer.toString(batLevel) + "%");
            Log.e(ScMain.DEBUG_TAG, "Set minimum charge: " + Integer.toString(minCharge) + "%");

            // If we get to set maximum stop charging to prevent overcharging!
            if (!terminated && batLevel >= ScMain.MAXIMUM_CHARGE)
                stopCharging(); // Wakelock is released here!

                // If we get to set minimum threshold start charging!
            else if (!terminated && batLevel <= minCharge)
                startCharging(); // Wakelock is released here!

            else {

                Log.e(ScMain.DEBUG_TAG, "discharging = " + Boolean.toString(discharging));

                // Don't forget to release the wakelock!!!
                if (wakeLock.isHeld())
                    wakeLock.release();
            }

        } else {

            Log.e(ScMain.DEBUG_TAG, "TERMINATED: battery == null");

            // Don't forget to release the wakelock!!!
            if (wakeLock.isHeld())
                wakeLock.release();

            terminate(ERROR_INTENT_DATA);
        }

    }

    private void terminate(int code) {

        terminated = true;

        NotificationManager nManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        nManager.notify((int) (System.currentTimeMillis() % NOTIFICATION_ID_RANGE), getErrorNotification(code));

        stopSelf();

    }

    private void sendBluetoothSignal(String signal) {

        String response = ScMain.BAD_RESPONSE;

        if(signal.equals(START_CHARGING_SIGNAL))
            chargingStarted = false;
        else if(signal.equals(STOP_CHARGING_SIGNAL))
            chargingStopped = false;

        scBluetooth = new ScBluetooth(ScBluetooth.SPP_UUID, this);

        if(scBluetooth.initDeviceConnection(ScMain.SC_DEVICE_NAME)) {

            for(int i = 0; i < ScBluetooth.MAX_TRIALS; i++ ) {

                response = scBluetooth.sendAndReceiveData(signal);

                Log.e(ScMain.DEBUG_TAG, "Signals sent: " + Integer.toString(i+1) + " " + "Device response: " + response);

                if(response.equals(START_CHARGING_RESPONSE) || response.equals(STOP_CHARGING_RESPONSE))
                    break;
            }

            if(response.equals(START_CHARGING_RESPONSE) && scBluetooth.isConnected())
                chargingStarted = true;

            else if(response.equals(STOP_CHARGING_RESPONSE) && scBluetooth.isConnected())
                chargingStopped = true;

            if (wakeLock.isHeld())
                wakeLock.release();
        }
        else {

            Log.e(ScMain.DEBUG_TAG, "TERMINATED: Failed to establish bluetooth connection..." + "Device response: " + scBluetooth.getLastInput());
            terminate(ERROR_BLUETOOTH);
        }

    }

    private void startCharging() {

        Log.e(ScMain.DEBUG_TAG, "discharging = false");
        discharging = false;

        // Start a new task to send the required signal to switching device
        if (!terminated && !plugged) {

            Log.e(ScMain.DEBUG_TAG, "********** Start charging signal started! **********");
            sendBluetoothSignal(START_CHARGING_SIGNAL);
            scBluetooth.endConnection();

        }
        // Don't forget to release the wakelock!!!
        else if (wakeLock.isHeld())
            wakeLock.release();
    }

    private void stopCharging() {

        Log.e(ScMain.DEBUG_TAG, "discharging = true;");
        discharging = true;
        firstCharge = false;

        // Start a new task to send the required signal to switching device
        if (!terminated && plugged) {

            Log.e(ScMain.DEBUG_TAG, "********** Stop charging signal started! **********");
            sendBluetoothSignal(STOP_CHARGING_SIGNAL);
            scBluetooth.endConnection();

        }
        // Don't forget to release the wakelock!!!
        else if (wakeLock.isHeld())
            wakeLock.release();
    }

    // Build a little notification for the foreground service
    private Notification getServiceNotification(int prc) {

        /*Make sure we stop the service if the user clicks on the
        notification in the main activity that will be started*/

        Intent intent = new Intent(this, ScMain.class);
        intent.putExtra(ScMain.ON_START_REQUEST_CODE, CODE_STOP_SERVICE);
        PendingIntent clickIntent = PendingIntent.getActivity(this, CODE_STOP_SERVICE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);


        // Build the appearance of the notification
        NotificationCompat.Builder nBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.charge_icon)
                        .setContentTitle("Minimum charge: " + Integer.toString(prc) + "%")
                        .setContentText("You can leave the phone safely plugged...")
                        .setContentIntent(clickIntent);

        return nBuilder.build();
    }

    private Notification getErrorNotification(int errorCode) {

        String text;

        switch (errorCode) {
            case ERROR_BLUETOOTH: {
                text = "Bluetooth connection failed.";
                break;
            }
            case ERROR_POWER_DISCONNECTED: {
                text = "Power plug disconnected.";
                break;
            }
            case ERROR_INTENT_DATA: {
                text = "Failed to get information.";
                break;
            }
            case ERROR_DEVICE: {
                text = "Device failure.";
                break;
            }
            case ERROR_LOG_FILE: {
                text = "Log file error.";
                break;
            }
            default: {
                text = "Reason unknown.";
                break;
            }
        }

        /*Make sure we stop the service if the user clicks on the
        notification in the info activity that will be started*/

        Intent intent = new Intent(this, ScWarningInfo.class);
        intent.putExtra(ScMain.ON_START_REQUEST_CODE, CODE_STOP_SERVICE);
        PendingIntent clickIntent = PendingIntent.getActivity(this, CODE_STOP_SERVICE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.error_icon)
                        .setContentTitle("Service terminated!")
                        .setContentText(text)
                        .setContentIntent(clickIntent)
                        .setAutoCancel(true);


        return nBuilder.build();
    }
}

