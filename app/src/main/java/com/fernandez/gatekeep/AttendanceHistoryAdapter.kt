package com.fernandez.gatekeep

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AttendanceHistoryAdapter(private val attendanceList: MutableList<AttendanceAdmin>) : RecyclerView.Adapter<AttendanceHistoryAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvGrade: TextView = itemView.findViewById(R.id.tvGrade)
        private val tvSection: TextView = itemView.findViewById(R.id.tvSection)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(attendance: AttendanceAdmin) {
            tvName.text = attendance.name
            tvDate.text = attendance.formattedDate
            tvTime.text = attendance.formattedTime
            tvGrade.text = attendance.grade
            tvSection.text = attendance.section

            itemView.setOnClickListener {
                showDialog(itemView.context, attendance)
            }
        }

        override fun onClick(view: View) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val attendance = attendanceList[position]
                showDialog(view.context, attendance)
            }
        }

        private fun showDialog(context: Context, attendance: AttendanceAdmin) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_attendance_details, null)

            val dialogTvName = dialogView.findViewById<TextView>(R.id.dialogTvName)
            val dialogTvDateTime = dialogView.findViewById<TextView>(R.id.dialogTvDateTime)
            val dialogTvGradeSection = dialogView.findViewById<TextView>(R.id.dialogTvGradeSection)
            val dialogBtnOk = dialogView.findViewById<Button>(R.id.dialogBtnOk)
            val dialogBtnDelete = dialogView.findViewById<Button>(R.id.DeleteHistoryBtn)

            dialogTvName.text = attendance.name
            dialogTvDateTime.text = "${attendance.formattedDate} ${attendance.formattedTime}"
            dialogTvGradeSection.text = "${attendance.grade} ${attendance.section}"

            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .show()

            dialogBtnOk.setOnClickListener {
                dialog.dismiss()
            }

            dialogBtnDelete.setOnClickListener {
                showDeleteConfirmationDialog(context, attendance)
                dialog.dismiss()
            }
        }

        private fun showDeleteConfirmationDialog(context: Context, attendance: AttendanceAdmin) {
            val passwordEditText = EditText(context)
            passwordEditText.hint = "Enter your password"

            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            layout.addView(passwordEditText)

            AlertDialog.Builder(context)
                .setTitle("Delete Confirmation")
                .setMessage("Are you sure you want to delete this entry?")
                .setView(layout)
                .setPositiveButton("Delete") { dialog, _ ->
                    val password = passwordEditText.text.toString()
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (password.isEmpty()) {
                        Toast.makeText(context, "Please enter your password", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (currentUser != null) {
                        val credentials  = EmailAuthProvider.getCredential(currentUser.email!!, password)
                        currentUser.reauthenticate(credentials)
                            .addOnSuccessListener {
                                deleteDataFromFirebase(attendance)
                                dialog.dismiss()
                            }
                            .addOnFailureListener { exception ->
                                Toast.makeText(context, "Authentication failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        private fun deleteDataFromFirebase(attendance: AttendanceAdmin) {
            val firebaseDatabase = FirebaseDatabase.getInstance()
            val usersRef = firebaseDatabase.getReference("users")

            // Get the user's UUID based on the attendance name
            usersRef.orderByChild("name").equalTo(attendance.name)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        for (snapshot in dataSnapshot.children) {
                            val userUUID = snapshot.key
                            if (userUUID != null) {
                                // Proceed to delete the attendance data based on UUID, date, and time
                                val attendanceRef = firebaseDatabase.getReference("attendance").child(userUUID)

                                attendanceRef.orderByChild("date").equalTo(attendance.date)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(attendanceSnapshot: DataSnapshot) {
                                            for (attendanceChildSnapshot in attendanceSnapshot.children) {
                                                val attendanceTime =
                                                    attendanceChildSnapshot.child("time").getValue(String::class.java)
                                                if (attendanceTime == attendance.time) {
                                                    // Delete the specific attendance record
                                                    attendanceChildSnapshot.ref.removeValue()
                                                }
                                            }
                                        }

                                        override fun onCancelled(databaseError: DatabaseError) {
                                            // Handle cancellation or error
                                        }
                                    })
                            }
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {
                        // Handle cancellation or error
                    }
                })
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
