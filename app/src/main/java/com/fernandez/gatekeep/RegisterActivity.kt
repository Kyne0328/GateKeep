package com.fernandez.gatekeep

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.glxn.qrgen.android.QRCode
import java.io.ByteArrayOutputStream

@Suppress("SpellCheckingInspection", "SameParameterValue")
class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        val etName = findViewById<EditText>(R.id.et_name)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnRegister = findViewById<Button>(R.id.btn_register)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val isAdmin = false

            // Perform validation on user input
            if (name.isEmpty()) {
                Toast.makeText(applicationContext, "Please enter your name", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                Toast.makeText(applicationContext, "Please enter your email", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                Toast.makeText(applicationContext, "Please enter your password", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            // Create user account with email and password in Firebase Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    // Sign in success, update UI with the signed-in user's information
                    val user = authResult.user
                    Toast.makeText(
                        applicationContext,
                        "Registration successful",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Save user's name and admin status in Firebase Database
                    saveUserName(user, name, isAdmin)
                    finish()
                }
                .addOnFailureListener { exception ->
                    // If sign in fails, display a message to the user.
                    Toast.makeText(
                        applicationContext,
                        "Registration failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun saveUserName(user: FirebaseUser?, name: String, isAdmin: Boolean) {
        user?.let {
            // Generate QR code and convert to Bitmap
            val qrCode = QRCode.from(name).withSize(250, 250).bitmap()

            // Upload QR code to Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference
            val qrCodeRef = storageRef.child("qr_codes/${user.uid}.jpg")

            CoroutineScope(Dispatchers.IO).launch {
                val baos = ByteArrayOutputStream()
                qrCode.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                val data = baos.toByteArray()
                try {
                    qrCodeRef.putBytes(data).await()

                    // Save user name and admin status to Firebase Realtime Database
                    val databaseRef = FirebaseDatabase.getInstance().getReference("users/${user.uid}")
                    databaseRef.child("name").setValue(name).await()
                    databaseRef.child("isAdmin").setValue(isAdmin).await()

                    // Log success message
                    Log.d(TAG, "QR code saved to Firebase Storage")
                } catch (e: Exception) {
                    // Handle any exceptions that occurred
                    Log.e(TAG, "Failed to save user data to Firebase", e)
                }
            }
        }
    }
}