package com.fernandez.gatekeep

import android.annotation.SuppressLint
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


class AttendanceHistoryFragment : Fragment() {
    private lateinit var database: FirebaseDatabase
    private lateinit var rvAttendance: RecyclerView
    private lateinit var attendanceHistoryAdapter: AttendanceHistoryAdapter
    private val attendanceList = mutableListOf<Attendance>()

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
                        val name = attendanceData["name"] as String
                        val date = attendanceData["date"] as String
                        val time = attendanceData["time"] as String
                        // Do something with the attendance record
                        val attendance = Attendance(name, date, time)
                        attendanceList.add(attendance)
                    }
                }
                val sortedList = attendanceList.sortedWith(compareByDescending<Attendance> { it.date }.thenByDescending { it.time })
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
}