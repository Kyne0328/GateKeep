package com.fernandez.gatekeep

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Disable the ActionBar
        supportActionBar!!.hide()

        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)

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
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
                                    startActivity(intent)
                                    finish() // Call finish() to prevent going back to LoginActivity using the back button
                                } else {
                                    val intent = Intent(this, MainActivity::class.java)
                                    startActivity(intent)
                                    finish() // Call finish() to prevent going back to LoginActivity using the back button
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
