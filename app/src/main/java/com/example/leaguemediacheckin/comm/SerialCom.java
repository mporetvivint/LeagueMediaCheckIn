package com.example.leaguemediacheckin.comm;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import com.example.leaguemediacheckin.MainActivity;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;



public class SerialCom extends Observable {
    public final int HORIZONTAL = 1;
    public final int VERTICAL = 2;
    public final int FOCUS = 3;

    private Context context;
    private UsbSerialPort port;
    private UsbManager manager;
    private List<UsbSerialDriver> availableDrivers;
    private UsbSerialDriver driver;
    private Semaphore commLock; //semaphore to prevent multiple simultaneous messages
    private ExecutorService executor;
    private Handler handler;
    private boolean reader_running;


    public SerialCom(Context context) {
        this.context = context;
        port = null;
        commLock = new Semaphore(1);
        driver = null;
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        reader_running = true;

        //Set up reader thread
        executor = Executors.newSingleThreadExecutor();
    }

    public int requestPermission(){
        //Request Permission
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        for(int i = 0; i < availableDrivers.size(); i ++) {
            String manufacture = availableDrivers.get(i).getDevice().getManufacturerName();
            if (manufacture.contains("Arduino")) {
                driver = availableDrivers.get(i);
                manager.requestPermission(driver.getDevice(), PendingIntent.getActivity(context,1,new Intent(), PendingIntent.FLAG_UPDATE_CURRENT));
                return 0;
            }
        }
        return -1;
    }

    public int connectArduino(){

        if((driver == null) || (!manager.hasPermission(driver.getDevice()))){ //We didn't get permission and we need to ask again
            requestPermission();
            return -1;
        }
        else { //We have permission, so now we are connecting

            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection == null) {
                Log.d("ArduinoConnect", "Connection Error");
                return -1;
            } else {
                port = driver.getPorts().get(0);
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
                        MainActivity main = (MainActivity) context;
                        main.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setChanged();
                                notifyObservers(new String(uid_read.toString()).replaceAll("\\s", ""));
                                Log.d("Serially","handler triggered");
                                uid_read.setLength(0);
                            }
                        });
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

}