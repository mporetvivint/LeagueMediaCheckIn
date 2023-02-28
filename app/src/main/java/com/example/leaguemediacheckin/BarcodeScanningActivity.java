package com.example.leaguemediacheckin;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

public interface BarcodeScanningActivity {

    public void barcodeDetectedCallback(FirebaseVisionBarcode barcode);
}
