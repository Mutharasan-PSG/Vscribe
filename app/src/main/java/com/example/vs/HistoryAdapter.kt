package com.example.vs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class HistoryAdapter(private val context: Context, private val historyList: List<Task>) : BaseAdapter() {

    override fun getCount(): Int {
        return historyList.size
    }

    override fun getItem(position: Int): Any {
        return historyList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.history_item, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val task = historyList[position]
        viewHolder.taskName.text = task.taskName // Ensure this matches your Task class property
        viewHolder.taskTime.text = task.timing // Ensure this matches your Task class property

        return view
    }

    private class ViewHolder(view: View) {
        val taskName: TextView = view.findViewById(R.id.task_name)
        val taskTime: TextView = view.findViewById(R.id.task_time)
    }
}
