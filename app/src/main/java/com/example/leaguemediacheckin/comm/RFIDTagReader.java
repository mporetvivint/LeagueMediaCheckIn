package com.example.leaguemediacheckin.comm;


import org.json.JSONException;
import org.json.JSONObject;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

public class RFIDTagReader{
    private String hostname;
    private int port;
    private Socket socket;
    private BufferedReader reader;
    String jsonLine;
    String tagID;
    String newTagRead;
    PropertyChangeSupport pcs;
    boolean reading;
    boolean connecting;
    boolean running;
    ReadThread readThread;
    long lastheartbeat;
    boolean pagedOpened; //do not allow the reader webpage to open multiple times
    boolean antenna1; //keep track of which antennas we are paying attention to
    boolean antenna2;
    int connectionAttempts;
    int tagReads;
    OutputStream repeaterOutput;
    final int MAX_CONNECTION_ATTEMPTS = 10; //How many connection attempts are allowed before intervention
    final int MAX_TAG_READS = 200; //max number of tag reads before resetting connection

    public RFIDTagReader(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        this.pcs = new PropertyChangeSupport(this);
        repeaterOutput = null;
        antenna1 = true;
        antenna2 = true;

        //close network connection on exit
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                try {
                    close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void addObserver(PropertyChangeListener pcl){
        pcs.addPropertyChangeListener("tid", pcl);
        pcs.addPropertyChangeListener("online",pcl);
        pcs.addPropertyChangeListener("offline",pcl);
    }

    public void connect(){

        running = true;
        connecting = true;
        reading = false;
        pagedOpened = false;
        Thread thread = new Thread(new RFID_Worker_Thread());
        thread.start();
    }

    private void readTag(){
        if(LocalDateTime.now().getLong(ChronoField.SECOND_OF_DAY) - lastheartbeat > 15){
            //no heartbeat. Try reconnecting
            System.out.println("Connection is dead");
            connecting = true;
            reading = false;
            pcs.firePropertyChange("offline", 0, "offline");
            return;
        }
        try{
            jsonLine = reader.readLine();
        }catch(SocketTimeoutException e){
            System.out.println("RFID_read_timeout");
            return;
        }catch(IOException e){
            e.printStackTrace();
            return;
        }

        //Parse JSON
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(jsonLine);
            if(jsonObject.getBoolean("isHeartBeat")){
                //keep track of heartbeats
                lastheartbeat = LocalDateTime.now().getLong(ChronoField.SECOND_OF_DAY);
                System.out.println("RFID_Heartbeat");
            }else {
                if(tagReads++>MAX_TAG_READS &&  !pagedOpened){ //Open webpage to reconnect before we hit tag read limit
                    try {
                        URI uri = new URI("https://"+hostname);
//                        Desktop.getDesktop().browse(uri);
                        pagedOpened = true;
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }

                //Only send info if we are listening to this antenna
                String antenna = jsonObject.getString("antennaPort");
                if((antenna1 && antenna.contains("1")) || (antenna2 && antenna.contains("2"))) {
                    newTagRead = jsonObject.getString("epc");
//                System.out.println("READ: " + newTagRead + " Strength: " + jsonObject.getString("peakRssi") + " ANT: " + jsonObject.getString("antennaPort"));
                    pcs.firePropertyChange("tid", 0, newTagRead);
                    tagID = newTagRead;
                }
            }
        } catch (Exception e) {//Connection needs to be remade
            System.out.println("Connection is dead");
            connecting = true;
            reading = false;
            pcs.firePropertyChange("offline", 0, "offline");
        }
    }

    public void close() throws IOException {
        connecting = false;
        reading = false;
        running = false;
        System.out.println("RFID_close");
        if(reader!=null) {
            reader.close();
            socket.close();
        }
    }

    public void setAntenna1(boolean antenna1) {
        this.antenna1 = antenna1;
    }

    public void setAntenna2(boolean antenna2) {
        this.antenna2 = antenna2;
    }

    //Background thread for reading input stream
    class ReadThread implements Runnable{

        @Override
        public void run() {
            while(reading){
                readTag();
            }
        }
    }

    class RFID_Worker_Thread implements Runnable{
        @Override
        public void run() { //While running, attempts continuously to reconnect if connection is lost
            while (running) {
                while (connecting) {
                    try {
                        if(connectionAttempts++ > MAX_CONNECTION_ATTEMPTS && !pagedOpened){ //Open reader webpage for help
                            try {
                                URI uri = new URI("https://"+hostname);
//                                Desktop.getDesktop().browse(uri);
                                pagedOpened = true;
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                        Thread.sleep(1000);
                        System.out.println("RFID_connection Attempt");
                        socket = new Socket();
                        socket.setSoTimeout(1500);
                        socket.connect(new InetSocketAddress(hostname, port), 1000);
                        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        connecting = false;
                        System.out.println("RFID_connection success");
                        lastheartbeat = LocalDateTime.now().getLong(ChronoField.SECOND_OF_DAY);
                        pcs.firePropertyChange("online", 0, "online");
                        //Move on to running
                        reading = true;
                        pagedOpened = false;

                    } catch (Exception e) { //connection was unsuccessful, try again
                        e.printStackTrace();
                    }
                }
                while (reading) {
                    readTag();
                }
            }
        }
    }
}
