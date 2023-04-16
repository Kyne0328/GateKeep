package com.fernandez.gatekeep

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

/**
 * A simple [Fragment] subclass.
 * Use the [QRFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class QRFragment : Fragment() {
    private lateinit var ivQrCode: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

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

        val currentUserUid = auth.currentUser?.uid
        val usersRef = database.getReference("users")
        if (currentUserUid != null) {
            usersRef.child(currentUserUid).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val qrCodeRef = storageRef.child("qr_codes/$currentUserUid.jpg")
                    val ONE_MEGABYTE: Long = 1024 * 1024
                    qrCodeRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
                        val qrCodeBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ivQrCode.setImageBitmap(qrCodeBitmap)
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
