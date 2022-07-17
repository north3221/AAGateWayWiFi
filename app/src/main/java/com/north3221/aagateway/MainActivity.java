package com.north3221.aagateway;


import static com.north3221.aagateway.AAlogger.SHARED_PREF_KEY_LOG;
import static com.north3221.aagateway.ConnectionStateReceiver.ACTION_USB_ACCESSORY_ATTACHED;
import static com.north3221.aagateway.ConnectionStateReceiver.ACTION_USB_ACCESSORY_DETACHED;
import static com.north3221.aagateway.ConnectionStateReceiver.SHARED_PREF_KEY_USB_CONTROL_TYPE;
import static com.north3221.aagateway.ConnectionStateReceiver.SHARED_PREF_KEY_WIFI_CONTROL;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    public static final String
            TAG = "AAGateWay",
            SHARED_PREF_NAME = TAG;

    public static final String
            MESSAGE_INTENT_BROADCAST = "TEXTVIEW_MESSAGE_BROADCAST",
            MESSAGE_EXTRA = "MESSAGE",
            MESSAGE_TV_NAME = "TEXTVIEWNAME";

    private static SharedPreferences sharedpreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedpreferences = getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        checkBatterOptimised();

        Button button = findViewById(R.id.exitButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        createLoggingSpinner();
        createWiFiSwitch();
        createUsbSwitch();

    }

    @Override
        protected void onResume() {
        super.onResume();
        registerReceivers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        //lbm.unregisterReceiver(brConnStateRecv);
        lbm.unregisterReceiver(brlogging);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    protected void onNewIntent(Intent paramIntent) {
        Log.i(TAG, "Got new intent: " + paramIntent);
        Intent usbIntent;
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(paramIntent.getAction()) && paramIntent.getParcelableExtra("accessory") != null){
            usbIntent = new Intent(ACTION_USB_ACCESSORY_ATTACHED);
            usbIntent.putExtra("accessory",paramIntent.getParcelableExtra("accessory"));
        } else {
         usbIntent = new Intent(ACTION_USB_ACCESSORY_DETACHED);
        }
        sendBroadcast(usbIntent);
    }

    private void registerReceivers(){
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(brlogging, new IntentFilter(MESSAGE_INTENT_BROADCAST));

    }

    private void updateLoggingInfo(Intent intent){
        String message = intent.getStringExtra(MESSAGE_EXTRA);
        String tvid = intent.getStringExtra(MESSAGE_TV_NAME);
        Log.d(TAG, "Message: " + message);
        Log.d(TAG, "TextView Id: " + tvid);

        int id = getResources().getIdentifier(tvid, "id", getPackageName());
        TextView tv = (TextView) findViewById(id);
        Log.d(TAG, "ID: " + id);
        if (tvid.equals("log")) {
            tv.setText(tv.getTag() + ": " + message + "\n" + tv.getText());
        } else {
            tv.setText(tv.getTag() + ": " + message);
        }
    }

    private void createLoggingSpinner(){
        final Spinner spinnerLogging = findViewById(R.id.logging);
        ArrayAdapter<CharSequence> adapter=ArrayAdapter.createFromResource(this, R.array.logging, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        spinnerLogging.setAdapter(adapter);

        spinnerLogging.setSelection(sharedpreferences.getInt(SHARED_PREF_KEY_LOG,0));

        spinnerLogging.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putInt(SHARED_PREF_KEY_LOG, spinnerLogging.getSelectedItemPosition());
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void createWiFiSwitch(){
        final Switch swWiFiCtrl = findViewById(R.id.wificontrol);
        swWiFiCtrl.setChecked(sharedpreferences.getBoolean(SHARED_PREF_KEY_WIFI_CONTROL,true));
        swWiFiCtrl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(SHARED_PREF_KEY_WIFI_CONTROL,b);
                editor.apply();
            }
        });

    }

    private void createUsbSwitch(){
        final Switch swUsbCtrl = findViewById(R.id.alternateusbtoggle);
        swUsbCtrl.setChecked(sharedpreferences.getBoolean(SHARED_PREF_KEY_USB_CONTROL_TYPE,false));
        swUsbCtrl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){

          @Override
          public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
              SharedPreferences.Editor editor = sharedpreferences.edit();
              editor.putBoolean(SHARED_PREF_KEY_USB_CONTROL_TYPE,b);
              editor.apply();
          }
        });

    }



    private final BroadcastReceiver brlogging = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLoggingInfo(intent);
        }
    };

    private void checkBatterOptimised(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }

}
