package com.example.leaguemediacheckin;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.example.leaguemediacheckin.comm.SerialCom;

import java.util.Observable;
import java.util.Observer;

public class MainActivity extends AppCompatActivity {

    TextView txt;
    private final Observer arduino_callback = new Observer() {
        @Override
        public void update(Observable observable, Object o) {
            txt.setText((String) o);
            Log.d("Serially", "Ovbservertriggered");
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txt = findViewById(R.id.hello_txt);
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
}