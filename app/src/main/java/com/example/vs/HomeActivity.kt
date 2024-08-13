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
import androidx.core.content.ContextCompat
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

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE_PERMISSION)
        } else {
            initializeSpeechRecognizer()
        }

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Handle Home navigation
                    true
                }
                R.id.nav_download -> {
                    // Navigate to DownloadActivity
                    startActivity(Intent(this, DownloadActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    // Navigate to ProfileActivity
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
            // Show Voice Calculator Bottom Sheet
            val bottomSheet = VoiceCalculatorBottomSheet()
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }

        itemVoiceToDoList.setOnClickListener {
            startActivity(Intent(this, VoiceToDoListActivity::class.java))
        }

        // Set click listener for speech button
        btnSpeech.setOnClickListener {
            startSpeechRecognition()
            Toast.makeText(this, "Listening to your speech", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeSpeechRecognizer() {
        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer", "Ready for speech")
                btnSpeech.setImageResource(R.drawable.voice_frequency)
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
                btnSpeech.setImageResource(R.drawable.voice_frequency)
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

    private fun handleSpeechResult(recognizedText: String) {
        when {
            recognizedText.contains("Speech To Text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
            }
            recognizedText.contains("Voice Calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            }
            recognizedText.contains("Voice To Do List", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
            }
            recognizedText.contains("Profile", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            recognizedText.contains("Downloads", ignoreCase = true) -> {
                startActivity(Intent(this, DownloadActivity::class.java))
            }
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
                    // Optionally, guide the user to the app settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data =
                            Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
