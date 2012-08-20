package org.jigsawrenaissance.hotcold;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.jigsawrenaissance.hotcold.R;

import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class HotColdActivity extends Activity {

    /** Name of file for storing status log. */
    private static final String LOG_FILENAME = "log";

    private static final String TAG = "HotCold";

    private static final int REQUEST_ENABLE_BT = 1;
    
    /** Our Bluetooth adapter. */
    private BluetoothAdapter mBluetoothAdapter = null;
    
    /** Has Bluetooth scan been started? */
    private boolean bluetoothDiscoveryStarted = false;
    
    /** Output stream set up to append to the log. */
    private FileOutputStream logOut = null;
    
    /** Read in the entire log and return it as a String.
     *  A better method would tail the log... */
    private String readLog() {
        File logFile = new File(getFilesDir(), LOG_FILENAME);
        int logSize = 0;
        String logContents;
        
        if (logFile.exists())
           logSize = (int) logFile.length();
        
        if (logSize != 0)
        {
            int actualLogSize = 0;
            
            try {
                FileInputStream logIn = openFileInput(LOG_FILENAME);
                byte[] buffer = new byte[logSize];
                actualLogSize = logIn.read(buffer, 0, logSize);
                logIn.close();
                logContents = new String(buffer, 0, actualLogSize);
            }
            catch (Exception e) {
                // Act as though the log is empty.
                logContents = new String();
            }
        } else {
            logContents = new String();
        }
        
        return logContents;
    }
    
    /** Create the status log if needed and open it for appending.  If we have
     *  a log already, read it into the log view. */
    private void openLog() {
        // If we already have a log, read it in
        String logContents = readLog();
        updateLog(logContents, false);
        // Open the log for append, creating it if needed.  (Do this after
        // attempting to read -- don't need to read it if it's empty.)
        try {
            logOut = openFileOutput(LOG_FILENAME, Context.MODE_APPEND);
        }
        catch (Exception e) {
            logOut = null;
            updateLog("\nopenFileOutput failed in openLog.", false);
        }
        updateLog("\nSuccessfully opened & read log in openLog.", true);
    }
    
    /** Add a message to the status log.  This appears in the status box, and
     *  is saved to a file so it can be restored on restart.
     *  The caller should add a return to the message if needed.
     *  
     *  @param msg The message to add to the status log.
     *  @param writeToLogFile If true, message will also be written out to the
     *  log file. */
    private void updateLog(String msg, boolean writeToLogFile) {
        TextView logView = (TextView)findViewById(R.id.log);
        logView.append(msg);
        if (writeToLogFile && (logOut != null)) {
            try {
                logOut.write(msg.getBytes());
            }
            catch (Exception e) {
                logOut = null;
                logView.append("\nupdateLog failed to write log:");
                logView.append("\n" + e.toString());
            }
        }
    }
    
    /** Clear the log view and optionally delete the log file. */
    private void clearLog(boolean deleteLogFile) {
        TextView logView = (TextView)findViewById(R.id.log);
        logView.setText("");
        if (deleteLogFile) {
            deleteFile(LOG_FILENAME);
        }
    }
    
    /** Close the log in preparation for (possibly) shutting down. */
    private void closeLog() {
        clearLog(false);
        try {
            logOut.close();
        }
        catch (Exception e) {}
        logOut = null;
    }
    
    /** Service a Clear Log File click:
     *  Clear the log view and delete the log file. */
    public void clearLogClick(View view) {
        clearLog(true);
    }
    
    /** Make sure a Bluetooth is enabled, and do a scan when it is. */
    private void requestBluetoothScan() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "No Bluetooth support.");
            return;
        }
        Log.i(TAG, "Have Bluetooth support!");
        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Bluetooth not enabled -- requesting enable.");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            Log.i(TAG, "Bluetooth is already enabled.");
            performBluetoothScan();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "In onActivityResult");
        
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Got Bluetooth enable activity result -- ok.");
                    performBluetoothScan();
                } else {
                    Log.e(TAG, "Got Bluetooth enable activity result -- not ok.");
                }
                break;
        }
    }
    
    /** Attempt a Bluetooth device discovery scan.
     *  Note this will see each device only once, so is not adequate for
     *  monitoring signal strength. */
    private void performBluetoothScan() {
        Log.d(TAG, "Got to performBluetoothScan");
        bluetoothDiscoveryStarted = mBluetoothAdapter.startDiscovery();
        Log.d(TAG, "startDiscovery returned " + bluetoothDiscoveryStarted);
    }
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String rssi = intent.getParcelableExtra(BluetoothDevice.EXTRA_RSSI);
                // Record device data in the log.
                updateLog(device.getName() + "\n" + device.getAddress() + "\n" + rssi + "\n", true);
            }
        }
    };
    
    /** Service a Bluetooth Scan click. */
    public void bluetoothScanClick(View view) {
        requestBluetoothScan();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hot_cold);
        
        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
    }
    
    protected void onStop() {
        super.onStop();
        //updateLog("\nonStop received.", true);
        // Close the log file and clear the log view.
        closeLog();
    }
    
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        if ((mBluetoothAdapter != null) && bluetoothDiscoveryStarted) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }
    
    /** Undo what we did in onStop.  The activity still retains its state, so
     *  we don't need to read in info that we didn't get rid of in onStop or
     *  onPause.  This is called after onCreate so we don't need to repeat
     *  work there.  Note onRestart is *not* called after onCreate. */
    protected void onStart() {
        super.onStart();
        // Read in the status log if it exists, and open it for append.
        openLog();
    }
}
