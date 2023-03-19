package com.fernandez.gatekeep

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.os.Bundle
import android.view.Menu
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var ivQrCode: ImageView
    private lateinit var tvName: TextView

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enable the ActionBar
        supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        supportActionBar!!.setDisplayShowTitleEnabled(true)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Check if user is already logged in
        val currentUser = auth.currentUser
        if (currentUser ==  null) {
            // User is not logged in, go to Login Activity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val nameTextView = findViewById<TextView>(R.id.user_name_textview)
        val emailTextView = findViewById<TextView>(R.id.user_email_textview)

        // Retrieve user's name and email from Firebase
        val currentUser2 = FirebaseAuth.getInstance().currentUser
        val name = currentUser2?.displayName
        val email = currentUser2?.email

        // Set the text of the TextViews
        nameTextView.text = name
        emailTextView.text = email

        // Initialize ivQrCode and tvName
        ivQrCode = findViewById(R.id.ivQrCode)
        tvName = findViewById(R.id.user_name_textview)

        // Load user's QR code and name
        val currentUser1 = auth.currentUser
        val currentUserUid = currentUser1?.uid ?: return
        val usersRef = database.getReference("users")
        usersRef.child(currentUserUid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val userName = dataSnapshot.child("name").getValue(String::class.java)
                if (userName != null) {
                    // Set the user's name in the TextView
                    tvName.text = userName
                    // Set the ActionBar title
                    supportActionBar!!.title = "GateKeep"

                    // Load user's QR code from Firebase Storage and display it in ImageView
                    val storageRef = FirebaseStorage.getInstance().reference
                    val qrCodeRef = storageRef.child("qr_codes/$currentUserUid.jpg")
                    val ONE_MEGABYTE: Long = 1024 * 1024
                    qrCodeRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
                        val qrCodeBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ivQrCode.setImageBitmap(qrCodeBitmap)
                    }.addOnFailureListener { exception ->
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to load QR code: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load user data: ${databaseError.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })


        // Check if user is admin
        usersRef.child(currentUserUid).child("isAdmin")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val isAdmin = dataSnapshot.getValue(Boolean::class.java)
                    if (isAdmin != null && isAdmin) {
                        // User is admin, go to QR Scanner Activity
                        startActivity(Intent(this@MainActivity, QRScannerActivity::class.java))
                        finish()
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
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu layout
        menuInflater.inflate(R.menu.menu_main, menu)

        // Get a reference to the logout menu item
        val logoutItem = menu.findItem(R.id.action_logout)

        // Set a click listener on the logout menu item
        logoutItem.setOnMenuItemClickListener {
            // Sign out user from Firebase Auth and go to Login Activity
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            true
        }
        return true
    }
}