package com.north3221.aagateway;


import static com.north3221.aagateway.AAlogger.SHARED_PREF_KEY_LOG;
import static com.north3221.aagateway.ConnectionStateReceiver.ACTION_USB_ACCESSORY_ATTACHED;
import static com.north3221.aagateway.ConnectionStateReceiver.ACTION_USB_ACCESSORY_DETACHED;
import static com.north3221.aagateway.ConnectionStateReceiver.SHARED_PREF_KEY_USB_CONTROL_TYPE;
import static com.north3221.aagateway.ConnectionStateReceiver.SHARED_PREF_KEY_WIFI_CONTROL;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {

    public static final String
            TAG = "AAGateWay",
            SHARED_PREF_NAME = TAG,
            SHARED_PREF_KEY_ROOT = "HAS_ROOT";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 0;

    private static SharedPreferences sharedpreferences;
    private AAlogger aalogger;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        aalogger = new AAlogger(this);

        sharedpreferences = getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        checkBatteryOptimised();
        checkRoot();
        checkExternalStorage();

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
        updateAllTextViews();
        sharedpreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sharedpreferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    @Override
    protected void onNewIntent(Intent paramIntent) {
        Log.i(TAG, "Got new intent: " + paramIntent);
        Intent usbIntent;
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(paramIntent.getAction()) && paramIntent.hasExtra(UsbManager.EXTRA_ACCESSORY)){
            usbIntent = new Intent(ACTION_USB_ACCESSORY_ATTACHED);
            ComponentName componentName = new ComponentName(this,ConnectionStateReceiver.class);
            usbIntent.setComponent(componentName);
            usbIntent.putExtra(UsbManager.EXTRA_ACCESSORY, paramIntent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY));
        } else {
         usbIntent = new Intent(ACTION_USB_ACCESSORY_DETACHED);
        }
        sendBroadcast(usbIntent);
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
                Log.d(TAG,"Logging level changed:= "+i);

                aalogger.loggingLevelChanged();

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

    private void checkBatteryOptimised(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                findViewById(R.id.requestGroup).setVisibility(View.VISIBLE);
                final Button batteryButton = findViewById(R.id.requestBattery);
                batteryButton.setVisibility(View.VISIBLE);
                batteryButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        batteryButton.setVisibility(View.INVISIBLE);
                    }
                });
            }
        }
    }

    private void checkRoot(){
        if (sharedpreferences.getBoolean(SHARED_PREF_KEY_ROOT,false))
            return;

        findViewById(R.id.requestGroup).setVisibility(View.VISIBLE);
        final Button rootButton = findViewById(R.id.requestRoot);
        rootButton.setVisibility(View.VISIBLE);
        rootButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestRoot();
                finish();
                startActivity(getIntent());
            }
        });
    }

    private void requestRoot(){
        try {
            String [] cmdTestSU = new String[]{"su", "-c", "ls", "/"};
            Process p = Runtime.getRuntime().exec(cmdTestSU);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));

            // Grab the results
            StringBuilder resRoot = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                resRoot.append(line).append("\n");
            }
            if (resRoot.length()>0) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putBoolean(SHARED_PREF_KEY_ROOT, true);
                editor.apply();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"Error getting root:", e);
        }
    }

    private void checkExternalStorage(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            findViewById(R.id.requestGroup).setVisibility(View.VISIBLE);
            final Button storageButton = findViewById(R.id.requestStorage);
            storageButton.setVisibility(View.VISIBLE);
            storageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    requestStorage();
                }
            });
        }
    }

    private void requestStorage(){
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                finish();
                startActivity(getIntent());
            } else {
                Toast.makeText(this,"Unknown permission:= " + Arrays.toString(grantResults),Toast.LENGTH_SHORT).show();
                aalogger.log("Unknown permission:= " + Arrays.toString(grantResults));
            }

        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            try{
                int id = Integer.parseInt(key);
                Log.d(TAG, "updating text view id:= " + id);
                if (findViewById(id) instanceof TextView) {
                    updateTextView((TextView) findViewById(id));
                }
            } catch (NumberFormatException e) {
                Log.d(TAG, "None Automated Shared Preferences update Key:= " + key);
            }

        }
    };


    private void updateTextView(TextView tv){
        tv.setText(sharedpreferences.getString(String.valueOf(tv.getId()), (String) tv.getText()));
    }

    private void updateAllTextViews(){
        LinearLayout maLayout = findViewById(R.id.main_activity);
        for (int i = 0; i < maLayout.getChildCount(); ++i){
            View v = maLayout.getChildAt(i);
            if (v instanceof ViewGroup){
                ViewGroup vg = (ViewGroup) v;
                for (int j = 0;j < vg.getChildCount(); j++){
                    View cv = vg.getChildAt(j);
                    if (cv instanceof AppCompatTextView){
                        updateTextView((TextView) cv);
                    }
                }
            } else {
                if (v instanceof AppCompatTextView) {
                    updateTextView((TextView) v);
                }
            }
        }

    }

}
