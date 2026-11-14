package com.example.safora.services.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.safora.data.repository.SaforaRepository
import com.google.android.gms.location.*

class SaforaLocationClient(private val context: Context) {
    private val repository = SaforaRepository.getInstance(context)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                repository.updateLocation(location.latitude, location.longitude, isRealGps = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(onLocationReceived: ((Location) -> Unit)? = null) {
        try {
            // First try high accuracy current location
            val priority = Priority.PRIORITY_HIGH_ACCURACY
            val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        repository.updateLocation(loc.latitude, loc.longitude, isRealGps = true)
                        onLocationReceived?.invoke(loc)
                    } else {
                        // Fallback to lastLocation if current is null
                        fusedLocationClient.lastLocation.addOnSuccessListener { fallbackLoc ->
                            fallbackLoc?.let {
                                repository.updateLocation(it.latitude, it.longitude, isRealGps = true)
                                onLocationReceived?.invoke(it)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateIntervalMillis(2000)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
