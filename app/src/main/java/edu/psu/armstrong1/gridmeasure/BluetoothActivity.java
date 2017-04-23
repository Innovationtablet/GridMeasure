/*
Code derived from a tutorial posted by David Vassallo on his blog:
    http://blog.davidvassallo.me/
    http://blog.davidvassallo.me/2014/05/11/android-linux-raspberry-pi-bluetooth-communication/
*/

package edu.psu.armstrong1.gridmeasure;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;

public class BluetoothActivity extends AppCompatActivity {
    // Constants
    public static int START_BLUETOOTH_CODE = 1;
    public static String STARTING_TEXT_INTENT_KEY = "STARTING_TEXT";

    // Constants for Bluetooth information
    public static String DEVICE_UUID = "94f39d29-7d6d-437d-973b-fba39e49d4ee";
    public static String DEVICE_NAME = "raspberrypi";

    // Constants for Message Protocol
    public static int MESSAGE_SIZE_BYTES = 4;

    // Variables for bluetooth service information
    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    BluetoothDevice bluetoothDevice = null;
    ArrayList<BluetoothDevice> pairedDevices;
    ArrayList<String> devicesFound;
    int selectedDevice = 0;

    // Variables for interacting with the GUI
    private Handler handler;
    private TextView textView;
    private TextView enteredText;
    private Spinner spinner;

    // Variables for data transfer
    private workerThread dataTransferThread = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        handler = new Handler();

        // Get the required views
        textView = (TextView) findViewById(R.id.bluetooth_receivedDataTextView);
        spinner = (Spinner) findViewById(R.id.bluetooth_spinner);

        // Get the bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Make sure bluetooth is available
        if (bluetoothAdapter != null) {
            // Make sure bluetooth is started
            if (!bluetoothAdapter.isEnabled()) {
                turnOnBluetooth();
            } else {
                populateSpinner();
            }
        } else {
            // No bluetooth available
            Toast.makeText(getApplicationContext(), R.string.error_noBluetooth, Toast.LENGTH_LONG).show();
            ((Button) findViewById(R.id.button_bluetooth_sendData)).setClickable(false);
            ((ImageButton) findViewById(R.id.button_bluetooth_updateBluetooth)).setClickable(false);
        }

        // Check for starting text in the intent
        if (getIntent().hasExtra(STARTING_TEXT_INTENT_KEY)) {
            // Get the TextView and hide the EditText view
            enteredText = (TextView) findViewById(R.id.bluetooth_sendDataTextView);
            findViewById(R.id.bluetooth_sendDataEditText).setVisibility(View.GONE);

            // Set the starting text
            String startingText = getIntent().getExtras().getString(STARTING_TEXT_INTENT_KEY, "");
            enteredText.setText(startingText);
        } else {
            // Get the EditText view and hide the TextView
            enteredText = (EditText) findViewById(R.id.bluetooth_sendDataEditText);
            findViewById(R.id.bluetooth_sendDataTextView).setVisibility(View.GONE);
            findViewById(R.id.bluetooth_sendDataScrollView).setVisibility(View.GONE);
        }
    }


    @Override
    public void onWindowFocusChanged (boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

       if (bluetoothDevice == null) {
           Log.i("BluetoothActivity", "Device \"" + DEVICE_NAME + "\" not found");
       }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == START_BLUETOOTH_CODE && resultCode == RESULT_OK) {
            // Bluetooth turned on - populate spinner
            populateSpinner();
        }
    }

    // Called when the user clicks the send data button
    public void sendData(View view) {
        // Stop any old threads
        if (dataTransferThread != null) {
            dataTransferThread.cancel();
        }

        if (devicesFound != null && devicesFound.size() > 0) {
            bluetoothDevice = pairedDevices.get(spinner.getSelectedItemPosition());
        }

        // Make sure a device is selected
        if (bluetoothDevice != null && bluetoothAdapter.isEnabled()) {
            // Start a new worker thread to send data
            dataTransferThread = new workerThread(enteredText.getText().toString());
            (new Thread(dataTransferThread)).start();
        } else if (!bluetoothAdapter.isEnabled()) {
            // Bluetooth is off
            turnOnBluetooth();
        } else {
            Toast.makeText(getApplicationContext(), R.string.warn_bluetooth_noPairedDeviceSelected, Toast.LENGTH_LONG).show();
        }
    }


    // Called when the user clicks the refresh button for the spinner
    public void updateBluetoothSpinner(View view) {
        if (!bluetoothAdapter.isEnabled()) {
            turnOnBluetooth();
        } else {
            populateSpinner();
        }
    }

    private void turnOnBluetooth() {
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, START_BLUETOOTH_CODE);
    }


    public boolean sendBtMsg(String msg2send){
        // Make sure to send a valid message
        if (msg2send.length() >= 0) {
            UUID uuid = UUID.fromString(DEVICE_UUID);
            try {
                // Create a socket and connect
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
                if (!bluetoothSocket.isConnected()) {
                    bluetoothSocket.connect();
                }

                // Get the message bytes and their length
                byte[] msgBytes = msg2send.getBytes();
                final int msgLength = msgBytes.length;
                int totalLength = msgLength + MESSAGE_SIZE_BYTES;

                // Check if message is too long
                if (MESSAGE_SIZE_BYTES * 8 < Integer.SIZE && msgLength >>> (MESSAGE_SIZE_BYTES * 8) > 0) {
                    Log.e("BluetoothActivity", "Message is too big");

                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), R.string.warn_bluetooth_messageTooBig + ": " + msgLength + " bytes", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                // Create a byte buffer to hold the message length and the message
                ByteBuffer buff = ByteBuffer.allocate(totalLength);

                // Put the message length into the buffer
                for (int i = 1; i <= MESSAGE_SIZE_BYTES; i++) {
                    byte putByte;
                    if ((MESSAGE_SIZE_BYTES - i) * 8 >= Integer.SIZE) {
                        // Shifting by more than the number of bits in an int (Android/Java ignores it)
                        //   So put a zero

                        putByte = (byte) 0;
                    } else {
                        putByte = (byte) (msgLength >> ((MESSAGE_SIZE_BYTES - i) * 8));
                    }

                    buff.put(putByte);
                }

                // Put the message into the buffer
                buff.put(msgBytes);

                // Send the message
                OutputStream outputStream = bluetoothSocket.getOutputStream();
                outputStream.write(buff.array());

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        } else {
            handler.post(new Runnable() {
                public void run() {
                    Toast.makeText(getApplicationContext(), R.string.warn_bluetooth_emptyMessage, Toast.LENGTH_LONG).show();
                }
            });

            return false;
        }
    }

    private void populateSpinner() {
        // Make sure bluetooth is available
        if (bluetoothAdapter != null) {

            // Loop through the devices found
            pairedDevices = new ArrayList<BluetoothDevice>(bluetoothAdapter.getBondedDevices());
            devicesFound = new ArrayList<String>();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    Log.i("BluetoothActivity", "Found device: " + device.getName());

                    // Add the device to the list
                    devicesFound.add(device.getName());

                    // If default device found
                    if (device.getName().equals(DEVICE_NAME))
                    {
                        Log.i("BluetoothActivity", "Found cutter: " + device.getName());
                        bluetoothDevice = device;
                        selectedDevice = devicesFound.size() - 1;
                    }
                }
            }

            // Create the array adapter for the spinner
            ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, devicesFound);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            // Populate the spinner
            spinner.setAdapter(adapter);
            spinner.setSelection(selectedDevice);
        }
    }

    final class workerThread implements Runnable {

        private String btMsg;
        private Boolean keepRunning = true;

        public workerThread(String msg) {
            btMsg = msg;
        }

        public void cancel(){
            keepRunning = false;
        }

        public void run()
        {
            byte[] messageSizePacket = new byte[MESSAGE_SIZE_BYTES];
            byte[] packetBytes;
            int bytesLeft = MESSAGE_SIZE_BYTES;
            int bytesAvailable;
            boolean knowMsgLength = false;

            Log.i("BluetoothWorker", "Sending message to " + bluetoothDevice.getName());

            // Send the message and wait for a response if successful
            if (sendBtMsg(btMsg)) {
                Log.i("BluetoothWorker", "Message sent successfully. Waiting for a response.");

                // Get a response
                while (!Thread.currentThread().isInterrupted() && keepRunning) {
                    try {
                        // Try to read from the input stream
                        final InputStream inputStream = bluetoothSocket.getInputStream();
                        bytesAvailable = inputStream.available();

                        // Wait till there are as many bytes as expected before reading (no partial reads)
                        if (bytesAvailable >= bytesLeft) {
                            Log.i("BluetoothWorkerThread", bytesAvailable + " bytes available");

                            // Check if we have already gotten the message header
                            if (!knowMsgLength) {
                                // First - get the message length from the message header
                                inputStream.read(messageSizePacket);

                                // Get the length of the message
                                bytesLeft = java.nio.ByteBuffer.wrap(messageSizePacket).getInt();
                                knowMsgLength = true;
                                Log.i("BluetoothWorkerThread", "Expecting message of length " + bytesLeft);
                            } else {
                                // Second - Get the message (already have header)
                                packetBytes = new byte[bytesAvailable];
                                inputStream.read(packetBytes);

                                // Convert message to a string
                                final String data = new String(packetBytes, "US-ASCII");

                                // Put the message on the screen
                                handler.post(new Runnable() {
                                    public void run() {
                                        textView.setText(data);
                                    }
                                });

                                // Close the socket
                                bluetoothSocket.close();
                                break;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        }
    };
}
