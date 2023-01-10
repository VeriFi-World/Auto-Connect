package world.verifi.auto_connect

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class ActivityTransitionBroadcastReceiver : BroadcastReceiver() {

  companion object {
    const val TAG = "ACTIVITY_TRANSITION_RECEIVER"
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "Activity transition receiver called")
    if (ActivityTransitionResult.hasResult(intent)) {
      val locationClient =
        LocationServices.getFusedLocationProviderClient(context)
      if (ActivityCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        return
      }
      locationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        null,
      ).addOnSuccessListener { location ->
        run {
          val workManager = WorkManager.getInstance(context)
          val data = Data.Builder()
            .putString(EVENT_ARG, LOCATION_EVENT_METHOD)
            .putDouble(GEOFENCE_LAT_ARG, location.latitude)
            .putDouble(GEOFENCE_LNG_ARG, location.longitude)
            .build()
          val request = OneTimeWorkRequestBuilder<BackgroundWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
          workManager.enqueue(request)
          Log.d(TAG, "Location event work enqueued")
        }
      }
    }
  }
}
