package com.fernandez.gatekeep

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class AttendanceHistoryFragment : Fragment() {
    private lateinit var database: FirebaseDatabase
    private lateinit var rvAttendance: RecyclerView
    private lateinit var attendanceHistoryAdapter: AttendanceHistoryAdapter
    private val attendanceList = mutableListOf<AttendanceAdmin>()

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
        val exportToExcel = view.findViewById<ImageView>(R.id.btn_register)

        exportToExcel.setOnClickListener {
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

        spinnerGrade.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("DiscouragedApi")
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedGrade = parent.getItemAtPosition(position).toString()
                if (selectedGrade == "All") {
                    // Disable the section spinner and set its value to "All"
                    sectionSpinner.isEnabled = false
                    sectionSpinner.setSelection(0)
                } else {
                    // Set the section spinner based on the selected grade
                    sectionSpinner.isEnabled = true
                    val sectionArray = if (position > 0) {
                        val sectionArrayResourceId = context.resources.getIdentifier(
                            "grade_${position + 6}_sections",
                            "array",
                            context.packageName
                        )
                        context.resources.getStringArray(sectionArrayResourceId)
                    } else {
                        arrayOf("All")
                    }

                    val allSections = arrayOf("All") + sectionArray
                    val sectionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, allSections)
                    sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    sectionSpinner.adapter = sectionAdapter
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

    private fun exportAttendanceToExcel(context: Context, grade: String, section: String) {
        val database: FirebaseDatabase = FirebaseDatabase.getInstance()
        val attendanceRef: DatabaseReference = database.getReference("attendance")

        attendanceRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val workbook = XSSFWorkbook()
                    val sheet = workbook.createSheet("Attendance")

                    // Write the header row
                    val headerRow = sheet.createRow(0)
                    headerRow.createCell(0).setCellValue("Name")
                    headerRow.createCell(1).setCellValue("Section")
                    headerRow.createCell(2).setCellValue("Grade")
                    headerRow.createCell(3).setCellValue("Time")
                    headerRow.createCell(4).setCellValue("Date")

                    // Iterate over the attendance data
                    for (attendanceSnapshot in dataSnapshot.children) {
                        val attendanceData = attendanceSnapshot.value as HashMap<*, *>
                        val attendanceGrade = attendanceData["grade"] as String
                        val attendanceSection = attendanceData["section"] as String

                        // Check if the attendance record matches the selected grade and section
                        if (grade == "All" || (attendanceGrade == grade && (section == "All" || attendanceSection == section))) {
                            val name = attendanceData["name"] as String
                            val time = attendanceData["time"] as String
                            val date = attendanceData["date"] as String

                            // Create a new row
                            val row = sheet.createRow(sheet.lastRowNum + 1)
                            row.createCell(0).setCellValue(name)
                            row.createCell(1).setCellValue(attendanceSection)
                            row.createCell(2).setCellValue(attendanceGrade)
                            row.createCell(3).setCellValue(time)
                            row.createCell(4).setCellValue(date)
                        }
                    }

                    // Generate the filename based on the selected grade and section
                    val filename = if (grade == "All" && section == "All") {
                        "gatekeep_all.xlsx"
                    } else if (grade == "All") {
                        "gatekeep_all_${section}.xlsx"
                    } else if (section == "All") {
                        "gatekeep_${grade}_all.xlsx"
                    } else {
                        "gatekeep_${grade}_${section}.xlsx"
                    }

                    // Save the workbook to a file
                    val fileUri = Uri.fromFile(File(context.cacheDir, filename))
                    val outputStream = context.contentResolver.openOutputStream(fileUri)
                    workbook.write(outputStream)
                    outputStream?.close()

                    // Close the workbook
                    workbook.close()

                    // Show a success message to the user
                    Toast.makeText(context, "Attendance exported to Excel", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ExportAttendance", "Failed to read attendance data: ${error.message}")
            }
        })
    }
}