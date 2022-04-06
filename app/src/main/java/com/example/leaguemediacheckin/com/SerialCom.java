package com.example.leaguemediacheckin.com;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

public class SerialCom {
    public final int HORIZONTAL = 1;
    public final int VERTICAL = 2;
    public final int FOCUS = 3;

    private Context context;
    private UsbSerialPort port;
    private UsbManager manager;
    private List<UsbSerialDriver> availableDrivers;
    private UsbSerialDriver driver;
    private Semaphore commLock; //semaphore to prevent multiple simultaneous messages


    public SerialCom(Context context) {
        this.context = context;
        port = null;
        commLock = new Semaphore(1);
        driver = null;
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);


    }

    public void requestPermission(){
        //Request Permission
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        for(int i = 0; i < availableDrivers.size(); i ++) {
            String manufacture = availableDrivers.get(i).getDevice().getManufacturerName();
            if (manufacture.contains("Arduino")) {
                driver = availableDrivers.get(i);
                manager.requestPermission(driver.getDevice(), PendingIntent.getActivity(context,1,new Intent(), PendingIntent.FLAG_UPDATE_CURRENT));
            }
        }
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

                //Inistialize controller
                byte outbuff[] = "init\n".getBytes();
                int bytesWritten = 0;
//                while (outbuff.length > bytesWritten){
                    port.write(Arrays.copyOfRange(outbuff, bytesWritten, outbuff.length), 1000);

//                }

                //read initialization response
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


                port.write("xdmp2\n".getBytes(), 1000);
                commLock.release();
                if(read.toString().contains("controller_ready")){
                    return 0;
                }
                else {
                    return 0;
                }
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
}