package com.example.vs

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpeechToTextActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var transcribedTextView: TextView
    private lateinit var startSpeechButton: ImageButton
    private lateinit var saveTextButton: Button
    private lateinit var languageSpinner: Spinner
    private lateinit var refreshButton: ImageButton
    private lateinit var firestore: FirebaseFirestore
    private val inactivityTimeoutMillis: Long = 10000 // 10 seconds
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        stopSpeechRecognition()
    }

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_to_text)

        // Initializing UI components from XML
        transcribedTextView = findViewById(R.id.text_view_transcribed)
        startSpeechButton = findViewById(R.id.btn_speech)
        saveTextButton = findViewById(R.id.button_save_text)
        languageSpinner = findViewById(R.id.spinner_language)
        refreshButton = findViewById(R.id.button_refresh)

        // Requesting audio recording permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()

        // Set up the language spinner
        setupLanguageSpinner()

        // Handle speech button click
        startSpeechButton.setOnClickListener {
            val selectedLanguage = getLanguageCode(languageSpinner.selectedItem.toString())
            startSpeechRecognition(selectedLanguage)
        }

        // Handle refresh button click
        refreshButton.setOnClickListener {
            transcribedTextView.text = ""  // Clear transcribed text
            //startSpeechRecognition(getLanguageCode(languageSpinner.selectedItem.toString()))
        }

        // Save the transcribed text
        saveTextButton.setOnClickListener {
            val transcribedText = transcribedTextView.text.toString()
            if (transcribedText.isNotEmpty()) {
                showFileNameInputDialog(transcribedText)
            } else {
                Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSpeechRecognition(language: String) {
        stopSpeechRecognition() // Ensure previous recognizer is stopped

        // Create a speech recognition intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak something...")
        }

        // Handle speech recognition results
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                startSpeechButton.setImageResource(R.drawable.voice_frequencyy)
                resetInactivityTimeout()
            }

            override fun onBeginningOfSpeech() {
                startSpeechButton.setImageResource(R.drawable.voice_frequencyy)
                resetInactivityTimeout()
            }

            override fun onRmsChanged(rmsdB: Float) {
                resetInactivityTimeout()
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                resetInactivityTimeout()
            }

            override fun onEndOfSpeech() {
                startSpeechButton.setImageResource(R.drawable.mic)
                resetInactivityTimeout()
            }

            override fun onError(error: Int) {
                startSpeechButton.setImageResource(R.drawable.mic)
                Toast.makeText(this@SpeechToTextActivity, "Error recognizing speech", Toast.LENGTH_SHORT).show()
                resetInactivityTimeout()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    var spokenText = matches[0]
                    var formattedText = handleFormattingCommands(spokenText)

                    // Check if the transcribed text needs capitalization based on sentence boundaries
                    val currentText = transcribedTextView.text.toString()

                    // Check if current text is empty or ends with sentence-ending punctuation
                    if (currentText.isEmpty() || currentText.endsWith(".") || currentText.endsWith("!") || currentText.endsWith("?")) {
                        // Capitalize the first letter if starting a new sentence
                        formattedText = formattedText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    } else {
                        // If it's in the middle of a sentence, lowercase the first character
                        formattedText = formattedText.replaceFirstChar { it.lowercase(Locale.getDefault()) }
                    }

                    // Preserve the existing find/replace logic
                    if (isFindReplaceCommand(formattedText)) {
                        handleFindReplace(formattedText)
                    } else {
                        // Append the new spoken text to the existing text
                        if (currentText.isEmpty() || currentText.endsWith("\n")) {
                            transcribedTextView.append(formattedText) // No space if new line
                        } else {
                            transcribedTextView.append(" $formattedText") // Add space if needed
                        }
                    }

                    // Preserve the navigation commands logic
                    handleNavigationCommands(formattedText)
                }
                startSpeechButton.setImageResource(R.drawable.mic) // Restore mic icon
                inactivityHandler.removeCallbacks(inactivityRunnable)
            }



            override fun onPartialResults(partialResults: Bundle?) {
                resetInactivityTimeout()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                resetInactivityTimeout()
            }
        })
        speechRecognizer.startListening(intent)
        resetInactivityTimeout()
    }

    private fun resetInactivityTimeout() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, inactivityTimeoutMillis)
    }

    private fun processVoiceCommand(spokenText: String) {
        if (isFindReplaceCommand(spokenText)) {
            handleFindReplace(spokenText)
            return
        }

        val formattedText = handleFormattingCommands(spokenText)
        transcribedTextView.text = formattedText
    }

    private fun handleFormattingCommands(spokenText: String): String {
        var formattedText = spokenText.trim()
        formattedText = formattedText.replace("comma", ",")
        formattedText = formattedText.replace("period", ".")
        formattedText = formattedText.replace("exclamatory", "!")
        formattedText = formattedText.replace("question mark", "?")
        formattedText = formattedText.replace("hyphen", "-")
        formattedText = formattedText.replace("underscore", "_")
        formattedText = formattedText.replace("under score", "_")
        formattedText = formattedText.replace("quotes", "\"")
        formattedText = formattedText.replace("double quotes", "\"\"")
        formattedText = formattedText.replace("single quotes", "'")
        formattedText = formattedText.replace("new line", "\n").replace("next line", "\n")
        formattedText = formattedText.replace("tab space", "     ")
        return formattedText
    }

    private fun isFindReplaceCommand(spokenText: String): Boolean {
        val words = spokenText.split(" ")
        return words.size >= 4 && words[0].equals("find", ignoreCase = true) && words[2].equals("replace", ignoreCase = true)
    }

    private fun handleFindReplace(spokenText: String) {
        val words = spokenText.split(" ")
        val findWord = words[1]
        val replaceWord = words[3]

        if (findWord.isNotEmpty() && replaceWord.isNotEmpty()) {
            val currentText = transcribedTextView.text.toString()
            val newText = currentText.replace(findWord, replaceWord, ignoreCase = true)
            transcribedTextView.text = newText
            Toast.makeText(this, "Replaced '$findWord' with '$replaceWord'", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid find and replace command format", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleNavigationCommands(spokenText: String): Boolean {
        return when {
            spokenText.startsWith("Home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
                true
            }
            spokenText.startsWith("Speech To Text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
                true
            }
            spokenText.startsWith("Voice Calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
                true
            }
            spokenText.startsWith("Voice To Do List", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
                true
            }
            spokenText.startsWith("Profile", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            spokenText.startsWith("Downloads", ignoreCase = true) -> {
                startActivity(Intent(this, DownloadActivity::class.java))
                true
            }
            spokenText.contains("History of task", ignoreCase = true) -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            spokenText.contains("Go to History of task page", ignoreCase = true) -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            spokenText.startsWith("Go to Home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
                true
            }
            spokenText.startsWith("Go to Speech To Text page", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
                true
            }
            spokenText.startsWith("Go to Voice Calculator page", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
                true
            }
            spokenText.startsWith("Go to Voice To Do List page", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
                true
            }
            spokenText.startsWith("Go to Profile page", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            spokenText.startsWith("Go to Downloads page", ignoreCase = true) -> {
                startActivity(Intent(this, DownloadActivity::class.java))
                true
            }
            else -> false
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
                saveFileToFirestore(fileName, transcribedText)
            } else {
                Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }

        builder.show()
    }

    private fun saveFileToFirestore(fileName: String, transcribedText: String) {
        val sdfMonth = SimpleDateFormat("MMMM-yyyy", Locale.getDefault())
        val currentMonth = sdfMonth.format(Date())
        val sdfTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdfTime.format(Date())

        val fileId = firestore.collection("Files").document().id
        val userId = SessionManager(this).getUserId() ?: "unknown"

        val fileData = mapOf(
            "timestamp" to currentTime,
            "fileId" to fileId,
            "fileName" to "$fileName.txt",
            "content" to transcribedText,
            "userId" to userId
        )

        firestore.collection("Files").document(currentMonth)
            .collection("UserFiles").document(fileId)
            .set(fileData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, DownloadActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupLanguageSpinner() {
        val languages = listOf("English", "Hindi", "Tamil", "Telugu", "Malayalam")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
    }

    private fun getLanguageCode(language: String): String {
        return when (language) {
            "English" -> "en-US"
            "Hindi" -> "hi-IN"
            "Tamil" -> "ta-IN"
            "Telugu" -> "te-IN"
            "Malayalam" -> "ml-IN"
            else -> "en-US"
        }
    }

    private fun stopSpeechRecognition() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
            startSpeechButton.setImageResource(R.drawable.mic)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeechRecognition()
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }
}