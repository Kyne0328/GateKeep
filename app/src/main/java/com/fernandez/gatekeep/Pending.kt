package com.fernandez.gatekeep

data class Pending(
    var name: String,
    var section: String,
    var grade: String,
    var approved: Boolean,
    var lrn: String,
    val userId: String,
    val fcmToken: String,
    var rejected: Boolean?
)
