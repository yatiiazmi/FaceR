package com.fyp.facer.utils

import kotlin.math.*

data class LatLng(val lat: Double, val lng: Double)

/** Simple Haversine distance in meters */
fun distanceMeters(p1: LatLng, p2: LatLng): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(p2.lat - p1.lat)
    val dLng = Math.toRadians(p2.lng - p1.lng)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(p1.lat)) * cos(Math.toRadians(p2.lat)) *
            sin(dLng / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

/** true if within radius meters */
fun insideGeofence(current: LatLng, center: LatLng, radius: Double = 150.0): Boolean {
    return distanceMeters(current, center) <= radius
}
