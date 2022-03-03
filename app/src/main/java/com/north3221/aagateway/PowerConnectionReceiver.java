package com.north3221.aagateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PowerConnectionReceiver extends BroadcastReceiver {
    private static final String TAG = "AAGateWay";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "power disconnected");
        Intent nopowerintent = new Intent(context, HackerService.class);
        context.stopService(nopowerintent);
    }
}
