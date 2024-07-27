package com.example.vs

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_to_text)

        textViewTranscribed = findViewById(R.id.text_view_transcribed)
        buttonSaveText = findViewById(R.id.button_save_text)
        buttonRefresh = findViewById(R.id.button_refresh)
        btnSpeech = findViewById(R.id.btn_speech)

        val spinnerLanguage: Spinner = findViewById(R.id.spinner_language)
        val languages = listOf("en_US", "ta_IN", "te_IN", "ml_IN", "hi_IN")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLanguage = languages[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnSpeech.setOnClickListener { startSpeechRecognition() }

        buttonRefresh.setOnClickListener {
            Toast.makeText(this, "Listening your speech", Toast.LENGTH_SHORT).show()
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
                btnSpeech.setImageResource(R.drawable.mic)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    val spokenText = matches[0]
                    if (isFindReplaceCommand(spokenText)) {
                        handleFindReplace(spokenText)
                    } else {
                        textViewTranscribed.append(spokenText + " ")
                    }
                    handleNavigationCommands(spokenText)
                }
                btnSpeech.setImageResource(R.drawable.mic)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }

        speechRecognizer.startListening(intent)

    }

    private fun handleNavigationCommands(spokenText: String) {
        when {
            spokenText.contains("Home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
            }
            spokenText.contains("Speech To Text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
            }
            spokenText.contains("Voice Calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
            }
            spokenText.contains("Voice To Do List", ignoreCase = true) -> {
                startActivity(Intent(this, VoiceToDoListActivity::class.java))
            }
            spokenText.contains("Profile", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            spokenText.contains("Downloads", ignoreCase = true) -> {
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

        val fileId = database.push().key ?: return
        val userId = SessionManager(this).getUserId() ?: "unknown"
        val fileData = mapOf(
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
