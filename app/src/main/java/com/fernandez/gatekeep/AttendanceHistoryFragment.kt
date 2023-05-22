package com.fernandez.gatekeep

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale


class AttendanceHistoryFragment : Fragment() {
    private lateinit var database: FirebaseDatabase
    private lateinit var rvAttendance: RecyclerView
    private lateinit var attendanceHistoryAdapter: AttendanceHistoryAdapter
    private val attendanceList = mutableListOf<AttendanceAdmin>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_attendance_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance()
        rvAttendance = view.findViewById(R.id.rvAttendance)
        attendanceHistoryAdapter = AttendanceHistoryAdapter(attendanceList)
        rvAttendance.adapter = attendanceHistoryAdapter
        rvAttendance.layoutManager = LinearLayoutManager(requireContext())

        // Retrieve all attendance data
        val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance")
        attendanceRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                attendanceList.clear()
                for (userSnapshot in dataSnapshot.children) {
                    for (attendanceSnapshot in userSnapshot.children) {
                        val attendanceData = attendanceSnapshot.value as HashMap<*, *>
                        val name = attendanceData["name"] as? String
                        val time = attendanceData["time"] as? String
                        val date = attendanceData["date"] as? String
                        val grade = attendanceData["grade"] as? String
                        val section = attendanceData["section"] as? String
                        if (name == null || date == null || time == null || grade == null || section == null) {
                            continue
                        }
                        // Convert date format
                        val formattedDate = formatDate(date)
                        // Convert time format
                        val formattedTime = formatTime(time)
                        val attendance = AttendanceAdmin(name, date, time, grade, section, formattedDate, formattedTime)
                        attendanceList.add(attendance)
                    }
                }
                val sortedList = attendanceList.sortedWith(compareByDescending<AttendanceAdmin> { it.date }.thenByDescending { it.time })
                attendanceList.clear()
                attendanceList.addAll(sortedList)
                attendanceHistoryAdapter.notifyDataSetChanged()
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
    private fun formatDate(date: String): String {
        val inputFormat = SimpleDateFormat("dd-MM-yy", Locale.US)
        val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        val inputDate = inputFormat.parse(date)
        return outputFormat.format(inputDate)
    }

    private fun formatTime(time: String): String {
        val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
        val inputTime = inputFormat.parse(time)
        return outputFormat.format(inputTime)
    }
}