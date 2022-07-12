package com.north3221.aagateway;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AAGateWay";
    public static final String
            MESSAGE_INTENT_BROADCAST = "TEXTVIEW_MESSAGE_BROADCAST",
            MESSAGE_EXTRA = "MESSAGE",
            MESSAGE_TVID = "TEXTVIEWID";
    private ConnectionState connState;
    private Boolean logging = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connState = new ConnectionState(this);
        connState.registerWifiReceiver();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String message = intent.getStringExtra(MESSAGE_EXTRA);
                        String tvid = intent.getStringExtra(MESSAGE_TVID);
                        Log.d(TAG, "Message: " + message);
                        Log.d(TAG, "TextView Id: " + tvid);

                        int id = getResources().getIdentifier(tvid,"id",getPackageName());
                        TextView tv = (TextView) findViewById(id);
                        Log.d(TAG, "ID: " + id);
                        if (tvid.equals("log")) {
                            if (logging)
                                tv.setText(message + "\n" + tv.getText());
                        } else {
                            tv.setText(tv.getTag() + ": " + message);
                        }
                    }
                }, new IntentFilter(MESSAGE_INTENT_BROADCAST)
        );

        if (getIntent().getAction()!=null && getIntent().getAction().equalsIgnoreCase("android.intent.action.MAIN")) {
            Button button = findViewById(R.id.exitButton);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    connState.unregisterWifiReceiver();
                    finish();
                }
            });
        }


    }

    @Override
        protected void onResume() {
        super.onResume();

        Intent paramIntent = getIntent();
        Intent i = new Intent(this, HackerService.class);

        if (paramIntent.getAction() != null && paramIntent.getAction().equalsIgnoreCase("android.hardware.usb.action.USB_ACCESSORY_DETACHED")) {
            Log.d(TAG, "USB DISCONNECTED");
            connState.setUsbAccessoryConnectedState(false);
            //stopService(i);
            connState.unregisterWifiReceiver();
            connState.setServiceState();
            //finish();
        } else if (paramIntent.getAction() != null && paramIntent.getAction().equalsIgnoreCase("android.hardware.usb.action.USB_ACCESSORY_ATTACHED")) {
            connState.registerWifiReceiver();
            Log.d(TAG, "USB CONNECTED");

            if (paramIntent.getParcelableExtra("accessory") != null) {
                ConnectivityManager connMgr = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = null;
                if (connMgr != null) {
                    activeNetworkInfo = connMgr.getActiveNetworkInfo();
                    if (activeNetworkInfo != null) {
                        if (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                            //i.putExtra("accessory", paramIntent.getParcelableExtra("accessory"));
                            //ContextCompat.startForegroundService(this, i);
                            connState.setUsbAccessoryConnectedState(true);
                            connState.setServiceState();
                        }
                    }
                }
            }
            //finish();
        }
    }

    @Override
    protected void onNewIntent(Intent paramIntent)
    {
        Log.i(TAG, "Got new intent: " + paramIntent);
        super.onNewIntent(paramIntent);
        setIntent(paramIntent);
    }

}
