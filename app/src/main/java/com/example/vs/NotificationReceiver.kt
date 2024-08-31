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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class NotificationReceiver : BroadcastReceiver() {

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
        val database: DatabaseReference = FirebaseDatabase.getInstance().reference
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Reference to the user's tasks
        val taskRef = database.child("Voice_ToDo").child(userId)

        // Find the task by name and move it to the history
        taskRef.orderByChild("taskName").equalTo(taskName).get().addOnSuccessListener { snapshot ->
            for (data in snapshot.children) {
                val task = data.getValue(Task::class.java)
                if (task != null) {
                    // Save the task to the history node
                    database.child("Task_History").child(userId).push().setValue(task)
                    // Remove the task from the active list
                    data.ref.removeValue()
                }
            }
        }.addOnFailureListener { error ->
            // Log the error
            Log.e("NotificationReceiver", "Failed to move task to history: ${error.message}")

            // You can also show a toast message to the user if needed
            Toast.makeText(context, "Failed to archive task. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }
}
