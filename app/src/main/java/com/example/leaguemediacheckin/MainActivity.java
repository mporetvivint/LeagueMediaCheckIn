package com.example.leaguemediacheckin;

import static org.nanohttpd.protocols.http.NanoHTTPD.MIME_PLAINTEXT;
import static org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.leaguemediacheckin.comm.OnEventListener;
import com.example.leaguemediacheckin.comm.WebRequest;
import com.example.leaguemediacheckin.comm.UsbComm;
import com.example.leaguemediacheckin.comm.WebServer;
import com.example.leaguemediacheckin.comm.WebServerReceiver;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

import org.json.JSONException;
import org.json.JSONObject;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Semaphore;

import pl.droidsonroids.gif.GifImageView;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MainActivity extends AppCompatActivity implements BarcodeScanningActivity, WebRequestReceiver, WebServerReceiver {

    //Barcode Scanning
    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // constants used to pass extra data in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";

    //QR code decryption
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String ENCRYPTION_MODE = "AES/CBC/PKCS5Padding"; // No IV, using PKCS5Padding

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;

    private static final String TAG = "Main_Barbode_scanner";

    TextView txt_name;
    TextView txt_proceed;
    GifImageView gifView;
    Button btn_connect;
    int fail_timeout;
    Handler fail_handler;
    HandlerThread fail_thread;
    boolean fail_handler_running;
    boolean searching;
    Semaphore fail_semaphore;
    UsbComm usbComm;
    int portNumber;
    Semaphore qr_read_request;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        

        //Barcode Scanning Prep
        mPreview = findViewById(R.id.main_preview);
        // read parameters from the intent used to launch the activity.
        boolean autoFocus = getIntent().getBooleanExtra(AutoFocus, true);
        boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);
        qr_read_request = new Semaphore(1);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash);
        } else {
            requestCameraPermission();
        }

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
        searching = false;
        fail_semaphore = new Semaphore(1);

        usbComm = new UsbComm(this);
        if(usbComm.requestPermission()==-1){
            Toast.makeText(this,R.string.reconnect,Toast.LENGTH_LONG).show();
        }
        if(usbComm.connect() == -1){
            //We need to prompt for connection
            btn_connect.setEnabled(true);
//            btn_connect.setAlpha(1f);
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

    @Override //Listen for volume button events to reset tablet status
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            setBusy(false);
        }
        return true;
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
            gifView.setImageResource(R.drawable.main_scan);
            gifView.animate().alpha(.8f).setDuration(850).start();
            txt_name.animate().alpha(0f).setDuration(50).start();
            txt_proceed.animate().alpha(0f).setDuration(50).start();
            Log.d("SEM","S_Release_NotBusy");
            qr_read_request.release();
        }
    }

    public void pause(){ //temporarily pauses scanning inputs
        setBusy(true);
        gifView.setImageResource(R.drawable.please_wait);
    }

    private void searchRep(String badge_uid){
        String decrypted_badge_uid = null;
        try {
//            decrypted_badge_uid = decrypt(badge_uid,"runpass96%PASS");
//            Log.d("SERCH", decrypted_badge_uid);
            //Block Scans while searching

            try {
                // Try parsing the string as JSON
                JSONObject repJSON = new JSONObject(badge_uid);
                // If no exception is thrown, then it's a valid JSON
                repJSON.put("badgeID",repJSON.get("email"));
                repJSON.put("type","STANDARD");
                repJSON.put("entrance",0);
                repJSON.put("additions",
                        "rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAAAdwQAAAAAeA==");

                Rep repAdder = new Rep(repJSON.toString());
                badge_uid = repAdder.getBadgeID();
                //Add rep to database
                WebRequest addRepRequest = new WebRequest(getIntent().getStringExtra("url"), repAdder, new OnEventListener() {
                    @Override
                    public void onSuccess(Object object) {
                        String output = object.toString();
                        output += " ";
                    }

                    @Override
                    public void onFail() {
                        searching = false;
                    }
                },this,true);
                addRepRequest.execute();

            } catch (Exception e) {
                // If an exception is thrown, then it's likely plain text
            }

            WebRequest sendRep = new WebRequest(this.getIntent().getStringExtra("url"),badge_uid, new OnEventListener() {
                @Override
                public void onSuccess(Object object) {
                    searching = false;
                }

                @Override
                public void onFail(){
                    searching = false;
                }
            }, this);
            sendRep.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static String decrypt(String encryptedText, String key) throws Exception {
        // Decode the base64 encoded encrypted text
        byte[] encryptedData = Base64.getDecoder().decode(encryptedText);

        // Create a secret key
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM);

        // Initialize the cipher in decryption mode
        Cipher cipher = Cipher.getInstance(ENCRYPTION_MODE);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

        // Perform the decryption
        byte[] decryptedData = cipher.doFinal(encryptedData);

        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        try {
            String encryptedText = "YOUR_ENCRYPTED_STRING_HERE";
            String key = "YOUR_SECRET_KEY_HERE";

            String decryptedText = decrypt(encryptedText, key);
            System.out.println("Decrypted Text: " + decryptedText);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                        gifView.setImageResource(R.drawable.main_scan);
                        Log.d("SEM","S_Release_FailedUI");
                        qr_read_request.release();
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

    @Override
    public void webRequestCallback(String response, String address){
        if(response.equals("fail")){
            failedUIHandler(3);
            return;
        }

        //Rep was found in the database, and we will now record
        busy = true;
        String displayText;
        try {
            String shorter = response.substring(6);
            JSONObject jsonObject = new JSONObject(response.substring(6));
            displayText = jsonObject.getString("name").toUpperCase();
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        txt_name.setText(displayText);
        txt_name.animate().alpha(1f).setDuration(500).start();
        txt_proceed.animate().alpha(1f).setDuration(500).start();
        gifView.animate().alpha(1f).setDuration(850).start();
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

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if(mPreview == null)
            mPreview = findViewById(R.id.main_preview);
        startCameraSource();
        mPreview.setScaling();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbComm.close();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    //Barcode Scanning Functions
    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        findViewById(R.id.topLayout).setOnClickListener(listener);
//        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
//                Snackbar.LENGTH_INDEFINITE)
//                .setAction(R.string.ok, listener)
//                .show();
    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
        Context context = getApplicationContext();

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
//        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();

//        if (!barcodeDetector.isOperational()) {
//            // Note: The first time that an app using the barcode or face API is installed on a
//            // device, GMS will download a native libraries to the device in order to do detection.
//            // Usually this completes before the app is run for the first time.  But if that
//            // download has not yet completed, then the above call will not detect any barcodes
//            // and/or faces.
//            //
//            // isOperational() can be used to check if the required native libraries are currently
//            // available.  The detectors will automatically become operational once the library
//            // downloads complete on device.
//            Log.d("B-Code", "Detector dependencies are not yet available.");
//
//            // Check for low storage.  If there is low storage, the native library will not be
//            // downloaded, so detection will not become operational.
//            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
//            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;
//
//            if (hasLowStorage) {
//                Log.d("B-Code", "LowStorage");
//            }
//        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(),this)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        // make sure that auto focus is an available option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            builder = builder.setFocusMode(
                    autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);
        }

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            boolean autoFocus = getIntent().getBooleanExtra(AutoFocus,false);
            boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);
            createCameraSource(autoFocus, useFlash);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Multitracker sample")
                .setMessage("NO CAMERA PERMISSION")
                .setPositiveButton("OK", listener)
                .show();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    @Override
    public void barcodeDetectedCallback(FirebaseVisionBarcode barcode){

        try {
            if(!qr_read_request.tryAcquire()){
                return;
            }
            Log.d("SEM","S_Acquire");
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(barcode.getRawValue().equals("Read a B-code")){
            return;
        }
        if (!searching && !busy) {
            searching = true;
            gifView.setImageResource(R.drawable.searching);
            MediaPlayer mediaPlayer = MediaPlayer.create(txt_name.getContext(), R.raw.magic_band_read);
            mediaPlayer.start();
            String text = barcode.getRawValue();
            searchRep(text);
        }
    }

    @Override
    public void mdnsCallback(ServerObject serverObject) {

    }

    @Override
    public Response serverResponse(String uri, Map<String, List<String>> params) {
        if(uri.equals("/pause")){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pause();
                }
            });
            return newFixedLengthResponse(Status.OK,MIME_PLAINTEXT,"SessionPaused");
        }
        if (uri.equals("/endSession")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setBusy(false);

                }
            });
            return newFixedLengthResponse(Status.OK,MIME_PLAINTEXT,"SessionEnded");
        }
        if (uri.equals("/beginSession")){
            try {
                String rep = params.get("rep").get(0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webRequestCallback(rep,null);
                    }
                });
            }catch (NullPointerException e){
                e.printStackTrace();
                return newFixedLengthResponse(Status.BAD_REQUEST,MIME_PLAINTEXT,"No Name Found");
            }
            return newFixedLengthResponse(Status.OK,MIME_PLAINTEXT,"SessionStarted");
        }
        return newFixedLengthResponse(Status.BAD_REQUEST,MIME_PLAINTEXT,"Watchu takin' bout??");
    }
}