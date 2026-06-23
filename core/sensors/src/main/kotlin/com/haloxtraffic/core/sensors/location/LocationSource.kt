package com.haloxtraffic.core.sensors.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.haloxtraffic.core.model.GeoFix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits [GeoFix] updates from the fused provider (§10). A weak/low-accuracy fix is still emitted (and
 * flagged via [GeoFix.isWeak]) rather than dropped — evidence carries the accuracy, never silence.
 * Caller must hold a location permission before collecting.
 */
@Singleton
class LocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun fixes(intervalMs: Long = 1_000L): Flow<GeoFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    GeoFix(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
                        headingDeg = if (loc.hasBearing()) loc.bearing else null,
                        obtainedAtMs = loc.time,
                    ),
                )
            }
        }

        Timber.d("Requesting location updates @ ${intervalMs}ms")
        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose {
            client.removeLocationUpdates(callback)
            Timber.d("Stopped location updates")
        }
    }
}
