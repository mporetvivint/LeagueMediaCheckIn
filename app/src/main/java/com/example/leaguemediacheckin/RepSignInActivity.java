package com.example.leaguemediacheckin;

import static java.security.AccessController.getContext;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.leaguemediacheckin.comm.OnEventListener;
import com.example.leaguemediacheckin.comm.WebRequest;

import java.util.regex.Pattern;

public class RepSignInActivity extends AppCompatActivity {

    EditText fn;
    EditText ln;
    EditText email;
    ImageButton btn_submit;
    TextView txt_submitting;

    boolean isQRShowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rep_sign_in);

        //Hide Bars
        getSupportActionBar().hide();
        hideSystemBars();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        fn = findViewById(R.id.txt_add_fn);
        ln = findViewById(R.id.txt_add_ln);
        email = findViewById(R.id.txt_add_email);
        btn_submit = findViewById(R.id.btn_add_submit);
        txt_submitting = findViewById(R.id.txt_submitting);
        isQRShowing = false;

        View.OnKeyListener onKeyListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                validateInput();
                return true;
            }
        };

        btn_submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validateInput();
            }
        });
//
//        fn.setOnKeyListener(onKeyListener);
//        ln.setOnKeyListener(onKeyListener);
//        email.setOnKeyListener(onKeyListener);
    }

    private void validateInput() {
        boolean validated = true;
        if(fn.getText().toString().equals("")){
            fn.setError("First Name Cannot be Left Blank");
            validated = false;
        }
        if(ln.getText().toString().equals("")) {
            ln.setError("Last Name Cannot be Left Blank");
            validated = false;
        }
        if(!Patterns.EMAIL_ADDRESS.matcher(email.getText().toString()).matches()) {
            email.setError("Invalid Email Address");
            validated = false;
        }
        if (validated) {
            submitRep();
        }
    }

    private void submitRep(){
        txt_submitting.setVisibility(View.VISIBLE);
        btn_submit.setVisibility(View.INVISIBLE);
        Rep addedrep = new Rep(fn.getText().toString() + " " + ln.getText().toString(),
                email.getText().toString(),email.getText().toString(),"STANDARD",0,null);
        WebRequest request = new WebRequest(this.getIntent().getStringExtra("url"), addedrep, new OnEventListener() {
            @Override
            public void onSuccess(Object object) {
                displayQRCode(addedrep);
            }

            @Override
            public void onFail() {
                MediaPlayer mediaPlayer = MediaPlayer.create(txt_submitting.getContext(), R.raw.error_sound);
                mediaPlayer.start();
                isQRShowing = false;
                txt_submitting.setVisibility(View.INVISIBLE);
                btn_submit.setVisibility(View.VISIBLE);
            }
        },this,true);
        request.execute();
    }

    private void displayQRCode(Rep rep){
        Intent intent = new Intent(this,QRCodeDisplayActivity.class);
        intent.putExtra("email",rep.getEmail());
        if(!isQRShowing) {
            isQRShowing = true;
            startActivity(intent);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        fn.setText("");
        ln.setText("");
        email.setText("");
        isQRShowing = false;
        txt_submitting.setVisibility(View.INVISIBLE);
        btn_submit.setVisibility(View.VISIBLE);
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

}