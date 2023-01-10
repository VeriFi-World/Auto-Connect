package world.verifi.auto_connect

import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/** AutoConnectPlugin */
class AutoConnectPlugin : FlutterPlugin, ActivityAware {
  private lateinit var autoConnectChannel: MethodChannel
  private lateinit var activityRecognitionController: ActivityRecognitionController
  private lateinit var geofenceController: GeofenceController
  private lateinit var wifiSuggestionController: WifiSuggestionController
  private lateinit var autoConnectMethodCallHandler: AutoConnectMethodCallHandler

  companion object {
    private const val TAG = "AutoConnectPlugin"
  }

  override fun onAttachedToEngine(
    flutterPluginBinding: FlutterPlugin.FlutterPluginBinding,
  ) {
    setupPlugin(
      flutterPluginBinding.applicationContext,
      flutterPluginBinding.binaryMessenger
    )
  }

  override fun onDetachedFromEngine(
    binding: FlutterPlugin.FlutterPluginBinding,
  ) {
    WiFiConnectionBroadcastReceiver.foregroundChannel = null
    autoConnectChannel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    GeofenceEventReceiver.foregroundChannel = autoConnectChannel
    WiFiConnectionBroadcastReceiver.foregroundChannel = autoConnectChannel
  }

  override fun onDetachedFromActivity() {
    tearDownPlugin()
  }

  override fun onReattachedToActivityForConfigChanges(
    binding: ActivityPluginBinding
  ) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  private fun setupPlugin(context: Context, binaryMessenger: BinaryMessenger) {
    Log.d(TAG, "Setting up Auto Connect plugin")
    geofenceController = GeofenceController(context)
    wifiSuggestionController = WifiSuggestionController(context)
    activityRecognitionController = ActivityRecognitionController(context)
    activityRecognitionController.requestActivityUpdates()
    autoConnectMethodCallHandler = AutoConnectMethodCallHandler(
      context,
      geofenceController,
      wifiSuggestionController
    )
    autoConnectChannel = MethodChannel(binaryMessenger, MAIN_CHANNEL_NAME)
    autoConnectChannel.setMethodCallHandler(autoConnectMethodCallHandler)
    autoConnectMethodCallHandler.setForegroundChannel(autoConnectChannel)
    Notifications.createNotificationChannel(context)
  }

  private fun tearDownPlugin() {
    GeofenceEventReceiver.foregroundChannel = null
    WiFiConnectionBroadcastReceiver.foregroundChannel = null
    autoConnectChannel.setMethodCallHandler(null)
  }
}
