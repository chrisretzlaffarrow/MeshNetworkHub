package com.a80034.hubserver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.a80034.R;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity{
    private static final String TAG = "MainActivity";
    private static final long DATA_SEND_INTERVAL_MS = 6000; // interval to send data over Bluetooth
    private static final String UART_PORT = "/dev/ttymxc4"; // XBee UART Port on CC6
    private static final int BAUD_RATE = 9600; //UART Baud Rate
    private static final long POLLING_INTERVAL = 10*1000; // interval to poll all nodes
    private static final long DISCOVERY_INTERVAL = 60*1000; // interval to conduct node discovery


    Timer dataSendTimer;
    ZigBeeSensorNetwork ZigBeeNetwork;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        ZigBeeNetwork = new ZigBeeSensorNetwork(this, UART_PORT, BAUD_RATE, POLLING_INTERVAL, DISCOVERY_INTERVAL);
        dataSendTimer = new Timer();
        dataSendTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendData();
            }
        }, 0, DATA_SEND_INTERVAL_MS);

	}

	@Override
	protected void onDestroy() {
        super.onDestroy();
        ZigBeeNetwork.close();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	private void sendData(){
        String toSend = "";
        for (String s : ZigBeeNetwork.getData() ) {
            toSend += (s+";");
        }
        // TODO: Send s over Bluetooth
        Log.i(TAG, "Sending sensor data over Bluetooth: ");
        Log.i(TAG, toSend);
    }
}

