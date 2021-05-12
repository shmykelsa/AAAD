package com.legs.appsforaa;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import java.io.IOException;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        getApplicationContext();
        final SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestLatest();

        remainingDownloads = findViewById(R.id.remaining_downloads);
        verified = new Boolean[1];

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
                    if (verified[0]) {
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

                                                long computeNext = currentTime - 2629743000L;
                                                boolean latestTime = computeNext > lastTime[0];


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

                        if (!verified[0]) {
                            remainingDownloads.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    final Intent intent = new Intent(MainActivity.this, AboutPaymentActivity.class);
                                    if (!eligible) {
                                        intent.putExtra("date", ts);
                                    }
                                    startActivity(intent);
                                }
                            });
                        }
                    }
                } else {
                    verified[0] = false; //SINCE IT'S A NEW USER IT HASN'T YET UPGRADED
                    myRef.child(deviceId).setValue(Boolean.FALSE, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(@Nullable DatabaseError databaseError, @NonNull DatabaseReference databaseReference) {
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
                                remainingDownloads.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        final Intent intent = new Intent(MainActivity.this, AboutPaymentActivity.class);
                                        if (!eligible) {
                                            intent.putExtra("date", ts);
                                        }
                                        startActivity(intent);
                                    }
                                });
                            }
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
        carStreamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        downloadCarStream();
                    }
                } else {
                    shakeButton();
                }
            }
        });
        setLongClickListener(carStreamButton, R.string.carstream_description);

        Button fermataAutoButton = findViewById(R.id.download_fermata);
        setLongClickListener(fermataAutoButton, R.string.fermata_description);
        fermataAutoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {

                        String baseUrl = "https://api.github.com/repos/AndreyPavlenko/Fermata/releases/latest";

                        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();


                        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                                (Request.Method.GET, baseUrl, null, new Response.Listener<JSONObject>() {
                                    @Override
                                    public void onResponse(JSONObject response) {
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

                                            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    downloadFermata(false);
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

            }
        });

        Button aamirrrorplusButton = findViewById(R.id.download_aamirror_plus);
        setLongClickListener(aamirrrorplusButton, R.string.aa_mirror_plus_description);
        aamirrrorplusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        downloadAAMirrorPlus();
                    }

                } else shakeButton();
            }
        });

        Button performanceMonitor = findViewById(R.id.download_performance_monitor);
        setLongClickListener(performanceMonitor, R.string.performance_monitor_description);
        performanceMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (eligible) {

                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {


                        String baseUrl = "https://api.github.com/repos/jilleb/mqb-pm/releases/latest";

                        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();


                        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                                (Request.Method.GET, baseUrl, null, new Response.Listener<JSONObject>() {
                                    @Override
                                    public void onResponse(JSONObject response) {
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

            }
        });

        Button aaPassenger = findViewById(R.id.download_aa_passenger);
        setLongClickListener(aaPassenger, R.string.aapassenger_description);
        aaPassenger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        downloadAAP();
                    }
                } else shakeButton();
            }
        });

        Button s2a = findViewById(R.id.download_screentwoauto);
        setLongClickListener(s2a, R.string.s2a_description);
        s2a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setMessage(getString(R.string.s2a_redirect));

                        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://inceptive.ru/blog/20/screen2auto-dublirovanie-ekrana-smartfona-v-android-auto-na-gu")));

                            }
                        });

                        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                alertDialog.dismiss();
                            }
                        });
                        alertDialog.show();
                    }
                } else {
                    shakeButton();
                }
            }
        });

        Button aamirrorButton = findViewById(R.id.download_aamirror);
        setLongClickListener(aamirrorButton, R.string.aamirror_description);
        aamirrorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        downloadAAMirror();
                    }
                } else {
                    shakeButton();
                }
            }
        });

        Button aawidgets = findViewById(R.id.download_aa_widgets);
        setLongClickListener(aawidgets, R.string.aawidgets_description);
        aawidgets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        downloadAAWidgets();
                    }

                } else {
                    shakeButton();
                }
            }
        });

        Button aaStreamButton = findViewById(R.id.download_aa_stream);
        setLongClickListener(aaStreamButton, R.string.aa_stream_description);
        aaStreamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (eligible) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED ) {
                        askForStoragePermission();
                    } else {
                        downloadAAStream();
                    }

                } else {
                    shakeButton();
                }
            }
        });

        Button nav2contacts = findViewById(R.id.download_nav2contacts);
        setLongClickListener(nav2contacts, R.string.nav2contacts_description);
        nav2contacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

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
            }
        }


    }

    private void askForStoragePermission() {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    101);

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
                                        downloader.run();
                                        pDialog.dismiss();
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

        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.show();




    }

    public void downloadAAStream () {

        final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage(getString(R.string.about_to_download, "AAStream 1.1.0.29"));

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                alertDialog.dismiss();
                final String url = BuildConfig.AASTREAM_LINK;
                final File file= new File(getApplicationContext().getExternalFilesDir("AAAD") , "aastream" + ".apk");
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
                                        downlaoder.run();
                                        pDialog.dismiss();
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
                                            downlaoder.run();
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
                                    downlaoder.run();
                                    pDialog.dismiss();
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
            alertDialog.setMessage(getString(R.string.screen2auto_download));

            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {


                    alertDialog.dismiss();

                    final File file = new File(getApplicationContext().getExternalFilesDir("AAAD") , "s2a" + ".apk");

                    pDialog = ProgressDialog.show(MainActivity.this, "",
                            getString(R.string.loading), true);
                    pDialog.show();
                    new Handler().postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            Downloader downlaoder = new Downloader(MainActivity.this, file, data);
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


    void installAPK(File file) {

        if (!verified[0]) {
            registerDownload();
        }
        dismissProgressDialog(pDialog);

            Intent intent;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setData(getUri(file));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndTypeAndNormalize(Uri.fromFile(file), "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending");
            getApplicationContext().startActivity(intent);
        }



    private void dismissProgressDialog(ProgressDialog pDialog) {

        pDialog.dismiss();


    }

    public Uri getUri(File file) {
        return FileProvider.getUriForFile(
                getApplicationContext(),
                "sksa.aa.customapps.fileProvider",
                file
        );
    }

    public void registerDownload ()  {
        try {
            getTime();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mySecondRef.child(deviceId).setValue(currentTime, new DatabaseReference.CompletionListener() {
                @Override
                public void onComplete(@Nullable DatabaseError databaseError, @NonNull DatabaseReference databaseReference) {
                    eligible = false;
                    remainingDownloads.setText(getResources().getQuantityString(R.plurals.remaining_downloads, 0, 0));
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
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
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
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        newVersionName = null;
                    }
                });
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

}