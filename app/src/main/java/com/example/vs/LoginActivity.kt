package com.example.vs

import User
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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

        // SignUp text view click handler
        findViewById<TextView>(R.id.tv_signup_clickable).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInResultLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            showCustomToast("Please wait...", 2000) // Show custom toast for 2 seconds

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign-in successful
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        checkIfUserExists(user.uid, user)
                    }
                } else {
                    Log.e("LoginActivity", "Firebase sign-in failed", task.exception)
                    // Handle sign-in failure
                }
            }
        }
    }

    private fun checkIfUserExists(userId: String, user: FirebaseUser) {
        database.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // User already exists
                    val userDetails = User(
                        id = user.uid, // Assuming UID as ID
                        name = user.displayName.toString(),
                        email = user.email.toString(),
                        photoUrl = user.photoUrl?.toString()
                    )
                    sessionManager.setLoggedIn(true) // Update login status
                    sessionManager.saveUserDetails(userDetails)

                    // Redirect to HomeActivity
                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                } else {
                    // User does not exist, redirect to SignUpActivity
                    showCustomToast("Please Register your account", 1800)
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this@LoginActivity, SignUpActivity::class.java))
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LoginActivity", "Database error: ${error.message}")
                // Handle database error
            }
        })
    }

    private fun showCustomToast(message: String, duration: Int) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()

        // Cancel the toast after the specified duration
        Handler(Looper.getMainLooper()).postDelayed({
            toast.cancel()
        }, duration.toLong())
    }
}
