package com.fernandez.gatekeep

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mUser: FirebaseUser
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mStorageRef: StorageReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        val updateProfileBtn = findViewById<Button>(R.id.updateProfileBtn)
        val updatePasswordBtn = findViewById<Button>(R.id.updatePasswordBtn)
        val updateEmailBtn = findViewById<Button>(R.id.updateEmailBtn)
        val deleteAccountBtn = findViewById<Button>(R.id.deleteAccountBtn)

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Feature not yet implemented")
        dialogBuilder.setMessage("This feature is currently under development and will be available in a future update.")
        dialogBuilder.setPositiveButton("OK") { _, _ ->
            // Dismiss the dialog
        }

        mAuth = FirebaseAuth.getInstance()
        mUser = mAuth.currentUser!!
        mDatabaseRef = FirebaseDatabase.getInstance().reference.child("users").child(mUser.uid)
        mStorageRef = FirebaseStorage.getInstance().reference.child("qr_codes").child("${mUser.uid}.jpg")

        updateProfileBtn.setOnClickListener {
//            startActivity(Intent(this, UpdateProfileActivity::class.java))
            val dialog = dialogBuilder.create()
            dialog.show()
        }

        updateEmailBtn.setOnClickListener {
//            startActivity(Intent(this, UpdateEmailActivity::class.java))
            val dialog = dialogBuilder.create()
            dialog.show()
        }

        updatePasswordBtn.setOnClickListener {
//            startActivity(Intent(this, UpdatePasswordActivity::class.java))
            val dialog = dialogBuilder.create()
            dialog.show()
        }

        deleteAccountBtn.setOnClickListener {
            val passwordDialog = AlertDialog.Builder(this)
            passwordDialog.setTitle("Enter your password")
            val passwordET = EditText(this)
            passwordET.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordDialog.setView(passwordET)

            passwordDialog.setPositiveButton("OK") { _, _ ->
                val password = passwordET.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val credential = EmailAuthProvider.getCredential(mUser.email!!, password)
                mUser.reauthenticate(credential)
                    .addOnSuccessListener {
                        val confirmDialog = AlertDialog.Builder(this)
                        confirmDialog.setTitle("Are you sure you want to delete your account?")
                        confirmDialog.setPositiveButton("Delete") { _, _ ->
                            deleteAccount()
                        }
                        confirmDialog.setNegativeButton("Cancel", null)
                        confirmDialog.show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
            }
            passwordDialog.setNegativeButton("Cancel", null)
            passwordDialog.show()
        }
    }

    private fun deleteAccount() {
        mDatabaseRef.removeValue()
            .addOnSuccessListener {
                mUser.delete()
                    .addOnSuccessListener {
                        mStorageRef.delete()
                        deleteAttendanceData()
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteAttendanceData() {
        val attendanceRef = FirebaseDatabase.getInstance().reference.child("attendance").child(mUser.uid)
        attendanceRef.removeValue()
    }
}
