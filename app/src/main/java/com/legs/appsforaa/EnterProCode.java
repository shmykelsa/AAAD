package com.legs.appsforaa;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class EnterProCode extends AppCompatActivity {

    private String m_Text = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.code_input);


        final String deviceId = getIntent().getExtras().getString("did");

        final EditText input = findViewById(R.id.input_text);

        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.promo_code);

        Button proceedButton = findViewById(R.id.validate);
        Button cancelButton = findViewById(R.id.cancel);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        proceedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                m_Text = input.getText().toString();

                if (m_Text.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.please_enter_code, Toast.LENGTH_LONG).show();
                } else {
                    FirebaseDatabase database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE);
                    final DatabaseReference myRef = database.getReference("pc");
                    final DatabaseReference secondRef = database.getReference("users");

                    myRef.child(m_Text).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                if (snapshot.getValue(Boolean.class)) {
                                    myRef.child(m_Text).removeValue();
                                    secondRef.child(deviceId).setValue(Boolean.TRUE, new DatabaseReference.CompletionListener() {
                                        @Override
                                        public void onComplete(@Nullable DatabaseError databaseError, @NonNull DatabaseReference databaseReference) {
                                            if (databaseError != null) {
                                                Toast.makeText(getApplicationContext(), getString(R.string.connection_error), Toast.LENGTH_LONG).show();
                                            } else {
                                                Toast.makeText(getApplicationContext(), getString(R.string.pro_unlocked), Toast.LENGTH_LONG).show();
                                                startActivity(new Intent(EnterProCode.this, MainActivity.class));
                                            }

                                        }
                                    });
                                } else {

                                    Intent i = new Intent(EnterProCode.this, AboutPaymentActivity.class);
                                    i.putExtra("promotion", true);
                                    Toast.makeText(getApplicationContext(), getString(R.string.promotion), Toast.LENGTH_LONG).show();

                                    startActivity(i);
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.not_valid_code), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(getApplicationContext(), getString(R.string.connect_error), Toast.LENGTH_LONG).show();
                        }
                    });

                }

            }
        });

    }


}
