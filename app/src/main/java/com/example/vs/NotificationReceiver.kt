package com.example.vs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationReceiver : BroadcastReceiver() {

    private val firestore = FirebaseFirestore.getInstance()

    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra("taskName") ?: "No task name"

        val channelId = "task_notification_channel"
        val channelName = "Task Notification"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Channel for task notifications"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(context, VoiceToDoListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.launchlogo)
            .setContentTitle("Task Reminder")
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())

        // Move the task to history after the notification is sent
        moveTaskToHistory(context, taskName)
    }
    private fun moveTaskToHistory(context: Context, taskName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Reference to the user's tasks and history
        val tasksRef = firestore.collection("Voice_ToDo").document("Scheduled_tasks").collection(userId)
        val historyRef = firestore.collection("Voice_ToDo").document("Task_History").collection(userId)

        // Find the task by name and move it to the history
        tasksRef.whereEqualTo("taskName", taskName).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // Task might have been removed, handle appropriately
                    return@addOnSuccessListener
                }

                for (document in snapshot.documents) {
                    val task = document.toObject(Task::class.java)
                    if (task != null) {
                        // Save the task to the history collection
                        historyRef.add(task)
                        // Remove the task from the active tasks
                        document.reference.delete()
                    }
                }
                val refreshIntent = Intent("REFRESH_TASK_LIST")
                context.sendBroadcast(refreshIntent)
            }
            .addOnFailureListener { error ->
                // Log the error
                Log.e("NotificationReceiver", "Failed to move task to history: ${error.message}")

                // Show a toast message to the user if needed
                Toast.makeText(context, "Failed to archive task. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }


}