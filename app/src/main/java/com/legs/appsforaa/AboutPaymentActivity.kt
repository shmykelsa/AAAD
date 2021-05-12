package com.legs.appsforaa

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.kpstv.library.Paypal
import java.lang.Boolean
import java.text.SimpleDateFormat
import java.util.*


class AboutPaymentActivity : AppCompatActivity() {

    private lateinit var paypal: Paypal
    private var deviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        val extras = intent.extras

        var ppId = BuildConfig.PAYPAL_ID

        val nextTry = extras?.getLong("date")
        val promo = extras?.getBoolean("promotion")

        if (promo == true) {
            ppId = BuildConfig.PAYPAL_ID_PROMO
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_payment)
        val imGood = findViewById<Button>(R.id.noproblem)
        imGood.setOnClickListener { v: View? -> finish() }

        if (nextTry != null) {
            val nextDownloadTv = findViewById<TextView>(R.id.next_time_download)
            val nextPossible = (nextTry + 2629743000 )
            val dateFormat = DateFormat.getDateFormat(applicationContext)
            val date: String = dateFormat.format(nextPossible)
            nextDownloadTv.text = getString(R.string.next_download_available, date)
        }

        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val options = Paypal.Options(
            paypalButtonId = ppId,
            purchaseCompleteUrl = BuildConfig.RETURN_URL,
            isSandbox = false
        )
        paypal = Paypal.Builder(options)
            .setCallingContext(this)

    }

    fun checkout(view: View) {

        val alertDialog = AlertDialog.Builder(this@AboutPaymentActivity).create()
        alertDialog.setMessage(getString(R.string.warning_payment))
        alertDialog.setTitle(getString(R.string.attention))

        alertDialog.setButton(
            AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)
        ) { dialog, which ->
            alertDialog.dismiss()
            paypal.checkout()
        }

        alertDialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (paypal.isPurchaseComplete(requestCode, resultCode)) {

            val details = data?.getSerializableExtra(Paypal.PURCHASE_DATA) as? Paypal.History

            val database2 =
                FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE)
            val myRef2 = database2.getReference("transactions")

            if (details != null) {
                myRef2.child(deviceId).setValue(
                    details.buyerId,
                    DatabaseReference.CompletionListener { databaseError, databaseReference ->
                        if (databaseError != null) {
                            Toast.makeText(
                                this@AboutPaymentActivity,
                                getString(R.string.connection_error),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            //PUSH THE PRO STATUS TO DATABASE
                            val database =
                                FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE)
                            val myRef = database.getReference("users")
                            myRef.child(deviceId).setValue(
                                Boolean.TRUE
                            ) { databaseError, databaseReference ->
                                if (databaseError != null) {
                                    Toast.makeText(
                                        this@AboutPaymentActivity,
                                        getString(R.string.connection_error),
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@AboutPaymentActivity,
                                        getString(R.string.congratsPro),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    val intent = Intent(
                                        this@AboutPaymentActivity,
                                        MainActivity::class.java
                                    )
                                    this@AboutPaymentActivity.startActivity(intent)
                                }
                            }
                        }
                    })
            }
            myRef2.push()







        } else if (paypal.isPurchaseCancelled(requestCode, resultCode)) {

            Toast.makeText(this, getString(R.string.cancelled_payment), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): kotlin.Boolean {
        when (item.itemId) {
            R.id.have_code -> {
                val intent = Intent(this@AboutPaymentActivity, EnterProCode::class.java)
                intent.putExtra("did", deviceId)
                this@AboutPaymentActivity.startActivity(intent)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): kotlin.Boolean {
        menuInflater.inflate(R.menu.payment_activity_menu, menu)
        return true
    }
}