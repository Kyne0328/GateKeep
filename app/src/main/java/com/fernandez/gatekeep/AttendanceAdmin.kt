package com.fernandez.gatekeep

data class AttendanceAdmin(
    var name: String,
    var date: String,
    var time: String,
    var grade: String,
    var section: String,
    val formattedDate: String,
    val formattedTime: String
)