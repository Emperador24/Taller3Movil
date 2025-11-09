package com.example.taller3samuelemperador.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("No location permission"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L // 10 segundos
        ).apply {
            setMinUpdateIntervalMillis(5000L) // 5 segundos mínimo
            setWaitForAccurateLocation(false)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (e: SecurityException) {
                continuation.resume(null)
            }
        }
    }
}