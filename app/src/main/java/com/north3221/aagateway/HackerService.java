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
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.support.annotation.RequiresApi;
import android.util.Log;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;



import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static com.north3221.aagateway.ConnectionStateReceiver.ACTION_RESET_AASERVICE;
import static java.lang.Thread.sleep;

public class HackerService extends Service {
    private static final String TAG = "AAGateWay";
    private NotificationManager mNotificationManager;
    private final IBinder mBinder = new LocalBinder();
    private String gatewayIP;
    private ParcelFileDescriptor mFileDescriptor;
    private FileOutputStream phoneOutputStream;
    private FileInputStream phoneInputStream;
    private AAlogger logger;
    private static final String tvName = "aaservice";

    private static Socket phoneTcpSocket;
    private static OutputStream socketoutput;
    private static DataInputStream socketinput;
    public static boolean running=false;
    private boolean localCompleted,usbCompleted;
    byte [] readbuffer = new byte[16384];

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
        logger = new AAlogger(getApplicationContext());

        String CHANNEL_ONE_ID = "com.north3221.aagateway";
        String CHANNEL_ONE_NAME = "Channel One";
        NotificationChannel notificationChannel;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(notificationChannel);
            }
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
            logger.log("Already Running", tvName);
            return START_STICKY;
        }
        Log.d(TAG,"Service Started");
        logger.log("Started",tvName);
        super.onStartCommand(intent, flags, startId);
        UsbAccessory mAccessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
        gatewayIP = intent.getStringExtra("gwip");
        UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (mUsbManager != null) {
            mFileDescriptor = mUsbManager.openAccessory(mAccessory);
        }
        if (mFileDescriptor != null) {
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            phoneInputStream = new FileInputStream(fd);
            phoneOutputStream = new FileOutputStream(fd);
            usbCompleted=false;
        } else {
            Log.e(TAG, "Cannot open usb accessory "+ mAccessory.toString());
            stopSelf();
            return START_STICKY;
        }

        //Manually start AA.
        logger.log("Running",tvName);
        running=true;
        localCompleted = false;
        usbCompleted = false;
        Thread usbreader = new Thread(new usbpollthread());
        Thread tcpreader = new Thread(new tcppollthread());
        usbreader.start();
        tcpreader.start();

        return START_STICKY;
    }

    class tcppollthread implements Runnable {
        public void run() {
            Looper.prepare();
            Log.d(TAG,"tcp - run");
            logger.log("TCP polling thread started - running:= " + running, "log");

            try {
                logger.log("Connecting to phone: " + gatewayIP, tvName);
                InetAddress addr = InetAddress.getByName(gatewayIP);

                if (addr.isReachable(300)) {
                    Log.d(TAG, "tcp - reachable " + gatewayIP);
                } else {
                    Log.d(TAG, "tcp - not reachable " + gatewayIP);
                    logger.log("Phone not reachable", tvName);
                    logger.log("TCP polling thread - Unable to reach phone on " + addr, "log");
                    stopSelf();
                }

                Log.d(TAG, "tcp - connecting to phone" );
                logger.log("TCP polling thread -  Opening connection to phone", "log");
                phoneTcpSocket = new Socket();
                phoneTcpSocket.setSoTimeout(5000);
                phoneTcpSocket.connect(new InetSocketAddress(gatewayIP, 5277), 500);
                Log.d(TAG, "tcp - connected");
                logger.log("TCP polling thread - Opening Socket to phone", "log");
                socketoutput = phoneTcpSocket.getOutputStream();
                socketinput = new DataInputStream(phoneTcpSocket.getInputStream());
                socketoutput.write(new byte[]{0, 3, 0, 6, 0, 1, 0, 1, 0, 2});
                socketoutput.flush();

                byte[] recv = new byte[12];
                socketinput.read(recv);
                Log.d(TAG, "tcp - recv from phone " + bytesToHex(recv));
                localCompleted = true;
                logger.log("TCP Polling thread recv from phone " + bytesToHex(recv), "log");

            } catch (Exception e) {
                Log.e(TAG, "tcp - error opening phone " + e.getMessage());
                logger.log("TCP polling thread - error opening phone " + e.getMessage(), "log");
                stopSelf();
            }

            //wait for usb initialization
            if (!usbCompleted && running)
                Log.d(TAG, "tcp - waiting for usb");
            while (!usbCompleted && running) {
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    Log.e(TAG, "tcp - error sleeping "+e.getMessage());
                    logger.log("TCP error sleeping = " + e.getMessage(), "log");
                }
            }

            while (running)
            {
                try {
                    getLocalmessage();

                } catch (Exception e) {
                    Log.e(TAG,"tcp - in main loop "+e.getMessage());
                    logger.log("TCP error in main loop = " + e.getMessage(), "log");
                    stopSelf();
                    break;
                }
            }
            Log.d(TAG,"tcp - end");
            logger.log("tcp end",tvName);
            stopSelf();
        }

    }

    class usbpollthread implements Runnable {

        public void run() {
            Looper.prepare();
            Log.d(TAG,"usb - run");

            byte[] buf = new byte[16384];
            int x;

            try {
                x=phoneInputStream.read(buf);
                Log.d(TAG, "usb -received from usb "+bytesToHex((Arrays.copyOf(buf, x))));
                logger.log("received from usb",tvName);
                phoneOutputStream.write(new byte[]{0, 3, 0, 8, 0, 2, 0, 1, 0, 4, 0, 0});
                usbCompleted = true;
            } catch (Exception e) {
                Log.e(TAG, "usb - error init "+e.getMessage());
                stopSelf();
            }

            if (!localCompleted && running) {
                Log.d(TAG, "usb - waiting for local");
                logger.log("usb waiting for local",tvName);
            }
            int i = 0;
            while (!localCompleted && running) {
                i++;
                try {
                    sleep(100);
                    if ((i % 10) == 0) {
                        logger.log("usb waiting for local count " + i,tvName);
                    }
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
                    stopSelf();
                }

            }
            Log.d(TAG,"usb - end");
            stopSelf();
        }
    }

    private void getLocalmessage() throws IOException {
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
        // Attempt to close phone connection gracefully
        if (phoneTcpSocket != null) {
            try {
                socketoutput.close();
                socketinput.close();
                phoneTcpSocket.close();
            } catch (IOException e) {
                Log.d(TAG, "error closing phone tcp socket " + e.getMessage());
                logger.log("error closing phone tcp socket",tvName);
            }
        }
        // Attempt to close usb connection gracefully
        if (mFileDescriptor != null) {
            try {
                phoneInputStream.close();
                phoneOutputStream.close();
                mFileDescriptor.close();
            } catch (IOException e) {
                Log.d(TAG, "error closing usb " + e.getMessage());
                logger.log("error closing usb",tvName);
            }
        }
        Log.d(TAG,"service destroyed");
        logger.log("Service Destroyed", "log");
        resetService();
        mNotificationManager.cancelAll();
    }

    private void resetService() {
        Intent intent = new Intent();
        intent.setAction(ACTION_RESET_AASERVICE);
        sendBroadcast(intent);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
