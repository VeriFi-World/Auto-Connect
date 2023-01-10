package world.verifi.auto_connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RebootBroadcastReceiver: BroadcastReceiver() {

  companion object {
    const val TAG = "RebootBroadcastReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action.equals("android.intent.action.BOOT_COMPLETED")) {
      Log.d(TAG, "Received reboot")
      GeofenceController(context).registerGeofencesAfterReboot()
      ActivityRecognitionController(context).requestActivityUpdates()
    }
  }
}
