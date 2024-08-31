package com.example.vs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView


class TaskAdapter(context: Context, private var tasks: List<Task>) : ArrayAdapter<Task>(context, 0, tasks) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_task_card, parent, false)
        val task = tasks[position]

        val taskNameTextView = view.findViewById<TextView>(R.id.text_task_name)
        val taskTimeTextView = view.findViewById<TextView>(R.id.text_task_time)

        taskNameTextView.text = task.taskName
        taskTimeTextView.text = task.timing ?: ""

        return view
    }


}

