/**
 * Copyright (c) 2016, Digi International Inc. <support@digi.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.a80034.hubserver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.a80034.R;
import com.digi.android.serial.NoSuchPortException;
import com.digi.android.serial.PortInUseException;
import com.digi.android.serial.*;
import com.digi.android.serial.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TreeMap;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity{
    private static final String TAG = "MainActivity";
    private static final int DATA_RECEIVED = 0;
    private static final long POLL_INTERVAL_MS = 6000; //interval to poll all nodes
    private static final String UART_PORT = "/dev/ttymxc4"; // XBee UART Port on CC6
    private TextView textBox;


	SerialPortManager serialPortManager;
    SerialPort port;
    InputStream serialIn;
    OutputStream serialOut;
    Timer pollTimer;
    TreeMap<Character, String> nodeData; // maps node numbers to data from nodes' sensors



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        serialPortManager = new SerialPortManager(this); // allows access to serial port
        nodeData = new TreeMap<>();
        initializeUI();
        openSerialPort();
        pollTimer = new Timer();
        discoverNodes();

	}

	@Override
	protected void onDestroy() {
        super.onDestroy();
        port.close();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void openSerialPort() {
        try {
            port = serialPortManager.openSerialPort(UART_PORT);
            serialIn = port.getInputStream();
            serialOut = port.getOutputStream();
        } catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException
                | IOException e) {
            e.printStackTrace();
        }
    }

    // Right now this just displays data in a TextView, later this should send data
    // over Bluetooth
    protected void processSensorData(){
        // TODO: take sensor data and send it on to the app via bluetooth
        String ss = "";
        for (Character key : nodeData.keySet()){
            ss += ("node " + key + ":  " + nodeData.get(key) + '\n');
        }
        final String s = ss;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textBox.setText(s);
                textBox.invalidate();
            }
        });
    }

    private void initializeUI(){
        textBox = (TextView)findViewById(R.id.textBox);
        textBox.setText("UI Initialized");
    }

    protected String readSerial (){
        try {
            int availableBytes = serialIn.available();
            if (availableBytes > 0) {
                byte[] readBuffer = new byte[availableBytes];

                // Read the serial port.
                int numBytes = serialIn.read(readBuffer, 0, availableBytes);

                if (numBytes > 0){
                    String read = new String(readBuffer, 0, availableBytes);
                    Log.d(TAG, "READ FROM SERIAL:"+read);
                    return read;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    protected void pollNodes () {
        try {
            readSerial();
            for (char c : nodeData.descendingKeySet()){
                Log.d(TAG, "POLLING: "+c);
                serialOut.write(c);
                Thread.sleep(500);
            }
            int i = 0;
            String[] splitData = readSerial().split("\\$");
            if (splitData.length != nodeData.keySet().size()){
                Log.d(TAG, "SERIAL INPUT SIZE MISMATCH a");
                return;
            }
            for (String s : splitData){
                if (i >= nodeData.size()){
                    Log.d(TAG, "SERIAL INPUT SIZE MISMATCH b");
                    return;
                }
                Log.d(TAG, "SPLIT DATA: "+s);
                Log.d(TAG, "KEY FOR DATA: "+nodeData.descendingKeySet().toArray()[i]);
                nodeData.put((char) nodeData.descendingKeySet().toArray()[i], s);
                i++;
            }
            readSerial(); // clears input stream in case of errors in ZigBee network
            processSensorData();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    // must be run each time a node is added or removed from mesh network
    public void discoverNodes(){
        stopPolling();
        nodeData.clear();
        try {
            serialOut.write('g'); // command for requesting ids from connected nodes
            Thread.sleep(1000);
            for (char c : readSerial().toCharArray()){
                Log.d(TAG, "DISCOVERED NODE: "+c);
                nodeData.put(c, "");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        startPolling();
    }


    protected void stopPolling () {
        pollTimer.cancel();
    }

    protected void startPolling () {
        pollTimer = new Timer();
        pollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                pollNodes();
            }
        }, 0, POLL_INTERVAL_MS); // polls sensors once per specified interval
    }
}

