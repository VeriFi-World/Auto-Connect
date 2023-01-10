package world.verifi.auto_connect

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import io.flutter.plugin.common.MethodChannel

class WifiSuggestionController(
  private val context: Context,
) {
  companion object {
    const val TAG = "WifiSuggestionController"
  }

  private val wifiManager =
    context.getSystemService(Context.WIFI_SERVICE) as WifiManager

  fun addWifiSuggestions(ssids: List<String>, passwords: List<String>): Int {
    if (ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED
    }
    wifiManager.addSuggestionConnectionStatusListener(
      { command -> command.run() }
    )
    { suggestion, failureReason ->
      run {
        val workManager = WorkManager.getInstance(context)
        val data = Data.Builder()
          .putString(EVENT_ARG, ACCESS_POINT_EVENT_METHOD)
          .putString(SSID_ARG, suggestion.ssid)
          .putString(
            CONNECTION_RESULT_ARG,
            failureReasonToString(failureReason)
          )
          .build()
        val request = OneTimeWorkRequestBuilder<BackgroundWorker>()
          .setInputData(data)
          .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
          .build()
        workManager.enqueue(request)
      }
    }
    val suggestions = mutableListOf<WifiNetworkSuggestion>()
    ssids.forEachIndexed { i, ssid ->
      val suggestion = WifiNetworkSuggestion.Builder()
        .setSsid(ssid)
        .setIsAppInteractionRequired(true)
      if (passwords[i] != "none") {
        suggestion.setWpa2Passphrase(passwords[i])
      }
      suggestions.add(suggestion.build())
    }
    return wifiManager.addNetworkSuggestions(suggestions.toList())
  }

  @SuppressLint("MissingPermission")
  fun validateWifiSuggestion(
    ssid: String,
    password: String,
    result: MethodChannel.Result
  ) {
    // Add receiver for successful connection
    val intentFilter =
      IntentFilter(
        WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION,
      )
    val broadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra(WifiManager.EXTRA_NETWORK_SUGGESTION)) {
          // Ensure we received successful connection for this specific ssid
          val suggestion: WifiNetworkSuggestion? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              intent.getParcelableExtra(
                WifiManager.EXTRA_NETWORK_SUGGESTION,
                WifiNetworkSuggestion::class.java
              )
            } else {
              intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_SUGGESTION)
            }
          if (suggestion != null && suggestion.ssid == ssid) {
            result.success("Success")
            // Unregister after connection received
            context.unregisterReceiver(this)
          }
        }
      }
    }
    context.registerReceiver(broadcastReceiver, intentFilter)
    // Add status listener for failed connections
    wifiManager.addSuggestionConnectionStatusListener(
      { command -> command.run() }
    )
    { suggestion, failureReason ->
      run {
        Log.d(TAG, "Failed to connect to ${suggestion.ssid}")
        result.success(failureReasonToString(failureReason))
        context.unregisterReceiver(broadcastReceiver)
      }
    }
    // Add wifi suggestion. If it fails, log why
    val status = addWifiSuggestions(listOf(ssid), listOf(password))
    if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
      Log.d(TAG, "Unable to add suggestions. Error code $status")
      result.success(statusToString(status))
      context.unregisterReceiver(broadcastReceiver)
    }
  }

  private fun failureReasonToString(reason: Int): String {
    return when (reason) {
      WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_ASSOCIATION -> "Association failure"
      WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION -> "Failed to authenticate"
      WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_IP_PROVISIONING -> "Failed to provision IP"
      else -> "Unknown error"
    }
  }

  private fun statusToString(reason: Int): String {
    return when (reason) {
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL -> "Internal error"
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED -> "App disallowed"
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> "Duplicate suggestion"
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP -> "Exceeds max suggestions allowed"
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED -> "Suggestion not allowed"
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID -> "Invalid suggestion"
      WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_RESTRICTED_BY_ADMIN -> "Unable to add due to admin restriction"
      else -> "Unknown error"
    }
  }
}
