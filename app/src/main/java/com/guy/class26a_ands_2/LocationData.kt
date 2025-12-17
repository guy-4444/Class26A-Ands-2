package com.guy.class26a_ands_2

import android.location.Location

/**
 * Data class to hold location information for UI display.
 */
data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,           // meters per second
    val speedKmh: Float = 0f,        // kilometers per hour
    val altitude: Double = 0.0,
    val bearing: Float = 0f,         // direction in degrees
    val timestamp: Long = 0L,
    val locationCount: Int = 0
) {
    companion object {
        /**
         * Create LocationData from Android Location object.
         */
        fun fromLocation(location: Location, count: Int): LocationData {
            return LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                speedKmh = location.speed * 3.6f,  // m/s to km/h
                altitude = location.altitude,
                bearing = location.bearing,
                timestamp = location.time,
                locationCount = count
            )
        }
    }
}