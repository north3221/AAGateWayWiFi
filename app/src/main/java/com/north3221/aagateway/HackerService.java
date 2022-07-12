package com.north3221.aagateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;



import static android.app.NotificationManager.IMPORTANCE_HIGH;

/**
 * Created by Emil on 25/03/2018.
 */

public class HackerService extends Service {
    private static final String TAG = "AAGateWay";
    private NotificationManager mNotificationManager;
    private Intent notificationIntent;
    private final IBinder mBinder = new LocalBinder();
    private UsbAccessory mAccessory;
    private UsbManager mUsbManager;
    private String gatewayIP;
    private ParcelFileDescriptor mFileDescriptor;
    private FileDescriptor fd;
    private FileOutputStream phoneOutputStream;
    private FileInputStream phoneInputStream;

    private static OutputStream socketoutput;
    private static DataInputStream socketinput;
    private static Socket socket;
    public static boolean running=false;
    private boolean localCompleted,usbCompleted;
    private boolean listening;
    private boolean ignoreipv6;
    byte [] readbuffer=new byte[16384];
    private Thread tcpreader;
    private Thread usbreader;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    public class LocalBinder extends Binder {
        HackerService getService() {
            return HackerService.this;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onCreate() {
        super.onCreate();

        String CHANNEL_ONE_ID = "uk.co.borconi.emil.aagateway";
        String CHANNEL_ONE_NAME = "Channel One";
        NotificationChannel notificationChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
        }


        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder mynotification = new Notification.Builder(this)
                .setContentTitle("Android Auto GateWay")
                .setContentText("Running....")
                .setSmallIcon(R.drawable.aawifi)
                .setTicker("");
        if (Build.VERSION.SDK_INT>=26)
            mynotification.setChannelId(CHANNEL_ONE_ID);

        startForeground(1, mynotification.build());

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (running) {
            Log.d(TAG,"Service already running");
            sendBroadcastMessage("Already Running");
            return START_STICKY;
        }
        Log.d(TAG,"Service Started");
        sendBroadcastMessage("Started");
        super.onStartCommand(intent, flags, startId);
        mAccessory = (UsbAccessory) intent.getParcelableExtra("accessory");
        gatewayIP = intent.getStringExtra("gwip");
        sendBroadcastMessage("USB = Connected IP = " + gatewayIP,"usbconnection");
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mFileDescriptor = mUsbManager.openAccessory(mAccessory);
        if (mFileDescriptor != null) {
            fd = mFileDescriptor.getFileDescriptor();
            phoneInputStream = new FileInputStream(fd);
            phoneOutputStream = new FileOutputStream(fd);
            usbCompleted=false;
        } else {
            Log.e(TAG, "Cannot open usb accessory "+mAccessory.toString());
            stopSelf();
            return START_STICKY;
        }

        //Manually start AA.
        sendBroadcastMessage("Running");
        running=true;
        localCompleted = false;
        usbCompleted = false;
        usbreader = new Thread(new usbpollthread());
        tcpreader = new Thread(new tcppollthread());
        usbreader.start();
        tcpreader.start();

        return START_STICKY;
    }

    class tcppollthread implements Runnable {
        //private ServerSocket serversocket=null;

        public void run() {
            Log.d(TAG,"tcp - run");
            sendBroadcastMessage("TCP Run - running = " + running, "log");

            //connect or accept connection from the phone
            try {
                sendBroadcastMessage("Connecting to phone: " + gatewayIP);

                InetAddress addr = InetAddress.getByName(gatewayIP);

                if (addr.isReachable(300)) {
                    Log.d(TAG, "tcp - reachable " + gatewayIP);
                } else {
                    Log.d(TAG, "tcp - not reachable " + gatewayIP);
                    sendBroadcastMessage("Phone not reachable");
                    sendBroadcastMessage("Unable to reach phone on " + addr, "log");
                    running = false;
                    stopSelf();
                }

                Log.d(TAG, "tcp - connecting to phone" );
                socket = new Socket();
                socket.setSoTimeout(5000);
                socket.connect(new InetSocketAddress(gatewayIP, 5277), 500);
                Log.d(TAG, "tcp - connected");
                sendBroadcastMessage("Connected to phone and running = " + running, "log");
                //}

                //at this point running could be false in non listening mode and no address found
                if (running) {
                    sendBroadcastMessage("Getting tcp from phone", "log");
                    socketoutput = socket.getOutputStream();
                    socketinput = new DataInputStream(socket.getInputStream());
                    socketoutput.write(new byte[]{0, 3, 0, 6, 0, 1, 0, 1, 0, 2});
                    socketoutput.flush();
                    byte[] recv = new byte[12];
                    socketinput.read(recv);
                    Log.d(TAG, "tcp - recv from phone " + bytesToHex(recv));
                    localCompleted = true;
                    sendBroadcastMessage("tcp from phone " + bytesToHex(recv), "log");
                }
            } catch (Exception e) {
                Log.e(TAG, "tcp - error opening phone " + e.getMessage());
                sendBroadcastMessage("error opening phone " + e.getMessage(), "log");
                running = false;
                stopSelf();
            }

            //wait for usb initialization
            if (!usbCompleted && running)
                Log.d(TAG, "tcp - waiting for usb");
            while (!usbCompleted && running) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.e(TAG, "tcp - error sleeping "+e.getMessage());
                    sendBroadcastMessage("TCP error sleeping = " + e.getMessage(), "log");
                }
            }

            //Looper.prepare();
            while (running)
            {
                try {
                    getLocalmessage(false);

                } catch (Exception e) {
                    Log.e(TAG,"tcp - in main loop "+e.getMessage());
                    sendBroadcastMessage("TCP error in main loop = " + e.getMessage(), "log");
                    running = false;
                    stopSelf();
                }
            }

            Log.d(TAG,"tcp - end");
            sendBroadcastMessage("tcp end");
            stopSelf();
        }

    }

    class usbpollthread implements Runnable {


        public void run() {

            Log.d(TAG,"usb - run");

            byte buf [] = new byte[16384];
            int x;

            try {
                x=phoneInputStream.read(buf);
                Log.d(TAG, "usb -received from usb "+bytesToHex((Arrays.copyOf(buf, x))));
                sendBroadcastMessage("received from usb");
                phoneOutputStream.write(new byte[]{0, 3, 0, 8, 0, 2, 0, 1, 0, 4, 0, 0});
                //tcpreader.join();
                usbCompleted = true;
            } catch (Exception e) {
                Log.e(TAG, "usb - error init "+e.getMessage());
                running = false;
                stopSelf();
            }

            if (!localCompleted && running)
                Log.d(TAG, "usb - waiting for local");
                sendBroadcastMessage("usb waiting for local");
            while (!localCompleted && running) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "usb - error sleeping "+e.getMessage());
                }
            }

            while (running)
            {
                try {
                    x = phoneInputStream.read(buf);
                    processCarMessage(Arrays.copyOf(buf, x));
                }
                catch (Exception e)
                {
                    Log.e(TAG,"usb - in main loop " + e.getMessage());
                    running = false;
                    stopSelf();
                }

            }
            if (mFileDescriptor!=null) {
                try {
                    mFileDescriptor.close();
                } catch (IOException e) {
                    Log.d(TAG, "error closing usb " + e.getMessage());
                    sendBroadcastMessage("error closing usb");
                }
            }
            Log.d(TAG,"usb - end");
            stopSelf();
        }
    };

    private void getLocalmessage(boolean canBeEmpty) throws IOException {


        int enc_len;
        socketinput.readFully(readbuffer,0,4);
        int pos=4;
        enc_len = (readbuffer[2] & 0xFF) << 8 | (readbuffer[3] & 0xFF);
        if ((int) readbuffer[1] == 9)   //Flag 9 means the header is 8 bytes long (read it in a separate byte array)
        {
            pos+=4;
            socketinput.readFully(readbuffer,4,4);
        }

        socketinput.readFully(readbuffer,pos,enc_len);
        phoneOutputStream.write(Arrays.copyOf(readbuffer,enc_len+pos));


    }

    private void processCarMessage(final byte[] buf) throws IOException {
       socketoutput.write(buf);
    }

    @Override
    public void onDestroy() {
        running=false;
        mNotificationManager.cancelAll();
        Log.d(TAG,"service destroyed");
        //android.os.Process.killProcess (android.os.Process.myPid ());
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        String aux = new String(hexChars);
        // Log.d("AAGateWay","ByteTohex: " + aux);
        return aux;
    }

    private void sendBroadcastMessage(String message) {
        sendBroadcastMessage(message, "aaservice");
    }
    private void sendBroadcastMessage(String message, String tvid) {
        try {
            Intent tvIntent = new Intent(MainActivity.MESSAGE_INTENT_BROADCAST);
            tvIntent.putExtra(MainActivity.MESSAGE_TVID, tvid);
            tvIntent.putExtra(MainActivity.MESSAGE_EXTRA, message);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(tvIntent);
        } catch (Exception e) {
            //TODO
        }
    }

}
