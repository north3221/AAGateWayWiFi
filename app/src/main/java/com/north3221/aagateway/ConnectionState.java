package com.north3221.aagateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ConnectionState {
    public static Boolean
            NET_CONNECTED = false,
            USB_ACCESSORY_CONNECTED = false;

    private Context mContext;
    private Intent hsIntent;

    public ConnectionState(Context context){
        mContext = context;
        hsIntent = new Intent(mContext, HackerService.class);
    }

    final BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext = context;
            NET_CONNECTED = checkWifiConnect();
            String connectedState = "Nothing";
            if (NET_CONNECTED) {
                try {
                    WifiManager wm = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    DhcpInfo dhcp = wm.getDhcpInfo();
                    if (dhcp.gateway != 0) {
                        String gwip = Formatter.formatIpAddress(dhcp.gateway);
                        connectedState = "Connected to: " + gwip;
                        hsIntent.putExtra("gwip", gwip);
                    }
                } catch (Exception e) {
                    NET_CONNECTED = false;
                }
            }
            if (!NET_CONNECTED) {
                connectedState = "Not Connected";
                if (hsIntent.hasExtra("gwip"))
                    hsIntent.removeExtra("gwip");
            }

            Intent tvIntent = new Intent(MainActivity.MESSAGE_INTENT_BROADCAST);
            tvIntent.putExtra(MainActivity.MESSAGE_TVID, "wificonnection");
            tvIntent.putExtra(MainActivity.MESSAGE_EXTRA, connectedState);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(tvIntent);

            setServiceState();

        }
    };

    public void registerWifiReceiver() {
        IntentFilter filter = new IntentFilter();
        //filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        //filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mWifiReceiver, filter);
    }

    public void unregisterWifiReceiver() {
        mContext.unregisterReceiver(mWifiReceiver);
    }

    private boolean checkWifiConnect() {
        try {
            ConnectivityManager manager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = manager.getActiveNetworkInfo();
            return networkInfo != null
                    && networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                    && networkInfo.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public void setServiceState(){
        if (USB_ACCESSORY_CONNECTED && NET_CONNECTED)
            if (!HackerService.running) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mContext.startForegroundService(hsIntent);
                } else {
                    mContext.startService(hsIntent);
                }
            }
        else
            if (HackerService.running)
                mContext.stopService(hsIntent);

    }

    public void setUsbAccessoryConnectedState(boolean state) {
        Intent tvIntent = new Intent(MainActivity.MESSAGE_INTENT_BROADCAST);
        tvIntent.putExtra(MainActivity.MESSAGE_TVID, "usbconnection");
        try {
            UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessoryList = manager.getAccessoryList();
            UsbAccessory accessory;
            if (state && accessoryList != null) {
                accessory = accessoryList[0];
                hsIntent.putExtra("accessory", accessory);
                USB_ACCESSORY_CONNECTED = true;
                tvIntent.putExtra(MainActivity.MESSAGE_EXTRA, "connected");
                LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(tvIntent);
            } else
                USB_ACCESSORY_CONNECTED = false;
        } catch (Exception e){
            USB_ACCESSORY_CONNECTED = false;
        }

        if (!USB_ACCESSORY_CONNECTED) {
            if (hsIntent.hasExtra("accessory"))
                hsIntent.removeExtra("accessory");
            tvIntent.putExtra(MainActivity.MESSAGE_EXTRA, "disconnected");
            LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(tvIntent);

        }
    }


}
