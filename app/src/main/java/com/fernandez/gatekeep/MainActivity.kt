package com.fernandez.gatekeep

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import me.ibrahimsn.lib.SmoothBottomBar

class MainActivity : AppCompatActivity() {

    private lateinit var smoothbottombar: SmoothBottomBar
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var loadingOverlay: ProgressBar
    private lateinit var usersRef: DatabaseReference
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasNotificationPermissionGranted = isGranted
            if (!isGranted) {
                if (Build.VERSION.SDK_INT >= 33) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationPermissionRationale()
                    } else {
                        showSettingDialog()
                    }
                }
            }
        }

    private fun showSettingDialog() {
        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
            .setTitle("Notification Permission")
            .setMessage("Notification permission is required. Please allow notification permission from settings.")
            .setPositiveButton("OK") { _, _ ->
                val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNotificationPermissionRationale() {
        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
            .setTitle("Alert")
            .setMessage("Notification permission is required to show notifications.")
            .setPositiveButton("OK") { _, _ ->
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var hasNotificationPermissionGranted = false

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
        usersRef = database.getReference("users")

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
                        // User is not an admin, perform additional approval check
                        checkUserApproval(currentUserUid)
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
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermissionGranted = true
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

    private fun checkUserApproval(uid: String) {
        usersRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val approvalStatus = dataSnapshot.child("isApproved").getValue(Boolean::class.java)
                val rejectionStatus = dataSnapshot.child("isRejected").getValue(Boolean::class.java)

                if (approvalStatus == true) {
                    // Proceed with the requested functionality
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
                } else if (approvalStatus == false && rejectionStatus == true) {
                    showAccountRejectedDialog(uid)
                } else {
                    showAccountPendingDialog()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to check user approval: ${databaseError.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showAccountRejectedDialog(uid: String) {
        AlertDialog.Builder(this)
            .setTitle("Account Rejected")
            .setMessage("Your account has been rejected. Please register again with the correct details.")
            .setPositiveButton("OK") { dialog, _ ->
                val currentUser = auth.currentUser
                currentUser?.delete()?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        usersRef.child(uid).removeValue()
                        auth.signOut()
                        startActivity(Intent(this@MainActivity, FirstLoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to delete account: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showAccountPendingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Account Pending Approval")
            .setMessage("Your account is pending approval. Please wait until your account is approved.")
            .setPositiveButton("OK") { _, _ ->
                startActivity(Intent(this@MainActivity, FirstLoginActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
