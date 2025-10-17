package com.fyp.facer.data

import com.google.firebase.Timestamp

data class GeoCenter(val lat: Double = 0.0, val lng: Double = 0.0)

data class Session(
    val id: String = "",
    val courseId: String = "",
    val start_time: Timestamp? = null,
    val end_time: Timestamp? = null,
    val method: String = "face",
    val radius_m: Long = 150,
    val center: GeoCenter = GeoCenter()
)
