package com.haloxtraffic.core.sensors.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** Device motion + heading from the IMU. Drives the motion gate (skip inference when static) and heading. */
@Singleton
class MotionSensors @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Motion gate: emits true when linear acceleration magnitude exceeds [thresholdMs2]. Lets the
     * frame gate skip inference on a perfectly static scene to save power (§14).
     */
    fun isMoving(thresholdMs2: Float = 0.35f): Flow<Boolean> = callbackFlow {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            trySend(true) // no sensor → never gate out frames
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val mag = sqrt(x * x + y * y + z * z)
                // For the raw accelerometer, subtract gravity baseline (~9.81).
                val linear = if (accel.type == Sensor.TYPE_ACCELEROMETER) kotlin.math.abs(mag - SensorManager.GRAVITY_EARTH) else mag
                trySend(linear > thresholdMs2)
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.distinctUntilChanged()

    /** Compass heading in degrees (0..360) from the rotation vector, or empty if unavailable. */
    fun heading(): Flow<Float> = callbackFlow {
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotation == null) {
            awaitClose { }
            return@callbackFlow
        }
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, e.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val deg = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                trySend(deg)
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
