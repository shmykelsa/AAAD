package com.legs.appsforaa;

import android.Manifest;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager.widget.ViewPager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.legs.appsforaa.utils.BottomDialog;
import com.legs.appsforaa.utils.Version;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainActivity extends AppCompatActivity  {

    String deviceId;
    private Long[] lastTime;

    private ProgressDialog pDialog;

    String newVersionName = "";

    public static final String TIME_SERVER = "time-a.nist.gov";

    boolean doubleBackToExitPressedOnce = false;

    public static List<String> SUPPORTED_ABIS = Arrays.asList(Build.SUPPORTED_ABIS);
    private Boolean[] verified;
    private boolean eligible;
    private TextView remainingDownloads;
    long currentTime;
    private long ts;
    private DatabaseReference mySecondRef;
    private boolean unsupportedVersion;
    private boolean pendingInstallation = false;
    private String pendingPackageName = null;
    private long installationStartTime = 0;
    private static final long INSTALLATION_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes timeout

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        getApplicationContext();
        final SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        
        // Check for any pending installation from previous session
        pendingInstallation = sharedPreferences.getBoolean("pending_installation", false);
        pendingPackageName = sharedPreferences.getString("pending_package_name", null);
        installationStartTime = sharedPreferences.getLong("installation_start_time", 0);


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Handle installation result
        handleInstallationResult(getIntent());
        
        // Handle pro status refresh request
        handleProStatusRefresh(getIntent());

        requestLatest();

        remainingDownloads = findViewById(R.id.remaining_downloads);
        verified = new Boolean[1];
        verified[0] = false; // Initialize to false to prevent NullPointerException

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE);
        final DatabaseReference myRef = database.getReference("users");
        mySecondRef = database.getReference("lastdownload");

        final ProgressBar pb = findViewById(R.id.loading_circle);
        final TextView connecting = findViewById(R.id.connecting);

        Intent intent = getIntent();
        final Uri iData = intent.getData();

        //BEGIN THE CHECK FOR LICENSE AND LAST DOWNLOAD
        myRef.child(deviceId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    //USER ALREADY OPENED AAAD
                    verified[0] = snapshot.getValue(Boolean.class);
                    if (verified[0] != null && verified[0]) {
                        //USER HAS PRO VERSION
                        remainingDownloads.setText(R.string.congratsPro);
                        pb.setVisibility(View.GONE);
                        connecting.setVisibility(View.GONE);
                        eligible = true;
                        if (iData != null) {
                            downloadS2A(iData.toString());
                        }
                    } else {
                        //USER HAS NO PRO VERSION
                        mySecondRef.child(deviceId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    try {
                                        //BEGIN CHECK FOR LAST DOWNLOAD DATE
                                        lastTime = new Long[1];
                                        mReadDataOnce("lastdownload/" + deviceId , new OnGetDataListener() {
                                            @Override
                                            public void onStart() { }

                                            @Override
                                            public void onSuccess(DataSnapshot data) {
                                                lastTime[0] = data.getValue(Long.class);
                                                try {
                                                    getTime();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }

                                                // Calculate exact 30 days in milliseconds (30 * 24 * 60 * 60 * 1000)
                                                long thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L;
                                                long nextAvailableTime = lastTime[0] + thirtyDaysInMillis;
                                                boolean latestTime = currentTime > nextAvailableTime;


                                                SpannableString mySpannableString;
                                                if (latestTime) {
                                                    mySpannableString = new SpannableString(getResources().getQuantityString(R.plurals.remaining_downloads, 1, 1));
                                                    mySpannableString.setSpan(new UnderlineSpan(), 0, mySpannableString.length(), 0);
                                                    remainingDownloads.setText(mySpannableString);
                                                    pb.setVisibility(View.GONE);
                                                    connecting.setVisibility(View.GONE);
                                                    eligible = true;
                                                    if (iData != null) {
                                                        downloadS2A(iData.toString());
                                                    }
                                                } else {
                                                    mySpannableString = new SpannableString(getResources().getQuantityString(R.plurals.remaining_downloads, 0, 0));
                                                    mySpannableString.setSpan(new UnderlineSpan(), 0, mySpannableString.length(), 0);
                                                    remainingDownloads.setText(mySpannableString);
                                                    pb.setVisibility(View.GONE);
                                                    connecting.setVisibility(View.GONE);

                                                    eligible = false;
                                                    if (iData != null) {
                                                        shakeButton();
                                                    }
                                                }


                                            }

                                            @Override
                                            public void onFailed(DatabaseError databaseError) {
                                                remainingDownloads.setText(R.string.connect_error);
                                                pb.setVisibility(View.GONE);
                                                connecting.setVisibility(View.GONE);

                                            }
                                        });

                                    } catch (Exception e) {
                                        Toast.makeText(MainActivity.this, getString(R.string.connection_error), Toast.LENGTH_LONG).show();
                                        e.printStackTrace();
                                    }
                                } else {
                                    eligible = true;
                                    if (iData != null) {
                                        downloadS2A(iData.toString());
                                    }
                                    SpannableString mySpannableString = new SpannableString(getResources().getQuantityString(R.plurals.remaining_downloads, 1,1));
                                    mySpannableString.setSpan(new UnderlineSpan(), 0, mySpannableString.length(), 0);
                                    remainingDownloads.setText(mySpannableString);
                                    pb.setVisibility(View.GONE);
                                    connecting.setVisibility(View.GONE);

                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                            }
                        });

                        if (verified[0] == null || !verified[0]) {
                            remainingDownloads.setOnClickListener(v -> {
                                final Intent intent1 = new Intent(MainActivity.this, AboutPaymentActivity.class);
                                if (!eligible) {
                                    // Pass the next available download time instead of last download time
                                    long thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L;
                                    long nextAvailableTime = ts + thirtyDaysInMillis;
                                    intent1.putExtra("date", nextAvailableTime);
                                }
                                startActivity(intent1);
                            });
                        }
                    }
                } else {
                    verified[0] = false; //SINCE IT'S A NEW USER IT HASN'T YET UPGRADED
                    myRef.child(deviceId).setValue(Boolean.FALSE, (databaseError, databaseReference) -> {
                        //PUSH THE DEVICE ID TO THE DATABASE FOR PRO VERIFICATION
                        if (databaseError != null) {
                            Toast.makeText(MainActivity.this, getString(R.string.connection_error), Toast.LENGTH_LONG).show();
                            remainingDownloads.setText(R.string.notConnected);
                        } else {
                            //VALUE PUSHED
                            pb.setVisibility(View.GONE);
                            connecting.setVisibility(View.GONE);
                            eligible = true;
                            if (iData != null) {
                                downloadS2A(iData.toString());
                            }
                            SpannableString mySpannableString = new SpannableString(getResources().getQuantityString(R.plurals.remaining_downloads, 1,1));
                            mySpannableString.setSpan(new UnderlineSpan(), 0, mySpannableString.length(), 0);
                            remainingDownloads.setText(mySpannableString);
                            pb.setVisibility(View.GONE);
                            connecting.setVisibility(View.GONE);
                            remainingDownloads.setOnClickListener(v -> {
                                final Intent intent12 = new Intent(MainActivity.this, AboutPaymentActivity.class);
                                if (!eligible) {
                                    // Pass the next available download time instead of last download time
                                    long thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L;
                                    long nextAvailableTime = ts + thirtyDaysInMillis;
                                    intent12.putExtra("date", nextAvailableTime);
                                }
                                startActivity(intent12);
                            });
                        }
                    });

                    myRef.push();
                }



            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText (MainActivity.this, getString(R.string.connection_error), Toast.LENGTH_LONG).show ();
                remainingDownloads.setText(R.string.notConnected);
                pb.setVisibility(View.GONE);

                shakeButton();
            }
        });

        mySecondRef.child(deviceId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ts = 0;
                if (snapshot.exists()) {
                    ts = snapshot.getValue(Long.class);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                remainingDownloads.setText(R.string.notConnected);
                pb.setVisibility(View.GONE);

                shakeButton();
            }
        });

        //BUTTONS


        Button carStreamButton = findViewById(R.id.download_carstream);
        carStreamButton.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadCarStream();
                }
            } else {
                shakeButton();
            }
        });
        setLongClickListener(carStreamButton, R.string.carstream_description);

        Button fermataAutoButton = findViewById(R.id.download_fermata);
        setLongClickListener(fermataAutoButton, R.string.fermata_description);
        fermataAutoButton.setOnClickListener(v -> {

            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {

                    String baseUrl = "https://api.github.com/repos/AndreyPavlenko/Fermata/releases/latest";

                    RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                    final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();


                    final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                            (Request.Method.GET, baseUrl, null, response -> {
                                try {

                                    alertDialog.setMessage(getString(R.string.fermata_control_dialog, "Fermata Auto", response.getString("name"), "Fermata Control"));

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } finally {

                                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            downloadFermata(true);
                                            alertDialog.dismiss();
                                        }
                                    });

                                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), (dialog, which) -> {
                                        downloadFermata(false);
                                        alertDialog.dismiss();
                                    });

                                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            alertDialog.dismiss();
                                        }
                                    });
                                    alertDialog.show();
                                }

                            }, error -> {

                            });


                    queue.add(jsonObjectRequest);
                }
            } else {
                shakeButton();
            }

        });

        Button aamirrrorplusButton = findViewById(R.id.download_aamirror_plus);
        setLongClickListener(aamirrrorplusButton, R.string.aa_mirror_plus_description);
        aamirrrorplusButton.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadAAMirrorPlus();
                }

            } else shakeButton();
        });

        Button performanceMonitor = findViewById(R.id.download_performance_monitor);
        setLongClickListener(performanceMonitor, R.string.performance_monitor_description);
        performanceMonitor.setOnClickListener(v -> {

            if (eligible) {

                if (needsPermission()) {
                    askForStoragePermission();
                } else {


                    String baseUrl = "https://api.github.com/repos/jilleb/mqb-pm/releases/latest";

                    RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                    final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();


                    final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                            (Request.Method.GET, baseUrl, null, response -> {
                                try {

                                    alertDialog.setMessage(getString(R.string.fermata_control_dialog, "Performance Monitor", response.getString("tag_name"), "Extensions For Vag"));

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } finally {

                                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            downloadPM(true);
                                            alertDialog.dismiss();
                                        }
                                    });

                                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            downloadPM(false);
                                            alertDialog.dismiss();
                                        }
                                    });

                                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            alertDialog.dismiss();
                                        }
                                    });
                                    alertDialog.show();
                                }

                            }, new Response.ErrorListener() {


                                @Override
                                public void onErrorResponse(VolleyError error) {

                                }
                            });


                    queue.add(jsonObjectRequest);
                }
            } else {
                shakeButton();
            }

        });

        Button aaTorque = findViewById(R.id.download_aatorque);
        setLongClickListener(aaTorque, R.string.aatorque_description);
        aaTorque.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadAATorque();
                }
            } else shakeButton();
        });

        Button aaPassenger = findViewById(R.id.download_aa_passenger);
        setLongClickListener(aaPassenger, R.string.aapassenger_description);
        aaPassenger.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadAAP();
                }
            } else shakeButton();
        });

        Button s2a = findViewById(R.id.download_screentwoauto);
        setLongClickListener(s2a, R.string.s2a_description);
        s2a.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    // Download Screen2Auto 3.7 directly from Firebase Storage
                    downloadS2A(null); // URL is now hardcoded in downloadS2A method
                }
            } else {
                shakeButton();
            }
        });

        Button aamirrorButton = findViewById(R.id.download_aamirror);
        setLongClickListener(aamirrorButton, R.string.aamirror_description);
        aamirrorButton.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadAAMirror();
                }
            } else {
                shakeButton();
            }
        });

        Button aawidgets = findViewById(R.id.download_aa_widgets);
        setLongClickListener(aawidgets, R.string.aawidgets_description);
        aawidgets.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadAAWidgets();
                }

            } else {
                shakeButton();
            }
        });

        Button aaStreamButton = findViewById(R.id.download_aa_stream);
        setLongClickListener(aaStreamButton, R.string.aa_stream_description);
        aaStreamButton.setOnClickListener(v -> {
            if (eligible) {
                if (needsPermission()) {
                    askForStoragePermission();
                } else {
                    downloadAAStream();
                }

            } else {
                shakeButton();
            }
        });

        Button nav2contacts = findViewById(R.id.download_nav2contacts);
        setLongClickListener(nav2contacts, R.string.nav2contacts_description);
        nav2contacts.setOnClickListener(v -> {

            if (eligible) {


                if (Build.VERSION.SDK_INT < 26) {
                    notMeetingSDK(8);
                } else {
                    String baseUrl = "https://api.github.com/repos/frankkienl/Nav2Contacts/releases/latest";

                    RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                    final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();


                    final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                            (Request.Method.GET, baseUrl, null, new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    try {

                                        alertDialog.setMessage(getString(R.string.about_to_download, "Nav2Contacts " + response.getString("tag_name")));

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    } finally {

                                        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                downloadN2C();
                                                alertDialog.dismiss();
                                            }
                                        });

                                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                alertDialog.dismiss();
                                            }
                                        });
                                        alertDialog.show();
                                    }

                                }
                            }, new Response.ErrorListener() {


                                @Override
                                public void onErrorResponse(VolleyError error) {

                                }
                            });


                    queue.add(jsonObjectRequest);
                }
            } else {
                shakeButton();
            }

        });

        //HEADERS

        RelativeLayout firstHeader = findViewById(R.id.first_header);
        TextView firstHeaderTextView = firstHeader.findViewById(R.id.header_text);
        firstHeaderTextView.setText(R.string.first_section_name);

        RelativeLayout secondHeader = findViewById(R.id.second_header);
        TextView secondHeaderTextView = secondHeader.findViewById(R.id.header_text);
        secondHeaderTextView.setText(R.string.second_section_name);


        RelativeLayout thirdHeader = findViewById(R.id.third_header);
        TextView thirdHeaderTextView = thirdHeader.findViewById(R.id.header_text);
        thirdHeaderTextView.setText(R.string.third_section_name);




        if (!SUPPORTED_ABIS.contains("arm64-v8a") && !SUPPORTED_ABIS.contains("armeabi-v7a") && sharedPreferences.getBoolean("ignored_abi", false)) {
            final BottomDialog builder2 = new BottomDialog.Builder(MainActivity.this)
                    .setTitle(R.string.attention)
                    .setContent(getString(R.string.cpu_architecture_warning))
                    .setPositiveBackgroundColor(R.color.colorPrimary)
                    .setPositiveText(R.string.i_understand)
                    .setNegativeText(R.string.ignore)
                    .onPositive(new BottomDialog.ButtonCallback() {
                        @Override
                        public void onClick(@NonNull BottomDialog dialog) {
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putBoolean("ignored_abi", true);
                            editor.apply();
                        }
                    })
                    .onNegative(new BottomDialog.ButtonCallback() {
                        @Override
                        public void onClick(@NonNull BottomDialog dialog) {

                        }
                    })
                    .setBackgroundColor(R.color.centercolor).build();

            builder2.show();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {

            final BottomDialog builder2 = new BottomDialog.Builder(MainActivity.this)
                    .setTitle(R.string.attention)
                    .setContent(getString(R.string.unknown_sources_warning))
                    .setPositiveBackgroundColor(R.color.colorPrimary)
                    .setPositiveText(android.R.string.ok)
                    .setNegativeText(R.string.ignore)
                    .onPositive(new BottomDialog.ButtonCallback() {
                        @Override
                        public void onClick(@NonNull BottomDialog dialog) {
                            Intent unKnownSourceIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                            startActivity(unKnownSourceIntent);
                        }
                    })
                    .onNegative(new BottomDialog.ButtonCallback() {
                        @Override
                        public void onClick(@NonNull BottomDialog dialog) {

                        }
                    })
                    .setBackgroundColor(R.color.centercolor).build();

            builder2.show();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {

                final BottomDialog builder2 = new BottomDialog.Builder(MainActivity.this)
                        .setTitle(R.string.attention)
                        .setContent(getString(R.string.unknown_sources_warning))
                        .setPositiveBackgroundColor(R.color.colorPrimary)
                        .setPositiveText(android.R.string.ok)
                        .setNegativeText(R.string.ignore)
                        .onPositive(new BottomDialog.ButtonCallback() {
                            @Override
                            public void onClick(@NonNull BottomDialog dialog) {
                                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
                            }
                        })
                        .onNegative(new BottomDialog.ButtonCallback() {
                            @Override
                            public void onClick(@NonNull BottomDialog dialog) {

                            }
                        })
                        .setBackgroundColor(R.color.centercolor).build();

                builder2.show();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && unsupportedVersion) {
                final BottomDialog builder2 = new BottomDialog.Builder(MainActivity.this)
                        .setTitle(R.string.attention)
                        .setContent(getString(R.string.android_auto_update))
                        .setPositiveBackgroundColor(R.color.colorPrimary)
                        .setPositiveText(android.R.string.ok)
                        .setNegativeText(R.string.ignore)
                        .onPositive(new BottomDialog.ButtonCallback() {
                            @Override
                            public void onClick(@NonNull BottomDialog dialog) {
                                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
                            }
                        })
                        .onNegative(new BottomDialog.ButtonCallback() {
                            @Override
                            public void onClick(@NonNull BottomDialog dialog) {

                            }
                        })
                        .setBackgroundColor(R.color.centercolor).build();

                builder2.show();

            }
        }


    }

    private boolean needsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    private void askForStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    101);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    101);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        File dir = new File(getApplicationContext().getExternalFilesDir("AAAD") + "/");
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (String child : children) {
                new File(dir, child).delete();
            }
        }
        finish();

    }

    private void shakeButton() {

        final Animation animShake = AnimationUtils.loadAnimation(this, R.anim.shake);
        TextView downloadRemainings = findViewById(R.id.remaining_downloads);
        downloadRemainings.startAnimation(animShake);

    }


    public void mReadDataOnce(String child, final OnGetDataListener listener) {
        listener.onStart();
        FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE).getReference().child(child).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NotNull DataSnapshot dataSnapshot) {
                listener.onSuccess(dataSnapshot);
            }

            @Override
            public void onCancelled(@NotNull DatabaseError databaseError) {
                listener.onFailed(databaseError);
            }
        });
    }

    private void getTime() throws IOException {

        runOnUiThread(new Thread() {
            @Override
            public void run() {
                try {

                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);
                    NTPUDPClient timeClient = new NTPUDPClient();
                    InetAddress inetAddress = InetAddress.getByName(TIME_SERVER);
                    TimeInfo timeInfo = timeClient.getTime(inetAddress);
                    currentTime = timeInfo.getMessage().getReceiveTimeStamp().getTime();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.infos : {
                DialogFragment aboutDialog = new AboutDialog();
                Bundle b = new Bundle();
                b.putString("did", deviceId);
                aboutDialog.setArguments(b);
                aboutDialog.show(getSupportFragmentManager(), "AboutDialog");
                break;
            }
            case R.id.help : {
                DialogFragment contactDialog = new ContactDialog();
                contactDialog.show(getSupportFragmentManager(), "ContactDialog");
                break;
            }
            case R.id.transfer_license : {
                startActivity(new Intent(this, TransferLicense.class));
                break;
            }
            case R.id.show_intro : {
                startActivity(new Intent(this, OnboardingActivityNew.class));
                break;
            }
            default: break;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }



    public interface OnGetDataListener {
        void onStart();
        void onSuccess(DataSnapshot data);
        void onFailed(DatabaseError databaseError);
    }


    public void downloadFermata (final boolean control) {

        final ArrayList<String> downloadURLS = new ArrayList<String>();

        String baseUrl = "https://api.github.com/repos/AndreyPavlenko/Fermata/releases/latest";

        RequestQueue queue = Volley.newRequestQueue(this.getApplicationContext());


        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, baseUrl, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {

                            JSONArray allAssets = response.getJSONArray("assets");

                            for (int i = 0; i < allAssets.length(); i++) {

                                JSONObject thisObj = (JSONObject) allAssets.get(i);
                                if (thisObj.getString("name").contains("arm64") && SUPPORTED_ABIS.contains("arm64-v8a")) {
                                    downloadURLS.add(thisObj.getString("browser_download_url"));
                                }
                                if (thisObj.getString("name").contains("arm.apk") && !SUPPORTED_ABIS.contains("arm64-v8a") && SUPPORTED_ABIS.contains("armeabi-v7a")) {
                                    downloadURLS.add(thisObj.getString("browser_download_url"));
                                }
                                if (control && thisObj.getString("name").contains("control")) {
                                    downloadURLS.add(thisObj.getString("browser_download_url"));
                                }
                            }




                        } catch (JSONException e) {
                            e.printStackTrace();
                        } finally {
                            pDialog = ProgressDialog.show(MainActivity.this, "",
                                    getString(R.string.loading), true);
                            pDialog.show();

                            if (downloadURLS.isEmpty()) {
                                dismissProgressDialog(pDialog);
                                final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                                alertDialog.setMessage(getString(R.string.might_not_be_compatible));

                                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        alertDialog.dismiss();
                                    }
                                });
                                alertDialog.show();
                            }

                            for (int i = 0; i< Objects.requireNonNull(downloadURLS).size(); i++) {
                                final File file;


                                    file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "fermata" + i + ".apk");



                                final int finalI = i;
                                new Handler().postDelayed(new Runnable() {

                                    @Override
                                    public void run() {
                                        GitHubDownloader downloader = new GitHubDownloader(MainActivity.this, file, downloadURLS.get(finalI));
                                        downloader.startDownload();
                                    }
                                },3000);

                            }
                        }

                    }
                }, new Response.ErrorListener() {


                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });
        queue.add(jsonObjectRequest);
    }

    public void downloadCarStream () {


        final String[] url = new String[1];
        url[0] = "";

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        CharSequence[] items = new CharSequence[]{"CarStream 2.0.5 by Paesani2006", "CarStream 2.0.4 by Kristakos", "CarStream 2.0.2 by Eselter"};
        adb.setSingleChoiceItems(items, 4, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface d, int n) {
                switch (n) {
                    case 0:
                        url[0] = BuildConfig.CARSTREAM205_LINK;
                        break;
                    case 1:
                        url[0] = BuildConfig.CARSTREAM204_LINK;
                        break;
                    case 2:
                        url[0] = BuildConfig.CARSTREAM202_LINK;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + n);
                }
            }

        });
        adb.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File file =  new File(getApplicationContext().getExternalFilesDir("AAAD") , "carstream" + ".apk");


                pDialog = ProgressDialog.show(MainActivity.this, "",
                        getString(R.string.loading), true);
                pDialog.show();

                final File finalFile = file;
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        Downloader downlaoder = new Downloader(MainActivity.this, finalFile, url[0]);
                        downlaoder.run();
                        pDialog.dismiss();
                    }
                }, 3000);
            }
        });
        adb.setNegativeButton(getString(android.R.string.cancel), null);
        adb.setTitle(R.string.select_which);
        adb.show();


    }

    public void downloadAAMirrorPlus () {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage(getString(R.string.about_to_download, "AAMirror Plus v. 1.01a"));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {


                alertDialog.dismiss();


                final String url = BuildConfig.AAMP_LINK;
                final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "aamp" + ".apk");
                pDialog = ProgressDialog.show(MainActivity.this, "",
                        getString(R.string.loading), true);
                pDialog.show();

                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        Downloader downlaoder = new Downloader(MainActivity.this, file, url);
                        downlaoder.run();
                        pDialog.dismiss();
                    }
                }, 3000);


            }
        });

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();


    }

    public void downloadAAMirror () {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage(getString(R.string.about_to_download, "AAMirror 1.0"));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                alertDialog.dismiss();

                final String url = BuildConfig.AAMIRROR_LINK;
                final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "aamirror" + ".apk");

                pDialog = ProgressDialog.show(MainActivity.this, "",
                        getString(R.string.loading), true);
                pDialog.show();

                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        Downloader downlaoder = new Downloader(MainActivity.this, file, url);
                        downlaoder.run();
                        pDialog.dismiss();
                    }
                }, 3000);

            }
        });

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();

    }

    public void downloadAAWidgets () {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage(getString(R.string.about_to_download, "Widgets For AA 0.2.0"));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                alertDialog.dismiss();

                final String url = BuildConfig.AAWIDGETS_LINK;

                final File file= new File(getApplicationContext().getExternalFilesDir("AAAD") , "aawidgets" + ".apk");


                pDialog = ProgressDialog.show(MainActivity.this, "",
                        getString(R.string.loading), true);
                pDialog.show();

                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        Downloader downlaoder = new Downloader(MainActivity.this, file, url);
                        downlaoder.run();
                        pDialog.dismiss();
                    }
                }, 3000);

            }
        });

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), (dialog, which) -> alertDialog.dismiss());
        alertDialog.show();




    }

    public void downloadAAStream () {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage(getString(R.string.about_to_download, "AAStream 1.1.0.29"));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), (dialog, which) -> {

            alertDialog.dismiss();
            final String url = BuildConfig.AASTREAM_LINK;
            final File file= new File(getApplicationContext().getExternalFilesDir("AAAD") , "aastream" + ".apk");
            pDialog = ProgressDialog.show(MainActivity.this, "",
                    getString(R.string.loading), true);
            pDialog.show();
            new Handler().postDelayed(() -> {
                Downloader downlaoder = new Downloader(MainActivity.this, file, url);
                downlaoder.run();
                pDialog.dismiss();
            }, 3000);
        });

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), (dialog, which) -> alertDialog.dismiss());
        alertDialog.show();


    }

    public void downloadPM (final boolean extension) {

        final ArrayList<String> downloadURLS = new ArrayList<>();

        String baseUrl = "https://api.github.com/repos/jilleb/mqb-pm/releases/latest";
        String baseUrl2 = "https://api.github.com/repos/martoreto/aauto-vex-vag/releases/latest";


        RequestQueue queue = Volley.newRequestQueue(this.getApplicationContext());


        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, baseUrl, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {

                            JSONArray allAssets = response.getJSONArray("assets");

                            for (int i = 0; i < allAssets.length(); i++) {

                                JSONObject thisObj = (JSONObject) allAssets.get(i);
                                if (thisObj.getString("name").contains("apk") ) {
                                    downloadURLS.add(thisObj.getString("browser_download_url"));
                                }
                            }


                        } catch (JSONException e) {
                            e.printStackTrace();
                        } finally {
                            pDialog = ProgressDialog.show(MainActivity.this, "",
                                    getString(R.string.loading), true);
                            pDialog.show();


                            for (int i = 0; i< Objects.requireNonNull(downloadURLS).size(); i++) {

                                final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "pm" + i + ".apk");



                                final int finalI = i;
                                new Handler().postDelayed(new Runnable() {

                                    @Override
                                    public void run() {
                                        GitHubDownloader downlaoder = new GitHubDownloader(MainActivity.this, file, downloadURLS.get(finalI));
                                        downlaoder.startDownload();
                                    }
                                },3000);

                            }
                        }

                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });

        if (extension) {

            final JsonObjectRequest jsonObjectRequest2 = new JsonObjectRequest
                    (Request.Method.GET, baseUrl2, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {

                                JSONArray allAssets = response.getJSONArray("assets");

                                for (int i = 0; i < allAssets.length(); i++) {

                                    JSONObject thisObj = (JSONObject) allAssets.get(i);
                                    if (thisObj.getString("name").contains("apk")) {
                                        downloadURLS.add(thisObj.getString("browser_download_url"));
                                    }
                                }


                            } catch (JSONException e) {
                                e.printStackTrace();
                            } finally {


                                for (int i = 0; i < Objects.requireNonNull(downloadURLS).size(); i++) {
                                    final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "pm" + i + ".apk");



                                    final int finalI = i;
                                    new Handler().postDelayed(new Runnable() {

                                        @Override
                                        public void run() {
                                            GitHubDownloader downlaoder = new GitHubDownloader(MainActivity.this, file, downloadURLS.get(finalI));
                                            downlaoder.startDownload();
                                        }
                                    }, 3000);

                                }
                            }

                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {

                        }
                    });

            queue.add(jsonObjectRequest2);

        }

        queue.add(jsonObjectRequest);
    }

    public void downloadN2C () {

        final String[] downloadURL1 = {""};

        String baseUrl1 = "https://api.github.com/repos/frankkienl/Nav2Contacts/releases/latest";

        RequestQueue queue = Volley.newRequestQueue(this.getApplicationContext());


        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, baseUrl1, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {

                            JSONArray allAssets = response.getJSONArray("assets");

                            for (int i = 0; i < allAssets.length(); i++) {

                                JSONObject thisObj = (JSONObject) allAssets.get(i);
                                if (thisObj.getString("name").contains("apk")) {
                                    downloadURL1[0] = thisObj.getString("browser_download_url");
                                }
                            }


                        } catch (JSONException e) {
                            e.printStackTrace();
                        } finally {
                            pDialog = ProgressDialog.show(MainActivity.this, "",
                                    getString(R.string.loading), true);
                            pDialog.show();

                            final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "n2c" + ".apk");

                            new Handler().postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    GitHubDownloader downlaoder = new GitHubDownloader(MainActivity.this, file, downloadURL1[0]);
                                    downlaoder.startDownload();
                                }
                            }, 3000);
                        }

                    }
                }, new Response.ErrorListener() {


                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });


        queue.add(jsonObjectRequest);


    }

    public void downloadAAP () {


        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage(getString(R.string.about_to_download, "AAPassenger v1.9-alpha8"));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {


                alertDialog.dismiss();

                final String url = BuildConfig.AAPASSENGER_LINK;
                final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "aap"  + ".apk");
                pDialog = ProgressDialog.show(MainActivity.this, "",
                        getString(R.string.loading), true);
                pDialog.show();
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        Downloader downlaoder = new Downloader(MainActivity.this, file, url);
                        downlaoder.run();
                        pDialog.dismiss();
                    }
                }, 3000);

            }
        });

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();




    }

    public void downloadS2A (final String data) {

        if (eligible) {

            final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setMessage(getString(R.string.about_to_download, "Screen2Auto 3.7"));

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {

                    alertDialog.dismiss();

                    final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "s2a-3.7" + ".apk");

                    // Use direct Firebase Storage link for Screen2Auto 3.7
                    final String s2aUrl = "https://firebasestorage.googleapis.com/v0/b/appsforaa-1b443.appspot.com/o/s2a%2F3.7.apk?alt=media&token=658e85c3-22bc-44d3-be98-7a847c5b26e8";

                    pDialog = ProgressDialog.show(MainActivity.this, "",
                            getString(R.string.loading), true);
                    pDialog.show();
                    new Handler().postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            Downloader downlaoder = new Downloader(MainActivity.this, file, s2aUrl);
                            downlaoder.setScreen2auto(true);
                            downlaoder.run();
                            pDialog.dismiss();
                        }
                    }, 3000);

                }
            });

            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    alertDialog.dismiss();
                }
            });
            alertDialog.show();
        } else {
            shakeButton();
        }

    }

    private static void installAPKAlt(Context context) {
        File directory = Environment.getExternalStoragePublicDirectory("myapp_folder");

        File file = new File(directory, "myapp.apk"); // assume refers to "sdcard/myapp_folder/myapp.apk"


        Uri fileUri = Uri.fromFile(file); //for Build.VERSION.SDK_INT <= 24

        if (Build.VERSION.SDK_INT >= 24) {

            fileUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, fileUri);
        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
        intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); //dont forget add this line
        context.startActivity(intent);
    }



    void installAPK(File file) {
        dismissProgressDialog(pDialog);
        
        // Track installation for non-pro users, but don't count download yet
        if (verified[0] == null || !verified[0]) {
            pendingInstallation = true;
            installationStartTime = System.currentTimeMillis();
            // Try to extract package name from file for verification
            try {
                android.content.pm.PackageInfo packageInfo = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (packageInfo != null) {
                    pendingPackageName = packageInfo.packageName;
                }
            } catch (Exception e) {
                // If we can't get package name, we'll rely on the installation callback
                pendingPackageName = null;
            }
            // Save the pending installation state
            savePendingInstallationState();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }

        // Use modern PackageInstaller API for Android 14+ (API 34)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            installAPKWithPackageInstaller(file);
        } else {
            // Fallback to Intent-based installation for older versions
            installAPKWithIntent(file);
        }
    }

    private void installAPKWithPackageInstaller(File file) {
        try {
            PackageManager packageManager = getPackageManager();
            android.content.pm.PackageInstaller packageInstaller = packageManager.getPackageInstaller();

            // Create session parameters
            android.content.pm.PackageInstaller.SessionParams params = 
                new android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            
            // Set installer package name to Google Play Store
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                params.setInstallerPackageName("com.android.vending");
            }



            // Create session
            int sessionId = packageInstaller.createSession(params);
            android.content.pm.PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            // Copy APK data to session
            try (OutputStream out = session.openWrite("package", 0, -1);
                 FileInputStream in = new FileInputStream(file)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                session.fsync(out);
            }

            // Create pending intent for installation result
            Intent intent = new Intent(this, MainActivity.class);
            intent.setAction("PACKAGE_INSTALLED_ACTION");
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            // Commit session
            session.commit(pendingIntent.getIntentSender());
            session.close();

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to Intent method
            installAPKWithIntent(file);
        }
    }

    private void installAPKWithIntent(File file) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(getUri(file));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending");
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        
        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to install APK", Toast.LENGTH_SHORT).show();
        }
    }



    public void dismissProgressDialog(ProgressDialog pDialog) {
        if (pDialog != null && pDialog.isShowing()) {
            pDialog.dismiss();
        }
    }

    public void dismissCurrentProgressDialog() {
        if (pDialog != null && pDialog.isShowing()) {
            pDialog.dismiss();
        }
    }

    public Uri getUri(File file) {
        return FileProvider.getUriForFile(
                getApplicationContext(),
                "sksa.aa.customapps.fileProvider",
                file
        );
    }

    public void registerDownload ()  {
        // Add logging to track when downloads are counted
        android.util.Log.w("DownloadCounter", "registerDownload() called from: " + android.util.Log.getStackTraceString(new Exception()));
        
        // Immediately set eligible to false to prevent multiple downloads
        eligible = false;
        remainingDownloads.setText(getResources().getQuantityString(R.plurals.remaining_downloads, 0, 0));
        
        try {
            getTime();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mySecondRef.child(deviceId).setValue(currentTime, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(@Nullable DatabaseError databaseError, @NonNull DatabaseReference databaseReference) {
                    if (databaseError != null) {
                        // If Firebase write failed, we might want to revert eligible status
                        // but for security, we'll keep it false to prevent abuse
                        android.util.Log.e("RegisterDownload", "Failed to register download", databaseError.toException());
                    }
                    // Keep eligible = false regardless of database result for security
                    android.util.Log.i("DownloadCounter", "Download registered successfully, eligible = false");
                }
            });
            mySecondRef.push();
        }


    }

    void setLongClickListener (Button button, final int resId) {
        button.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View arg0) {
                final Dialog dialog = new Dialog(MainActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.setCancelable(true);
                View view = getLayoutInflater().inflate(R.layout.dialog_layout, null);


                TextView tutorial = view.findViewById(R.id.dialog_content);
                tutorial.setText(getString(resId));

                dialog.setContentView(view);

                dialog.show();

                Window window = dialog.getWindow();
                window.setLayout(ViewPager.LayoutParams.MATCH_PARENT, WRAP_CONTENT);

                return true;
            }
        });

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.version);
        item.setTitle("V." + BuildConfig.VERSION_NAME);
        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleInstallationResult(intent);
        handleProStatusRefresh(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if there was a pending installation that completed while the app was in background
        if (pendingInstallation) {
            // Check for timeout (installation taking too long, likely failed or cancelled)
            if (System.currentTimeMillis() - installationStartTime > INSTALLATION_TIMEOUT_MS) {
                showInstallationTimeoutDialog();
            } else if (pendingPackageName != null) {
                checkAndRegisterInstallation();
            }
        }
        
        // Also periodically check pro status in case it was updated elsewhere
        // but only if user is not already verified as pro
        if (verified[0] == null || !verified[0]) {
            checkProStatusQuietly();
        }
    }

    private void checkAndRegisterInstallation() {
        if (pendingPackageName != null) {
            android.util.Log.i("DownloadCounter", "Checking installation for package: " + pendingPackageName);
            try {
                // Check if the package is now installed
                getPackageManager().getPackageInfo(pendingPackageName, 0);
                // If we get here, the package is installed successfully
                android.util.Log.i("DownloadCounter", "Package found installed: " + pendingPackageName);
                if (pendingInstallation) {
                    // Additional safety check: only register if installation check happens soon after initiation
                    long timeSinceInstallationStarted = System.currentTimeMillis() - installationStartTime;
                    if (timeSinceInstallationStarted < 60000) { // Within 1 minute
                        android.util.Log.i("DownloadCounter", "pendingInstallation=true, installation detected within 1 minute, registering download");
                        // Register download for non-pro users (this handles Intent-based installations)
                        if (verified[0] == null || !verified[0]) {
                            registerDownload();
                        }
                        clearPendingInstallation();
                        Toast.makeText(this, getString(R.string.app_installed_successfully), Toast.LENGTH_SHORT).show();
                    } else {
                        android.util.Log.w("DownloadCounter", "Package found but too much time passed (" + timeSinceInstallationStarted + "ms), not registering download");
                        clearPendingInstallation();
                    }
                } else {
                    android.util.Log.i("DownloadCounter", "pendingInstallation=false, not registering download");
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                // Package not installed yet, keep waiting
                android.util.Log.i("DownloadCounter", "Package not found: " + pendingPackageName);
            }
        } else {
            android.util.Log.i("DownloadCounter", "checkAndRegisterInstallation called but pendingPackageName is null");
        }
    }

    private void showInstallationTimeoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.installation_timeout_title));
        builder.setMessage("Installation verification timed out. We couldn't confirm if the app was installed successfully.");
        builder.setPositiveButton(getString(android.R.string.ok), (dialog, which) -> {
            // Just clear the pending state - download was already counted when installation started
            clearPendingInstallation();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void clearPendingInstallation() {
        pendingInstallation = false;
        pendingPackageName = null;
        installationStartTime = 0;
        
        // Clear from preferences
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        prefs.edit()
            .remove("pending_installation")
            .remove("pending_package_name")
            .remove("installation_start_time")
            .apply();
    }

    private void savePendingInstallationState() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        prefs.edit()
            .putBoolean("pending_installation", pendingInstallation)
            .putString("pending_package_name", pendingPackageName)
            .putLong("installation_start_time", installationStartTime)
            .apply();
    }

    private void handleProStatusRefresh(Intent intent) {
        if (intent != null && intent.getBooleanExtra("refresh_pro_status", false)) {
            // Force refresh the pro status from Firebase
            refreshProStatusFromFirebase();
        }
    }

    private void refreshProStatusFromFirebase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE);
        DatabaseReference userRef = database.getReference("users").child(deviceId);
        
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean proStatus = snapshot.getValue(Boolean.class);
                    if (proStatus != null && proStatus) {
                        // User is now pro - update UI immediately
                        verified[0] = true;
                        updateUIForProUser();
                        Toast.makeText(MainActivity.this, getString(R.string.congratsPro), Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silent fail - user can manually refresh or restart app
            }
        });
    }

    private void updateUIForProUser() {
        remainingDownloads.setText(R.string.congratsPro);
        eligible = true;
        
        // Remove the click listener that opens payment activity
        remainingDownloads.setOnClickListener(null);
        
        // Clear any pending installation state since user is now pro
        clearPendingInstallation();
    }

    private void checkProStatusQuietly() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE);
        DatabaseReference userRef = database.getReference("users").child(deviceId);
        
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean proStatus = snapshot.getValue(Boolean.class);
                    if (proStatus != null && proStatus) {
                        // User is now pro - update UI immediately (no toast, it's quiet)
                        verified[0] = true;
                        updateUIForProUser();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Silent fail
            }
        });
    }

    private void handleInstallationResult(Intent intent) {
        if (intent != null && "PACKAGE_INSTALLED_ACTION".equals(intent.getAction())) {
            // Check if installation was successful by looking at the result
            int resultCode = intent.getIntExtra("android.content.pm.extra.STATUS", -1);
            if (resultCode == 0) { // PackageInstaller.STATUS_SUCCESS
                // Installation successful - now register the download
                if ((verified[0] == null || !verified[0]) && pendingInstallation) {
                    registerDownload();
                    clearPendingInstallation();
                }
                Toast.makeText(this, getString(R.string.app_installed_successfully), Toast.LENGTH_SHORT).show();
            } else {
                // Installation failed or was cancelled - don't register download
                clearPendingInstallation();
                String errorMessage = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
                Toast.makeText(this, (errorMessage != null ? errorMessage : "Unknown error"), Toast.LENGTH_LONG).show();
                Toast.makeText(this, getString(R.string.download_not_counted), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            this.finishAffinity();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, getString(R.string.press_back_warning), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }



    public void requestLatest() {

        RequestQueue queue = Volley.newRequestQueue(this.getApplicationContext());

        String BASE_URL = "https://api.github.com/repos/shmykelsa/AAAD/releases/latest";

        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, BASE_URL, null, new Response.Listener<JSONObject>() {

                    private String fetchedVersion;

                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            fetchedVersion = response.getString("tag_name");

                        } catch (JSONException e) {
                            newVersionName = null;
                            e.printStackTrace();
                        } finally {
                            Version actualCheck = new Version(BuildConfig.VERSION_NAME);
                            Version newCheck = new Version(fetchedVersion.substring(1));

                            if (actualCheck.compareTo(newCheck) < 0) {
                                newVersionName = fetchedVersion.substring(1);

                                final BottomDialog builder2 = new BottomDialog.Builder(MainActivity.this)
                                        .setTitle(R.string.new_version_available)
                                        .setContent(getString(R.string.go_to_new_version, newVersionName))
                                        .setPositiveBackgroundColor(R.color.colorPrimary)
                                        .setPositiveText(R.string.go_to_download)
                                        .setNegativeText(R.string.ignore_for_now)
                                        .onPositive(new BottomDialog.ButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull BottomDialog dialog) {
                                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shmykelsa/AAAD/releases/")));
                                            }
                                        })
                                        .onNegative(new BottomDialog.ButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull BottomDialog dialog) {

                                            }
                                        })
                                        .setBackgroundColor(R.color.centercolor).build();

                                builder2.show();

                            }
                        }

                    }
                }, error -> newVersionName = null);
        queue.add(jsonObjectRequest);

    }

    public void notMeetingSDK (int version) {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();

        alertDialog.setMessage(getString(R.string.meeting_requirements, version));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });

        alertDialog.show();


    }

    public void downloadAATorque() {
        String apiUrl = "https://api.github.com/repos/agronick/aa-torque/releases/latest";
        
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
            Request.Method.GET, apiUrl, null,
            response -> {
                try {
                    String tagName = response.getString("tag_name");
                    JSONArray assets = response.getJSONArray("assets");
                    
                    String downloadUrl = null;
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.getString("name");
                        if (assetName.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url");
                            break;
                        }
                    }
                    
                    if (downloadUrl != null) {
                        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setMessage(getString(R.string.fermata_control_dialog, "AATorque", tagName, "agronick"));
                        
                        final String finalDownloadUrl = downloadUrl;
                        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), (dialog, which) -> {
                            pDialog = ProgressDialog.show(MainActivity.this, "", getString(R.string.loading), true);
                            
                            final File file = new File(getApplicationContext().getExternalFilesDir("AAAD"), "aatorque.apk");
                            GitHubDownloader downloader = new GitHubDownloader(MainActivity.this, file, finalDownloadUrl);
                            downloader.startDownload();
                            
                            alertDialog.dismiss();
                        });
                        
                        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel), (dialog, which) -> {
                            alertDialog.dismiss();
                        });
                        
                        alertDialog.show();
                    } else {
                        Toast.makeText(this, "APK not found in latest release", Toast.LENGTH_SHORT).show();
                    }
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error parsing release data", Toast.LENGTH_SHORT).show();
                }
            },
            error -> {
                Toast.makeText(this, "Error fetching release data", Toast.LENGTH_SHORT).show();
            }
        );
        
        queue.add(jsonObjectRequest);
    }

}