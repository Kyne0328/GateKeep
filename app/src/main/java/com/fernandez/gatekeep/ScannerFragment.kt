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
import java.util.Date
import java.util.Locale

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
        scannerView.setLaserEnabled(false)
        scannerView.setSquareViewFinder(true)

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
        result?.let {
            val qrData = it.text.split(",")
            val userId = qrData[0]
            val name = qrData[1]
            val grade = qrData[2]
            val section = qrData[3]
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

            val dialogBuilder = AlertDialog.Builder(activity)
            dialogBuilder.setMessage("Allow entry for the student: \n$name?")
            dialogBuilder.setCancelable(false)
            dialogBuilder.setPositiveButton("Yes") { _, _ ->
                // Save data to Firebase Realtime Database
                val databaseRef = FirebaseDatabase.getInstance().getReference("attendance")
                val attendanceData = HashMap<String, Any>()
                attendanceData["date"] = date
                attendanceData["name"] = name
                attendanceData["time"] = time
                attendanceData["grade"] = grade
                attendanceData["section"] = section
                databaseRef.child(userId).push().setValue(attendanceData)
                    .addOnSuccessListener {
                        Toast.makeText(
                            activity,
                            "Attendance recorded for $name",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d(TAG, "Attendance recorded for $name at $time on $date")

                        // Resume scanning for more QR codes
                        scannerView.resumeCameraPreview(this)
                    }
                    .addOnFailureListener { exception ->
                        // Handle any exceptions that occurred
                        Toast.makeText(
                            activity,
                            "Failed to record attendance: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "Failed to record attendance", exception)

                        // Resume scanning for more QR codes
                        scannerView.resumeCameraPreview(this)
                    }
            }
            dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
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