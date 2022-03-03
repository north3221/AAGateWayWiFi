package com.north3221.aagateway;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ToggleButton;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AAGateWay";
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getIntent().getAction()!=null && getIntent().getAction().equalsIgnoreCase("android.intent.action.MAIN")) {
            
            Button button = findViewById(R.id.exitButton);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
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
            stopService(i);
            finish();
        } else if (paramIntent.getAction() != null && paramIntent.getAction().equalsIgnoreCase("android.hardware.usb.action.USB_ACCESSORY_ATTACHED")) {
            Log.d(TAG, "USB CONNECTED");

            if (paramIntent.getParcelableExtra("accessory") != null) {
                i.putExtra("accessory", paramIntent.getParcelableExtra("accessory"));
                ContextCompat.startForegroundService(this,i);
            }
            finish();
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
