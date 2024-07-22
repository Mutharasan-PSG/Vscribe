package com.example.vs

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Ensure this is correct
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val user = sessionManager.getUserDetails()

        if (user != null) {
            val nameTextView: TextView = findViewById(R.id.profile_name)
            val emailTextView: TextView = findViewById(R.id.profile_email)
            val profileImageView: ImageView = findViewById(R.id.profile_image)
            val signOutButton: Button = findViewById(R.id.btn_sign_out)

            nameTextView.text = user.name
            emailTextView.text = user.email

            // Load profile image using Glide
            Glide.with(this)
                .load(user.photoUrl)
                .placeholder(R.drawable.ic_default_profile_image)
                .error(R.drawable.ic_default_profile_image)
                .into(profileImageView)

            // Set up sign-out button click listener
            signOutButton.setOnClickListener {
                signOut()
            }
        }
    }

    private fun signOut() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Sign out from Google Sign-In
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // Clear session data
            sessionManager.clearSession()

            // Redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
