package com.example.vs

import User
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Ensure this is correct
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize Firebase Realtime Database
        database = FirebaseDatabase.getInstance().reference

        // Initialize ActivityResultLauncher
        signInResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    handleSignInResult(account)
                } catch (e: ApiException) {
                    Log.e("LoginActivity", "Google sign-in failed: ${e.message}")
                }
            }
        }

        // Google Sign-In button click handler
        findViewById<com.google.android.gms.common.SignInButton>(R.id.btn_google_sign_in).setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInResultLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign-in successful
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val userDetails = User(
                            id = user.uid, // Assuming UID as ID
                            name = user.displayName.toString(),
                            email = user.email.toString(),
                            photoUrl = user.photoUrl?.toString()
                        )
                        sessionManager.setLoggedIn(true) // Update login status
                        sessionManager.saveUserDetails(userDetails)

                        // Store user details in Firebase Realtime Database
                        storeUserInDatabase(user.uid, userDetails)

                        // Redirect to HomeActivity
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                } else {
                    Log.e("LoginActivity", "Firebase sign-in failed", task.exception)
                    // Handle sign-in failure
                }
            }
        }
    }

    private fun storeUserInDatabase(userId: String, userDetails: User) {
        database.child("users").child(userId).setValue(userDetails)
            .addOnSuccessListener {
                Log.d("LoginActivity", "User data saved successfully.")
            }
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Failed to save user data: ${e.message}")
            }
    }
}
