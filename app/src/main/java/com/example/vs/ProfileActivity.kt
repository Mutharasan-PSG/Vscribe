package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var btnSpeech: ImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        btnSpeech = findViewById(R.id.btn_speech)

        sessionManager = SessionManager(this)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Ensure this is correct
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val user = sessionManager.getUserDetails()

        if (user != null) {
            val nameTextView: TextView = findViewById(R.id.profile_name)
            val emailTextView: TextView = findViewById(R.id.profile_email)
            val profileImageView: ImageView = findViewById(R.id.profile_image)
            val signOutButton: Button = findViewById(R.id.btn_sign_out)
            btnSpeech = findViewById(R.id.btn_speech)

            nameTextView.text = user.name
            emailTextView.text = user.email

            // Load profile image using Glide
            Glide.with(this)
                .load(user.photoUrl)
                .placeholder(R.drawable.ic_default_profile_image)
                .error(R.drawable.ic_default_profile_image)
                .into(profileImageView)

            // Set up sign-out button click listener
            signOutButton.setOnClickListener {
                signOut()
            }

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

            // Set click listener for speech button
            btnSpeech.setOnClickListener {
                startSpeechRecognition()
            }
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleSpeechResult(recognizedText: String) {
        when {
            recognizedText.contains("Home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
            }
            recognizedText.contains("Sign Out", ignoreCase = true) -> {
                signOut()
            }
            recognizedText.contains("Downloads", ignoreCase = true) -> {
                startActivity(Intent(this, DownloadActivity::class.java))
            }
            recognizedText.contains("Speech To Text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
            }
            recognizedText.contains("Voice Calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            }
            recognizedText.contains("Voice ToDoList", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
            }
        }
    }

    private fun signOut() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Sign out from Google Sign-In
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // Clear session data
            sessionManager.clearSession()

            // Redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
