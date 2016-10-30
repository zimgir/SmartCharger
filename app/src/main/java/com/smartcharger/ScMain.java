package com.smartcharger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


public class ScMain extends Activity {

    // Some constants
    protected static final String DEBUG_TAG = "***!~SMARTCHARGER~!***";
    protected static final String ON_START_REQUEST_CODE = "request code";
    protected static final String SET_CHARGE = "prc";
    protected static final String CHECK_COMMAND = "K";
    protected static final String BAD_RESPONSE = "NO RESPONSE";
    protected static final String DEFAULT_RESPONSE = "Received: ";
    protected static final String SC_DEVICE_NAME = "MEOW";
    protected static final int MAXIMUM_CHARGE = 99;
    protected static final int DEFAULT_CHARGE = 90;
    protected static final int RED_PRC_SHIFT = 70;
    protected static final int YELLOW_PRC_SHIFT = 90;


    // Some fields
    private SeekBar prcBar;
    private TextView prcTxt, errTxt;
    private ImageView usbErrIcn, btErrIcn;
    private BroadcastReceiver plugReceiver;
    private ScBluetooth scBluetooth;
    private Thread checkBtThread;
    private ImageButton chargeBtn;
    private Handler mHandler;
    private Context context;
    private boolean plugged, btWorks, btCheck;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sc_main); // Set The designed layout

        // Link required layout items

        prcBar = (SeekBar) findViewById(R.id.prcbar);
        prcTxt = (TextView) findViewById(R.id.prctxt);
        errTxt = (TextView) findViewById(R.id.errtxt);
        usbErrIcn = (ImageView) findViewById(R.id.usberricn);
        btErrIcn = (ImageView) findViewById(R.id.jackerricn);
        chargeBtn = (ImageButton) findViewById(R.id.chargebtn);

        context = this;

        btCheck = true;

        mHandler = new Handler();

        scBluetooth = new ScBluetooth(ScBluetooth.SPP_UUID, this);

        // Enable bluetooth for checking
        scBluetooth.enableBluetooth();

        // Init routines
        initBar();
        checkBatteryPlug();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Get that request code then figure out what to do with it.
        int code = getIntent().getIntExtra(ON_START_REQUEST_CODE, -1);

        // Stop service if user clicked on the notification.
        if (code == ScChargingService.CODE_STOP_SERVICE) {

            Intent stopIntent = new Intent(this, ScChargingService.class);
            stopService(stopIntent);
            Toast toast = Toast.makeText(this, "Charging service stopped!", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (plugReceiver != null)
            unregisterReceiver(plugReceiver);

        scBluetooth.endConnection();

    }

    // Check Bletooth communication with device
    private void checkBluetooth() {

        // Init flag
        btWorks = false;

        String response = BAD_RESPONSE;

        if (scBluetooth == null || !scBluetooth.isConnected())
            scBluetooth = new ScBluetooth(ScBluetooth.SPP_UUID, this);

        if (scBluetooth.initDeviceConnection(SC_DEVICE_NAME)) {

            for (int i = 0; i < ScBluetooth.MAX_TRIALS; i++) {

                response = scBluetooth.sendAndReceiveData(CHECK_COMMAND);

                if (response.equals(DEFAULT_RESPONSE + CHECK_COMMAND))
                    break;
            }

            if (response.equals(DEFAULT_RESPONSE + CHECK_COMMAND) && scBluetooth.isConnected())
                btWorks = true;
        } else if (scBluetooth.isConnected())
            btWorks = true;

        if (response.equals(BAD_RESPONSE))
            response = scBluetooth.getLastInput();

        Log.e(ScMain.DEBUG_TAG, "Bluetooth works: " + Boolean.toString(btWorks) + " " + response);

    }


    private void checkBatteryPlug() {

        // Receiver to get the plug state
        plugReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) try {

                    // Receive the state of the power plug
                    int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

                    if (plug != BatteryManager.BATTERY_PLUGGED_AC && plug != BatteryManager.BATTERY_PLUGGED_USB) {

                        // Issue AC plug warning
                        plugged = false;
                        usbErrIcn.setVisibility(View.VISIBLE);
                        usbErrIcn.setClickable(true);

                    } else {
                        // Hide AC plug warning
                        plugged = true;
                        usbErrIcn.setVisibility(View.INVISIBLE);
                        usbErrIcn.setClickable(false);

                    }

                } catch (Exception e) {
                    Log.e(ScMain.DEBUG_TAG, "Battery Info Error");
                }
            }
        };

        // Get battery state filter
        IntentFilter batFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        // Register receiver with filter to make it active
        registerReceiver(plugReceiver, batFilter);
    }

    private void initBar() {

        // Add SeekBar functionallity
        prcBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                // Set the text according to the seekbar slider
                prcTxt.setText(Integer.toString(i) + "%");

                // Add some color change minor visual effects
                if (i < RED_PRC_SHIFT)
                    prcTxt.setTextColor(Color.rgb(255, i * 255 / 100, 0));
                else if (i < YELLOW_PRC_SHIFT)
                    prcTxt.setTextColor(Color.rgb(200 + 55 * (100 - i) / 100, 200 + i * 55 / 100, 0));
                else
                    prcTxt.setTextColor(Color.rgb(255 * (100 - i) / 100, 255, 0));

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Irrelevant
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Irrelevant
            }
        });
    }

    public void startChargeService(View v) {

		 /*This is where we start the actual functionality of the app + device.
         We must not start the service until warnings were resolved by the user
		 else it will not work.*/

        chargeBtn.setBackgroundResource(R.drawable.error_icon);

        if (btCheck) {

            Toast toast = Toast.makeText(this, "Checking Bluetooth connection...", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();

            if (checkBtThread == null || !checkBtThread.isAlive()) {

                checkBtThread = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        checkBluetooth();

                        btCheck = false;

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                chargeBtn.setBackgroundResource(R.drawable.charge_icon);

                                // Issue warning Icon also prevent from starting charging service if bluetooth doesn't work
                                if (!btWorks) {

                                    btErrIcn.setVisibility(View.VISIBLE);
                                    btErrIcn.setClickable(true);

                                } else {

                                    btErrIcn.setVisibility(View.INVISIBLE);
                                    btErrIcn.setClickable(false);
                                }

                                if (!btWorks)
                                    errTxt.setVisibility(View.VISIBLE);
                                else
                                    errTxt.setVisibility(View.INVISIBLE);

                                if (!btCheck && errTxt.getVisibility() == View.INVISIBLE) {

                                    Intent startCharging = new Intent(context, ScChargingService.class);
                                    startCharging.putExtra(SET_CHARGE, prcBar.getProgress());
                                    startService(startCharging);
                                    finish();

                                }
                            }
                        });
                    }
                });

                checkBtThread.start();

            }

        } else if (!btCheck && errTxt.getVisibility() == View.INVISIBLE) {


            Intent startCharging = new Intent(this, ScChargingService.class);
            startCharging.putExtra(SET_CHARGE, prcBar.getProgress());
            startService(startCharging);
            finish();

        }

    }

    public void startAppInfo(View v) {

        // Display new activity window with info about the app
        Intent startInfo = new Intent(this, ScAppInfo.class);
        startActivity(startInfo);
    }

    public void startErrorInfo(View v) {

        // Display new activity window with info about the warnings
        Intent startWarningInfo = new Intent(this, ScWarningInfo.class);
        startActivity(startWarningInfo);
    }

    public void exitMain(View v) {

        // Exit the application
        finish();
    }


}
