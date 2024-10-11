package com.example.vs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var itemSpeechToText: LinearLayout
    private lateinit var itemVoiceCalculator: LinearLayout
    private lateinit var itemVoiceToDoList: LinearLayout
    private lateinit var btnSpeech: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer

    companion object {
        private const val REQUEST_MICROPHONE_PERMISSION = 1
        private const val REQUEST_NOTIFICATION_PERMISSION = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val viewFlipper = findViewById<ViewFlipper>(R.id.viewflipper)
        viewFlipper.startFlipping()

        bottomNavigationView = findViewById(R.id.bottom_nav)
        itemSpeechToText = findViewById(R.id.item_speech_to_text)
        itemVoiceCalculator = findViewById(R.id.item_voice_calculator)
        itemVoiceToDoList = findViewById(R.id.item_voice_to_do_list)
        btnSpeech = findViewById(R.id.btn_speech)

        // Check for notification permission
        if (!areNotificationsEnabled()) {
            requestNotificationPermission()
        }

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE_PERMISSION)
        } else {
            initializeSpeechRecognizer()
        }

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_download -> {
                    startActivity(Intent(this, DownloadActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Set click listeners for items
        itemSpeechToText.setOnClickListener {
            startActivity(Intent(this, SpeechToTextActivity::class.java))
        }

        itemVoiceCalculator.setOnClickListener {
            val bottomSheet = VoiceCalculatorBottomSheet()
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }

        itemVoiceToDoList.setOnClickListener {
            startActivity(Intent(this, VoiceToDoListActivity::class.java))
        }

        btnSpeech.setOnClickListener {
            startSpeechRecognition()
            Toast.makeText(this, "Listening to your speech", Toast.LENGTH_SHORT).show()
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        } else {
            // For earlier versions, we need to direct the user to the app settings
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }

    private fun initializeSpeechRecognizer() {
        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
                Glide.with(this@HomeActivity)
                    .asGif()
                    .load(R.drawable.voice_frequencyy) // Replace with your GIF
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(btnSpeech)
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
                Glide.with(this@HomeActivity)
                    .asGif()
                    .load(R.drawable.voice_frequencyy) // Replace with your GIF
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(btnSpeech)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "End of speech")
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error: $error")
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { resultList ->
                    val recognizedText = resultList[0]
                    handleSpeechResult(recognizedText)
                }
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }


    private fun handleSpeechResult(recognizedText: String) {
        val pageMappings = mapOf(
            "Home page" to HomeActivity::class.java,
            "Speech To Text" to SpeechToTextActivity::class.java,
            "Voice Calculator" to VoiceCalculatorBottomSheet::class.java,
            "Voice To Do List" to VoiceToDoListActivity::class.java,
            "Profile" to ProfileActivity::class.java,
            "History of task page" to HistoryActivity::class.java,
            "Go to Home page" to HomeActivity::class.java,
            "Go to Speech To Text page" to SpeechToTextActivity::class.java,
            "Go to Voice Calculator page" to VoiceCalculatorBottomSheet::class.java,
            "Go to Downloads page" to DownloadActivity::class.java,
            "Go to History of task page" to HistoryActivity::class.java,
            "Go to Voice to do list page" to VoiceToDoListActivity::class.java,
            "Go to Profile page" to ProfileActivity::class.java
        )

        pageMappings.entries.find { recognizedText.contains(it.key, ignoreCase = true) }?.let { entry ->
            val clazz = entry.value
            if (clazz == VoiceCalculatorBottomSheet::class.java) {
                // Show bottom sheet if the action is to display the VoiceCalculatorBottomSheet
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            } else {
                // Start activity for other cases
                startActivity(Intent(this, clazz))
            }
        }
    }


    private fun startSpeechRecognition() {
        if (!isInternetAvailable()) {
            Toast.makeText(this, "Internet connection is required for speech recognition", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_MICROPHONE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeSpeechRecognizer()
                } else {
                    Toast.makeText(this, "Microphone permission is required for speech recognition", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (!areNotificationsEnabled()) {
                    Toast.makeText(this, "Notifications won't be sent if not enabled", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNavigationView.selectedItemId = R.id.nav_home
    }


    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
