package world.verifi.auto_connect

import android.content.Context
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class AutoConnectMethodCallHandler(
  private val context: Context,
  private val geofenceController: GeofenceController,
  private val wifiSuggestionController: WifiSuggestionController
) : MethodChannel.MethodCallHandler {

  private lateinit var foregroundChannel: MethodChannel

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    // Determine action
    val action = AutoConnectAction.fromMethod(call.method)
    // If action is not implemented, return
    if (action == (AutoConnectAction.UNDEFINED_ACTION)) {
      result.notImplemented()
      return
    }
    val args = call.arguments<Map<String, Any>>()
    handleMethod(action, args, result)
  }

  /**
   * Main entrypoint from [onMethodCall].
   *
   * [action] is used to determine which handler method to pass [args].
   *
   *
   * @param[action] the action to be handled.
   * @param[args] the arguments to pass to the action handler.
   */
  private fun handleMethod(
    action: AutoConnectAction,
    args: Map<String, Any>?,
    result: MethodChannel.Result
  ) {
    val valid: Boolean = when (action) {
      AutoConnectAction.INITIALIZE -> handleInitialize(args, result)
      AutoConnectAction.GET_GEOFENCES -> handleGetGeofences(result)
      AutoConnectAction.REMOVE_ALL_GEOFENCES -> handleRemoveAllGeofences(result)
      AutoConnectAction.GET_PINNED_GEOFENCES -> handleGetPinnedGeofences(result)
      AutoConnectAction.ADD_GEOFENCE_WITH_AP -> handleAddGeofence(args, result)
      AutoConnectAction.REMOVE_GEOFENCE_WITH_AP -> handleRemoveGeofence(
        args,
        result,
      )
      AutoConnectAction.VERIFY_AP_SUGGESTION -> handleVerifyAPSuggestion(
        args,
        result,
      )
      AutoConnectAction.ADD_PIN_AP -> handleAddPinAccessPoint(args, result)
      AutoConnectAction.REMOVE_PIN_AP -> handleRemovePinAccessPoint(
        args,
        result,
      )
      AutoConnectAction.IS_AP_PINNED -> handleIsAccessPointPinned(args, result)
      else -> false // this should never occur
    }
    if (!valid) {
      result.error(
        "invalid-args",
        "Invalid arguments for ${action.name}",
        null
      )
    }
  }

  /**
   * Saves callback dispatcher handle for background channel communication
   * and event handles for geofence events and wifi connection events
   *
   * @param[args] the arguments passed over the channel
   * @param[result] the method call result callback
   *
   * @return true if arguments are valid, false if invalid. Actual result of
   * operation is sent over [result].
   */
  private fun handleInitialize(
    args: Map<String, Any>?,
    result: MethodChannel.Result
  ): Boolean {
    if (args == null) return false
    val callbackDispatcherHandle: Long =
      args[CALLBACK_DISPATCHER_HANDLE_ARG] as? Long ?: return false
    val locationEventCallbackHandle: Long =
      args[LOCATION_EVENT_CALLBACK_HANDLE_ARG] as? Long ?: return false
    val accessPointEventCallbackHandle: Long =
      args[ACCESS_POINT_EVENT_CALLBACK_HANDLE_ARG] as? Long ?: return false
    context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putLong(CALLBACK_DISPATCHER_HANDLE_ARG, callbackDispatcherHandle)
      .putLong(LOCATION_EVENT_CALLBACK_HANDLE_ARG, locationEventCallbackHandle)
      .putLong(
        ACCESS_POINT_EVENT_CALLBACK_HANDLE_ARG,
        accessPointEventCallbackHandle
      )
      .apply()
    result.success(true)
    return true
  }

  private fun handleGetGeofences(
    result: MethodChannel.Result
  ): Boolean {
    val geofenceIds = geofenceController.getRegisteredGeofences()
    result.success(geofenceIds)
    return true
  }

  private fun handleGetPinnedGeofences(result: MethodChannel.Result): Boolean {
    val pinnedGeofences = geofenceController.getPinnedGeofences()
    result.success(pinnedGeofences)
    return true
  }

  /**
   * Validates [args] before calling [GeofenceController.addGeofence].
   *
   * @param[args] the arguments to validate
   * @param[result] the callback to send results over
   *
   * @return true if arguments are valid, false otherwise. The actual result of
   * operation is sent via [result] callback.
   */
  private fun handleAddGeofence(
    args: Map<String, Any>?,
    result: MethodChannel.Result
  ): Boolean {
    if (args == null) return false
    val geofenceId: String = args[GEOFENCE_ID_ARG] as? String ?: return false
    val geofenceLat: Double = args[GEOFENCE_LAT_ARG] as? Double ?: return false
    val geofenceLng: Double = args[GEOFENCE_LNG_ARG] as? Double ?: return false
    val ssid: String = args[SSID_ARG] as? String ?: return false
    val password: String = args[PASSWORD_ARG] as? String ?: return false
    geofenceController.addGeofence(
      geofenceId,
      geofenceLat,
      geofenceLng,
      ssid,
      password,
      result
    )
    return true
  }

  /**
   * Validates [args] before calling [GeofenceController.removeGeofence].
   *
   * @param[args] the arguments to validate
   * @param[result] the callback to send results over
   *
   * @return true if arguments are valid, false otherwise. The actual result of
   * the operation is sent via [result] callback.
   */
  private fun handleRemoveGeofence(
    args: Map<String, Any>?,
    result: MethodChannel.Result
  ): Boolean {
    if (args == null) return false
    val geofenceId: String = args[GEOFENCE_ID_ARG] as? String ?: return false
    geofenceController.removeGeofence(geofenceId, result)
    return true
  }

  /**
   * Remove all geofences
   */
  private fun handleRemoveAllGeofences(result: MethodChannel.Result): Boolean {
    geofenceController.removeAllGeofences()
    result.success(true)
    return true
  }

  private fun handleVerifyAPSuggestion(
    args: Map<String, Any>?,
    result: MethodChannel.Result
  ): Boolean {
    if (args == null) return false
    val ssid: String = args[SSID_ARG] as? String ?: return false
    val password: String = args[PASSWORD_ARG] as? String ?: return false
    wifiSuggestionController.validateWifiSuggestion(ssid, password, result)
    return true
  }

  /**
   * Sets the foreground method channel
   *
   * This should only be set by [AutoConnectPlugin] in response to lifecycle
   * changes.
   *
   * @param[channel] the method channel to communicate over
   */
  fun setForegroundChannel(channel: MethodChannel) {
    foregroundChannel = channel
  }

  private fun handleAddPinAccessPoint(
    args: Map<String, Any>?,
    result: MethodChannel.Result,
  ): Boolean {
    if (args == null) return false
    val geofenceId: String = args[GEOFENCE_ID_ARG] as? String ?: return false
    geofenceController.addPinAccessPoint(geofenceId, result)
    return true
  }

  /**
   * Validates [args] before calling [GeofenceController.removePinAccessPoint]
   *
   * @param[args] the arguments to validate
   * @param[result] the callback to send result over
   *
   * @return true if arguments are valid, false otherwise. The actual result of
   * the operation is sent via [result] callback.
   */
  private fun handleRemovePinAccessPoint(
    args: Map<String, Any>?,
    result: MethodChannel.Result,
  ): Boolean {
    if (args == null) return false
    val geofenceId: String = args[GEOFENCE_ID_ARG] as? String ?: return false
    geofenceController.removePinAccessPoint(geofenceId, result)
    return true
  }

  private fun handleIsAccessPointPinned(
    args: Map<String, Any>?,
    result: MethodChannel.Result,
  ): Boolean {
    if (args == null) return false
    val geofenceId: String = args[GEOFENCE_ID_ARG] as? String ?: return false
    val isPinned = geofenceController.isAccessPointPinned(geofenceId)
    result.success(isPinned)
    return true
  }
}
