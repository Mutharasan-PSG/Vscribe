package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class VoiceToDoListActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var editTextTask: EditText
    private lateinit var buttonAddTask: Button
    private lateinit var listViewTasks: ListView
    private val tasks = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_to_do_list)

        // Initialize UI components
        editTextTask = findViewById(R.id.edit_text_task)
        buttonAddTask = findViewById(R.id.button_add_task)
        listViewTasks = findViewById(R.id.list_view_tasks)
        speechButton = findViewById(R.id.btn_speech)

        // Setup ListView adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tasks)
        listViewTasks.adapter = adapter

        // Set click listener for Add Task button
        buttonAddTask.setOnClickListener {
            val task = editTextTask.text.toString()
            if (task.isNotBlank()) {
                tasks.add(task)
                adapter.notifyDataSetChanged()
                editTextTask.text.clear()
            }
        }

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                speechButton.setImageResource(R.drawable.voice_frequency)
            }

            override fun onBeginningOfSpeech() {
                speechButton.setImageResource(R.drawable.voice_frequency)
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                speechButton.setImageResource(R.drawable.mic)
            }

            override fun onError(error: Int) {
                Toast.makeText(this@VoiceToDoListActivity, "Error recognizing speech", Toast.LENGTH_SHORT).show()
                speechButton.setImageResource(R.drawable.mic)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    val input = matches[0]
                    if (!handleNavigationCommands(input)) {
                        handleTaskInput(input)
                    }
                }
                speechButton.setImageResource(R.drawable.mic)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Set click listener for speech button
        speechButton.setOnClickListener {
            if (NetworkUtil.isNetworkAvailable(this)) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                speechRecognizer.startListening(intent)
                Toast.makeText(this, "Listening to your speech", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleTaskInput(input: String) {
        val task = input.lowercase(Locale.getDefault())
        if (task.contains("add") || task.contains("task")) {
            val taskText = task.replace("add", "").replace("task", "").trim()
            if (taskText.isNotBlank()) {
                tasks.add(taskText)
                adapter.notifyDataSetChanged()
                textToSpeech.speak("Task added: $taskText", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                textToSpeech.speak("Task input is invalid", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    private fun handleNavigationCommands(input: String): Boolean {
        return when {
            input.contains("home page", ignoreCase = true) -> {
                startActivity(Intent(this, HomeActivity::class.java))
                true
            }
            input.contains("speech to text", ignoreCase = true) -> {
                startActivity(Intent(this, SpeechToTextActivity::class.java))
                true
            }
            input.contains("voice calculator", ignoreCase = true) -> {
                val bottomSheet = VoiceCalculatorBottomSheet()
                bottomSheet.show(supportFragmentManager, bottomSheet.tag)
                true
            }
            input.contains("profile", ignoreCase = true) -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            input.contains("downloads", ignoreCase = true) -> {
                startActivity(Intent(this, DownloadActivity::class.java))
                true
            }
            else -> false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = textToSpeech.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
    }
}
