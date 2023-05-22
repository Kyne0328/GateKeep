package com.fernandez.gatekeep

data class Attendance(
    var name: String,
    var date: String,
    var time: String,
    val formattedDate: String,
    val formattedTime: String
)