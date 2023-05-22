package com.fernandez.gatekeep

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SettingsFragment : Fragment() {

    private lateinit var githubButton: Button
    private lateinit var updateButton: Button
    private lateinit var logoutButton: Button
    private lateinit var disclaimerButton: Button
    private lateinit var accountSettingsButton: Button

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        accountSettingsButton = view.findViewById(R.id.account_settings)

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
}
