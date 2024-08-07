package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpeechToTextActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textViewTranscribed: TextView
    private lateinit var buttonSaveText: Button
    private lateinit var buttonRefresh: ImageButton
    private lateinit var selectedLanguage: String
    private lateinit var database: DatabaseReference
    private lateinit var btnSpeech: ImageButton

    private val inactivityTimeoutMillis: Long = 10000 // 10 seconds
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        stopSpeechRecognition()
       // Toast.makeText(this, "Speech recognition timed out due to inactivity", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_to_text)

        textViewTranscribed = findViewById(R.id.text_view_transcribed)
        buttonSaveText = findViewById(R.id.button_save_text)
        buttonRefresh = findViewById(R.id.button_refresh)
        btnSpeech = findViewById(R.id.btn_speech)

        val spinnerLanguage: Spinner = findViewById(R.id.spinner_language)
        val languages = listOf("English", "Tamil", "Telugu", "Malayalam", "Hindi")
        val languageCodes = mapOf(
            "English" to "en_US",
            "Tamil" to "ta_IN",
            "Telugu" to "te_IN",
            "Malayalam" to "ml_IN",
            "Hindi" to "hi_IN"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLanguage = languageCodes[languages[position]] ?: "en_US"
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnSpeech.setOnClickListener { startSpeechRecognition() }

        buttonRefresh.setOnClickListener {
            Toast.makeText(this, "Listening to your speech", Toast.LENGTH_SHORT).show()
            textViewTranscribed.text = ""
            startSpeechRecognition()
        }

        // Initialize Firebase
        database = FirebaseDatabase.getInstance().reference.child("Files")

        buttonSaveText.setOnClickListener {
            val transcribedText = textViewTranscribed.text.toString()
            if (transcribedText.isNotEmpty()) {
                showFileNameInputDialog(transcribedText)
            } else {
                Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSpeechRecognition() {
        stopSpeechRecognition() // Ensure previous recognizer is stopped
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                btnSpeech.setImageResource(R.drawable.voice_frequencyy)
                resetInactivityTimeout()
            }

            override fun onBeginningOfSpeech() {
                btnSpeech.setImageResource(R.drawable.voice_frequencyy)
                resetInactivityTimeout()
            }

            override fun onRmsChanged(rmsdB: Float) {
                resetInactivityTimeout()
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                resetInactivityTimeout()
            }

            override fun onEndOfSpeech() {
                btnSpeech.setImageResource(R.drawable.mic)
                resetInactivityTimeout()
            }

            override fun onError(error: Int) {
                btnSpeech.setImageResource(R.drawable.mic)
                resetInactivityTimeout()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    val spokenText = matches[0]
                    val formattedText = handleFormattingCommands(spokenText)
                    if (isFindReplaceCommand(formattedText)) {
                        handleFindReplace(formattedText)
                    } else {
                        textViewTranscribed.append(formattedText + " ")
                    }
                    handleNavigationCommands(formattedText)
                }
                btnSpeech.setImageResource(R.drawable.mic)
                inactivityHandler.removeCallbacks(inactivityRunnable) // Remove handler on success
            }

            override fun onPartialResults(partialResults: Bundle?) {
                resetInactivityTimeout()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                resetInactivityTimeout()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }

        speechRecognizer.startListening(intent)
        resetInactivityTimeout()
    }

    private fun stopSpeechRecognition() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun resetInactivityTimeout() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, inactivityTimeoutMillis)
    }

    private fun handleFormattingCommands(spokenText: String): String {
        var formattedText = spokenText
        formattedText = formattedText.replace("comma", ",")
        formattedText = formattedText.replace("new line", "\n")
        formattedText = formattedText.replace("next line", "\n")
        formattedText = formattedText.replace("period", ".")
        formattedText = formattedText.replace("exclamation", "!")
        formattedText = formattedText.replace("question mark", "?")
        formattedText = formattedText.replace("hyphen", "-")
        formattedText = formattedText.replace("underscore", "_")
        formattedText = formattedText.replace("under score", "_")
        formattedText = formattedText.replace("quotes", "\"")
        formattedText = formattedText.replace("double quotes", "\"\" ")
        formattedText = formattedText.replace("single quotes", "'")
        // Add more replacements as needed

        return formattedText
    }

    private fun handleNavigationCommands(spokenText: String) {
        when {
            spokenText.startsWith("Home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
            }
            spokenText.startsWith("Speech To Text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
            }
            spokenText.startsWith("Voice Calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            }
            spokenText.startsWith("Voice To Do List", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
            }
            spokenText.startsWith("Profile", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            spokenText.startsWith("Downloads", ignoreCase = true) -> {
                startActivity(Intent(this, DownloadActivity::class.java))
            }
        }
    }


    private fun isFindReplaceCommand(spokenText: String): Boolean {
        val words = spokenText.split(" ")
        return words.size >= 4 && words[0].equals("find", ignoreCase = true) && words[2].equals("replace", ignoreCase = true)
    }

    private fun handleFindReplace(spokenText: String) {
        val words = spokenText.split(" ")
        val findWord = words[1]
        val replaceWord = words[3]

        if (findWord != null && replaceWord != null) {
            val currentText = textViewTranscribed.text.toString()
            val newText = currentText.replace(findWord, replaceWord, ignoreCase = true)
            textViewTranscribed.text = newText
            Toast.makeText(this, "Replaced '$findWord' with '$replaceWord'", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid find and replace command format", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFileNameInputDialog(transcribedText: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Save File")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("Save") { dialog, which ->
            val fileName = input.text.toString()
            if (fileName.isNotEmpty()) {
                saveFileToFirebase(fileName, transcribedText)
            } else {
                Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }

        builder.show()
    }

    private fun saveFileToFirebase(fileName: String, transcribedText: String) {
        val sdf = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        val currentMonth = sdf.format(Date())
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val fileId = database.push().key ?: return
        val userId = SessionManager(this).getUserId() ?: "unknown"
        val fileData = mapOf(
            "timestamp" to currentTime,
            "fileId" to fileId,
            "fileName" to "$fileName.txt",
            "content" to transcribedText,
            "userId" to userId  // Add userId to the file data
        )
        database.child(currentMonth).child(fileId).setValue(fileData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DownloadActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
