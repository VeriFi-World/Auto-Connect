package world.verifi.auto_connect

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionController(private val context: Context) {
  private val activityRecognitionClient = ActivityRecognition.getClient(context)

  fun requestActivityUpdates() {
    if (ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    activityRecognitionClient.requestActivityTransitionUpdates(
      getActivityTransitionRequest(),
      getActivityTransitionPendingIntent(),
    )
  }

  private fun getActivityTransitionRequest() : ActivityTransitionRequest {
    val transitions = mutableListOf<ActivityTransition>()
    transitions.add(ActivityTransition.Builder()
      .setActivityType(DetectedActivity.IN_VEHICLE)
      .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
      .build()
    )
    transitions.add(ActivityTransition.Builder()
      .setActivityType(DetectedActivity.ON_BICYCLE)
      .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
      .build()
    )
    return ActivityTransitionRequest(transitions)
  }

  private fun getActivityTransitionPendingIntent() : PendingIntent {
      val intent = Intent(context, ActivityTransitionBroadcastReceiver::class.java)
      val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
      return PendingIntent.getBroadcast(
        context,
        ACTIVITY_TRANSITION_BROADCAST_REQUEST_CODE,
        intent,
        flags
      )
  }
}
