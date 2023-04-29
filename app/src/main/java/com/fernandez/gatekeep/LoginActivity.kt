package com.fernandez.gatekeep

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var loadingOverlay: ProgressBar

    private fun showErrorDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
        val alert = builder.create()
        alert.show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)

        //initialize the loadingOverlay
        loadingOverlay = findViewById(R.id.loadingOverlay)

        val tvForgotPassword = findViewById<TextView>(R.id.tv_forgot_password)

        tvForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                showErrorDialog("Please fill in all fields")
                return@setOnClickListener
            }
            loadingOverlay.visibility = View.VISIBLE
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = FirebaseAuth.getInstance().currentUser
                        if (user != null) {
                            val databaseRef =
                                FirebaseDatabase.getInstance().getReference("users/${user.uid}")
                            databaseRef.get().addOnSuccessListener { snapshot ->
                                val isAdmin = snapshot.child("isAdmin").value as? Boolean
                                if (isAdmin != null && isAdmin) {
                                    val intent = Intent(this, QRScannerActivity::class.java)
                                    // Create a new task stack and add QRScannerActivity to it
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                } else {
                                    val intent = Intent(this, MainActivity::class.java)
                                    // Create a new task stack and add MainActivity to it
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                }
                                // Clear all background intents
                                finishAffinity()
                            }
                        }
                    } else {
                        when (task.exception) {
                            is FirebaseAuthInvalidUserException -> showErrorDialog("Invalid email address")
                            is FirebaseAuthInvalidCredentialsException -> showErrorDialog("Invalid password")
                            is FirebaseNetworkException -> showErrorDialog("Unable to connect to the network")
                            else -> showErrorDialog("Authentication failed")
                        }
                    }
                    loadingOverlay.visibility = View.GONE
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                }
        }
    }
}
