package world.verifi.auto_connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import io.flutter.plugin.common.MethodChannel


class WiFiConnectionBroadcastReceiver : BroadcastReceiver() {
  companion object {
    const val TAG = "WiFiConnectionBroadcastReceiver"
    var foregroundChannel: MethodChannel? = null
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "Received wifi connection broadcast")
    if (intent.hasExtra(WifiManager.EXTRA_NETWORK_SUGGESTION)) {
      val suggestion: WifiNetworkSuggestion? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(
            WifiManager.EXTRA_NETWORK_SUGGESTION,
            WifiNetworkSuggestion::class.java
          )
        } else {
          intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_SUGGESTION)
        }
      val workManager = WorkManager.getInstance(context)
      val ssid = suggestion!!.ssid
      val data = Data.Builder()
        .putString(EVENT_ARG, ACCESS_POINT_EVENT_METHOD)
        .putString(SSID_ARG, ssid)
        .putString(CONNECTION_RESULT_ARG, "Success")
        .build()
      val request = OneTimeWorkRequestBuilder<BackgroundWorker>()
        .setInputData(data)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
      workManager.enqueue(request)
    }
  }
}
