package com.example.toda.service

import com.example.toda.data.DriverTracking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import kotlin.math.*
import kotlin.random.Random


class DriverTrackingService {
    private val _driverUpdates = MutableStateFlow<DriverTracking?>(null)
    val driverUpdates: Flow<DriverTracking?> = _driverUpdates.asStateFlow()

    private var isTracking = false
    private var trackingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    suspend fun startTracking(
        driverId: String,
        driverName: String,
        pickupLocation: GeoPoint
    ) {
        if (isTracking) {
            stopTracking()
        }

        isTracking = true

        // Starting position for driver (somewhere within Barangay 177)
        var currentLocation = GeoPoint(14.746, 121.048)
        val speed = 30.0f // km/h

        trackingJob = coroutineScope.launch {
            while (isTracking) {
                val distanceToPickup = calculateDistance(currentLocation, pickupLocation)

                if (distanceToPickup <= 0.05) { // Within 50 meters
                    // Driver has arrived
                    _driverUpdates.value = DriverTracking(
                        driverId = driverId,
                        driverName = driverName,
                        currentLocation = pickupLocation,
                        heading = 0f,
                        speed = 0f,
                        estimatedArrival = System.currentTimeMillis(),
                        distanceToPickup = 0.0,
                        isMoving = false,
                        lastUpdated = System.currentTimeMillis()
                    )
                    stopTracking()
                    break
                }

                // Move driver towards pickup location
                currentLocation = moveTowardsTarget(currentLocation, pickupLocation, speed)

                val eta = calculateETA(distanceToPickup, speed.toDouble())
                val heading = calculateBearing(currentLocation, pickupLocation)

                _driverUpdates.value = DriverTracking(
                    driverId = driverId,
                    driverName = driverName,
                    currentLocation = currentLocation,
                    heading = heading,
                    speed = speed,
                    estimatedArrival = eta,
                    distanceToPickup = distanceToPickup,
                    isMoving = true,
                    lastUpdated = System.currentTimeMillis()
                )

                delay(3000) // Update every 3 seconds
            }
        }
    }

    fun stopTracking() {
        isTracking = false
        trackingJob?.cancel()
        trackingJob = null
        _driverUpdates.value = null
    }

    private fun calculateDistance(from: GeoPoint, to: GeoPoint): Double {
        val earthRadius = 6371.0
        val lat1Rad = Math.toRadians(from.latitude)
        val lon1Rad = Math.toRadians(from.longitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val lon2Rad = Math.toRadians(to.longitude)

        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad

        val a = sin(deltaLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun moveTowardsTarget(current: GeoPoint, target: GeoPoint, speedKmh: Float): GeoPoint {
        val distanceKm = calculateDistance(current, target)
        val timeHours = 3.0 / 3600.0 // 3 seconds in hours
        val moveDistanceKm = speedKmh * timeHours

        if (moveDistanceKm >= distanceKm) {
            return target
        }

        val bearing = calculateBearing(current, target)
        val bearingRad = Math.toRadians(bearing.toDouble())

        val earthRadius = 6371.0
        val angularDistance = moveDistanceKm / earthRadius

        val lat1Rad = Math.toRadians(current.latitude)
        val lon1Rad = Math.toRadians(current.longitude)

        val lat2Rad = asin(
            sin(lat1Rad) * cos(angularDistance) +
                    cos(lat1Rad) * sin(angularDistance) * cos(bearingRad)
        )

        val lon2Rad = lon1Rad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1Rad),
            cos(angularDistance) - sin(lat1Rad) * sin(lat2Rad)
        )

        return GeoPoint(Math.toDegrees(lat2Rad), Math.toDegrees(lon2Rad))
    }

    private fun calculateBearing(from: GeoPoint, to: GeoPoint): Float {
        val lat1Rad = Math.toRadians(from.latitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val deltaLonRad = Math.toRadians(to.longitude - from.longitude)

        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)

        return ((bearingDeg + 360) % 360).toFloat()
    }

    private fun calculateETA(distanceKm: Double, speedKmh: Double): Long {
        val timeHours = distanceKm / speedKmh
        val timeMillis = (timeHours * 3600 * 1000).toLong()
        return System.currentTimeMillis() + timeMillis
    }
}