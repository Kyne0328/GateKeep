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

class PendingSignUpFragment : Fragment() {
    private lateinit var rvPending: RecyclerView
    private lateinit var database: FirebaseDatabase
    private lateinit var pendingAdapter: PendingAdapter
    private val pendingList = mutableListOf<Pending>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pending_sign_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvPending = view.findViewById(R.id.rvPending)
        database = FirebaseDatabase.getInstance()
        pendingAdapter = PendingAdapter(pendingList)
        rvPending.adapter = pendingAdapter
        rvPending.layoutManager = LinearLayoutManager(requireContext())

        val pendingRef = FirebaseDatabase.getInstance().getReference("users")
        pendingRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                pendingList.clear()
                for (userSnapshot in dataSnapshot.children) {
                    val pendingData = userSnapshot.value as? HashMap<*, *>
                    val name = pendingData?.get("name") as? String ?: "null"
                    val grade = pendingData?.get("grade") as? String ?: "null"
                    val section = pendingData?.get("section") as? String ?: "null"
                    val lrn = pendingData?.get("lrn") as? String ?: "null"
                    val userID = userSnapshot.key ?: "null"
                    val fcmToken = pendingData?.get("fcmToken") as? String ?: "null"
                    val isApproved = pendingData?.get("isApproved") as? Boolean ?: "null"
                    val isRejected = pendingData?.get("isRejected") as? Boolean ?: "null"

                    if (name == "null" || lrn == "null" || isApproved == "null" || isApproved == "true" || isRejected == "true" || grade == "null" || section == "null") {
                        continue
                    }

                    val pending = Pending(
                        name,
                        section,
                        grade,
                        isApproved as Boolean,
                        lrn,
                        userID,
                        fcmToken,
                    isRejected as Boolean)
                    pendingList.add(pending)
                }
                pendingAdapter.notifyDataSetChanged()
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