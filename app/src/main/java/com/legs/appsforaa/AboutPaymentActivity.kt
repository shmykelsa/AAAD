package com.legs.appsforaa

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import com.legs.appsforaa.utils.Logger
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.*
import com.legs.appsforaa.utils.applyBottomInsetPadding
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AboutPaymentActivity : AppCompatActivity() {

    private lateinit var paymentSheet: PaymentSheet
    private lateinit var stripe: Stripe
    private var deviceId: String = ""
    private var customerConfig: PaymentSheet.CustomerConfiguration? = null
    private var paymentIntentClientSecret: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for proper insets handling
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_about_payment)

        // Apply bottom insets to bottom layout for 3-button navbar compatibility
        findViewById<View>(R.id.bottom_layout).applyBottomInsetPadding()

        val extras = intent.extras
        val nextTry = extras?.getLong("date")
        val promo = extras?.getBoolean("promotion") ?: false

        // Initialize Stripe
        PaymentConfiguration.init(
            applicationContext,
            BuildConfig.STRIPE_PUBLISHABLE_KEY
        )
        stripe = Stripe(applicationContext, BuildConfig.STRIPE_PUBLISHABLE_KEY)

        // Initialize PaymentSheet
        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)

        // Authenticate and initialize (MUST happen before database access)
        lifecycleScope.launch {
            try {
                // Authenticate with Firebase (silent, no UI)
                deviceId = com.legs.appsforaa.managers.AuthManager.ensureAuthenticated()
                Logger.d("AboutPaymentActivity", "Authentication successful, UID: $deviceId")

                // Display countdown in days if user is in cooldown
                if (nextTry != null && nextTry > 0) {
                    val nextTimeTextView = findViewById<TextView>(R.id.next_time_download)
                    val currentTime = System.currentTimeMillis()
                    val timeRemaining = nextTry - currentTime

                    if (timeRemaining > 0) {
                        val daysRemaining = (timeRemaining / (24L * 60L * 60L * 1000L)).toInt()
                        val hoursRemaining = ((timeRemaining % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L)).toInt()

                        val countdownText = when {
                            daysRemaining > 0 -> getString(R.string.days_remaining, daysRemaining)
                            hoursRemaining > 0 -> getString(R.string.hours_remaining, hoursRemaining)
                            else -> getString(R.string.less_than_hour)
                        }
                        nextTimeTextView.text = countdownText
                    } else {
                        nextTimeTextView.text = getString(R.string.download_available_now)
                    }
                }

                // Check if user is already pro
                checkProStatus()
            } catch (e: Exception) {
                Logger.e("AboutPaymentActivity", "Authentication failed", e)
                Toast.makeText(this@AboutPaymentActivity,
                    "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun checkProStatus() {
        val database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE)
        val userRef = database.getReference("users").child(deviceId)
        
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.getValue(Boolean::class.java) == true) {
                    // User is already pro
                    showProStatus()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AboutPaymentActivity, 
                    getString(R.string.connection_error), Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showProStatus() {
        val upperTextView = findViewById<TextView>(R.id.upper_textview)
        val centralTextView = findViewById<TextView>(R.id.central_textview)
        val proButton = findViewById<View>(R.id.proButton)
        
        upperTextView.text = getString(R.string.congratsPro)
        centralTextView.text = "You have unlimited downloads!"
        proButton.visibility = View.GONE
    }

    fun checkout(view: View) {
        val alertDialog = AlertDialog.Builder(this@AboutPaymentActivity).create()
        alertDialog.setMessage(getString(R.string.warning_payment))
        alertDialog.setTitle(getString(R.string.attention))

        alertDialog.setButton(
            AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok)
        ) { _, _ ->
            startStripeCheckout()
        }

        alertDialog.setButton(
            AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel)
        ) { _, _ -> }

        alertDialog.show()
    }

    private fun startStripeCheckout() {
        val promo = intent.extras?.getBoolean("promotion") ?: false
        val priceId = if (promo) BuildConfig.STRIPE_PRICE_ID_PROMO else BuildConfig.STRIPE_PRICE_ID

        lifecycleScope.launch {
            try {
                createPaymentIntent(priceId)
            } catch (e: Exception) {
                Log.e("Stripe", "Error creating payment intent", e)
                Toast.makeText(
                    this@AboutPaymentActivity,
                    "Error setting up payment: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun createPaymentIntent(priceId: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://${BuildConfig.FIREBASE_REGION}-${BuildConfig.FIREBASE_PROJECT_ID}.cloudfunctions.net/createPaymentIntent")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonInputString = JSONObject().apply {
                    put("price_id", priceId)
                    put("device_id", deviceId)
                }.toString()

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonInputString)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonResponse = JSONObject(response)
                    
                    paymentIntentClientSecret = jsonResponse.getString("client_secret")
                    
                    if (jsonResponse.has("customer")) {
                        val customerObj = jsonResponse.getJSONObject("customer")
                        customerConfig = PaymentSheet.CustomerConfiguration(
                            customerObj.getString("id"),
                            customerObj.getString("ephemeral_key")
                        )
                    }

                    withContext(Dispatchers.Main) {
                        presentPaymentSheet()
                    }
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e("Stripe", "Server error: $responseCode - $errorResponse")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AboutPaymentActivity,
                            "Server error: $responseCode",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Stripe", "Network error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AboutPaymentActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun presentPaymentSheet() {
        paymentIntentClientSecret?.let { clientSecret ->
            val configuration = PaymentSheet.Configuration(
                merchantDisplayName = "AAAD",
                customer = customerConfig,
                allowsDelayedPaymentMethods = true
            )

            paymentSheet.presentWithPaymentIntent(
                clientSecret,
                configuration
            )
        }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                Toast.makeText(this, getString(R.string.cancelled_payment), Toast.LENGTH_SHORT).show()
            }
            is PaymentSheetResult.Failed -> {
                Log.e("Stripe", "Payment failed", paymentSheetResult.error)
                Toast.makeText(
                    this,
                    "Payment failed: ${paymentSheetResult.error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
            is PaymentSheetResult.Completed -> {
                // Payment successful
                Toast.makeText(
                    this,
                    getString(R.string.congratsPro),
                    Toast.LENGTH_LONG
                ).show()
                
                // Record transaction in Firebase for reference
                recordTransaction()
                
                // Show email collection dialog (non-dismissable)
                showEmailDialog()
            }
        }
    }

    private fun recordTransaction() {
        val database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE)
        val transactionRef = database.getReference("stripe_transactions")
        
        transactionRef.child(deviceId).setValue(
            mapOf(
                "payment_intent" to paymentIntentClientSecret,
                "timestamp" to System.currentTimeMillis(),
                "device_id" to deviceId
            )
        ) { databaseError, _ ->
            if (databaseError != null) {
                Log.e("Firebase", "Failed to record transaction", databaseError.toException())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check pro status when returning to activity
        checkProStatus()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.have_code -> {
                val intent = Intent(this@AboutPaymentActivity, EnterProCode::class.java)
                intent.putExtra("did", deviceId)
                startActivity(intent)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.payment_activity_menu, menu)
        return true
    }

    private fun showEmailDialog() {
        val input = android.widget.EditText(this)
        input.hint = getString(R.string.input_email)
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val alertDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.attention))
            .setMessage(getString(R.string.email_dialog))
            .setView(input)
            .setCancelable(false) // Non-dismissable
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    // Save email to Firebase for purchase verification
                    saveUserEmail(email)
                    // Check pro status and update UI
                    checkProStatus()
                } else {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    // Show dialog again if email is invalid
                    showEmailDialog()
                }
            }
            .create()

        alertDialog.show()
    }

    private fun saveUserEmail(email: String) {
        val database = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_INSTANCE)
        val emailRef = database.getReference("user_emails").child(deviceId)
        val deviceRef = database.getReference("users").child(deviceId)


        emailRef.setValue(
            mapOf(
                "email" to email,
                "timestamp" to System.currentTimeMillis(),
                "payment_intent" to paymentIntentClientSecret
            )
        ) { databaseError, _ ->
            if (databaseError != null) {
                Log.e("Firebase", "Failed to save email", databaseError.toException())
            } else {
                Logger.d("Firebase", "Email saved successfully for device: $deviceId")
                // Set pro status to true in Firebase
                deviceRef.setValue(true) { dbError, _ ->
                    if (dbError == null) {
                        // Pro status updated successfully, now refresh MainActivity
                        refreshMainActivity()
                    }
                }
            }
        }
    }

    private fun refreshMainActivity() {
        // Create intent to return to MainActivity and refresh pro status
        val intent = Intent(this, MainActivityNew::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra("refresh_pro_status", true)
        startActivity(intent)
        finish()
    }
}