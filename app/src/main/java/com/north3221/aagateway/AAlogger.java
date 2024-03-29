package com.north3221.aagateway;

import static com.north3221.aagateway.MainActivity.SHARED_PREF_NAME;
import static com.north3221.aagateway.MainActivity.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;

class AAlogger {
    public static final String
            SHARED_PREF_KEY_LOG = "LOG",
            LOGFILE = "sdcard/aagatewaylog.txt";
    private final Context loggerContext;

    public AAlogger(Context context){
        loggerContext = context;
    }

    public void log(String message, String tvName){
        switch (loggingLevel()){
            case 0:
                break;
            case 3:
                log(message);
            case 2:
                if (!tvName.equals("log")){
                    updateTextView(message, "log");
                }
            case 1:
                if (tvName.equals("log") && loggingLevel() == 1){
                    break;
                }
                updateTextView(message,tvName);
                break;
        }

    }

    public void log(String message){
        if (loggingLevel() > 2 && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            appendLog(message);
    }

    private void updateTextView(String message, String tvName){

        int id = loggerContext.getResources().getIdentifier(tvName, "id", loggerContext.getPackageName());
        Log.d(TAG, "Updating Shared Preferences for tvName:= " + tvName + " ID:= " + id);
        SharedPreferences sp = loggerContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (tvName.equals("log")) {
            String timeStamp = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            message = timeStamp + ":" + message + "\n" + sp.getString(String.valueOf(id), "");
        }
        if (message.length() > 5120){
            message = message.substring(0, 5120);
        }
        SharedPreferences.Editor spEditor = sp.edit();
        spEditor.putString(String.valueOf(id), message);
        spEditor.apply();
    }


    private int loggingLevel(){
        SharedPreferences sharedpreferences = loggerContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedpreferences.getInt(SHARED_PREF_KEY_LOG,0);

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void appendLog(String text)
    {
        File logFile = new File(LOGFILE);
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
        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH:mm:ss").format(new java.util.Date());
        try (BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true))) {
            buf.append(timeStamp).append(": ").append(text);
            buf.newLine();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void loggingLevelChanged(){
        switch (loggingLevel()){
            case 0:
                removeSharedPref("usbconnection");
                removeSharedPref("wificonnection");
                removeSharedPref("aaservice");
            case 1:
                removeSharedPref("log");
            case 2:
                deleteLogfile();
        }
    }

    private void deleteLogfile(){
        Log.d(TAG, "Deleting Log File");
        File logFile = new File(LOGFILE);
        logFile.delete();

    }

    private void removeSharedPref(String name){
        String id = String.valueOf(loggerContext.getResources().getIdentifier(name, "id", loggerContext.getPackageName()));
        Log.d(TAG, "Removing Shared Preferences for tvName:= " + name);
        SharedPreferences sp = loggerContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor spEditor = sp.edit();
        spEditor.putString(id,"");
        spEditor.apply();
        spEditor.remove(id);
        spEditor.apply();

    }

}
