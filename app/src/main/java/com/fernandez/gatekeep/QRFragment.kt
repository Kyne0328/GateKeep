package com.fernandez.gatekeep

import android.annotation.SuppressLint
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
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import jp.wasabeef.glide.transformations.RoundedCornersTransformation
import java.text.SimpleDateFormat
import java.util.Locale

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
                @SuppressLint("SetTextI18n")
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
                            @SuppressLint("NotifyDataSetChanged")
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                attendanceList.clear()
                                for (attendanceSnapshot in dataSnapshot.children) {
                                    val attendanceData = attendanceSnapshot.value as HashMap<*, *>
                                    val date = attendanceData["date"] as? String
                                    val time = attendanceData["time"] as? String
                                    if (date == null || time == null) {
                                        continue
                                    }

                                    // Convert date format
                                    val formattedDate = formatDate(date)
                                    // Convert time format
                                    val formattedTime = formatTime(time)
                                    val attendance = Attendance(name, date, time, formattedDate, formattedTime)
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
                    val qrCodeRef: StorageReference = storageRef.child("qr_codes/$currentUserUid2.jpg")
                    qrCodeRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            Glide.with(this@QRFragment)
                                .load(uri)
                                .apply(RequestOptions.circleCropTransform())
                                .transform(RoundedCornersTransformation(30, 0))
                                .into(ivQrCode)
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
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
    private fun formatDate(date: String): String {
        val inputFormat = SimpleDateFormat("dd-MM-yy", Locale.US)
        val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        val inputDate = inputFormat.parse(date)
        return outputFormat.format(inputDate!!)
    }

    private fun formatTime(time: String): String {
        val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
        val inputTime = inputFormat.parse(time)
        return outputFormat.format(inputTime!!)
    }
}