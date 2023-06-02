package com.fernandez.gatekeep

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class PendingAdapter(private val pendingList: MutableList<Pending>) : RecyclerView.Adapter<PendingAdapter.PendingViewHolder>() {

    inner class PendingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSection: TextView = itemView.findViewById(R.id.tvSection)
        private val tvGrade: TextView = itemView.findViewById(R.id.tvGrade)

        fun bind(pending: Pending) {
            tvName.text = pending.name
            tvSection.text = pending.section
            tvGrade.text = pending.grade

            itemView.setOnClickListener {
                showDialog(itemView.context, pending)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PendingAdapter.PendingViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_pending_signup, parent, false)
        return PendingViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PendingAdapter.PendingViewHolder, position: Int) {
        val pending = pendingList[position]
        holder.bind(pending)
    }

    override fun getItemCount(): Int = pendingList.size

    private fun showDialog(context: Context, pending: Pending) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_pending_signup, null)

        val dialogTvName = dialogView.findViewById<TextView>(R.id.dialogTvName)
        val dialogTvGradeSection = dialogView.findViewById<TextView>(R.id.dialogTvGradeSection)
        val dialogTvLRN = dialogView.findViewById<TextView>(R.id.dialogTvLRN)
        val dialogBtnOk = dialogView.findViewById<Button>(R.id.dialogBtnOk)
        val dialogBtnAccept = dialogView.findViewById<Button>(R.id.dialogBtnAccept)
        val dialogBtnReject = dialogView.findViewById<Button>(R.id.dialogBtnReject)

        FirebaseStorage.getInstance().reference
        val databaseRef = FirebaseDatabase.getInstance().reference.child("users")
        databaseRef.orderByChild("name").equalTo(pending.name)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    for (snapshot in dataSnapshot.children) {
                        val studentName = snapshot.child("name").value.toString()
                        val studentGrade = snapshot.child("grade").value.toString()
                        val studentSection = snapshot.child("section").value.toString()
                        val studentLRN = snapshot.child("lrn").value.toString()

                        // Display data in the dialog
                        dialogTvName.text = studentName
                        dialogTvGradeSection.text = buildString {
                            append(studentGrade)
                            append(" ")
                            append(studentSection)
                        }
                        dialogTvLRN.text = studentLRN
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                }
            })

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .show()

        dialogBtnAccept.setOnClickListener {
            // Update the 'isApproved' status to true in the Firebase database
            val userRef = FirebaseDatabase.getInstance().reference.child("users").child(pending.userId)
            userRef.child("isApproved").setValue(true)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Send an FCM notification to the user
                        val notificationTitle = "Approval Notification"
                        val notificationMessage = "Your account creation request has been approved."
                        sendFCMNotification(pending.fcmToken, notificationTitle, notificationMessage)

                        Toast.makeText(context, "Approved", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, "Approval failed", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
        }

        dialogBtnReject.setOnClickListener {
            val userRef = FirebaseDatabase.getInstance().reference.child("users").child(pending.userId)
            userRef.child("isRejected").setValue(true)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val notificationTitle = "Rejection Notification"
                        val notificationMessage = "Your account creation request has been rejected."
                        sendFCMNotification(pending.fcmToken, notificationTitle, notificationMessage)

                        Toast.makeText(context, "Rejected", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, "Rejection failed", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
        }

        dialogBtnOk.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun sendFCMNotification(token: String, title: String, message: String) {
        FirebaseMessaging.getInstance().send(
            RemoteMessage.Builder(token)
                .setMessageId(UUID.randomUUID().toString())
                .addData("title", title)
                .addData("message", message)
                .build()
        )
    }
}