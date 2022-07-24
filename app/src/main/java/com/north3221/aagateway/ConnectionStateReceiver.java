package com.north3221.aagateway;

import static com.north3221.aagateway.MainActivity.SHARED_PREF_NAME;
import static com.north3221.aagateway.MainActivity.TAG;

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
import android.os.PowerManager;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;

public class ConnectionStateReceiver extends BroadcastReceiver {
    private AAlogger logger;
    private static CountDownTimer powerCountDown;
    private static PowerManager.WakeLock wakeLock;
    private static Boolean wifiConnected;
    private static UsbAccessory usbAccessory;

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
                usbDeviceAttachedAction(context, intent);
                break;
            case ACTION_USB_ACCESSORY_DETACHED:
                device = "Android Auto Device";
            case Intent.ACTION_POWER_DISCONNECTED:
                usbDeviceDetachedAction(context, device);
                break;
            case Intent.ACTION_POWER_CONNECTED:
                logger.log("USB Power Connected", "usbconnection");
                setWakeLock(context,true);
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
        NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
            if (ni.isConnected()) {
                logger.log("Wifi Connected", "wificonnection");
                wifiConnected = true;
                if (isUsbAttached(context)) {
                    requestServiceState(context, true, "wifi");
                } else {
                    logger.log("Waiting on USB", "aaservice");
                }
            } else {
                logger.log("Wifi Disconnected", "wificonnection");
                wifiConnected = false;
                requestServiceState(context, false, "wifi");
            }
        }

    }

    private String getGatewayIP (Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp;
            if (wm != null) {
                dhcp = wm.getDhcpInfo();
                if (dhcp.gateway != 0) {
                    return Formatter.formatIpAddress(dhcp.gateway);
                }
            }
        } catch (Exception ignored) {
            return "ERROR";
        }
        return "NO IP";
    }

    public void usbDeviceAttachedAction(Context context, Intent usbIntent){
        logger.log("USB Android Auto Device connected","usbconnection");
        if (usbIntent.hasExtra(UsbManager.EXTRA_ACCESSORY))
            usbAccessory = usbIntent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
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
        if (usbAccessory != null)
            return usbAccessory;

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
        logger.log("USB " + device + " Disconnected", "usbconnection");
        usbAccessory = null;

        if (powerCountDown != null){
            powerCountDown.cancel();
        }
        powerCountDown = new CountDownTimer(3000, 1000) {
            public void onTick(long millisUntilFinished) {
                if (isPowerConnected(context)) {
                    logger.log("Power connected during power off timer", "log");
                    logger.log("USB " + device + " Disconnected - Stopped", "usbconnection");
                    cancel();
                }
            }
            public void onFinish() {
                requestServiceState(context, false, "usb");
                setWifi(context, false);
                setWakeLock(context,false);
                logger.log("Power disconnected -  timer Finished", "usbconnection");
            }

        }.start();

    }

    private void setWifi(final Context context, boolean tostate){
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.getBoolean(SHARED_PREF_KEY_WIFI_CONTROL,true)){
            return;
        }
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            if (tostate != wifi.isWifiEnabled()){
                wifi.setWifiEnabled(tostate);
                logger.log("WiFi Set to " + tostate, "wificonnection");
            }
        }
        
    }

    private void setWakeLock (Context context,boolean wake){
        String WLTAG = TAG + ":WakeLock";

        if (wake) {
            //private static CountDownTimer wakeCountDown;
            PowerManager powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, WLTAG);
                // 5 Mins timeout
                wakeLock.acquire(5*60*1000L);
            }

        } else{
            if (wakeLock != null) {
                wakeLock.release();
                logger.log("Released wakelock", "");
                Log.d(WLTAG,"Released Wakelock");
            }

        }

    }

    private void requestServiceState(Context context,boolean reqState, String type){
        UsbAccessory usb = getUsbAccessory(context);
        String gatewayIP = getGatewayIP(context);
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
            if (type.equals("wifi")) {
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
        if (wifiConnected != null)
            return wifiConnected;
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager != null) {
                NetworkInfo networkInfo = manager.getActiveNetworkInfo();
                logger.log("isWiFiConnected got Connectivity Manager");
                wifiConnected = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) && networkInfo.isConnected();
            } else {
                logger.log("isWiFiConnected failed to get Connectivity Manager");
            }
        } catch (Exception e) {
            Log.e("WIFI CONNECTION", "Error checking WiFi := " + e);
        }
        return wifiConnected != null && wifiConnected;
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
            logger.log("Toggling USB - Alternative","log");
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
        checkServiceReminder(context,0);
    }

    /*
    // Keep this here in case anyone has issues waking device on power
    private void wakeDevice(final Context context){
        logger.log("Waking Device","log");

        wakeCountDown = new CountDownTimer(30000, 5000) {
            public void onTick(long millisUntilFinished) {
                // NB Root is required!
                if (isWifiConnected(context) || HackerService.running)
                    cancel();
                String[] cmdWake = {"su", "-c", "input", "keyevent", "KEYCODE_WAKEUP"};
                try {
                    Process p = Runtime.getRuntime().exec(cmdWake);
                    p.waitFor();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Log.e("WAKE DEVICE","ERROR", e);
                }
            }
            public void onFinish() {
                checkServiceReminder(context,0);
            }

        }.start();

    }

     */

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
            PendingIntent piServiceReminder;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                piServiceReminder = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                piServiceReminder = PendingIntent.getBroadcast(context, 0, intent, 0);
            }
            if (alarmManager != null) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30000, piServiceReminder);
            }
        }
    }

}
