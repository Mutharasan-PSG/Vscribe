package com.example.vs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class VoiceCalculatorAdapter(context: Context, private val items: List<String>) :
    ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.history_list_item, parent, false)
        val textView = view.findViewById<TextView>(R.id.history_item_text)
        textView.text = getItem(position)
        return view
    }
}
