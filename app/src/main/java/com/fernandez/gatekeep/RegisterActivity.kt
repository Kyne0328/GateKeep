package com.fernandez.gatekeep

import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
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
    private lateinit var loadingOverlay: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        //initialize the loadingOverlay
        loadingOverlay = findViewById(R.id.loadingOverlay)

        //Dropdown
        val spinnerGrade = findViewById<Spinner>(R.id.spinner_grade)
        val grades = resources.getStringArray(R.array.grades_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, grades)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGrade.adapter = adapter

        val etName = findViewById<EditText>(R.id.et_name)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etSection = findViewById<EditText>(R.id.et_section)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnRegister = findViewById<Button>(R.id.btn_register)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val section = etSection.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val selectedGrade = spinnerGrade.selectedItem as String
            val isAdmin = false

            // Check if any of the fields is empty
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                // Show an error dialog
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Error")
                if (name.isEmpty()) {
                    builder.setMessage("Please enter your name")
                } else if (selectedGrade.isEmpty()) {
                    builder.setMessage("Please choose your grade")
                } else if (section.isEmpty()) {
                    builder.setMessage("Please enter your email")
                } else if (email.isEmpty()) {
                    builder.setMessage("Please enter your email")
                } else if (password.isEmpty()) {
                    builder.setMessage("Please enter your password")
                }
                builder.setPositiveButton("OK", null)
                val dialog = builder.create()
                dialog.show()
                return@setOnClickListener
            }

            loadingOverlay.visibility = View.VISIBLE
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
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
                    saveUserName(user, name, selectedGrade, section, isAdmin)
                    loadingOverlay.visibility = View.GONE
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    finish()
                }
                .addOnFailureListener { exception ->
                    // If sign in fails, display a message to the user.
                    Toast.makeText(
                        applicationContext,
                        "Registration failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingOverlay.visibility = View.GONE
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                }
            }
        }

        private fun saveUserName(user: FirebaseUser?, name: String, selectedGrade: String, section: String, isAdmin: Boolean) {
        user?.let {
            // Generate QR code and convert to Bitmap
            val qrData = "${user.uid},$name,$selectedGrade,$section"
            val qrCode = QRCode.from(qrData).withSize(250, 250).bitmap()

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
                    databaseRef.child("grade").setValue(selectedGrade).await()
                    databaseRef.child("section").setValue(section).await()

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