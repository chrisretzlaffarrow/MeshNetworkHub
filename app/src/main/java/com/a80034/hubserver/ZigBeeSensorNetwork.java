package com.a80034.hubserver;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.RemoteZigBeeDevice;
import com.digi.xbee.api.XBeeNetwork;
import com.digi.xbee.api.ZigBeeDevice;
import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.io.IOLine;
import com.digi.xbee.api.io.IOValue;
import com.digi.xbee.api.models.XBee64BitAddress;

/**
 * <code>ZigBeeSensorNetwork</code> is a class used for fetching network and sensor
 * data for the <i>Reach</i> Mesh Network system. It requires an XBee configured as
 * a coordinator and in API mode connected to the ConnectCore. The network must consist
 * of remote Xbee nodes with sensors connected as described in the <i>Reach</i> system
 * documentation.
 */
public class ZigBeeSensorNetwork {
    private static final String TAG = "ZigBeeSensorNetwork";

    private long poll_interval_ms = 10*1000;

    private List<RemoteXBeeDevice> remotes;
    private ZigBeeDevice xbee;
    private XBeeNetwork network;

    private Timer discovery, polling;

    // Stores data in list of Strings
    // Each String cooresponds to remote node
    // with format:
    // MAC tempInCelsius light motion rssi (neighborMAC LQI)*
    private List<String> data;

    /**
     * Class constructor.
     * Generates a new ZigBeeSensorNetwork. This constructor
     * connects to the XBee via the UART port specified,
     * discovers the ZigBee network, and establishes timers for
     * periodic sensor polling and network discovery.
     *
     * @param context                   android <code>Context</code>
     * @param port                      UART port to which the XBee is connected
     * @param baud_rate                 operating UART baud rate of XBee
     * @param poll_interval_ms          period of sensor polling in milliseconds
     * @param discover_interval_ms      period of network discovery in milliseconds.
     *                                  Periodic network discovery allows new nodes to be
     *                                  connected to the network after instantiation.
     */
    public ZigBeeSensorNetwork(Context context, String port, int baud_rate, long poll_interval_ms, long discover_interval_ms) {
        remotes = new ArrayList<>();
        data = new ArrayList<>();
        this.poll_interval_ms = poll_interval_ms;
        xbee = new ZigBeeDevice(context, port, baud_rate);

        try {
            xbee.open();
        } catch (XBeeException e1) {
            Log.i(TAG, "ERROR: Xbee Exception");
            e1.printStackTrace();
        }
        while (!xbee.isOpen()){
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        network = xbee.getNetwork();
        discover();
        discovery = new Timer();
        discovery.schedule(new TimerTask() {

            @Override
            public void run() {
                discover();
            }
        }, discover_interval_ms, discover_interval_ms);

    }

    /**
     * Class constructor using default baud, polling period, and network discovery period.
     * Generates a new ZigBeeSensorNetwork. This constructor
     * connects to the XBee via the UART port specified,
     * discovers the ZigBee network, and establishes timers for
     * periodic sensor polling and network discovery.
     * <p>
     * Default parameters used:
     * <ul>
     *     <li>Baud Rate: 9600</li>
     *     <li>Sensor Polling Period: 1 second</li>
     *     <li>Network Discovery Period: 60 seconds</li>
     * </ul>
     *
     * @param context       android <code>Context</code>
     * @param port          UART port to which the XBee is connected
     */
    public ZigBeeSensorNetwork(Context context, String port) {
        this(context, port, 9600, (long) 10*1000, (long) 60*1000);
    }

    public ZigBeeSensorNetwork(Context context, String port, int baud_rate) {
        this(context, port, baud_rate, (long) 10*1000, (long) 60*1000);
    }

    /**
     * Discovers ZigBee network, refreshing list of remote devices.
     * If discovery is already running, it will allow discovery to
     * continue running.
     */
    private void discover() {
        if (!(polling==null))
            polling.cancel();
        if (network.isDiscoveryRunning())
            return;
        network.clearDeviceList();
        // Start the discovery process.
        network.startDiscoveryProcess();

        // Wait until the discovery process has finished.
        while (network.isDiscoveryRunning()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        remotes = network.getDevices();
//        System.out.print("The following nodes were discovered in network: ");
//        for (RemoteXBeeDevice x : remotes) {
//            System.out.print(x.getNodeID()+" | ");
//        }
//        System.out.println();
//        System.out.println();
        polling = new Timer();
        polling.schedule(new TimerTask() {

            @Override
            public void run() {
                pollNodes();
            }
        }, 0, poll_interval_ms);
    }

    /**
     * Fetches and stores new data data from remote sensor nodes. Currently
     * fetches the data return by <code>getData()</code>.
     *
     * @see #getData()
     */
    private void pollNodes() {
        data.clear();
        for (RemoteXBeeDevice r : remotes) {
            try {
                String thisData = "";
//                String id = r.getNodeID();
//                System.out.println("Node ID: "+ id);
                String address = r.get64BitAddress().toString();
//                System.out.println("Node Address: "+address);
                thisData += (address + " ");
                double temperature = (r.getADCValue(IOLine.DIO0_AD0)*120.0)/1023 -50;
//                System.out.println("Temperature: "+temperature+" C");
                thisData += (temperature + " ");
                double ambientLight = (double) r.getADCValue(IOLine.DIO1_AD1)/1024;
//                System.out.println("Ambient Light: "+ambientLight);
                thisData += (ambientLight + " ");
                //double gas = r.getADCValue(IOLine.DIO2_AD2);
                //System.out.println("Gas: "+gas);
                boolean motion = (r.getDIOValue(IOLine.DIO3_AD3) == IOValue.HIGH);
                thisData += ((motion ? 1 : 0) + " ");
//                System.out.print("Motion ");
                if (!motion) {
                    System.out.print("Not ");
                }
//                System.out.println("Detected");
                byte[] RSSI = r.getParameter("DB");
//                System.out.println("RSSI: -"+RSSI[0]+" dBm");
                thisData += (RSSI[0] + " ");
                NeighborTable nTable = new NeighborTable(xbee, (RemoteZigBeeDevice) r);
//                System.out.println("Neighbors: ");
                for (XBee64BitAddress a : nTable.getNeighbors()) {
                    int lqi = nTable.getLQI(a);
                    thisData += (a + " " + lqi +" ");
//                    System.out.println("  Address: "+a);
//                    System.out.println("  LQI: "+lqi);
                    boolean inRemotes = false;
                    for (RemoteXBeeDevice re : remotes) {
                        if (re.get64BitAddress().equals(a)) {
                            inRemotes = true;
                        }
                    }
                    if (!inRemotes && !xbee.get64BitAddress().equals(a)) {
//                        System.out.println("Found neighbor not in list of remotes, going into network discovery.");
                        polling.cancel();
                        discover();
                        return;
                    }
                }
//                System.out.println();
                data.add(thisData);
            } catch (TimeoutException e1) {
//                System.out.println("Unable to communicate with node (may be disconnected), going into network discovery.");
//                System.out.println();
                polling.cancel();
                discover();
                return;
            } catch (XBeeException e1) {
                Log.i(TAG, "XBee Exception");
                e1.printStackTrace();
            }
        }
    }

    /**
     * Returns sensor data from remote nodes.
     *
     * @return      a <code>List</code> of <code>Strings</code>,
     *              one per node, in the following format:
     *              <p>
     *               MAC tempInCelsius light motion RSSI (neighborMAC LQI)*
     *              <p>
     *              Where "MAC" is the MAC Address of node polled, "tempInCelcius" is the
     *              temperature in degrees Celsius, "light" is ambient light as a decimal out
     *              of a maximum of 1.0, "RSSI" is the signal strength in -dBm.
     *              Following is a list of the neighboring nodes. "neighborMAC" is the MAC
     *              address of the neighbor and LQI is the link quality out of a maximum link
     *              quality of 255 to that neighbor.
     */
    public List<String> getData(){
        return data;
    }

    /**
     * a <code>toString</code> method for viewing remote sensor data.
     * @return      a <code>String</code> with each entry of the <code>List</code>
     *              generated by <code>getData()</code> on a new line
     * @see         #getData()
     */
    public String toString() {
        String str = "";
        for (String string : data) {
            str += string + "\n";
        }
        return str;
    }

    /**
     * Closes the connection to the XBee. Should be called to allow other applications
     * access to UART port. New <code>ZigBeeSensorNetwork</code> must be instantiated
     * to access ZigBee network after this method is called.
     */
    public void close(){
        discovery.cancel();
        polling.cancel();
        xbee.close();
    }
}
