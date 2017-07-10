package com.a80034.hubserver;


import android.util.Log;

import java.util.HashMap;
import java.util.Set;

import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.RemoteZigBeeDevice;
import com.digi.xbee.api.ZigBeeDevice;
import com.digi.xbee.api.exceptions.TimeoutException;
import com.digi.xbee.api.exceptions.XBeeException;
import com.digi.xbee.api.models.ExplicitXBeeMessage;
import com.digi.xbee.api.models.XBee64BitAddress;

/**
 * <code>NeighborTable</code> is a class that fetches the neighbors
 * and corresponding link quality information (LQI) of a remote XBee
 * Zigbee device. It works by requesting and then parsing a ZigBee
 * Device Object (ZDO).
 */
public class NeighborTable {
    private static final String TAG = "NeighborTable";
    private final RemoteXBeeDevice remoteXBee;
    private final ZigBeeDevice localXBee;
    private HashMap<XBee64BitAddress, Integer> neighborToLQIMap;

    /**
     * Generates a new table for the specified devices.
     * @param local     a local <code>ZigBeeDevice</code> on the same ZigBee network as the
     *                  <code>remote</code> device
     * @param remote    Aa code>RemoteZigBeeDevice</code> on the same ZigBee network as the
     *                  <code>local</code> device from which the Neighbor Table will be fetched
     */
    public NeighborTable(ZigBeeDevice local, RemoteZigBeeDevice remote) {
        localXBee = local;
        remoteXBee = remote;
        neighborToLQIMap = new HashMap<>();
        refresh();
    }

    /**
     * Updates the Neighbor Table for the remote device.
     */
    public void refresh() {
        byte numOfNeighbors, startIndex, listCount;
        try {
            localXBee.setParameter("AO", new byte[] {0, 1});
            byte index = 0;
            do {
                localXBee.sendExplicitData(remoteXBee, 0, 0, 0x0031, 0, new byte[] {1, index});
                ExplicitXBeeMessage message = localXBee.readExplicitData();
                byte[] ZDOresponse = message.getData();
                numOfNeighbors = ZDOresponse[2]; // The number of neighbors of remote node
                startIndex = ZDOresponse[3]; // The starting point in the neighbor table
                listCount = ZDOresponse[4]; // The number of neighbor table entries in this response
                for (int i = 0; i < listCount; i++) {
                    neighborToLQIMap.put(new XBee64BitAddress(byteArrayToHexString(ZDOresponse, 13+22*i, 20+22*i)), ZDOresponse[26+22*i] & 0xFF);

                }
                index += listCount;
            } while (startIndex + listCount < numOfNeighbors);

        } catch (TimeoutException e) {
            Log.i(TAG, "Timeout Exception, unable to connect to remote device");
            e.printStackTrace();
        } catch (XBeeException e) {
            Log.i(TAG, "XBee Exception");
            e.printStackTrace();
        }

    }

    /**
     * Gets the neighbors connected to the remote device.
     * @return  a <code>Set</code> of <code>XBee64BitAddress</code>es of neighbors
     */
    public Set<XBee64BitAddress> getNeighbors(){
        return neighborToLQIMap.keySet();
    }

    /**
     * Gets the link quality of the connection between the remote devices and its neighbor.
     *
     * @param neighborAddress       64-bit address of neighboring node
     * @return                      the link quality from the remote node and its neighbor.
     *                              A link quality of 255 is maximum quality. 0 indicates
     *                              no connection.
     */
    public int getLQI(XBee64BitAddress neighborAddress) {
        if (neighborToLQIMap.containsKey(neighborAddress)) {
            return neighborToLQIMap.get(neighborAddress);
        }
        return 0;
    }

    /**
     * Helper method for <code>refresh()</code>. Returns a <code>String</code>
     * of the hex representation
     * of entries in the half open interval of <code>array</code> specified by <code>start</code>
     * and <code>end</code>.
     * @param array     the byte array from which to retrieve the hex string
     * @param start     start of the half-open interval
     * @param end       end of the half-open interval
     * @return          a <code>String</code>
     *                  of the hex representation
     *                  of entries in the half open interval of <code>array</code>
     *                  specified by <code>start</code>
     *                  and <code>end</code>
     */
    private static String byteArrayToHexString(byte[] array, int start, int end) {
        String str = "";
        String hex;
        for (int i = end-1; i >= start; i--)
        {
            hex =  Long.toHexString((array[i] & 0xffL));
            if (hex.length()<2) {
                str = str+"0"+hex;
            }
            else {
                str+=hex;
            }
        }
        return str;
    }
}
