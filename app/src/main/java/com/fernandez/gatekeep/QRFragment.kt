package com.fernandez.gatekeep

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class QRFragment : Fragment() {
    private lateinit var ivQrCode: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var rvAttendance: RecyclerView
    private lateinit var attendanceAdapter: AttendanceAdapter
    private val attendanceList = mutableListOf<Attendance>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_q_r, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivQrCode = view.findViewById(R.id.ivQrCode)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        rvAttendance = view.findViewById(R.id.rvAttendance)
        attendanceAdapter = AttendanceAdapter(attendanceList)
        rvAttendance.adapter = attendanceAdapter
        rvAttendance.layoutManager = LinearLayoutManager(requireContext())

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            // Retrieve user's name from the 'users' node
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserUid)
                .child("name")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        // Display the username in a TextView
                        val tv1 = view.findViewById<TextView>(R.id.tv1)
                        tv1.text = "Welcome, $name"
                        // Retrieve attendance data based on user's name
                        val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance")
                            .child(currentUserUid)
                        attendanceRef.addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                attendanceList.clear()
                                for (attendanceSnapshot in dataSnapshot.children) {
                                    val attendanceData = attendanceSnapshot.value as HashMap<*, *>
                                    val date = attendanceData["date"] as String
                                    val time = attendanceData["time"] as String
                                    // Do something with the attendance record
                                    val attendance = Attendance(name, date, time)
                                    attendanceList.add(attendance)
                                }
                                val sortedList = attendanceList.sortedWith(compareByDescending<Attendance> { it.date }.thenByDescending { it.time })
                                attendanceList.clear()
                                attendanceList.addAll(sortedList)
                                attendanceAdapter.notifyDataSetChanged()
                            }

                            override fun onCancelled(databaseError: DatabaseError) {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to load attendance data: ${databaseError.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load user data: ${databaseError.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
        // Retrieve QR code image from Firebase Storage
        val currentUserUid2 = auth.currentUser?.uid
        val usersRef = database.getReference("users")
        if (currentUserUid2 != null) {
            usersRef.child(currentUserUid2).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val qrCodeRef = storageRef.child("qr_codes/$currentUserUid2.jpg")
                    val ONE_MEGABYTE: Long = 1024 * 1024
                    qrCodeRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
                        val qrCodeBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                        // Create a new Bitmap with rounded corners
                        val roundedBitmap = Bitmap.createBitmap(qrCodeBitmap.width, qrCodeBitmap.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(roundedBitmap)
                        val paint = Paint()
                        paint.isAntiAlias = true
                        val shader = BitmapShader(qrCodeBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                        paint.shader = shader
                        val rect = RectF(0f, 0f, qrCodeBitmap.width.toFloat(), qrCodeBitmap.height.toFloat())
                        canvas.drawRoundRect(rect, 15f, 15f, paint)

                        // Set the rounded bitmap to the ivQrCode ImageView
                        ivQrCode.setImageBitmap(roundedBitmap)
                    }.addOnFailureListener { exception ->
                        Toast.makeText(
                            requireContext(),
                            "Failed to load QR code: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load user data: ${databaseError.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }
}