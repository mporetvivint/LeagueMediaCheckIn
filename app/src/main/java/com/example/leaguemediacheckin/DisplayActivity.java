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
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.example.leaguemediacheckin.comm.OnEventListener;
import com.example.leaguemediacheckin.comm.WebRequest;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class DisplayActivity extends AppCompatActivity implements BarcodeScanningActivity, WebRequestReceiver, WebServerReceiver {
    //Barcode Scanning
    // intent request code to handle updating play services if needed.
    private static final int RC_HANDLE_GMS = 9001;

    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    // constants used to pass extra data in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private Semaphore qr_read_request;

    private TextView message_view;
    private TextView timer;
    private Button btn_cancel;
    private Button btn_send;

    private MediaPlayer mediaSuccess;
    private MediaPlayer mediaFail;
    private TimerThread timerThread;
    private WebServer server;
    private String url;
    private int portNumber;
    private final int WALK_TIME = 10; //Number of seconds to delay between reps


    private static final String TAG = "Display_Barbode_scanner";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        getSupportActionBar().hide();
//        hideSystemBars();

        mediaSuccess = MediaPlayer.create(this, R.raw.magic_band_read);
        mediaFail = MediaPlayer.create(this, R.raw.error_sound);
        timerThread = new TimerThread();

        url = this.getIntent().getStringExtra("url");
        //Setup web server
        //Start Server We'll Loop until we get a good port number
        boolean searching = true;
        portNumber = 8922;
        while (searching) {
            server = new WebServer(portNumber, this);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
                portNumber++;
            } finally {
                searching = false;
            }
        }

        //Barcode Scanning Prep
        mPreview = findViewById(R.id.display_preview);

        // read parameters from the intent used to launch the activity.
        boolean autoFocus = getIntent().getBooleanExtra(AutoFocus, true);
        boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash);
        } else {
            requestCameraPermission();
        }

        qr_read_request = new Semaphore(1);

        WebRequest cancelRequest = new WebRequest(this.getIntent().getStringExtra("url")+"/cancel",null, new OnEventListener() {
            @Override
            public void onSuccess(Object object) {

            }

            @Override
            public void onFail(){

            }
        }, this);
        WebRequest takeRepRequest = new WebRequest(this.getIntent().getStringExtra("url")+"/displaytake",null, new OnEventListener() {
            @Override
            public void onSuccess(Object object) {

            }

            @Override
            public void onFail(){

            }
        }, this);

        message_view = findViewById(R.id.txt_display_message);
        btn_cancel = findViewById(R.id.btn_cancel_rep);
        btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebRequest cancel = new WebRequest(url+"/cancel",null, new OnEventListener() {
                    @Override
                    public void onSuccess(Object object) {

                    }

                    @Override
                    public void onFail(){

                    }
                }, view.getContext());
                cancel.execute();
                message_view.setText("Ready");
                if(qr_read_request.availablePermits() == 0){
                    qr_read_request.release();
                }
                btn_cancel.setEnabled(false);
                btn_send.setEnabled(false);
            }
        });
        btn_cancel.setEnabled(false);

        btn_send = findViewById(R.id.btn_send_rep);
        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                timerThread.stopTimer();
                btn_send.setEnabled(false);
                btn_cancel.setEnabled(false);
                WebRequest take = new WebRequest(url+"/displaytake",null, new OnEventListener() {
                    @Override
                    public void onSuccess(Object object) {

                    }

                    @Override
                    public void onFail(){

                    }
                }, view.getContext());
                take.execute();
                message_view.setText("Ready");
                if(qr_read_request.availablePermits() == 0){
                    qr_read_request.release();
                }

                Thread tm = new Thread(timerThread);
                tm.start();

            }
        });
        btn_send.setEnabled(false);

        timer = findViewById(R.id.txt_timer);
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


    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if(mPreview == null)
            mPreview = findViewById(R.id.display_preview);
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
        if (mPreview != null) {
            mPreview.release();
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
            if(qr_read_request.availablePermits() == 0)
                qr_read_request.release();
            return;
        }
            message_view.setText("Searching");
            String text = barcode.getRawValue();
            searchRep(text);
    }

    @Override
    public void mdnsCallback(ServerObject serverObject) {

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

    private void searchRep(String badge_uid){
        WebRequest sendRep = new WebRequest(this.getIntent().getStringExtra("url"),badge_uid, new OnEventListener() {
            @Override
            public void onSuccess(Object object) {

            }

            @Override
            public void onFail(){

            }
        }, this);
        sendRep.execute();
    }

    @Override
    public void webRequestCallback(String response, String address){
        if(response.equals("fail")){
            if(qr_read_request.availablePermits() == 0){
                qr_read_request.release();
            }
            return;
        }
    }

    @Override
    public Response serverResponse(String uri, Map<String, List<String>> params) {
        switch (uri) {
            case "/ready":  //A rep was found
                try {
                    String repJSON = params.get("rep").get(0).substring(6);
                    JSONObject parsedJSON;
                    String repName;
                    try {
                         parsedJSON = new JSONObject(repJSON);
                         repName = parsedJSON.getString("name");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Lies, you didn't send anything");
                    }
                    qr_read_request.drainPermits();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            message_view.setText(repName);
                            mediaSuccess.start();
                            btn_send.setEnabled(true);
                            btn_cancel.setEnabled(true);
                        }
                    });
                    return newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "Ready for rep");
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    if(qr_read_request.availablePermits() == 0){
                        qr_read_request.release();
                    }
                    return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Lies, you didn't send anything");
                }
            case "/nopics":
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        message_view.setText("Rep has no pictures on file");
                        mediaFail.start();
                        if(qr_read_request.availablePermits() == 0){
                            qr_read_request.release();
                        }
                    }
                });
                break;
            case "/lookuperr": //No rep found
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        message_view.setText("Rep not found");
                        mediaFail.start();
                        if(qr_read_request.availablePermits() == 0){
                            qr_read_request.release();
                        }
                    }
                });
                break;
        }

        return null;
    }

    private class TimerThread implements Runnable {
        boolean interrupt = false;

        @Override
        public void run() {
            timer.setAlpha(1f);
            for(int i = WALK_TIME; i>=0; i--){
                if(interrupt){
                    break;
                }
                timer.setText(i);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            timer.setAlpha(0f);
            interrupt = false;
        }

        public void stopTimer(){
            interrupt = true;
        }
    }
}