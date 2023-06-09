package com.fernandez.gatekeep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.canhub.cropper.CropImage.CancelledResult.bitmap
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import jp.wasabeef.glide.transformations.RoundedCornersTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mUser: FirebaseUser
    private lateinit var mDatabaseRef: DatabaseReference
    private lateinit var mStorageRef: StorageReference
    private lateinit var mStorageRef1: StorageReference

    private lateinit var changephoto: ImageView
    private lateinit var userProfileImageView: ImageView
    private val compressionQuality = 60

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var capturedImageUri: Uri? = null
    private val CAMERA_PERMISSION_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        val updateProfileBtn = findViewById<Button>(R.id.updateProfileBtn)
        val updatePasswordBtn = findViewById<Button>(R.id.updatePasswordBtn)
        val updateEmailBtn = findViewById<Button>(R.id.updateEmailBtn)
        val deleteAccountBtn = findViewById<Button>(R.id.deleteAccountBtn)

        loadProfileImage()

        mAuth = FirebaseAuth.getInstance()
        mUser = mAuth.currentUser!!
        mDatabaseRef = FirebaseDatabase.getInstance().reference.child("users").child(mUser.uid)
        mStorageRef1 = FirebaseStorage.getInstance().reference.child("qr_codes").child("${mUser.uid}.jpg")
        mStorageRef = FirebaseStorage.getInstance().reference.child("profiles")

        updateProfileBtn.setOnClickListener {
            showUpdateProfileDialog(it.context)
        }

        updateEmailBtn.setOnClickListener {
            showDialog(it.context)
        }

        updatePasswordBtn.setOnClickListener {
            showPasswordChangeDialog(it.context)
        }

        deleteAccountBtn.setOnClickListener {
            showDeleteAccountDialog(it.context)
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
                        val uname = findViewById<TextView>(R.id.UserName)
                        uname.text = name
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(applicationContext,
                        "Failed to load user data: ${databaseError.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

        changephoto = findViewById(R.id.changephoto)
        userProfileImageView = findViewById(R.id.UserProfile)

        // Initialize activity result launchers
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val selectedImageUri: Uri? = data?.data
                if (selectedImageUri != null) {
                    val squareCropUri = performSquareCrop(selectedImageUri)
                    uploadProfileImage(squareCropUri)
                }
            }
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                capturedImageUri?.let { uri ->
                    val squareCropUri = performSquareCrop(uri)
                    uploadProfileImage(squareCropUri)
                }
            }
        }

        changephoto.setOnClickListener {
            chooseImageSource()
        }
        capturedImageUri?.let { uri ->
            val exif = contentResolver.openInputStream(uri)?.let { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> bitmap?.let { rotateImage(it, 90) }
                ExifInterface.ORIENTATION_ROTATE_180 -> bitmap?.let { rotateImage(it, 180) }
                ExifInterface.ORIENTATION_ROTATE_270 -> bitmap?.let { rotateImage(it, 270) }
                else -> bitmap
            }
        }
    }

    private fun chooseImageSource() {
        val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Image Source")
        builder.setItems(options) { dialog, item ->
            when {
                options[item] == "Take Photo" -> openCamera()
                options[item] == "Choose from Gallery" -> openGallery()
                options[item] == "Cancel" -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(galleryIntent)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile: File? = createImageFile()
            // cameraIntent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION, 0)
            cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", 1)
            photoFile?.let { file ->
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.fernandez.gatekeep.fileprovider",
                    file
                )
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                capturedImageUri = photoURI // Store the URI in the capturedImageUri variable
                cameraLauncher.launch(cameraIntent)
            }
        }
    }

    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }

    private fun uploadProfileImage(imageUri: Uri) {
        // Get the Firebase storage reference
        val storage = FirebaseStorage.getInstance()
        val storageRef: StorageReference = storage.reference

        // Create a reference to the profile image location with the users Firebase UUID as the filename
        val currentUser = FirebaseAuth.getInstance().currentUser
        val filename = currentUser?.uid ?: return
        val imageRef = storageRef.child("profiles/$filename.jpg")

        // Upload the image to Firebase Storage
        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                loadProfileImage()
                Toast.makeText(this, "Profile image updated successfully", Toast.LENGTH_SHORT).show()
                val cachedImageFile = File(imageUri.path.toString())
                cachedImageFile.delete()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImage() {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference

        val currentUser = FirebaseAuth.getInstance().currentUser
        val filename = currentUser?.uid ?: return
        val profileImageRef = storageRef.child("profiles/$filename.jpg")
        profileImageRef.downloadUrl
            .addOnSuccessListener { uri ->
                Glide.with(this@AccountSettingsActivity)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .transform(RoundedCornersTransformation(30, 0))
                    .into(userProfileImageView)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performSquareCrop(sourceUri: Uri): Uri {
        val sourceBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(sourceUri))
        val size = sourceBitmap.width.coerceAtMost(sourceBitmap.height)
        val x = (sourceBitmap.width - size) / 2
        val y = (sourceBitmap.height - size) / 2
        val croppedBitmap = Bitmap.createBitmap(sourceBitmap, x, y, size, size)

        val tempFile = createImageFile()
        val outputStream = FileOutputStream(tempFile)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, compressionQuality, outputStream)
        outputStream.close()

        return Uri.fromFile(tempFile)
    }
    private fun deleteAccount() {
        mDatabaseRef.removeValue()
            .addOnSuccessListener {
                mStorageRef1.delete()
                    .addOnSuccessListener {
                        mUser.delete()
                        .addOnSuccessListener {
                        Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@AccountSettingsActivity, FirstLoginActivity::class.java))
                            finish()
                    }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to delete account", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete account storage", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete account database", Toast.LENGTH_SHORT).show()
            }
    }
    private fun rotateImage(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    private fun updateEmail(newEmail: String, password: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val credentials = EmailAuthProvider.getCredential(currentUser?.email!!, password)

        currentUser.let {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Re-authenticate the user with their current email and password
                    it.reauthenticate(credentials).await()

                    // Update the email address
                    it.updateEmail(newEmail).await()

                    // Update the user's display name if needed
                    val displayName = it.displayName
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                    it.updateProfile(profileUpdates).await()

                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Email address updated successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Failed to update email address: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun showDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_email_change, null)

        val dialogTvCurrentEmailAddress = dialogView.findViewById<TextView>(R.id.CurrentEmailAddress)
        val dialogEtNewEmail = dialogView.findViewById<EditText>(R.id.NewEmail)
        val dialogETPassword = dialogView.findViewById<EditText>(R.id.password)
        val dialogChangeEmail = dialogView.findViewById<Button>(R.id.ChangeEmail)
        val dialogCancel = dialogView.findViewById<Button>(R.id.cancel)

        dialogTvCurrentEmailAddress.text = mUser.email

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .show()

        dialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogChangeEmail.setOnClickListener {
            val newEmail = dialogEtNewEmail.text.toString().trim()
            val password = dialogETPassword.text.toString().trim()
            if (newEmail.isNotEmpty()) {
                updateEmail(newEmail, password)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Please enter a new email address.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission denied, Allow permission in app settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun changePassword(oldPassword: String, newPassword: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val credentials = EmailAuthProvider.getCredential(currentUser?.email!!, oldPassword)

        currentUser.let {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Re-authenticate the user with their current email and password
                    it.reauthenticate(credentials).await()

                    // Change the password
                    it.updatePassword(newPassword).await()

                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Password changed successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@AccountSettingsActivity,
                        "Failed to change password: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun showPasswordChangeDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_password_change, null)

        val dialogEtOldPassword = dialogView.findViewById<EditText>(R.id.old_password)
        val dialogEtNewPassword = dialogView.findViewById<EditText>(R.id.new_password1)
        val dialogETPasswordConfirmation = dialogView.findViewById<EditText>(R.id.new_password2)
        val dialogChangePassword = dialogView.findViewById<Button>(R.id.ChangePassword)
        val dialogCancel = dialogView.findViewById<Button>(R.id.cancel)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .show()

        dialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogChangePassword.setOnClickListener {
            val oldPassword = dialogEtOldPassword.text.toString().trim()
            val newPassword = dialogEtNewPassword.text.toString().trim()
            val passwordConfirmation = dialogETPasswordConfirmation.text.toString().trim()
            if (oldPassword.isNotEmpty()) {
                if (newPassword.isNotEmpty()) {
                    if (passwordConfirmation.isNotEmpty()) {
                        if (newPassword == passwordConfirmation) {
                            changePassword(oldPassword, newPassword)
                            dialog.dismiss()
                        } else {
                            Toast.makeText(
                                this@AccountSettingsActivity,
                                "New password and confirmation do not match.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(context, "Please enter confirmation password.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Please enter new password.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Please enter your current password.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showDeleteAccountDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_account, null)

        val dialogETPassword = dialogView.findViewById<EditText>(R.id.password)
        val dialogDeleteAccount = dialogView.findViewById<Button>(R.id.deleteAccount)
        val dialogCancel = dialogView.findViewById<Button>(R.id.cancel)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .show()

        dialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogDeleteAccount.setOnClickListener {
            val dialogPassword = dialogETPassword.text.toString().trim()
            if (dialogPassword.isNotEmpty()) {
                val credential = EmailAuthProvider.getCredential(mUser.email!!, dialogPassword)
                mUser.reauthenticate(credential)
                    .addOnSuccessListener {
                            deleteAccount()
                        }
                    .addOnFailureListener {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
            }
        }
    }
    @SuppressLint("SetTextI18n")
    private fun showUpdateProfileDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_update_profile, null)

        val dialogEditName = dialogView.findViewById<Button>(R.id.editName)
        val dialogCancel = dialogView.findViewById<Button>(R.id.cancel)
        val dialogEtName = dialogView.findViewById<EditText>(R.id.name)

        dialogEtName.isEnabled = false

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserUid)
                .child("name")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        dialogEtName.setText(name)
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Toast.makeText(
                        context,
                        "Failed to load user data: ${databaseError.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .show()

        dialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogEditName.setOnClickListener {
            if (dialogEtName.isEnabled) {
                // User pressed "Done" button
                dialogEtName.isEnabled = false
                dialogEditName.text = "Edit"
                updateInfo(dialogEtName.text.toString(), dialog)
            } else {
                // User pressed "Edit Name" button
                dialogEtName.isEnabled = true
                dialogEtName.requestFocus()
                dialogEditName.text = "Done"
            }
        }
    }
    private fun updateInfo(newName: String, dialog: Dialog) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserUid)
                .child("name")

            userRef.setValue(newName)
                .addOnSuccessListener {
                    Toast.makeText(
                        applicationContext,
                        "Name updated successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        applicationContext,
                        "Failed to update name: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}