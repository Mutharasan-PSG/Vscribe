
package com.example.vs

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class VoiceToDoListActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var listViewTasks: ListView
    private lateinit var speechButton: ImageButton
    private lateinit var database: DatabaseReference
    private lateinit var userId: String
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isModifyingTask = false
    private var taskToModify: String? = null
    private lateinit var historyButton: ImageButton
    private var taskName: String? = null
    private var isWaitingForTaskTime = false
    private lateinit var removingTasksMessage: TextView

    private var taskInputTimeoutHandler: Handler? = null

    private val taskList = mutableListOf<Task>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_to_do_list)

        // Initialize Firebase
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        database = FirebaseDatabase.getInstance().reference.child("Voice_ToDo").child(userId)

        // Initialize UI components
        listViewTasks = findViewById(R.id.list_view_tasks)
        speechButton = findViewById(R.id.btn_speech)
        removingTasksMessage = findViewById(R.id.tv_removing_tasks)
        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)


        val btnHistory = findViewById<ImageButton>(R.id.btn_history)
        btnHistory.setOnClickListener {
            // Start the history activity
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
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
                // Log the error for debugging
                Log.e("SpeechRecognizer", "Error: $error")
                // Handle specific errors and ensure the recognizer is restarted if needed
                Toast.makeText(this@VoiceToDoListActivity, "Error recognizing speech: $error", Toast.LENGTH_SHORT).show()
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    // You can restart the recognizer here if needed
                    startListening()
                }
                speechButton.setImageResource(R.drawable.mic)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    val input = matches[0]

                    handleSpeechResult(input)

                    if (isWaitingForTaskTime) {
                        handleTaskTimeInput(input)
                    } else {
                        handleTaskNameInput(input)
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
                startListeningForTaskName()
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        // Load tasks from Firebase
        loadTasksFromDatabase()
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



    private fun startListeningForTaskName() {
        textToSpeech.speak("Please say the task name.", TextToSpeech.QUEUE_FLUSH, null, null)
        isWaitingForTaskTime = false
        startListening()
        startTaskInputTimeout()
    }


    private fun startListeningForNewTaskName() {
        textToSpeech.speak("Please say the new task name.", TextToSpeech.QUEUE_FLUSH, null, null)
        isWaitingForTaskTime = false
        startListening()
        startTaskInputTimeout()
    }

    private fun startListeningForTaskTime() {
        textToSpeech.speak("Please say the task time.", TextToSpeech.QUEUE_FLUSH, null, null)
        isWaitingForTaskTime = true
        startListening()
        startTaskInputTimeout()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.startListening(intent)
        Toast.makeText(this, "Listening to your speech", Toast.LENGTH_SHORT).show()
    }


    private fun startTaskInputTimeout() {
        taskInputTimeoutHandler?.removeCallbacksAndMessages(null) // Remove any previous callbacks
        taskInputTimeoutHandler = Handler(Looper.getMainLooper())
        taskInputTimeoutHandler?.postDelayed({
            if (isWaitingForTaskTime) {
                // Stop listening and show timeout message
                speechRecognizer.stopListening()
                textToSpeech.speak("No task time provided. Operation terminated.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            resetTaskInput()
        }, 8000) // 8 seconds timeout
    }


    private fun handleTaskTimeInput(input: String) {
        taskInputTimeoutHandler?.removeCallbacksAndMessages(null) // Cancel timeout for task time
        val timing = parseTaskTime(input.trim())
        if (timing == null) {
            textToSpeech.speak("Task time is invalid or not provided. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            if (isModifyingTask) {
                updateTaskInDatabase(taskToModify!!, taskName!!, timing)
                textToSpeech.speak("Task modified: $taskName", TextToSpeech.QUEUE_FLUSH, null, null)
                isModifyingTask = false
            } else {
                saveTaskToDatabase(taskName!!, timing)
                scheduleNotification(taskName!!, timing)
                textToSpeech.speak("Task added: $taskName", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            resetTaskInput()
            taskInputTimeoutHandler?.removeCallbacksAndMessages(null)
        }
    }


    private fun handleTaskNameInput(input: String) {
        val trimmedInput = input.trim().toLowerCase(Locale.getDefault())
        if (trimmedInput.startsWith("remove all task")) {
            removeAllTasks()
        } else if (trimmedInput.startsWith("remove ")) {
            val taskToRemove = trimmedInput.removePrefix("remove ").trim()
            removeTaskFromList(taskToRemove)
        } else if (trimmedInput.startsWith("modify ")) {
            val taskToModify = trimmedInput.removePrefix("modify ").trim()
            taskToModify(taskToModify) // Call method to handle modifying task
        } else {
            taskName = input.trim()
            taskInputTimeoutHandler?.removeCallbacksAndMessages(null) // Cancel timeout for task name
            if (taskName.isNullOrEmpty()) {
                textToSpeech.speak("Task name is invalid. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null)

            } else if (isModifyingTask) {
                startListeningForTaskTime()
            } else {
                startListeningForTaskTime()
            }
        }
    }

    private fun taskToModify(taskToModify: String) {
        isModifyingTask = true
        this.taskToModify = taskToModify
        startListeningForNewTaskName()
    }

    private fun updateTaskInDatabase(oldTaskName: String, newTaskName: String, newTiming: String) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var taskFound = false
                for (data in snapshot.children) {
                    val task = data.getValue(Task::class.java)
                    if (task?.taskName?.trim()?.toLowerCase(Locale.getDefault()) == oldTaskName) {
                        val updatedTask = Task(newTaskName, newTiming, task.timestamp)
                        data.ref.setValue(updatedTask)
                        taskFound = true
                    }
                }
                if (taskFound) {
                    loadTasksFromDatabase() // Reload tasks to update UI
                } else {
                    textToSpeech.speak("No such task found to modify.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to modify task", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showRemovingTasksMessage(show: Boolean) {
        removingTasksMessage.visibility = if (show) View.VISIBLE else View.GONE
    }


    private fun removeAllTasks() {
        showRemovingTasksMessage(true)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (data in snapshot.children) {
                        data.ref.removeValue()
                    }
                    textToSpeech.speak("All tasks have been removed.", TextToSpeech.QUEUE_FLUSH, null, null)
                    loadTasksFromDatabase() // Reload tasks to update UI
                } else {
                    textToSpeech.speak("No tasks found to remove.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                showRemovingTasksMessage(false)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to remove tasks", Toast.LENGTH_SHORT).show()
                showRemovingTasksMessage(false)
            }
        })
    }

    private fun removeTaskFromList(taskName: String) {
        showRemovingTasksMessage(true)
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var taskFound = false
                for (data in snapshot.children) {
                    val task = data.getValue(Task::class.java)
                    if (task?.taskName?.trim()?.toLowerCase(Locale.getDefault()) == taskName) {
                        data.ref.removeValue()
                        taskFound = true
                    }
                }
                if (taskFound) {
                    textToSpeech.speak("Task removed: $taskName", TextToSpeech.QUEUE_FLUSH, null, null)
                    loadTasksFromDatabase() // Reload tasks to update UI
                } else {
                    textToSpeech.speak("No such task found.", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                showRemovingTasksMessage(false)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to remove task", Toast.LENGTH_SHORT).show()
                showRemovingTasksMessage(false)
            }
        })
    }

    private fun resetTaskInput() {
        taskName = null
        isWaitingForTaskTime = false
    }

    private fun parseTaskTime(input: String): String? {
        val taskPattern = Pattern.compile("(\\b(?:tomorrow|today|next\\s+week|monday|\\d{1,2}(?:am|pm|:|\\s))+.*)?$", Pattern.CASE_INSENSITIVE)
        val matcher = taskPattern.matcher(input)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleNotification(taskName: String, timing: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("taskName", taskName)
        }

        val requestCode = System.currentTimeMillis().toInt() // Unique request code
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = parseTime(timing)
        if (triggerTime > System.currentTimeMillis()) {
            try {
                if (canScheduleExactAlarms(this)) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    Toast.makeText(this, "Permission to schedule exact alarms is not granted. Please enable it in settings.", Toast.LENGTH_LONG).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission to schedule exact alarms is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } else {
            textToSpeech.speak("Your mentioned time is in the past. Kindly retry with a future time.", TextToSpeech.QUEUE_FLUSH, null, null)
            Toast.makeText(this, "The specified time is in the past. Please provide a future time.", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseTime(timing: String): Long {
        val calendar = Calendar.getInstance()
        val now = Calendar.getInstance()

        // Normalize timing to handle "p.m." and "pm"
        val normalizedTiming = timing.replace("p.m.", "pm")
            .replace("a.m.", "am")
            .replace("o'clock", "")
            .replace(" ", "")

        // Handle relative times
        if (normalizedTiming.contains("tomorrow")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }

        val daysOfWeek = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val nowDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        for (day in daysOfWeek) {
            if (normalizedTiming.contains(day)) {
                val targetDayOfWeek = daysOfWeek.indexOf(day) + 1 // Calendar.DAY_OF_WEEK is 1-based
                val daysUntilTarget = if (targetDayOfWeek >= nowDayOfWeek) {
                    targetDayOfWeek - nowDayOfWeek
                } else {
                    targetDayOfWeek + 7 - nowDayOfWeek
                }
                calendar.add(Calendar.DAY_OF_YEAR, daysUntilTarget)
                return calendar.timeInMillis
            }
        }

        if (normalizedTiming.contains("nextweek")) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            return calendar.timeInMillis
        }

        // Define various time formats
        val timePatterns = listOf(
            "h:mmaa", // e.g., 2:58pm, 7:00pm, 7pm
            "hmmaa",  // e.g., 258pm, 7pm
            "h:mm a", // e.g., 7:00 PM
            "H:mm",   // e.g., 14:00
            "h:mm",
            "h a",    // e.g., 2 pm, 7 am
            "ha"      // e.g., 7:00
        )

        for (pattern in timePatterns) {
            try {
                val timeFormat = SimpleDateFormat(pattern, Locale.getDefault())
                val date = timeFormat.parse(normalizedTiming)
                if (date != null) {
                    val calendarDate = Calendar.getInstance()
                    calendarDate.time = date

                    calendar.set(Calendar.HOUR_OF_DAY, calendarDate.get(Calendar.HOUR_OF_DAY))
                    calendar.set(Calendar.MINUTE, calendarDate.get(Calendar.MINUTE))
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    // Adjust for times that have already passed today
                    return if (calendar.before(now)) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        calendar.timeInMillis
                    } else {
                        calendar.timeInMillis
                    }
                }
            } catch (e: Exception) {
                // Continue trying with other patterns
                Log.e("TimeParsing", "Failed to parse time with pattern $pattern: ${e.message}")
            }
        }

        // If parsing fails, return a large value to indicate an invalid time
        return Long.MAX_VALUE
    }

    private fun saveTaskToDatabase(taskName: String, timing: String?) {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().time)
        val task = Task(taskName, timing, timeStamp)
        database.push().setValue(task)
    }
    private fun loadTasksFromDatabase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                taskList.clear()
                for (data in snapshot.children) {
                    val task = data.getValue(Task::class.java)
                    task?.let { taskList.add(it) }
                }
                sortTasksByTiming()
                updateTaskListView()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to load tasks", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateTaskListView() {
        if (taskList.isEmpty()) {
            // Show a message when there are no tasks
            listViewTasks.adapter = null
            listViewTasks.emptyView = findViewById(R.id.empty_view) // Assuming you have a TextView with ID empty_view in your layout
        } else {
            val adapter = TaskAdapter(this, taskList)
            listViewTasks.adapter = adapter
            listViewTasks.emptyView = null
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.getDefault()
        }
    }

    private fun sortTasksByTiming() {
        // Sort the taskList based on the parsed time from the timing string
        taskList.sortWith { t1, t2 ->
            val time1 = parseTime(t1.timing ?: "")
            val time2 = parseTime(t2.timing ?: "")
            time1.compareTo(time2)
        }
    }


    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
