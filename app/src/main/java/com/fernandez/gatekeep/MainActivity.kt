package com.fernandez.gatekeep

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import me.ibrahimsn.lib.SmoothBottomBar

class MainActivity : AppCompatActivity() {

    private lateinit var smoothbottombar: SmoothBottomBar
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SmoothBottomBar
        smoothbottombar = findViewById(R.id.bottomBar)

        smoothbottombar.setOnItemSelectedListener { pos ->
            val fragmentManager = supportFragmentManager
            val fragmentTransaction = fragmentManager.beginTransaction()
            when (pos) {
                0 -> fragmentTransaction.replace(R.id.frameLayout, QRFragment())
                1 -> fragmentTransaction.replace(R.id.frameLayout, SettingsFragment())
            }
            fragmentTransaction.commit()
        }

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Check if user is already logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not logged in, go to Login Activity
            startActivity(Intent(this, FirstLoginActivity::class.java))
            finish()
            return
        }

        // Load user's QR code and name
        val currentUser1 = auth.currentUser
        val currentUserUid = currentUser1?.uid ?: return
        val usersRef = database.getReference("users")

        // Check if user is admin
        usersRef.child(currentUserUid).child("isAdmin")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val isAdmin = dataSnapshot.getValue(Boolean::class.java)
                    if (isAdmin != null && isAdmin) {
                        // User is admin, go to QR Scanner Activity
                        startActivity(Intent(this@MainActivity, QRScannerActivity::class.java))
                        finish()
                    } else {
                        // User is not an admin, start QRFragment
                        val fragmentManager = supportFragmentManager
                        val fragmentTransaction = fragmentManager.beginTransaction()
                        fragmentTransaction.replace(R.id.frameLayout, QRFragment())
                        fragmentTransaction.commit()
                    }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to check user role: ${databaseError.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}
