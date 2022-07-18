package com.north3221.aagateway;

import static com.north3221.aagateway.MainActivity.SHARED_PREF_NAME;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;

public class ConnectionStateReceiver extends BroadcastReceiver {
    private AAlogger logger;
    public static final String
            ACTION_USB_ACCESSORY_ATTACHED = "com.north3221.aagateway.ACTION_USB_ACCESSORY_ATTACHED",
            ACTION_USB_ACCESSORY_DETACHED = "com.north3221.aagateway.ACTION_USB_ACCESSORY_DETACHED",
            ACTION_RESET_AASERVICE = "com.north3221.aagateway.ACTION_RESET_AA_SERVICE",
            ACTION_CHECK_SERVICE_REMINDER = "com.north3221.aagateway.ACTION_RESET_AA_SERVICE_REMINDER",
            SHARED_PREF_KEY_WIFI_CONTROL = "WIFI_CONTROL",
            SHARED_PREF_KEY_USB_CONTROL_TYPE = "USB_CONTROL";
    private static final String
            SERVICE_REMINDER_EXTRA = "CALL_COUNT";
    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action == null){
            return;
        }
        logger = new AAlogger(context);
        String device = "Power";

        switch (action){
            case ConnectivityManager.CONNECTIVITY_ACTION:
                wifiConnectivityAction(context, intent);
                break;
            case ACTION_USB_ACCESSORY_ATTACHED:
                usbDeviceAttachedAction(context);
                break;
            case ACTION_USB_ACCESSORY_DETACHED:
                device = "Android Auto Device";
            case Intent.ACTION_POWER_DISCONNECTED:
                usbDeviceDetachedAction(context, device);
                break;
            case Intent.ACTION_POWER_CONNECTED:
                logger.log("USB Power Connected", "usbconnection");
                setWifi(context, true);
                break;
            case ACTION_RESET_AASERVICE:
                resetAAService(context);
                break;
            case ACTION_CHECK_SERVICE_REMINDER:
                checkServiceReminderAction(context,intent);
                break;
            default:
                Toast.makeText(context,"Received unknown action: " + action,Toast.LENGTH_SHORT).show();
        }

    }

    private void wifiConnectivityAction(Context context, Intent intent){
        NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
            if (ni.isConnected()) {
                logger.log("Wifi Connected", "wificonnection");
                if (isUsbAttached(context)) {
                    requestServiceState(context, true, "wifi");
                } else {
                    logger.log("Waiting on USB", "aaservice");
                }
            } else {
                logger.log("Wifi Disconnected", "wificonnection");
                requestServiceState(context, false, "wifi");
            }
        }

    }

    private String getGWIP (Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp = wm.getDhcpInfo();
            if (dhcp.gateway != 0) {
                return Formatter.formatIpAddress(dhcp.gateway);
            }
        } catch (Exception ignored) {
            return "ERROR";
        }
        return "NO IP";
    }

    public void usbDeviceAttachedAction(Context context){
        logger.log("USB Android Auto Device connected","usbconnection");
        if (isWifiConnected(context)) {
            requestServiceState(context, true, "usb");
        } else {
            if (!HackerService.running)
                logger.log("Waiting on WiFi", "aaservice");
        }
    }

    private boolean isUsbAttached(Context context){
            return getUsbAccessory(context) != null;
    }

    private UsbAccessory getUsbAccessory (Context context){
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager != null) {
            UsbAccessory[] accessoryList = manager.getAccessoryList();
            if (accessoryList != null && accessoryList.length > 0 ) {
                for (UsbAccessory usbAccessory : accessoryList) {
                    if (usbAccessory.getManufacturer().equalsIgnoreCase("Android")) {
                        return usbAccessory;
                    }
                }
            }
        }
        return null;
    }

    private void usbDeviceDetachedAction(Context context, String device) {
        logger.log("USB "+ device + " Disconnected", "usbconnection");
        requestServiceState(context, false, "usb");
        setWifi(context, false);

    }

    private void setWifi(final Context context, boolean tostate){
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.getBoolean(SHARED_PREF_KEY_WIFI_CONTROL,true)){
            return;
        }

        if (tostate) {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                if (wifi.isWifiEnabled()) {
                    wifi.startScan();
                }
                wifi.setWifiEnabled(tostate);
                logger.log("WiFi Turned On", "wificonnection");
                wifi.startScan();
            }
        } else {
            delayedWiFiOff(context);
        }
    }

    private void delayedWiFiOff(final Context context){
        new CountDownTimer(5000, 1000) {
            public void onTick(long millisUntilFinished) {
                if (isPowerConnected(context)) {
                    logger.log("Power connected during wifi off timer", "log");
                    cancel();
                }
            }
            public void onFinish() {
                WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    wifi.setWifiEnabled(false);
                    logger.log("WiFi Turned Off", "wificonnection");
                }
            }

        }.start();
    }

    private void requestServiceState(Context context,boolean reqState, String type){
        UsbAccessory usb = getUsbAccessory(context);
        String gatewayIP = getGWIP(context);
        logger.log("Requested service: " + reqState + " - Current state: " + HackerService.running, "log");
        logger.log("Gateway IP: " + gatewayIP + " - USB attached : " + isUsbAttached(context),"log");
        if (reqState) {
            if (!(gatewayIP.length() > 5)) {
                if (!HackerService.running)
                    logger.log("Cant start Service (no gw ip)", "aaservice");
                checkServiceReminder(context, 0);
                return;
            }
            if (usb == null){
                if (!HackerService.running)
                    logger.log("Cant start Service (no usb)", "aaservice");
                checkServiceReminder(context, 0);
                return;
            }
        }

        if (reqState != HackerService.running){
            if (type == "wifi") {
                toggleUSB(context);
            } else {
                updateServiceState(context, reqState, gatewayIP, usb);
            }
        }
    }

    private void updateServiceState(Context context, boolean reqState, String gatewayIP, UsbAccessory usbAccessory){
        Intent hsIntent = new Intent(context, HackerService.class);
        if (reqState) {
            logger.log("Starting Service", "log");
            hsIntent.putExtra("gwip", gatewayIP);
            hsIntent.putExtra("accessory", usbAccessory);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(hsIntent);
            } else {
                context.startService(hsIntent);
            }
            checkServiceReminder(context,0);
        } else {
            logger.log("Stopping Service", "log");
            context.stopService(hsIntent);
        }

    }

    private boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = null;
            if (manager != null) {
                networkInfo = manager.getActiveNetworkInfo();
            }
            return networkInfo != null
                    && networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                    && networkInfo.isConnected();
        } catch (Exception e) {
            Log.e("WIFI CONNECTION", "Error checking WiFi := " + e);
            return false;
        }
    }

    private void toggleUSB(Context context){
        logger.log("Toggling USB","log");
        // NB Root is required!
        // NBB Doesnt work on all devices....
        String[] cmdOne = {"su", "-c", "svc", "usb", "setFunctions"};
        String[] cmdTwo = {"su", "-c", "svc", "usb", "setFunctions", "mtp", "true"};

        // Alternate method
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (sp.getBoolean(SHARED_PREF_KEY_USB_CONTROL_TYPE,false)){
            cmdOne = new String[]{"su", "-c", "settings", "put", "global", "adb_enabled", "0"};
            cmdTwo = new String[]{"su", "-c", "settings", "put", "global", "adb_enabled", "1"};
        }

        try {
            Process p = Runtime.getRuntime().exec(cmdOne);
            p.waitFor();
            p = Runtime.getRuntime().exec(cmdTwo);
            p.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Log.e("USB CONN","ERROR", e);
        }
    }

    private void resetAAService(Context context){
        logger.log("Reset Service Requested","aaservice");
        if (HackerService.running)
            logger.log("Reset Service Stopping Service","aaservice");
            updateServiceState(context,false, "", null);
        if ((isUsbAttached(context) || isPowerConnected(context)) && isWifiConnected(context))
            toggleUSB(context);
    }

    private boolean isPowerConnected(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private void checkServiceReminderAction(Context context, Intent intent){
        int count = intent.getIntExtra(SERVICE_REMINDER_EXTRA,0);
        if (isWifiConnected(context) && isUsbAttached(context) && !HackerService.running && count < 10){
            toggleUSB(context);
            checkServiceReminder(context,count);
        }

    }
    private void checkServiceReminder(Context context, int count) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            AlarmManager alarmManager = (AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent();
            intent.setAction(ACTION_CHECK_SERVICE_REMINDER);
            intent.putExtra(SERVICE_REMINDER_EXTRA, count + 1);
            PendingIntent piServiceReminder = PendingIntent.getBroadcast(context, 0, intent, 0);
            if (alarmManager != null) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30000, piServiceReminder);
            }
        }
    }

}
