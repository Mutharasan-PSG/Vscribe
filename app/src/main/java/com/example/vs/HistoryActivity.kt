package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class HistoryActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var listViewHistory: ListView
    private lateinit var database: DatabaseReference
    private lateinit var userId: String
    private lateinit var btnDeleteHistory: ImageButton
    private lateinit var btnSpeech: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private val historyList = mutableListOf<Task>()
    private var isWaitingForConfirmation = false
    private var taskInputTimeoutHandler: Handler? = null
    private lateinit var emptyMessageTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Initialize Firebase
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        database = FirebaseDatabase.getInstance().reference.child("Task_History").child(userId)

        // Initialize UI components
        listViewHistory = findViewById(R.id.list_view_history)
        btnDeleteHistory = findViewById(R.id.btn_delete_history)
        btnSpeech = findViewById(R.id.btn_speech)
        emptyMessageTextView = findViewById(R.id.text_view_empty_message)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                btnSpeech.setImageResource(R.drawable.voice_frequency)
            }

            override fun onBeginningOfSpeech() {
                btnSpeech.setImageResource(R.drawable.voice_frequency)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error: $error")
                Toast.makeText(this@HistoryActivity, "Error recognizing speech", Toast.LENGTH_SHORT).show()
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    startListening()
                }
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    val input = matches[0]

                    handleSpeechResult(input)

                    if (isWaitingForConfirmation) {
                        handleConfirmationInput(input)
                    } else if (input.contains("clear history", ignoreCase = true)) {
                        startListeningForConfirmation()
                    }
                }
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Set up delete button click listener
        btnDeleteHistory.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Set up mic button click listener
        btnSpeech.setOnClickListener {
            if (NetworkUtil.isNetworkAvailable(this)) {
                startListening()
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        // Load tasks from Firebase
        loadHistoryFromDatabase()
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



    private fun startListeningForConfirmation() {
        textToSpeech.speak("Do you want to clear all history of tasks? Please say 'confirm' to proceed or 'cancel' to abort.", TextToSpeech.QUEUE_FLUSH, null, null)
        isWaitingForConfirmation = true
        startListening()
        startTaskInputTimeout()
    }

    private fun startListening() {
        Log.d("HistoryActivity", "Button clicked")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
        Toast.makeText(this, "Listening for your command", Toast.LENGTH_SHORT).show()
    }

    private fun startTaskInputTimeout() {
        taskInputTimeoutHandler?.removeCallbacksAndMessages(null) // Remove any previous callbacks
        taskInputTimeoutHandler = Handler(Looper.getMainLooper())
        taskInputTimeoutHandler?.postDelayed({
            if (isWaitingForConfirmation) {
                // Stop listening and show timeout message
                speechRecognizer.stopListening()
                textToSpeech.speak("No confirmation provided. Operation terminated.", TextToSpeech.QUEUE_FLUSH, null, null)
                resetConfirmationInput()
            }
        }, 10000) // 10 seconds timeout
    }

    private fun handleConfirmationInput(input: String) {
        taskInputTimeoutHandler?.removeCallbacksAndMessages(null) // Cancel timeout for confirmation
        if (input.contains("confirm", ignoreCase = true)) {
            clearHistory()
        } else if (input.contains("cancel", ignoreCase = true)) {
            textToSpeech.speak("Operation cancelled.", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            textToSpeech.speak("Invalid input. Please say 'confirm' to proceed or 'cancel' to abort.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
        resetConfirmationInput()
    }

    private fun resetConfirmationInput() {
        isWaitingForConfirmation = false
    }

    private fun loadHistoryFromDatabase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyList.clear()
                for (data in snapshot.children) {
                    val task = data.getValue(Task::class.java)
                    task?.let { historyList.add(it) }
                }
                historyList.reverse() // Reverse the list to show the most recent task first
                updateHistoryListView()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HistoryActivity, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateHistoryListView() {
        val adapter = HistoryAdapter(this, historyList)
        listViewHistory.adapter = adapter

        if (historyList.isEmpty()) {
            emptyMessageTextView.visibility = View.VISIBLE
            listViewHistory.visibility = View.GONE
        } else {
            emptyMessageTextView.visibility = View.GONE
            listViewHistory.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear History")
            .setMessage("Do you want to clear all history of tasks?")
            .setPositiveButton("Confirm") { _, _ ->
                clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearHistory() {
        database.removeValue()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                    historyList.clear()
                    updateHistoryListView()
                } else {
                    Toast.makeText(this, "Failed to clear history", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
