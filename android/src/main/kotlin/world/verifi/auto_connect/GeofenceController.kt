package world.verifi.auto_connect

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray

class GeofenceController(private val context: Context) {

  private val mGeofencingClient: GeofencingClient =
    LocationServices.getGeofencingClient(context)
  private val mSharedPrefs: SharedPreferences = context.getSharedPreferences(
    SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE
  )

  companion object {
    const val TAG = "GeofenceController"
    private val sGeofenceCacheLock = Object()
    private val sGeofencePinCacheLock = Object()
  }

  // region PUBLIC

  /**
   * Adds a new geofence tied to an access point.
   *
   * Note: Ensure `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION`
   * permissions are granted prior to calling this function.
   *
   * @param[geofenceId] the unique identifier for the geofence
   * @param[geofenceLat] the latitude of the central coordinate of the circular
   * geofence
   * @param[geofenceLat] the longitude of the central coordinate of the circular
   * geofence
   * @param[result] the result callback. This should only be `null` when called
   * by [registerGeofencesAfterReboot].
   */
  fun addGeofence(
    geofenceId: String,
    geofenceLat: Double,
    geofenceLng: Double,
    apSSID: String,
    apPassword: String?,
    result: MethodChannel.Result?
  ) {
    // Make sure we have ACCESS_FINE_LOCATION
    if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
      == PackageManager.PERMISSION_DENIED
    ) {
      val errorMsg =
        "Missing ACCESS_FINE_LOCATION permission. Cannot add geofence"
      Log.w(TAG, errorMsg)
      result?.error(MISSING_PERMISSION_ERROR, errorMsg, null)
    }
    val geofence = Geofence.Builder()
      .setRequestId(geofenceId)
      .setCircularRegion(geofenceLat, geofenceLng, 100.0f)
      .setTransitionTypes(
        Geofence.GEOFENCE_TRANSITION_ENTER
            or Geofence.GEOFENCE_TRANSITION_DWELL
      )
      .setLoiteringDelay(20000) // 20 seconds
      .setExpirationDuration(Geofence.NEVER_EXPIRE)
      .build()
    val geofenceRequest = GeofencingRequest.Builder().apply {
      addGeofence(geofence)
    }.build()
    val geofencePendingIntent = getGeofencePendingIntent()
    mGeofencingClient.addGeofences(geofenceRequest, geofencePendingIntent).run {
      addOnSuccessListener {
        addGeofenceToCache(geofenceId, geofenceLat, geofenceLng)
        setGeofenceSSID(geofenceId, apSSID)
        if (apPassword != null) {
          setGeofencePassword(geofenceId, apPassword)
        }
        result?.success(true)
      }
      addOnFailureListener {
        result?.error(ADD_GEOFENCE_ERROR, it.message, null)
      }
    }
  }

  /**
   *
   * Removes a geofence previous registered via [addGeofence].
   *
   * @param[geofenceId] the id of the geofence
   * @param[result] the result callback
   *
   */
  fun removeGeofence(geofenceId: String, result: MethodChannel.Result) {
    mGeofencingClient.removeGeofences(mutableListOf(geofenceId)).run {
      addOnSuccessListener {
        removeGeofenceFromCache(geofenceId)
        removeGeofenceAccessPoint(geofenceId)
        result.success(true)
      }

      addOnFailureListener {
        result.error(REMOVE_GEOFENCE_ERROR, it.message, null)
      }
    }
  }

  /**
   * Gets geofences registered to [mGeofencingClient].
   *
   * @return List of geofence ids.
   */
  fun getRegisteredGeofences(): List<String> {
    return mSharedPrefs.getStringSet(
      PERSISTENT_GEOFENCE_IDS,
      mutableSetOf()
    )?.toList() ?: listOf()
  }

  /**
   * Gets pinned geofences that should not be deleted.
   *
   * @return List of geofence ids.
   */
  fun getPinnedGeofences(): List<String> {
    return mSharedPrefs.getStringSet(
      PINNED_GEOFENCE_IDS,
      mutableSetOf()
    )?.toList() ?: listOf()
  }

  /**
   * Removes all geofences.
   */
  fun removeAllGeofences() {
    val geofences = getRegisteredGeofences()
    mGeofencingClient.removeGeofences(geofences)
    for (geofenceId in geofences) {
      removeGeofenceFromCache(geofenceId)
      removeGeofenceAccessPoint(geofenceId)
      removeGeofencePin(geofenceId)
      removeGeofenceFromPinCache(geofenceId)
    }
  }

  /**
   * Pins an access point / geofence to prevent it from being deleted when the
   * user moves.
   *
   * @param[geofenceId] the id of the geofence to pin
   * @param[result] the channel to send the result of the operation over
   */
  fun addPinAccessPoint(geofenceId: String, result: MethodChannel.Result) {
    addGeofencePin(geofenceId)
    addGeofenceToPinCache(geofenceId)
    result.success(true)
  }

  /**
   * Removes a pin from an access point / geofence so it can be deleted if
   * the user moves.
   *
   * Sends `true` over [result] channel if access point existed and was removed
   * successfully. Sends `false` if access point did not exist.
   */
  fun removePinAccessPoint(geofenceId: String, result: MethodChannel.Result) {
    val existed = removeGeofenceFromPinCache(geofenceId)
    if (existed) {
      removeGeofencePin(geofenceId)
      result.success(true)
    }
    result.success(false)
  }

  /**
   * Checks whether access point is pinned or not.
   *
   * @param[geofenceId] the access point id to check.
   *
   * @return true if pinned, false if not pinned
   */
  fun isAccessPointPinned(id: String): Boolean {
    return getGeofencePinStatus(id)
  }

  // endregion

  // region PRIVATE

  private fun getGeofencePendingIntent(): PendingIntent {
    val intent = Intent(context, GeofenceEventReceiver::class.java)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    return PendingIntent.getBroadcast(
      context,
      GEOFENCE_BROADCAST_REQUEST_CODE,
      intent,
      flags
    )
  }

  private fun addGeofenceToCache(
    geofenceId: String,
    geofenceLat: Double,
    geofenceLng: Double
  ) {
    synchronized(sGeofenceCacheLock) {
      val geofenceArgs = JSONArray(listOf(geofenceLat, geofenceLng))
      var persistentGeofences =
        mSharedPrefs.getStringSet(PERSISTENT_GEOFENCE_IDS, null)
      persistentGeofences = if (persistentGeofences == null) {
        HashSet<String>()
      } else {
        HashSet<String>(persistentGeofences)
      }
      persistentGeofences.add(geofenceId)
      val geofenceKey = getPersistentGeofenceKey(geofenceId)
      mSharedPrefs.edit()
        .putStringSet(PERSISTENT_GEOFENCE_IDS, persistentGeofences)
        .putString(geofenceKey, geofenceArgs.toString())
        .apply()
    }
  }

  private fun addGeofenceToPinCache(geofenceId: String) {
    synchronized(sGeofencePinCacheLock) {
      var pinnedGeofences = mSharedPrefs.getStringSet(PINNED_GEOFENCE_IDS, null)
      pinnedGeofences = if (pinnedGeofences == null) {
        HashSet<String>()
      } else {
        HashSet<String>(pinnedGeofences)
      }
      pinnedGeofences.add(geofenceId)
      mSharedPrefs.edit()
        .putStringSet(PINNED_GEOFENCE_IDS, pinnedGeofences)
        .apply()

    }
  }


  private fun removeGeofenceFromCache(geofenceId: String) {
    synchronized(sGeofenceCacheLock) {
      var persistentGeofences =
        mSharedPrefs.getStringSet(PERSISTENT_GEOFENCE_IDS, null)
          ?: return
      persistentGeofences = HashSet<String>(persistentGeofences)
      persistentGeofences.remove(geofenceId)
      mSharedPrefs.edit()
        .remove(getPersistentGeofenceKey(geofenceId))
        .putStringSet(PERSISTENT_GEOFENCE_IDS, persistentGeofences)
        .apply()
    }
  }

  private fun removeGeofenceFromPinCache(geofenceId: String): Boolean {
    var existed: Boolean
    synchronized(sGeofencePinCacheLock) {
      var pinnedGeofences = mSharedPrefs.getStringSet(PINNED_GEOFENCE_IDS, null)
        ?: return false
      pinnedGeofences = HashSet<String>(pinnedGeofences)
      existed = pinnedGeofences.remove(geofenceId)
      mSharedPrefs.edit()
        .putStringSet(PINNED_GEOFENCE_IDS, pinnedGeofences)
        .apply()
    }
    return existed
  }

  fun registerGeofencesAfterReboot() {
    synchronized(sGeofenceCacheLock) {
      val persistentGeofences =
        mSharedPrefs.getStringSet(PERSISTENT_GEOFENCE_IDS, null)
          ?: return
      for (geofenceId in persistentGeofences) {
        val gfJson =
          mSharedPrefs.getString(getPersistentGeofenceKey(geofenceId), null)
            ?: continue
        val gfArgs = JSONArray(gfJson)
        val geofenceLat = gfArgs.getDouble(0)
        val geofenceLng = gfArgs.getDouble(1)
        val ssid = getGeofenceSSID(geofenceId)
        val password = getGeofencePassword(geofenceId)
        addGeofence(geofenceId, geofenceLat, geofenceLng, ssid, password, null)
      }
    }
  }


  private fun getPersistentGeofenceKey(geofenceId: String): String {
    return "persistent_geofence/$geofenceId"
  }

  private fun getGeofenceSSID(geofenceId: String): String {
    return mSharedPrefs
      .getString("geofence_access_points/$geofenceId/ssid", "")!!
  }

  private fun getGeofencePassword(geofenceId: String): String {
    return mSharedPrefs
      .getString("geofence_access_points/$geofenceId/password", "")!!
  }

  private fun setGeofenceSSID(
    geofenceId: String,
    ssid: String,
  ) {
    mSharedPrefs.edit()
      .putString("geofence_access_points/$geofenceId/ssid", ssid)
      .apply()
  }

  private fun setGeofencePassword(
    geofenceId: String,
    password: String,
  ) {
    mSharedPrefs.edit()
      .putString("geofence_access_points/$geofenceId/password", password)
      .apply()
  }

  private fun removeGeofenceAccessPoint(geofenceId: String) {
    mSharedPrefs.edit()
      .remove("geofence_access_points/$geofenceId/ssid")
      .remove("geofence_access_points/$geofenceId/password")
      .remove("geofence_access_points/$geofenceId/pinned")
      .apply()
  }

  private fun addGeofencePin(id: String) {
    mSharedPrefs.edit()
      .putBoolean("geofence_access_points/$id/pinned", true)
      .apply()
  }

  private fun removeGeofencePin(id: String) {
    mSharedPrefs.edit()
      .remove("geofence_access_points/$id/pinned")
      .apply()
  }

  private fun getGeofencePinStatus(id: String): Boolean {
    return mSharedPrefs.getBoolean(
      "geofence_access_points/$id/pinned",
      false
    )
  }

  // endregion
}
