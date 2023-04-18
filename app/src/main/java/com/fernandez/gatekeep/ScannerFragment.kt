package com.fernandez.gatekeep

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.zxing.Result
import me.dm7.barcodescanner.zxing.ZXingScannerView
import java.text.SimpleDateFormat
import java.util.*

class ScannerFragment : Fragment(), ZXingScannerView.ResultHandler {

    private lateinit var scannerView: ZXingScannerView
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        scannerView = ZXingScannerView(activity)
        scannerView.setSquareViewFinder(true) // set view finder to a square

        return scannerView
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
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            // Show a dialog to confirm the attendance
            val dialogBuilder = AlertDialog.Builder(activity)
            dialogBuilder.setMessage("Allow access to $name?")
                .setCancelable(false)
                .setPositiveButton("Accept") { _, _ ->
                    // Save data to Firebase Realtime Database
                    val databaseRef = FirebaseDatabase.getInstance().getReference("attendance/$date/$name")
                    val attendanceKey = databaseRef.push().key // Generate a unique key for this attendance record
                    val attendanceData = HashMap<String, Any>()
                    attendanceData["name"] = name
                    attendanceData["time"] = time
                    databaseRef.child(attendanceKey!!).setValue(attendanceData)

                    Toast.makeText(activity, "Attendance recorded for $name", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Attendance recorded for $name at $time on $date")

                    // Resume scanning for more QR codes
                    scannerView.resumeCameraPreview(this)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    // Resume scanning for more QR codes
                    scannerView.resumeCameraPreview(this)
                }
            val dialog = dialogBuilder.create()
            dialog.show()
        }
    }
    companion object {
        private const val TAG = "ScannerFragment"
    }
}
