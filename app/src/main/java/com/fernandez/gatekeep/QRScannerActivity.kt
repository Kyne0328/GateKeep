package com.fernandez.gatekeep

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.Result
import me.dm7.barcodescanner.zxing.ZXingScannerView
import java.text.SimpleDateFormat
import java.util.*

class QRScannerActivity : AppCompatActivity(), ZXingScannerView.ResultHandler {

    private val CAMERA_PERMISSION_CODE = 200
    private lateinit var scannerView: ZXingScannerView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable the ActionBar
        supportActionBar!!.hide()

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        scannerView = ZXingScannerView(this)
        scannerView.setSquareViewFinder(true) // set view finder to a square
        setContentView(scannerView)

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        scannerView.setResultHandler(this)
        scannerView.startCamera()
    }

    override fun onPause() {
        super.onPause()
        scannerView.stopCamera()
    }

    override fun handleResult(result: Result?) {
        // Process the scanned QR code data
        result?.let {
            val qrData = it.text.split(",")
            val name = qrData[0]
            if (qrData.size >= 2) qrData[1] else "Unknown"

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            // Save data to Firebase Realtime Database
            val databaseRef = FirebaseDatabase.getInstance().getReference("attendance/$date/$name")
            databaseRef.child("time").setValue(time)

            Toast.makeText(this, "Attendance recorded for $name", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Attendance recorded for $name at $time on $date")

            // Add a rescan button to resume scanning for more QR codes
            val rescanButton = Button(this)
            rescanButton.text = "Rescan"
            rescanButton.setOnClickListener {
                scannerView.resumeCameraPreview(this)
            }
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addContentView(rescanButton, layoutParams)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    companion object {
        private const val TAG = "QRScannerActivity"
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