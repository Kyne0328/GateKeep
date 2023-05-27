package com.fernandez.gatekeep

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.text.InputType
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import jp.wasabeef.glide.transformations.RoundedCornersTransformation
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

    private lateinit var changephoto: ImageView
    private lateinit var userProfileImageView: ImageView
    private val compressionQuality = 60

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var capturedImageUri: Uri? = null
    private val CAMERA_PERMISSION_CODE = 200
    private var currentUserEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)

        val updateProfileBtn = findViewById<Button>(R.id.updateProfileBtn)
        val updatePasswordBtn = findViewById<Button>(R.id.updatePasswordBtn)
        val updateEmailBtn = findViewById<Button>(R.id.updateEmailBtn)
        val deleteAccountBtn = findViewById<Button>(R.id.deleteAccountBtn)

        loadProfileImage()

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Feature not yet implemented")
        dialogBuilder.setMessage("This feature is currently under development and will be available in a future update.")
        dialogBuilder.setPositiveButton("OK") { _, _ ->
            // Dismiss the dialog
        }

        mAuth = FirebaseAuth.getInstance()
        mUser = mAuth.currentUser!!
        mDatabaseRef = FirebaseDatabase.getInstance().reference.child("users").child(mUser.uid)
        mStorageRef = FirebaseStorage.getInstance().reference.child("profiles")

        updateProfileBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
            val dialog = dialogBuilder.create()
            dialog.show()
        }

        updateEmailBtn.setOnClickListener {
            showDialog(it.context)
        }

        updatePasswordBtn.setOnClickListener {
            val dialog = dialogBuilder.create()
            dialog.show()
        }

        deleteAccountBtn.setOnClickListener {
            val passwordDialog = AlertDialog.Builder(this)
            passwordDialog.setTitle("Enter your password")
            val passwordET = EditText(this)
            passwordET.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordDialog.setView(passwordET)

            passwordDialog.setPositiveButton("OK") { _, _ ->
                val password = passwordET.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val credential = EmailAuthProvider.getCredential(mUser.email!!, password)
                mUser.reauthenticate(credential)
                    .addOnSuccessListener {
                        val confirmDialog = AlertDialog.Builder(this)
                        confirmDialog.setTitle("Are you sure you want to delete your account?")
                        confirmDialog.setPositiveButton("Delete") { _, _ ->
                            deleteAccount()
                        }
                        confirmDialog.setNegativeButton("Cancel", null)
                        confirmDialog.show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
            }
            passwordDialog.setNegativeButton("Cancel", null)
            passwordDialog.show()
        }
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            // Retrieve user's name from the 'users' node
            val userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserUid)
                .child("name")
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)
                    if (name != null) {
                        // Display the username in a TextView
                        val uname = findViewById<TextView>(R.id.UserName)
                        uname.text = "$name"
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
        mUser.delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete account", Toast.LENGTH_SHORT).show()
            }
    }
    private fun rotateImage(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    private fun updateEmail(newEmail: String) {
        val user = FirebaseAuth.getInstance().currentUser
        currentUserEmail = user?.email

        val credential = currentUserEmail?.let { EmailAuthProvider.getCredential(it, "") }
        if (credential != null) {
            mUser.reauthenticate(credential)
                .addOnSuccessListener {
                    mUser.updateEmail(newEmail)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(
                                    this, "Email address updated successfully.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to update email address.", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to reauthenticate.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_email_change, null)

        val dialogTvCurrentEmailAddress = dialogView.findViewById<TextView>(R.id.CurrentEmailAddress)
        val dialogEtNewEmail = dialogView.findViewById<EditText>(R.id.NewEmail)
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
            if (newEmail.isNotEmpty()) {
                updateEmail(newEmail)
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
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}