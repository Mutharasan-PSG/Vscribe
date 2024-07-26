package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var itemSpeechToText: LinearLayout
    private lateinit var itemVoiceCalculator: LinearLayout
    private lateinit var itemVoiceToDoList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        bottomNavigationView = findViewById(R.id.bottom_nav)
        itemSpeechToText = findViewById(R.id.item_speech_to_text)
        itemVoiceCalculator = findViewById(R.id.item_voice_calculator)
        itemVoiceToDoList = findViewById(R.id.item_voice_to_do_list)

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Handle Home navigation
                    true
                }
                R.id.nav_download -> {
                    // Navigate to DownloadActivity
                    startActivity(Intent(this, DownloadActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    // Navigate to ProfileActivity
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Set click listeners for items
        itemSpeechToText.setOnClickListener {
            startActivity(Intent(this, SpeechToTextActivity::class.java))
        }

        itemVoiceCalculator.setOnClickListener {
            // Show Voice Calculator Bottom Sheet
            val bottomSheet = VoiceCalculatorBottomSheet()
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }

        itemVoiceToDoList.setOnClickListener {
            startActivity(Intent(this, VoiceToDoListActivity::class.java))
        }
    }
}
