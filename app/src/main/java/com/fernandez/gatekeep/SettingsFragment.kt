package com.fernandez.gatekeep

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private lateinit var githubButton: Button
    private lateinit var logoutButton: Button
    private lateinit var disclaimerButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize views
        githubButton = view.findViewById(R.id.github_button)
        logoutButton = view.findViewById(R.id.logout_button)
        disclaimerButton = view.findViewById(R.id.disclaimer)

        // Set click listeners
        githubButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KyNe0328/GateKeep"))
            startActivity(intent)
        }

        logoutButton.setOnClickListener {
            // Log out the user from Firebase Auth
            FirebaseAuth.getInstance().signOut()
            // Redirect the user to the login activity
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        disclaimerButton.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Disclaimer")
                .setMessage("Please note that the GateKeep app is designed for School entry tracking purposes only. The app is not responsible for verifying the accuracy of the entry records of the attendees. The QR codes assigned to the students are solely for the purpose of entry tracking and should not be used for any other purpose. The app does not guarantee the security or confidentiality of the student's personal information, including their name and email address. The Gate Keeper is solely responsible for ensuring that the attendance records are accurate and up-to-date. The use of this app is at the discretion of the user, and we assume no liability for any issues arising from the use of this app.")
                .setPositiveButton("OK", null)
            builder.create().show()
        }

        return view
    }
}
