package world.verifi.auto_connect

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.ListenableFuture
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import org.json.JSONObject

class BackgroundWorker(
  private val context: Context,
  private val workerParameters: WorkerParameters,
) : ListenableWorker(context, workerParameters),
  MethodChannel.MethodCallHandler {
  companion object {
    private const val TAG = "BackgroundWorker"
    private val flutterLoader = FlutterLoader()
  }

  // Worker parameters
  private val event
    get() = workerParameters.inputData.getString(EVENT_ARG)

  // Location event parameters
  private val geofenceLat
    get() = workerParameters.inputData.getDouble(GEOFENCE_LAT_ARG, -1.0)
  private val geofenceLng
    get() = workerParameters.inputData.getDouble(GEOFENCE_LNG_ARG, -1.0)
  private val ssidList
    get() = workerParameters.inputData.getString(SSID_LIST_ARG)
  private val passwordList
    get() = workerParameters.inputData.getString(PASSWORD_LIST_ARG)

  // Wifi event parameters
  private val ssid
    get() = workerParameters.inputData.getString(SSID_ARG)
  private val connectionResult
    get() = workerParameters.inputData.getString(CONNECTION_RESULT_ARG)

  // WiFi suggestion controller
  private val wifiSuggestionController = WifiSuggestionController(context)

  private val wifiManager =
    context.getSystemService(Context.WIFI_SERVICE) as WifiManager

  // Flutter things
  private lateinit var backgroundChannel: MethodChannel
  private var engine: FlutterEngine? = null

  // Work completer and callbacks for method channel
  private lateinit var futureCompleter: CallbackToFutureAdapter.Completer<Result>
  private val futureCallback = object : MethodChannel.Result {
    override fun success(result: Any?) {
      val isSuccess = result.let { it as Boolean? } == true
      if (isSuccess) futureCompleter.set(Result.success()) else
        futureCompleter.set(
          Result.retry()
        )
    }

    override fun error(
      errorCode: String,
      errorMessage: String?,
      errorDetails: Any?
    ) {
      Log.d(TAG, "futureCallback error: $errorCode, $errorMessage")
      futureCompleter.set(
        Result.failure(
          Data.Builder()
            .putString("error_code", errorCode)
            .putString("error_message", errorMessage)
            .build()
        )
      )
    }

    override fun notImplemented() {
      futureCompleter.set(Result.failure())
    }
  }

  override fun startWork(): ListenableFuture<Result> {
    Log.d(TAG, "Starting new work")
    return CallbackToFutureAdapter.getFuture { completer ->
      futureCompleter = completer
      engine = FlutterEngine(applicationContext)
      if (!flutterLoader.initialized()) {
        flutterLoader.startInitialization(applicationContext)
      }
      // Initialize Flutter loader, and once initialized, start up Flutter
      // engine
      flutterLoader.ensureInitializationCompleteAsync(
        applicationContext,
        null,
        Handler(Looper.getMainLooper())
      ) {
        // Begin Flutter engine initialization
        // Get callback handle for background channel
        val callbackHandle = applicationContext.getSharedPreferences(
          SHARED_PREFERENCES_NAME,
          Context.MODE_PRIVATE,
        ).getLong(CALLBACK_DISPATCHER_HANDLE_ARG, -1L)
        val callbackInfo = FlutterCallbackInformation
          .lookupCallbackInformation(callbackHandle)
        val dartBundlePath = flutterLoader.findAppBundlePath()
        engine?.let { engine ->
          backgroundChannel = MethodChannel(
            engine.dartExecutor,
            BACKGROUND_CHANNEL_NAME,
          )
          backgroundChannel.setMethodCallHandler(this)
          engine.dartExecutor.executeDartCallback(
            DartExecutor.DartCallback(
              applicationContext.assets,
              dartBundlePath,
              callbackInfo,
            )
          )
        }
      }
      futureCallback
    }
  }

  override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
    return CallbackToFutureAdapter.getFuture {
      it.set(
        ForegroundInfo(
          Notifications.CHANNEL_ID.hashCode(),
          Notifications.buildForegroundNotification(context)
        )
      )
    }
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    // Determine action
    val action = AutoConnectAction.fromMethod(call.method)
    // If action is not implemented, return
    if (action == (AutoConnectAction.UNDEFINED_ACTION)) {
      result.notImplemented()
      return
    }
    when (action) {
      AutoConnectAction.BACKGROUND_CHANNEL_INITIALIZED -> {
        Log.d(TAG, "Background channel initialized")
        // Handle location event
        if (event != null && event == LOCATION_EVENT_METHOD) {
          val locationEventCallbackHandle = getLocationEventCallbackHandle()
          backgroundChannel.invokeMethod(
            LOCATION_EVENT_METHOD,
            JSONObject(
              mapOf(
                LOCATION_EVENT_CALLBACK_HANDLE_ARG to locationEventCallbackHandle,
                GEOFENCE_LAT_ARG to geofenceLat,
                GEOFENCE_LNG_ARG to geofenceLng,
              )
            ).toString(),
          )
          val ssids = ssidList?.split(CUSTOM_SEPARATOR)
          val passwords = passwordList?.split(CUSTOM_SEPARATOR)
          if (ssids != null && passwords != null) {
            Log.d(TAG, "Adding wifi suggestions")
            wifiSuggestionController.addWifiSuggestions(ssids, passwords)
          }
        // Handle access point event
        } else if (event != null && event == ACCESS_POINT_EVENT_METHOD) {
          val accessPointEventCallbackHandle =
            getAccessPointEventCallbackHandle()
          backgroundChannel.invokeMethod(
            ACCESS_POINT_EVENT_METHOD,
            JSONObject(
              mapOf(
                ACCESS_POINT_EVENT_CALLBACK_HANDLE_ARG to accessPointEventCallbackHandle,
                SSID_ARG to ssid,
                CONNECTION_RESULT_ARG to connectionResult,
              )
            ).toString()
          )
        }
      }
      AutoConnectAction.LOCATION_EVENT_COMPLETED -> {
        Log.d(TAG, "Location event completed")
        futureCallback.success(true)
      }
      AutoConnectAction.ACCESS_POINT_EVENT_COMPLETED -> {
        Log.d(TAG, "Access point event completed")
        futureCallback.success(true)
      }
      else -> return
    }
  }

  private fun getLocationEventCallbackHandle(): Long {
    return context.getSharedPreferences(
      SHARED_PREFERENCES_NAME,
      Context.MODE_PRIVATE
    )
      .getLong(LOCATION_EVENT_CALLBACK_HANDLE_ARG, -1)
  }

  private fun getAccessPointEventCallbackHandle(): Long {
    return context.getSharedPreferences(
      SHARED_PREFERENCES_NAME,
      Context.MODE_PRIVATE
    )
      .getLong(ACCESS_POINT_EVENT_CALLBACK_HANDLE_ARG, -1)
  }
}
