package com.fernandez.gatekeep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import me.ibrahimsn.lib.SmoothBottomBar

class QRScannerActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_CODE = 200
    private lateinit var smoothbottombar: SmoothBottomBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        smoothbottombar = findViewById(R.id.bottomBar)
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
                    val scannerFragment = fragmentManager.findFragmentByTag("scanner_fragment")
                    if (scannerFragment == null) {
                        fragmentTransaction.add(R.id.fragment_container, ScannerFragment(), "scanner_fragment")
                    } else {
                        fragmentTransaction.show(scannerFragment)
                    }
                }
                1 -> {
                    val settingsFragment = fragmentManager.findFragmentByTag("settings_fragment")
                    if (settingsFragment == null) {
                        fragmentTransaction.add(R.id.fragment_container, SettingsFragment(), "settings_fragment")
                    } else {
                        fragmentTransaction.show(settingsFragment)
                    }
                }
            }
            fragmentTransaction.commit()
        }

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }

        // Display the ScannerFragment if it hasn't been added yet
        if (supportFragmentManager.findFragmentByTag("scanner_fragment") == null) {
            val scannerFragment = ScannerFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, scannerFragment, "scanner_fragment")
                .commit()
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
}
