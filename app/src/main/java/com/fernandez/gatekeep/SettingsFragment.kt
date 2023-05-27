package com.fernandez.gatekeep

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.random.Random

class SettingsFragment : Fragment() {

    private lateinit var githubButton: Button
    private lateinit var updateButton: Button
    private lateinit var logoutButton: Button
    private lateinit var disclaimerButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var accountSettingsButton: Button
    private lateinit var userProfileImageView: ImageView

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        accountSettingsButton = view.findViewById(R.id.account_settings)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        userProfileImageView = view.findViewById(R.id.ProfilePicture)

        updateButton = view.findViewById(R.id.update_button)
        updateButton.setOnClickListener {
            checkForUpdates()
        }

        // Initialize views
        githubButton = view.findViewById(R.id.github_button)
        logoutButton = view.findViewById(R.id.logout_button)
        disclaimerButton = view.findViewById(R.id.disclaimer)

        // Set click listeners
        accountSettingsButton.setOnClickListener {
            val intent = Intent(requireContext(), AccountSettingsActivity::class.java)
            startActivity(intent)
        }

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
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            // Retrieve user's name from the 'users' node
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserUid)
                .child("name")
            userRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        // Display the username in a TextView
                        val uname = view.findViewById<TextView>(R.id.userName)
                        uname.text = name
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
        loadProfileImage()
        return view
    }
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
    private fun checkForUpdates() {
        coroutineScope.launch(Dispatchers.IO) {
            val latestVersion = getLatestVersionFromGithub()
            if (latestVersion != null && latestVersion.first?.let { isUpdateAvailable(it) } == true) {
                withContext(Dispatchers.Main) {
                    showUpdateDialog(latestVersion)
                }
            }
            else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "You are already on the latest version!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun getLatestVersionFromGithub(): Triple<String?, String?, String?>? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/repos/KyNe0328/GateKeep/releases/latest")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseString = response.body.string()
            val jsonObject = JSONObject(responseString)
            val tagName = jsonObject.getString("tag_name")
            val body = jsonObject.getString("body")
            val assetsArray = jsonObject.getJSONArray("assets")
            if (assetsArray.length() > 0) {
                val assetObject = assetsArray.getJSONObject(0)
                val downloadUrl = assetObject.getString("browser_download_url")
                Triple(tagName, downloadUrl, body)
            } else {
                Triple(tagName, null, body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isUpdateAvailable(latestVersion: String): Boolean {
        return latestVersion != BuildConfig.VERSION_NAME
    }

    private fun showUpdateDialog(updateInfo: Triple<String?, String?, String?>) {
        val message = "New update ${updateInfo.first} is available.\n\n${updateInfo.third}\n\nDo you want to download it?"
        AlertDialog.Builder(requireContext())
            .setTitle("New update found")
            .setMessage(message)
            .setPositiveButton("Download") { _, _ ->
                updateInfo.second?.let {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(it)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun loadProfileImage() {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference

        val currentUser = FirebaseAuth.getInstance().currentUser
        val filename = currentUser?.uid ?: return
        val profileImageRef = storageRef.child("profiles/$filename.jpg")

        profileImageRef.downloadUrl
            .addOnSuccessListener { uri ->
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(userProfileImageView)
            }
            .addOnFailureListener {
                // Failed to load the user's custom image, falling back to default avatar
                val defaultAvatarPrefix = if (userGender()) "male_" else "female_"
                val randomAvatarIndex = Random.nextInt(1, 4)
                val defaultAvatarFilename = "$defaultAvatarPrefix$randomAvatarIndex.jpg"
                val defaultAvatarRef = storageRef.child("profiles/default_avatars/$defaultAvatarFilename")

                defaultAvatarRef.downloadUrl
                    .addOnSuccessListener { defaultAvatarUri ->
                        Glide.with(this)
                            .load(defaultAvatarUri)
                            .circleCrop()
                            .into(userProfileImageView)

                        val userUUID = currentUser.uid
                        val userAvatarRef = storageRef.child("profiles/$userUUID.jpg")

                        defaultAvatarRef.getBytes(Long.MAX_VALUE)
                            .addOnSuccessListener { bytes ->
                                val inputStream = ByteArrayInputStream(bytes)

                                val uploadTask = userAvatarRef.putStream(inputStream)
                                uploadTask.addOnSuccessListener {
                                    Toast.makeText(context, "Default avatar uploaded", Toast.LENGTH_SHORT).show()
                                }.addOnFailureListener {
                                    Toast.makeText(context, "Failed to upload default avatar", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed to load default avatar", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to load default avatar", Toast.LENGTH_SHORT).show()
                    }
            }
    }
    private fun userGender(): Boolean {
        return Random.nextBoolean()
    }
    override fun onResume() {
        super.onResume()
        loadProfileImage()
    }
}
