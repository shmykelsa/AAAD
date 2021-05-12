package com.legs.appsforaa;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.blikoon.qrcodescanner.QrCodeActivity;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class TransferLicense extends AppCompatActivity {

    private static final int REQUEST_CODE_QR_SCAN = 101;
    private static final int REQUEST_CAMERA = 100;

    String deviceId;


    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_license);

        deviceId =  Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        final Button transferHereButton = findViewById(R.id.receive);
        final Button transferFromHereButton = findViewById(R.id.donate);
        final TextView tv = findViewById(R.id.textView_transfer);

        final FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE);

        final boolean[] eligible = new boolean[1];

        database.getReference("users").child(deviceId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                eligible[0] = snapshot.getValue(Boolean.class);
                if (eligible[0]) {

                    transferFromHereButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (ContextCompat.checkSelfPermission(TransferLicense.this, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_DENIED) {
                                ActivityCompat.requestPermissions(TransferLicense.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                            } else {
                                Intent i = new Intent(TransferLicense.this, QrCodeActivity.class);
                                startActivityForResult(i, REQUEST_CODE_QR_SCAN);
                            }
                        }
                    });
                } else {
                    transferFromHereButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Toast.makeText(TransferLicense.this, getString(R.string.no_license_found), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });






        transferHereButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transferHereButton.setVisibility(View.GONE);
                transferFromHereButton.setVisibility(View.GONE);


                tv.setText(R.string.qr_tutorial);

                QRCodeWriter writer = new QRCodeWriter();
                try {
                    BitMatrix bitMatrix = writer.encode(deviceId, BarcodeFormat.QR_CODE, 512, 512);
                    int width = bitMatrix.getWidth();
                    int height = bitMatrix.getHeight();
                    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                        }
                    }
                    ImageView qr = findViewById(R.id.img_result_qr);
                    qr.setImageBitmap(bmp);
                    qr.setVisibility(View.VISIBLE);

                } catch (WriterException e) {
                    e.printStackTrace();
                } finally {

                    database.getReference("users").child(deviceId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.getValue(Boolean.class)) {
                                Toast.makeText(getApplicationContext(), getString(R.string.success_transfer), Toast.LENGTH_LONG).show();
                                finish();
                                startActivity(new Intent(TransferLicense.this, MainActivity.class));
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                }
            }
        });




    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            if (data == null)
                return;
            //Getting the passed result
            String result = data.getStringExtra("com.blikoon.qrcodescanner.error_decoding_image");
            if (result != null) {
                AlertDialog alertDialog = new AlertDialog.Builder(TransferLicense.this).create();
                alertDialog.setTitle("Scan Error");
                alertDialog.setMessage("QR Code could not be scanned");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            }
            return;

        }
        if (requestCode == REQUEST_CODE_QR_SCAN) {
            if (data == null) {
                return;
            } else {
                //Getting the passed result
                final String result = data.getStringExtra("com.blikoon.qrcodescanner.got_qr_scan_relult");
                AlertDialog alertDialog = new AlertDialog.Builder(TransferLicense.this).create();
                alertDialog.setTitle(getString(R.string.confirm));
                alertDialog.setMessage(getString(R.string.license_here_confirm));
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                final FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE);
                                database.getReference("users").child(result).setValue(Boolean.TRUE).addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        database.getReference("users").child(deviceId).setValue(Boolean.FALSE).addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void aVoid) {
                                                Toast.makeText(TransferLicense.this, getString(R.string.license_done), Toast.LENGTH_LONG).show();
                                                startActivity(new Intent(TransferLicense.this, MainActivity.class));
                                                finish();
                                            }
                                        });
                                    }
                                });

                            }
                        });
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent i = new Intent(TransferLicense.this, QrCodeActivity.class);
                startActivityForResult(i, REQUEST_CODE_QR_SCAN);
            } else {
                Toast.makeText(TransferLicense.this, getString(R.string.qr_code_permission), Toast.LENGTH_LONG).show();
            }
        }
    }


}
