package com.fernandez.gatekeep

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendanceHistoryAdapter(private val attendanceList: MutableList<Attendance>) : RecyclerView.Adapter<AttendanceHistoryAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(attendance: Attendance) {
            tvName.text = attendance.name
            tvTime.text = attendance.time
            tvDate.text = attendance.date
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_history, parent, false)
        return AttendanceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        val attendance = attendanceList[position]
        holder.bind(attendance)
    }

    override fun getItemCount(): Int = attendanceList.size
}
