package com.yunmei.vibe.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 用系统 LocationManager 获取一次定位，返回 "经度,纬度"。 */
class LocationProvider(private val context: Context) {

    suspend fun getLocation(timeoutMs: Long = 12_000): String {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            throw IllegalStateException(context.getString(R.string.unlock_sign_location_denied))
        }

        val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!networkEnabled && !gpsEnabled) {
            throw IllegalStateException(context.getString(R.string.unlock_sign_location_unavailable))
        }

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    finish("${location.longitude},${location.latitude}")
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit

                private fun finish(value: String) {
                    if (!continuation.isActive) return
                    runCatching { lm.removeUpdates(this) }
                    handler.removeCallbacksAndMessages(null)
                    continuation.resume(value)
                }
            }

            val timeout = Runnable {
                if (!continuation.isActive) return@Runnable
                runCatching { lm.removeUpdates(listener) }
                val last = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                    ?: runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
                if (last != null) {
                    continuation.resume("${last.longitude},${last.latitude}")
                } else {
                    continuation.resumeWithException(IllegalStateException(context.getString(R.string.unlock_sign_location_timeout)))
                }
            }

            continuation.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
                handler.removeCallbacksAndMessages(null)
            }

            runCatching {
                if (networkEnabled) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 10f, listener, Looper.getMainLooper())
                }
                if (gpsEnabled) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 10f, listener, Looper.getMainLooper())
                }
            }.onFailure {
                handler.removeCallbacksAndMessages(null)
                continuation.resumeWithException(it)
            }

            handler.postDelayed(timeout, timeoutMs)
        }
    }
}
