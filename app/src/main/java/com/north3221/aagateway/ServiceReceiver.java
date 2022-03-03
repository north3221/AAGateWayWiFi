package com.north3221.aagateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class ServiceReceiver extends BroadcastReceiver {
    private static final String TAG = "AAGateWay";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Service Request");
        Intent i = new Intent(context, HackerService.class);

        if (intent.getAction() != null && intent.getAction().equalsIgnoreCase("com.north3221.aagateway.service.START")) {
            Log.d(TAG, "Service Start Requested");
            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessoryList = manager.getAccessoryList();
            if (accessoryList != null) {
                UsbAccessory accessory = accessoryList[0];
                i.putExtra("accessory", accessory);
                Log.d(TAG, "Got accessory:=" + accessory.getModel());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i);
                }else{
                    context.startService(i);
                }
            }else{
                Log.d(TAG, "No accessory Found");
                Toast.makeText(context, "No USB accessory found", Toast.LENGTH_SHORT).show();
            }

        }

    }





}
