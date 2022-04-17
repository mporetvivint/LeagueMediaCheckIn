package com.example.leaguemediacheckin.comm;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.example.leaguemediacheckin.MainActivity;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.acs.smartcard.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;



public class UsbComm extends Observable {
    public final int HORIZONTAL = 1;
    public final int VERTICAL = 2;
    public final int FOCUS = 3;

    private Context context;
    private UsbSerialPort port;
    private UsbManager manager;
    private List<UsbSerialDriver> availableSerialDrivers;
    private UsbSerialDriver serialDriver;
    private List<UsbDevice> deviceList;
    private UsbDevice deviceDriver;
    private boolean acsMode;
    private Reader acsReader;
    private Semaphore commLock; //semaphore to prevent multiple simultaneous messages
    private ExecutorService executor;
    private Handler handler;
    private boolean reader_running;
    private MainActivity main;


    public UsbComm(Context context) {
        this.context = context;
        port = null;
        commLock = new Semaphore(1);
        serialDriver = null;
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        reader_running = true;
        acsReader = new Reader(manager);
        acsMode = false;
        //Set up reader thread
        executor = Executors.newSingleThreadExecutor();
        main = (MainActivity) context;
    }

    public int requestPermission(){
        //Search for serial drivers first
        availableSerialDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        for(int i = 0; i < availableSerialDrivers.size(); i ++) {
            String manufacture = availableSerialDrivers.get(i).getDevice().getManufacturerName();
            if (manufacture.contains("Arduino")) {
                serialDriver = availableSerialDrivers.get(i);
                manager.requestPermission(serialDriver.getDevice(), PendingIntent.getActivity(context,1,new Intent(), PendingIntent.FLAG_UPDATE_CURRENT));
                return 0;
            }
        }

        //Search for Other USB drivers
        for(UsbDevice device : manager.getDeviceList().values()){
            if(acsReader.isSupported(device)){
                deviceDriver = device;
                manager.requestPermission(device, PendingIntent.getActivity(context,1,new Intent(), PendingIntent.FLAG_UPDATE_CURRENT));
                acsMode = true;
            }
        }

        return -1;
    }

    public int connect(){

        if(acsMode){
            if((deviceDriver == null) || (!manager.hasPermission(deviceDriver))){ //We didn't get permission and we need to ask again
                requestPermission();
                return -1;
            }
            new OpenTask().execute(deviceDriver);
            return 0;
        }
        else {
            if ((serialDriver == null) || (!manager.hasPermission(serialDriver.getDevice()))) { //We didn't get permission and we need to ask again
                requestPermission();
                return -1;
            } else { //We have permission, so now we are connecting

                UsbDeviceConnection connection = manager.openDevice(serialDriver.getDevice());
                if (connection == null) {
                    Log.d("ArduinoConnect", "Connection Error");
                    return -1;
                } else {
                    port = serialDriver.getPorts().get(0);
                }
                if (port == null) {
                    return -1;
                }
                try {
                    try {
                        commLock.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    port.open(connection);
                    port.setParameters(57600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);


                    //Clear buffer
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    byte readbuffer[] = new byte[100];
                    StringBuilder read = new StringBuilder();
                    int numBytesRead = 1;
                    while (numBytesRead > 0) {
                        numBytesRead = port.read(readbuffer, 1000);
                        read.append(new String(readbuffer, 0, numBytesRead));
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    commLock.release();
                    executor.execute(new MessageReader());
                    executor.shutdown();

                    return 0;
                } catch (IOException e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        }
    }

    public int sendMessage(String message){
        byte outbuff[] = message.getBytes(StandardCharsets.UTF_8);

        if(port == null){//Port not open
            return -1;
        }
//        Log.d("Follow", new String(outbuff));
        try {
            commLock.acquire();
            port.write(outbuff, 1000);
        } catch (IOException e) {
            e.printStackTrace();
        } catch(InterruptedException e){
            e.printStackTrace();
        } finally {
            commLock.release();
            return 0;
        }
    }

    public void close(){
        new CloseTask().execute();
    }

    private class MessageReader implements Runnable{


        @Override
        public void run() {
            Log.d("Serially","I'm here and running!");

            try {
                commLock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            byte[] readbuffer = new byte[800];
            StringBuilder uid_read = new StringBuilder();
            int numBytesRead;
            while(reader_running) {
                numBytesRead = 0;
                try {
                    numBytesRead = port.read(readbuffer, 50);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Separate UIDs read
                char curr_char;
                for (int i = 0; i < numBytesRead; i++){
                    curr_char = (char)readbuffer[i];
                    uid_read.append(curr_char);

                    //We only get the first complete UID and toss the rest because lessbe honest
                    //2 people aren't going to scan badges with 100ms of each other
                    if (curr_char == '\n') {
                        Log.d("Serially", uid_read.toString().replaceAll("\\s", ""));
                        notifyNewCard(new String(uid_read.toString()).replaceAll("\\s", ""));
                        uid_read.setLength(0);
                        break;
                    }
                }


//                Log.d("Serially", new String(readbuffer, 0, numBytesRead));
                Arrays.fill(readbuffer,(byte)0);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            commLock.release();
        }
    }

    private void notifyNewCard(String uid){
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setChanged();
                notifyObservers(uid);
            }
        });
    }

    private class OpenTask extends AsyncTask<UsbDevice, Void, Exception> {

        //Class Fields
        byte[] uidCommand = new byte[] { (byte) 0xFF, (byte) 0xCA, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        byte[] response = new byte[300];
        int responseLength;

        @Override
        protected Exception doInBackground(UsbDevice... params) {

            Exception result = null;

            try {

                acsReader.open(params[0]);

            } catch (Exception e) {

                result = e;
            }

            return result;
        }

        @Override
        protected void onPostExecute(Exception result) {
            //Turn off buzzer
            byte clazz = (byte)0xFF;
            byte ins = (byte)0x00;
            byte p1 = (byte)0x52;
            byte p2 = (byte)0x00;
            byte le = (byte)0x00;

            byte[] buzzerOffCmd = new byte[]{ (byte) 0xFF, (byte) 0x00, (byte) 0x52, (byte) 0x00, (byte) 0x00 };
            try {
                acsReader.control(0,Reader.IOCTL_CCID_ESCAPE,buzzerOffCmd,buzzerOffCmd.length,response,response.length);
            } catch (ReaderException e) {
                e.printStackTrace();
            }


            Log.d("Serially","ACS Opened");
            acsReader.setOnStateChangeListener(new Reader.OnStateChangeListener() {
                @Override
                public void onStateChange(int i, int i1, int i2) {
                    if(acsReader.getState(i) == Reader.CARD_PRESENT){
                        try {
                            acsReader.power(i,Reader.CARD_WARM_RESET);
                            acsReader.setProtocol(i,Reader.PROTOCOL_T0|Reader.PROTOCOL_T1);
                            responseLength = acsReader.transmit(i, uidCommand, uidCommand.length, response,response.length);
                            byte[] uid_only = Arrays.copyOf(response,responseLength-2);
                            notifyNewCard(bytesToHex(uid_only));
                        } catch (ReaderException e) {
                            e.printStackTrace();
                        }

                    }
                }
            });
        }

        //Convert bytes to UID string
        private final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        private String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_ARRAY[v >>> 4];
                hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        }

    }



    private class CloseTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            acsReader.close();
            return null;
        }


    }
}