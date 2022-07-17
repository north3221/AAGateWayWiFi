package com.north3221.aagateway;

import static com.north3221.aagateway.MainActivity.MESSAGE_EXTRA;
import static com.north3221.aagateway.MainActivity.MESSAGE_TV_NAME;
import static com.north3221.aagateway.MainActivity.SHARED_PREF_NAME;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;

class AAlogger {
    public static final String SHARED_PREF_KEY_LOG = "LOG";
    private Context loggerContext;

    public AAlogger(Context context){
        loggerContext = context;
    }

    public void log(String message, String tvName){
        log(message);
        sendBroadcast(message, tvName);
    }

    public void log(String message){
        if (loggingLevel() > 2 && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            appendLog(message);
    }

    private void sendBroadcast(String message, String tvName){
        try {
            Intent tvIntent = new Intent(MainActivity.MESSAGE_INTENT_BROADCAST);
            tvIntent.putExtra(MESSAGE_TV_NAME, tvName);
            tvIntent.putExtra(MESSAGE_EXTRA, message);
            LocalBroadcastManager.getInstance(loggerContext.getApplicationContext()).sendBroadcast(tvIntent);
        } catch (Exception ignored){}

    }


    private int loggingLevel(){
        SharedPreferences sharedpreferences = loggerContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedpreferences.getInt(SHARED_PREF_KEY_LOG,0);

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void appendLog(String text)
    {
        File logFile = new File("sdcard/aagatewaylog.txt");
        if (logFile.exists() && logFile.length() > 50000){
            logFile.delete();
        }
        if (!logFile.exists())
        {
            try {
                logFile.createNewFile();
            }
            catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
        try (BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true))) {
            buf.append(timeStamp).append(": ").append(text);
            buf.newLine();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
