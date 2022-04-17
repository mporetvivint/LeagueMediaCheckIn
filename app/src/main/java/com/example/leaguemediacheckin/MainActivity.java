package com.example.leaguemediacheckin;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.app.PendingIntent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.leaguemediacheckin.comm.OnEventListener;
import com.example.leaguemediacheckin.comm.SendRep;
import com.example.leaguemediacheckin.comm.UsbComm;
import com.example.leaguemediacheckin.comm.WebServer;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Semaphore;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {

    TextView txt_name;
    TextView txt_proceed;
    GifImageView gifView;
    Button btn_connect;
    int fail_timeout;
    Handler fail_handler;
    HandlerThread fail_thread;
    boolean fail_handler_running;
    Semaphore fail_semaphore;
    UsbComm usbComm;
    int portNumber;

    private boolean busy; //State variable if we are accepting new scans
    private final Observer arduino_callback = new Observer() {
        @Override
        public void update(Observable observable, Object o) {

            //Check if rep is in database if we are not busy
            if(!busy){
                searchRep((String) o);
            }

        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        hideSystemBars();
        txt_name = findViewById(R.id.txt_name);
        txt_proceed = findViewById(R.id.txt_proceed);
        gifView = findViewById(R.id.gif_bg);
        txt_name.setAlpha(0f);
        txt_proceed.setAlpha(0f);
        txt_proceed.setText(R.string.instructions);
        busy = false;
        btn_connect = findViewById(R.id.btn_connect);


        //Setup "Try Again" UI
        fail_timeout = 15;
        fail_thread = new HandlerThread("FailThread");
        fail_thread.start();
        fail_handler = new Handler(fail_thread.getLooper());
        fail_handler_running = false;
        fail_semaphore = new Semaphore(1);

        usbComm = new UsbComm(this);
        if(usbComm.requestPermission()==-1){
            Toast.makeText(this,R.string.reconnect,Toast.LENGTH_LONG).show();
        }
        if(usbComm.connect() == -1){
            //We need to prompt for connection
            btn_connect.setEnabled(true);
            btn_connect.setAlpha(1f);
        }
        else{
            usbComm.addObserver(arduino_callback);
        }

        btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(usbComm.connect() == 0){
                    usbComm.addObserver(arduino_callback);
                    //hide button
                    btn_connect.setAlpha(0f);
                    btn_connect.setEnabled(false);
                }
                else{
                    Toast.makeText(view.getContext(),R.string.reconnect,Toast.LENGTH_LONG).show();
                }
            }
        });

        //Setup web server
        //Start Server We'll Loop until we get a good port number
        boolean searching = true;
        portNumber = 8922;
        while (searching) {
            WebServer server = new WebServer(portNumber, this);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
                portNumber++;
            } finally {
                searching = false;
            }
        }

    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }
        // Configure the behavior of the hidden system bars
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        if(!busy) {
            gifView.setImageResource(R.drawable.main_bg);
            txt_name.animate().alpha(0f).setDuration(50).start();
            txt_proceed.animate().alpha(0f).setDuration(50).start();
        }
    }

    private void searchRep(String badge_uid){
        Log.d("Serially", "Searching rep" + badge_uid);
        SendRep sendRep = new SendRep(this.getIntent().getStringExtra("url"),badge_uid, new OnEventListener() {
            @Override
            public void onSuccess(Object object) {
                int i = 0;
            }

            @Override
            public void onFail(){
                int d = 0;
            }
        }, this);
        sendRep.execute();
    }

//    Function to handle changing and timing the "try again" screen
//    @param: int number of seconds to set timer to, 0 means stop
    private void failedUIHandler(int time){
        MediaPlayer mediaPlayer = MediaPlayer.create(txt_name.getContext(), R.raw.error_sound);
        mediaPlayer.start();
        if(!fail_handler_running){
            gifView.setImageResource(R.drawable.scan_again);
            fail_handler.post(new Runnable() {
                @Override
                public void run() {
                    fail_handler_running = true;
                    while (fail_timeout > 0){

                        //Sleep for 1 second
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        //decrement counter
                        try {
                            fail_semaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        fail_timeout --;
                        fail_semaphore.release();
                    }

                    //Timer has run out
                    if(!busy) { //If a rep has been accepted we are not going to change the UI
                        gifView.setImageResource(R.drawable.main_bg);
                    }
                    fail_handler_running = false; //We are done running
                }
            });
        }

        //In the event of multiple failures we reset the timer
        try {
            fail_semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        fail_timeout = time;
        fail_semaphore.release();
    }

    public void searchRepCallback(String response){
        if(response.equals("fail")){
            failedUIHandler(15);
            return;
        }


        //Rep was found in the database, and we will now record
        busy = true;

        String displayText = response.toUpperCase();
        txt_name.setText(displayText);
        txt_name.animate().alpha(1f).setDuration(500).start();
        txt_proceed.animate().alpha(1f).setDuration(500).start();
        gifView.setImageResource(R.drawable.accepted);
        MediaPlayer mediaPlayer = MediaPlayer.create(txt_name.getContext(), R.raw.magic_band_success);
        mediaPlayer.start();

        //After 10 seconds we will show the "please wait" UI
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                if(busy) {
                    gifView.setImageResource(R.drawable.please_wait);
                    txt_name.animate().alpha(0f).setDuration(50).start();
                    txt_proceed.animate().alpha(0f).setDuration(50).start();
                }
            }
        }, 10000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbComm.close();
    }
}