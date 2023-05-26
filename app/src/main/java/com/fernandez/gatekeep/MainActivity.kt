package com.fernandez.gatekeep

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
    private lateinit var loadingOverlay: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Disable night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initialize SmoothBottomBar
        smoothbottombar = findViewById(R.id.bottomBar)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        //initialize the loadingOverlay
        loadingOverlay = findViewById(R.id.loadingOverlay)

        // Check if user is already logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not logged in, go to Login Activity
            startActivity(Intent(this, FirstLoginActivity::class.java))
            finish()
            return
        }

        // Load user's QR code and name
        val currentUserUid = currentUser.uid
        val usersRef = database.getReference("users")

        // Check if user is admin
        loadingOverlay.visibility = View.VISIBLE
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
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
                        if (!isDestroyed) {
                            val fragmentTransaction = fragmentManager.beginTransaction()
                            val qrFragment = fragmentManager.findFragmentByTag("qr_fragment")
                            if (qrFragment == null) {
                                fragmentTransaction.add(R.id.frameLayout, QRFragment(), "qr_fragment")
                            } else {
                                fragmentTransaction.show(qrFragment)
                            }
                            fragmentTransaction.commit()
                        }
                    }
                    loadingOverlay.visibility = View.GONE
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to check user role: ${databaseError.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        smoothbottombar.setOnItemSelectedListener { pos ->
            val fragmentManager = supportFragmentManager
            val fragmentTransaction = fragmentManager.beginTransaction()

            // Hide all fragments
            val fragments = fragmentManager.fragments
            for (fragment in fragments) {
                fragmentTransaction.hide(fragment)
            }

            // Show the selected fragment
            when (pos) {
                0 -> {
                    val qrFragment = fragmentManager.findFragmentByTag("qr_fragment")
                    if (qrFragment == null) {
                        fragmentTransaction.add(R.id.frameLayout, QRFragment(), "qr_fragment")
                    } else {
                        fragmentTransaction.show(qrFragment)
                    }
                }
                1 -> {
                    val settingsFragment = fragmentManager.findFragmentByTag("settings_fragment")
                    if (settingsFragment == null) {
                        fragmentTransaction.add(R.id.frameLayout, SettingsFragment(), "settings_fragment")
                    } else {
                        fragmentTransaction.show(settingsFragment)
                    }
                }
            }
            fragmentTransaction.commit()
        }
    }
    override fun onResume() {
        super.onResume()

        val fragmentManager = supportFragmentManager
        val currentFragment = fragmentManager.findFragmentById(R.id.frameLayout)

        if (currentFragment is QRFragment) {
            smoothbottombar.itemActiveIndex = 0
        } else if (currentFragment is SettingsFragment) {
            smoothbottombar.itemActiveIndex = 1
        }
    }
}
