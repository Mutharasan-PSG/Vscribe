
package com.example.vs

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class VoiceToDoListActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var listViewTasks: ListView
    private lateinit var speechButton: ImageButton
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isModifyingTask = false
    private var taskToModify: String? = null
    private lateinit var historyButton: ImageButton
    private var taskName: String? = null
    private var isWaitingForTaskTime = false


    private var taskInputTimeoutHandler: Handler? = null

    private val taskList = mutableListOf<Task>()
    private val REQUEST_NOTIFICATION_PERMISSION = 101

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadTasksFromDatabase() // Reload tasks to update UI
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_to_do_list)

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Initialize UI components
        listViewTasks = findViewById(R.id.list_view_tasks)
        speechButton = findViewById(R.id.btn_speech)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this).apply{

        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Speech started
            }

            override fun onDone(utteranceId: String?) {
                // TTS finished speaking, start the speech recognizer
                runOnUiThread {
                    startListening() // Start listening after TTS completes
                }
            }

            override fun onError(utteranceId: String?) {
                // Handle errors if needed
            }
        })
    }

        checkNotificationPermission()

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
                speechButton.setImageResource(R.drawable.voice_frequencyy)
            }

            override fun onBeginningOfSpeech() {
                speechButton.setImageResource(R.drawable.voice_frequencyy)
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
                Toast.makeText(this@VoiceToDoListActivity, "Error recognizing speech", Toast.LENGTH_SHORT).show()
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
        registerReceiver(refreshReceiver, IntentFilter("REFRESH_TASK_LIST"))

        // Load tasks from Firestore
        loadTasksFromDatabase()
    }


    private fun checkNotificationPermission() {
        if (!isNotificationEnabled()) {
            showNotificationPermissionPrompt()
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.areNotificationsEnabled()
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==  PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showNotificationPermissionPrompt() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enable Notifications")
            .setMessage("Notifications are disabled. To receive reminders for your tasks, please enable notifications.")
            .setPositiveButton("Go to Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(intent)
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_NOTIFICATION_PERMISSION
                    )
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Notifications won't be sent for scheduled tasks if not enabled.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications won't be sent for scheduled tasks if not enabled.", Toast.LENGTH_LONG).show()
            }
        }
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
        val utteranceId = "taskNamePrompt"
        textToSpeech.speak("Please say the task name.", TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        isWaitingForTaskTime = false
        // Speech recognizer will start in onDone of TTS utterance progress listener
    }


    private fun startListeningForNewTaskName() {
        val utteranceId = "newTaskNamePrompt"
        textToSpeech.speak("Please say the new task name.", TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        isWaitingForTaskTime = false
        // Speech recognizer will start in onDone of TTS utterance progress listener
    }

    private fun startListeningForTaskTime() {
        val utteranceId = "taskTimePrompt"
        textToSpeech.speak("Please say the task time.", TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        isWaitingForTaskTime = true
        // Speech recognizer will start in onDone of TTS utterance progress listener
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



    private fun handleTaskTimeInput(input: String) {
        //  taskInputTimeoutHandler?.removeCallbacksAndMessages(null) // Cancel timeout for task time
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

            if (taskName.isNullOrEmpty()) {
                textToSpeech.speak("Task name is invalid. Please try again.", TextToSpeech.QUEUE_FLUSH, null, null)

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
        firestore.collection("Voice_ToDo")
            .document("Scheduled_tasks")
            .collection(userId)
            .whereEqualTo("taskName", oldTaskName)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    textToSpeech.speak("No such task found to modify.", TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    for (document in documents) {
                        val updatedTask = Task(newTaskName, newTiming, document.getString("timestamp"))
                        document.reference.set(updatedTask)
                    }
                    loadTasksFromDatabase() // Reload tasks to update UI
                    textToSpeech.speak("Task modified: $newTaskName", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to modify task", Toast.LENGTH_SHORT).show()
            }
    }



    private fun removeTaskFromList(taskName: String) {
        firestore.collection("Voice_ToDo")
            .document("Scheduled_tasks")
            .collection(userId)
            .whereEqualTo("taskName", taskName)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    textToSpeech.speak("No such task found.", TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    for (document in documents) {
                        document.reference.delete()
                        cancelScheduledNotification(taskName, alarmManager)
                    }
                    textToSpeech.speak("Task removed: $taskName", TextToSpeech.QUEUE_FLUSH, null, null)
                    loadTasksFromDatabase() // Reload tasks to update UI
                }
            }
            .addOnFailureListener {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to remove task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeAllTasks() {
        firestore.collection("Voice_ToDo")
            .document("Scheduled_tasks")
            .collection(userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    textToSpeech.speak("No tasks found to remove.", TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    for (document in documents) {
                        document.reference.delete()
                        cancelScheduledNotification(document.getString("taskName") ?: "", alarmManager)
                    }
                    textToSpeech.speak("All tasks have been removed.", TextToSpeech.QUEUE_FLUSH, null, null)
                    loadTasksFromDatabase() // Reload tasks to update UI
                }
            }
            .addOnFailureListener {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to remove tasks", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetTaskInput() {
        taskName = null
        isWaitingForTaskTime = false
    }

    private fun cancelScheduledNotification(taskName: String, alarmManager: AlarmManager) {
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("taskName", taskName)
        }
        val requestCode = taskName.hashCode() // Consistent request code
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("Notification", "Cancelled notification for task: $taskName with requestCode: $requestCode")
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleNotification(taskName: String, timing: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            putExtra("taskName", taskName)
        }
        val requestCode = taskName.hashCode() // Consistent request code
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
                    Log.d("Notification", "Scheduled notification for task: $taskName at $triggerTime with requestCode: $requestCode")
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
        firestore.collection("Voice_ToDo")
            .document("Scheduled_tasks")
            .collection(userId)
            .add(task)
        loadTasksFromDatabase()
    }

    private fun loadTasksFromDatabase() {
        firestore.collection("Voice_ToDo")
            .document("Scheduled_tasks")
            .collection(userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                taskList.clear()
                for (document in documents) {
                    val task = document.toObject(Task::class.java)
                    task?.let { taskList.add(it) }
                }
                sortTasksByTiming()
                updateTaskListView()
            }
            .addOnFailureListener {
                Toast.makeText(this@VoiceToDoListActivity, "Failed to load tasks", Toast.LENGTH_SHORT).show()
            }
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
        unregisterReceiver(refreshReceiver)
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}