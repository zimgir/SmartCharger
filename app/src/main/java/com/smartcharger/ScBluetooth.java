package com.smartcharger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

/**
 * Created by nefar on 07/02/2016.
 */
public class ScBluetooth extends Thread {

    protected static final String SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb"; //Standard SerialPortService ID
    protected static final String CHARSET_USASCII = "US-ASCII";
    protected static final int INPUT_LOOP_DELAY = 20; // Delay in milliseconds
    protected static final int INPUT_BUFFER_SIZE = 16;
    protected static final int MAX_TRIALS = 10;
    protected static final char COMMAND_DELIMITER = 0x0A;  // '/n' character;


    private String deviceName;
    private String data;
    private UUID uuid;
    private BluetoothAdapter mBluetoothAdapter;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private BroadcastReceiver btReceiver;
    private Context context;

    private char delimiter;
    private boolean connected, freshData;


    public ScBluetooth(String sUuid, Context sContext) {

        // Usually will be set to SPP UUID which is declared as a constant in this class
        uuid = UUID.fromString(sUuid);

        context = sContext;

        mDevice = null;

        delimiter = COMMAND_DELIMITER;

        connected = false;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        btReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF || state == -1) {

                        Log.e(ScMain.DEBUG_TAG, "WARNING: Bluetooth turned off...");
                        endConnection();

                    }

                } else if (intent.getAction().equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {

                    BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (btDevice != null && btDevice.getName().equals(deviceName)) {

                        Log.e(ScMain.DEBUG_TAG, "ERROR: device disconnected...");
                        endConnection();
                    }
                }

            }
        };

        IntentFilter btFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        btFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        context.registerReceiver(btReceiver, btFilter);

    }

    // Connect to the known paired device by name
    public boolean initDeviceConnection(String dName) {

        deviceName = dName;

        if (isConnected())
            return false;

        // If we cant get the adapter then the android device does not have bluetooth hardware
        if (mBluetoothAdapter == null) {
            // Device does not have bluetooth
            Log.e(ScMain.DEBUG_TAG, "ERROR: No Bluetooth adapter on device...");
            return false;
        }

        // Enable bluetooth without confirmation user dialog if bluetooth is disabled
        if (!mBluetoothAdapter.isEnabled()) {

            // Attempt to enable bluetooth
            if (mBluetoothAdapter.enable()) {

                Log.e(ScMain.DEBUG_TAG, "Turning on Bluetooth...");

                // Wait until bluetooth is enabled to proceed (blocking UI)
                while (!mBluetoothAdapter.isEnabled());

            } else if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) {

                // Wait until bluetooth is enabled to proceed (blocking UI)
                while (!mBluetoothAdapter.isEnabled());

            } else {

                // Failed to turn on bluetooth for some reason
                Log.e(ScMain.DEBUG_TAG, "ERROR: Cannot turn on Bluetooth...");
                return false;

            }

        }

        // Get a set of paired devices known to the android device
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        // If we got some known devices, search for the relevant device by name in the set
        if (pairedDevices.size() > 0) {

            for (BluetoothDevice device : pairedDevices) {

                if (device.getName().equals(deviceName)) {

                    // If device is found get it into our object device
                    mDevice = device;
                    break;
                }
            }
        } else {

            // The phone does not have any known paired bluetooth devices
            Log.e(ScMain.DEBUG_TAG, "ERROR: No paired devices in android...");
            return false;
        }

        if (mDevice == null) {

            // The device name we were searching for was not found in the known paired devices set
            Log.e(ScMain.DEBUG_TAG, "ERROR: device not found in paired devices...");
            return false;
        }


        if (mOutputStream == null && mInputStream == null) {

            // Try to create a communication socket with the relevant device
            try {
                mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to create socket...");
                e.printStackTrace();
                return false;
            }
            // Try to connect to the socket for communication
            try {

                mSocket.connect();
            } catch (IOException e) {

                Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to connect to device...");
                e.printStackTrace();
                return false;
            }
            // Try to get Output stream to send data from android to device
            try {
                mOutputStream = mSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to get output stream...");
                e.printStackTrace();
                return false;
            }
            // Try to get input stream to receive data from device to android
            try {
                mInputStream = mSocket.getInputStream();
            } catch (IOException e) {
                Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to get input stream...");
                e.printStackTrace();
                return false;
            }
        } else {
            Log.e(ScMain.DEBUG_TAG, "ERROR: IO Streams already created...");
            return false;

        }

        if (!connected) {

            this.start();

            connected = true;

        }

        return connected;
    }

    @Override
    public void run() {
        super.run();

        while (!this.isInterrupted() && connected) {

            int bytesAvailable;

            // Delay to allow some time for the connected device to respond to the input stream
            try {

                Thread.sleep((long) INPUT_LOOP_DELAY);

            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(ScMain.DEBUG_TAG, "ERROR: Input thread delay failed...");
                endConnection();
                break;
            }

            // Check if we got some bytes in the input stream
            try {

                bytesAvailable = mInputStream.available();

            } catch (IOException e) {

                Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to get available input bytes...");
                endConnection();
                e.printStackTrace();
                break;
            }

            // If we got some input bytes try to read them
            if (bytesAvailable > 0) {

                byte[] packetBytes = new byte[bytesAvailable];

                try {

                    mInputStream.read(packetBytes);

                } catch (IOException e) {

                    Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to read input bytes...");
                    endConnection();
                    e.printStackTrace();
                    break;
                }

                int readBufferPosition = 0;
                byte b;

                byte[] readBuffer = new byte[INPUT_BUFFER_SIZE];

                for (int i = 0; i < bytesAvailable; i++) {

                    b = packetBytes[i];

                    if (b == delimiter) {

                        byte[] encodedBytes = new byte[readBufferPosition];

                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                        try {

                            data = new String(encodedBytes, CHARSET_USASCII);
                            freshData = true;
                            Log.i(ScMain.DEBUG_TAG, data);

                        } catch (UnsupportedEncodingException e) {
                            Log.e(ScMain.DEBUG_TAG, "ERROR: Unsupported Encoding Exception...");
                            endConnection();
                            e.printStackTrace();
                            break;
                        }
                        readBufferPosition = 0;

                        //The variable data now contains our full command
                    } else {
                        readBuffer[readBufferPosition++] = b;
                    }
                }
            }

        }


    }

    // Blocking call: sends data and waits for response from connected device
    public String sendAndReceiveData(String send) {

        freshData = false;

        try {

            mOutputStream.write(send.getBytes());

        } catch (IOException e) {

            e.printStackTrace();
            Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to send command...");
            return null;
        }

        // Delay to allow some time for the connected device to respond to the input stream

        for (int i = 0; i < MAX_TRIALS; i++) {

            try {

                Thread.sleep((long) INPUT_LOOP_DELAY);

            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(ScMain.DEBUG_TAG, "ERROR: Response delay failed...");
                return null;
            }

            if (freshData)
                break;
        }

        if (freshData)
            return data;
        else
            return null;

    }

    // Send data without waiting for a response
    public boolean sendData(String send) {

        try {

            mOutputStream.write(send.getBytes());

        } catch (IOException e) {

            e.printStackTrace();
            Log.e(ScMain.DEBUG_TAG, "ERROR: Failed to send command...");
            return false;
        }

        return true;

    }

    // Terminates thread and connection and disables bluetooth
    public void endConnection() {

        connected = false;

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled())
            mBluetoothAdapter.disable();

        if (btReceiver != null) {

            try {
                context.unregisterReceiver(btReceiver);
            } catch (Exception e) {

                e.printStackTrace();
            }

        }


        this.interrupt();

    }

    public boolean isConnected() {

        return connected && !this.isInterrupted() && this.isAlive();
    }

    public void setCommandDelimiter(char d) {

        delimiter = d;
    }


    public String getLastInput() {

        freshData = false;
        return data;
    }

    public boolean enableBluetooth() {

        return mBluetoothAdapter.enable();

    }
}
