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

class SignUpActivity : AppCompatActivity(), PolicyBottomSheetFragment.PolicyListener {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var database: DatabaseReference
    private var pendingAccount: GoogleSignInAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

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
                    pendingAccount = task.getResult(ApiException::class.java)
                    showPolicyBottomSheet()
                } catch (e: ApiException) {
                    Log.e("SignUpActivity", "Google sign-in failed: ${e.message}")
                }
            }
        }

        // Google Sign-In button click handler
        findViewById<com.google.android.gms.common.SignInButton>(R.id.btn_google_sign_in).setOnClickListener {
            signInWithGoogle()
        }

        // Login TextView click handler to redirect to login page
        findViewById<TextView>(R.id.tvLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInResultLauncher.launch(signInIntent)
    }

    private fun showPolicyBottomSheet() {
        val bottomSheet = PolicyBottomSheetFragment()
        bottomSheet.show(supportFragmentManager, "PolicyBottomSheet")
    }

    override fun onConfirm() {
        pendingAccount?.let { account ->
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign-in successful
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        // Check if user already exists in the database
                        checkIfUserExists(user.uid, user)
                    }
                } else {
                    Log.e("SignUpActivity", "Firebase sign-in failed", task.exception)
                    // Handle sign-in failure
                }
            }
        }
    }

    override fun onCancel() {
        // Reset the Google sign-in flow
        googleSignInClient.signOut().addOnCompleteListener {
            pendingAccount = null
        }
    }

    private fun checkIfUserExists(userId: String, user: FirebaseUser) {
        database.child("users").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // User already exists
                    showCustomToast("Already you have an account", 1400)
                    // Redirect to HomeActivity
                    sessionManager.setLoggedIn(true) // Update login status
                    sessionManager.saveUserDetails(
                        User(
                            id = user.uid,
                            name = user.displayName.toString(),
                            email = user.email.toString(),
                            photoUrl = user.photoUrl?.toString()
                        )
                    )
                    startActivity(Intent(this@SignUpActivity, HomeActivity::class.java))
                    finish()
                } else {
                    // User does not exist, proceed with registration
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
                    startActivity(Intent(this@SignUpActivity, HomeActivity::class.java))
                    showCustomToast("Welcome! Your account has been created.", 2000)
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SignUpActivity", "Database error: ${error.message}")
                // Handle database error
            }
        })
    }

    private fun storeUserInDatabase(userId: String, userDetails: User) {
        database.child("users").child(userId).setValue(userDetails)
            .addOnSuccessListener {
                Log.d("SignUpActivity", "User data saved successfully.")
            }
            .addOnFailureListener { e ->
                Log.e("SignUpActivity", "Failed to save user data: ${e.message}")
            }
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
