package com.fernandez.gatekeep

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class AttendanceHistoryFragment : Fragment() {
    private lateinit var database: FirebaseDatabase
    private lateinit var rvAttendance: RecyclerView
    private lateinit var attendanceHistoryAdapter: AttendanceHistoryAdapter
    private val attendanceList = mutableListOf<AttendanceAdmin>()
    private var exportToExcel: ImageView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_attendance_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance()
        rvAttendance = view.findViewById(R.id.rvAttendance)
        attendanceHistoryAdapter = AttendanceHistoryAdapter(attendanceList)
        rvAttendance.adapter = attendanceHistoryAdapter
        rvAttendance.layoutManager = LinearLayoutManager(requireContext())

        // Retrieve all attendance data
        val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance")
        attendanceRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                attendanceList.clear()
                for (userSnapshot in dataSnapshot.children) {
                    for (attendanceSnapshot in userSnapshot.children) {
                        val attendanceData = attendanceSnapshot.value as HashMap<*, *>
                        val name = attendanceData["name"] as? String
                        val time = attendanceData["time"] as? String
                        val date = attendanceData["date"] as? String
                        val grade = attendanceData["grade"] as? String
                        val section = attendanceData["section"] as? String
                        if (name == null || date == null || time == null || grade == null || section == null) {
                            continue
                        }
                        // Convert date format
                        val formattedDate = formatDate(date)
                        // Convert time format
                        val formattedTime = formatTime(time)
                        val attendance = AttendanceAdmin(name, date, time, grade, section, formattedDate, formattedTime)
                        attendanceList.add(attendance)
                    }
                }
                val sortedList = attendanceList.sortedWith(compareByDescending<AttendanceAdmin> { it.date }.thenByDescending { it.time })
                attendanceList.clear()
                attendanceList.addAll(sortedList)
                attendanceHistoryAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(
                    requireContext(),
                    "Failed to load attendance data: ${databaseError.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        exportToExcel = view.findViewById(R.id.exportToExcel)
        exportToExcel?.setOnClickListener {
            showExportToExcelDialog(requireContext())
        }
    }
    private fun formatDate(date: String): String {
        val inputFormat = SimpleDateFormat("dd-MM-yy", Locale.US)
        val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        val inputDate = inputFormat.parse(date)
        return outputFormat.format(inputDate as Date)
    }

    private fun formatTime(time: String): String {
        val inputFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
        val inputTime = inputFormat.parse(time)
        return outputFormat.format(inputTime as Date)
    }
    private fun showExportToExcelDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_export_excel, null)

        val spinnerGrade = dialogView.findViewById<Spinner>(R.id.spinner_grade)
        val sectionSpinner = dialogView.findViewById<Spinner>(R.id.spinner_section)
        val dialogBtnExport = dialogView.findViewById<Button>(R.id.dialogBtnExport)
        val dialogBtnCancel = dialogView.findViewById<Button>(R.id.dialogBtnCancel)

        // Dropdown
        val grades = arrayOf("All") + context.resources.getStringArray(R.array.grades_array)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, grades)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGrade.adapter = adapter

        setUpSectionSpinner(context, sectionSpinner, arrayOf("All"))

        spinnerGrade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("DiscouragedApi")
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedGrade = parent.getItemAtPosition(position).toString()
                if (selectedGrade == "All") {
                    sectionSpinner.isEnabled = false
                    setUpSectionSpinner(context, sectionSpinner, arrayOf("All"))
                } else {
                    sectionSpinner.isEnabled = true
                    val sectionArray = if (position > 0) {
                        val sectionArrayResourceId = context.resources.getIdentifier(
                            "grade_${position + 6}_sections",
                            "array",
                            context.packageName
                        )
                        context.resources.getStringArray(sectionArrayResourceId)
                    } else {
                        emptyArray()
                    }

                    setUpSectionSpinner(context, sectionSpinner, sectionArray)

                    if (sectionArray.isEmpty()) {
                        sectionSpinner.setSelection(0)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .show()

        dialogBtnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBtnExport.setOnClickListener {
            val selectedGrade = spinnerGrade.selectedItem.toString()
            val selectedSection = sectionSpinner.selectedItem.toString()
            exportAttendanceToExcel(context, selectedGrade, selectedSection)
            dialog.dismiss()
        }
    }

    private fun setUpSectionSpinner(context: Context, sectionSpinner: Spinner, sectionArray: Array<String>) {
        val allSections = arrayOf("All") + sectionArray
        val sectionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, allSections)
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sectionSpinner.adapter = sectionAdapter
        sectionSpinner.setSelection(0) // Select "All" option
    }

    private fun exportAttendanceToExcel(context: Context, grade: String, section: String) {
        val database: FirebaseDatabase = FirebaseDatabase.getInstance()
        val attendanceRef: DatabaseReference = database.getReference("attendance")

        attendanceRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val workbook = XSSFWorkbook()
                    val sheetName = getSheetName(grade, section)
                    val sheet = workbook.createSheet(sheetName)
                    val headerRow = sheet.createRow(0)
                    headerRow.createCell(0).setCellValue("Name")
                    headerRow.createCell(1).setCellValue("Section")
                    headerRow.createCell(2).setCellValue("Grade")
                    headerRow.createCell(3).setCellValue("Time")
                    headerRow.createCell(4).setCellValue("Date")

                    // Initialize a counter variable
                    var retrievedCount = 0
                    var rowCount = 0

                    for (userSnapshot in dataSnapshot.children) {
                        val userAttendanceRef = userSnapshot.ref

                        userAttendanceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userAttendanceSnapshot: DataSnapshot) {
                                // Increment the counter for each user attendance data retrieved
                                retrievedCount++

                                for (attendanceSnapshot in userAttendanceSnapshot.children) {
                                    val attendanceData = attendanceSnapshot.value as? HashMap<*, *>

                                    attendanceData?.let {
                                        val attendanceGrade = it["grade"] as? String
                                        val attendanceSection = it["section"] as? String

                                        if (shouldIncludeAttendance(grade, section, attendanceGrade, attendanceSection)) {
                                            val name = it["name"] as? String
                                            val time = it["time"] as? String
                                            val date = it["date"] as? String
                                            rowCount++

                                            if (name != null && time != null && date != null) {
                                                val formattedTime1 = formatTime(time)
                                                val formattedDate1 = formatDate(date)

                                                val rowData = mutableListOf<Any?>()
                                                rowData.add(name)
                                                rowData.add(attendanceSection ?: "")
                                                rowData.add(attendanceGrade ?: "")
                                                rowData.add(formattedTime1)
                                                rowData.add(formattedDate1)

                                                val newRow = sheet.createRow(rowCount)
                                                for ((index, value) in rowData.withIndex()) {
                                                    newRow.createCell(index).setCellValue(value?.toString() ?: "")
                                                    val textLength = value?.toString()?.length ?: 0
                                                    sheet.setColumnWidth(index, (textLength + 5) * 256)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Check if all attendance data has been retrieved
                                if (retrievedCount == dataSnapshot.childrenCount.toInt()) {
                                    val filename = getFilename(grade, section)
                                    val file = File(context.cacheDir, filename)

                                    var renamedFile = file
                                    var counter = 1
                                    while (renamedFile.exists()) {
                                        renamedFile = if (counter > 1) {
                                            val newFilename = getRenamedFilename(grade, section,
                                                counter.toString()
                                            )
                                            File(context.cacheDir, newFilename)
                                        } else {
                                            val newFilename = getRenamedFilename(grade, section, "")
                                            File(context.cacheDir, newFilename)
                                        }
                                        counter++
                                    }

                                    val fileUri = Uri.fromFile(renamedFile)
                                    val outputStream = context.contentResolver.openOutputStream(fileUri)

                                    workbook.write(outputStream)
                                    outputStream?.close()
                                    workbook.close()

                                    val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val targetFile = File(downloadsDirectory, renamedFile.name)
                                    Files.copy(renamedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                    renamedFile.delete()

                                    val message = "Attendance exported to Excel.\nFile saved in: ${targetFile.absolutePath}"
                                    showSuccessDialog(context, message)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("ExportAttendance", "Failed to read user attendance data: ${error.message}")
                            }
                        })
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ExportAttendance", "Failed to read attendance data: ${error.message}")
            }
        })
    }

    private fun getRenamedFilename(grade: String, section: String, counter: String): String {
        val originalFilename = getFilename(grade, section)
        val extension = originalFilename.substringAfterLast(".")
        val baseName = originalFilename.substringBeforeLast(".")
        return "$baseName($counter).$extension"
    }



    private fun getSheetName(grade: String, section: String): String {
        return when {
            grade == "All" && section == "All" -> "Entry Record (All)"
            grade == "All" -> "Entry Record ($section)"
            section == "All" -> "Entry Record ($grade)"
            else -> "Entry Record ($grade - $section)"
        }
    }

    private fun shouldIncludeAttendance(selectedGrade: String, selectedSection: String, attendanceGrade: String?, attendanceSection: String?): Boolean {
        return (selectedGrade == "All" || attendanceGrade == selectedGrade || attendanceGrade == "Grade $selectedGrade")
                && (selectedSection == "All" || attendanceSection == selectedSection)
    }

    private fun getFilename(grade: String, section: String): String {
        return when {
            grade == "All" && section == "All" -> "gatekeep_all.xlsx"
            grade == "All" -> "gatekeep_all_${section}.xlsx"
            section == "All" -> "gatekeep_${grade}_all.xlsx"
            else -> "gatekeep_${grade}_${section}.xlsx"
        }
    }
    private fun showSuccessDialog(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setTitle("Export Success")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}