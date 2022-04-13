package com.example.leaguemediacheckin;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.example.leaguemediacheckin.comm.OnEventListener;
import com.example.leaguemediacheckin.comm.SendRep;
import com.example.leaguemediacheckin.comm.SerialCom;

import java.util.Observable;
import java.util.Observer;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {

    TextView txt;
    GifImageView gifView;
    private boolean busy; //State variable if we are accepting new scans
    private final Observer arduino_callback = new Observer() {
        @Override
        public void update(Observable observable, Object o) {

            //Check if rep is in database
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
        txt = findViewById(R.id.hello_txt);
        gifView = findViewById(R.id.gif_bg);
        txt.setAlpha(0f);
        busy = false;
        SerialCom serialCom = new SerialCom(this);
        serialCom.requestPermission();
        if(serialCom.connectArduino() == -1){
            txt.setText("I give up");
        }
        else{
            txt.setText("Ready!");
            serialCom.addObserver(arduino_callback);
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
    }

    private void searchRep(String badge_uid){
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

    public void searchRepCallback(String response){

        if(response.equals("fail")){
            gifView.setImageResource(R.drawable.scan_again);
            MediaPlayer mediaPlayer = MediaPlayer.create(txt.getContext(), R.raw.error_sound);
            mediaPlayer.start();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    gifView.setImageResource(R.drawable.main_bg);
                    txt.animate().alpha(0f).setDuration(50).start();
                }
            }, 10000);
            return;
        }


        String displayText = "HELLO,\n" + response.toUpperCase() + "\nPLEASE PROCEED TO THE\nGREEN SCREEN";
        txt.setText(displayText);
        txt.animate().alpha(1f).setDuration(500).start();
        gifView.setImageResource(R.drawable.accepted);
//        busy = true;
        MediaPlayer mediaPlayer = MediaPlayer.create(txt.getContext(), R.raw.magic_band_success);
        mediaPlayer.start();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                gifView.setImageResource(R.drawable.main_bg);
                txt.animate().alpha(0f).setDuration(50).start();
            }
        }, 10000);
    }

}