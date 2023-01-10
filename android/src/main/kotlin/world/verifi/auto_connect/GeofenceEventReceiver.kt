package world.verifi.auto_connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import io.flutter.plugin.common.MethodChannel


class GeofenceEventReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "GeofenceEventReceiver"

    // Set/unset by AutoConnectPlugin in response to lifecycle events
    var foregroundChannel: MethodChannel? = null
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "Received location event from geofence trigger")
    // extract geofencingEvent from intent
    val event = GeofencingEvent.fromIntent(intent)
    if (null == event) {
      Log.d(TAG, "Failed to extract geofencingEvent from intent")
      return
    }
    processGeofencingEvent(context, event)
  }

  private fun processGeofencingEvent(context: Context, event: GeofencingEvent) {
    // check for error
    if (event.hasError()) {
      val errorMessage =
        GeofenceStatusCodes.getStatusCodeString(event.errorCode)
      Log.d(TAG, "Error in geofenceEvent: $errorMessage")
      return
    }
    val workManager = WorkManager.getInstance(context)
    val location = event.triggeringLocation ?: return
    val geofences = event.triggeringGeofences ?: return
    val ssids = mutableListOf<String>()
    val passwords = mutableListOf<String>()

    for (gf in geofences) {
      val ssid = getGeofenceSSID(context, gf.requestId) ?: continue
      val password = getGeofencePassword(context, gf.requestId)
      ssids.add(ssid)
      passwords.add(password ?: "none")
    }

    val data = Data.Builder()
      .putString(EVENT_ARG, LOCATION_EVENT_METHOD)
      .putString(SSID_LIST_ARG, ssids.joinToString(CUSTOM_SEPARATOR))
      .putString(PASSWORD_LIST_ARG, passwords.joinToString(CUSTOM_SEPARATOR))
      .putDouble(GEOFENCE_LAT_ARG, location.latitude)
      .putDouble(GEOFENCE_LNG_ARG, location.longitude)
      .build()
    val request = OneTimeWorkRequestBuilder<BackgroundWorker>()
      .setInputData(data)
      .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
    workManager.enqueue(request)
    Log.d(TAG, "Location event work enqueued")
  }

  private fun getGeofenceSSID(context: Context, id: String): String? {
    return context
      .getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getString("geofence_access_points/$id/ssid", null)
  }

  private fun getGeofencePassword(context: Context, id: String): String? {
    return context
      .getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getString("geofence_access_points/$id/password", "none")
  }
}
