package com.example.toda.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import com.example.toda.data.CustomerLocation

class CustomerLocationService(private val context: Context) {
    private val _customerLocation = MutableStateFlow<CustomerLocation?>(null)
    val customerLocation: Flow<CustomerLocation?> = _customerLocation.asStateFlow()

    private val _isCurrentlyTracking = MutableStateFlow(false)
    val isCurrentlyTracking: Flow<Boolean> = _isCurrentlyTracking.asStateFlow()

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var currentCustomerId: String? = null

    fun startLocationTracking(customerId: String) {
        if (_isCurrentlyTracking.value) {
            stopLocationTracking()
        }

        if (!hasLocationPermission()) {
            return
        }

        currentCustomerId = customerId
        _isCurrentlyTracking.value = true

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val customerLocation = CustomerLocation(
                    customerId = customerId,
                    location = GeoPoint(location.latitude, location.longitude),
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis()
                )
                _customerLocation.value = customerLocation
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L, // 5 seconds
                10f,   // 10 meters
                locationListener!!
            )
        } catch (e: SecurityException) {
            // Permission not granted
            _isCurrentlyTracking.value = false
        }
    }

    fun stopLocationTracking() {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
        locationManager = null
        locationListener = null
        _isCurrentlyTracking.value = false
        currentCustomerId = null
        _customerLocation.value = null
    }

    fun isCurrentlyTracking(): Boolean {
        return _isCurrentlyTracking.value
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}