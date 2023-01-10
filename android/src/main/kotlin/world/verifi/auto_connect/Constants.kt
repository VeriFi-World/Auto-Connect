package world.verifi.auto_connect

const val MAIN_CHANNEL_NAME = "plugins.verifi.world/auto_connect"
const val BACKGROUND_CHANNEL_NAME =
  "plugins.verifi.world/auto_connect_background"

// Main channel methods
const val INITIALIZE_METHOD = "initialize"
const val GET_GEOFENCES_METHOD = "getGeofences"
const val REMOVE_ALL_GEOFENCES_METHOD = "removeAllGeofences"
const val GET_PINNED_GEOFENCES_METHOD = "getPinnedGeofences"
const val ADD_GEOFENCE_WITH_AP_METHOD = "addGeofenceWithAccessPoint"
const val REMOVE_GEOFENCE_WITH_AP_METHOD = "removeGeofenceWithAccessPoint"
const val VERIFY_AP_SUGGESTION_METHOD = "verifyAccessPoint"
const val ADD_PIN_AP_METHOD = "addPinAccessPoint"
const val REMOVE_PIN_AP_METHOD = "removePinAccessPoint"
const val IS_AP_PINNED_METHOD = "isAccessPointPinned"
const val NO_SUCH_METHOD = "noSuchMethod"

// Background channel methods
const val BACKGROUND_CHANNEL_INITIALIZED_METHOD = "backgroundChannelInitialized"
const val LOCATION_EVENT_METHOD = "locationEvent"
const val ACCESS_POINT_EVENT_METHOD = "accessPointEvent"
const val LOCATION_EVENT_COMPLETED_METHOD = "locationEventCompleted"
const val ACCESS_POINT_EVENT_COMPLETED_METHOD = "accessPointEventCompleted"


// Background worker arguments
const val EVENT_ARG = "event"
const val CONNECTION_RESULT_ARG = "connectionResult"

// Callback handle arguments
const val CALLBACK_DISPATCHER_HANDLE_ARG = "callbackDispatcherHandle"
const val LOCATION_EVENT_CALLBACK_HANDLE_ARG = "locationEventCallbackHandle"
const val ACCESS_POINT_EVENT_CALLBACK_HANDLE_ARG =
  "accessPointEventCallbackHandle"

// Geofence arguments
const val GEOFENCE_ID_ARG = "geofenceId"
const val GEOFENCE_LAT_ARG = "geofenceLat"
const val GEOFENCE_LNG_ARG = "geofenceLng"

// Access point arguments
const val SSID_ARG = "ssid"
const val PASSWORD_ARG = "password"
const val SSID_LIST_ARG = "ssidList"
const val PASSWORD_LIST_ARG = "passwordList"

// Shared preferences
const val SHARED_PREFERENCES_NAME = "world.verifi.auto_connect"
const val PERSISTENT_GEOFENCE_IDS = "persistentGeofenceIds"
const val PINNED_GEOFENCE_IDS = "pinnedGeofenceIds"

// Request Codes
const val GEOFENCE_BROADCAST_REQUEST_CODE = 21
const val ACTIVITY_TRANSITION_BROADCAST_REQUEST_CODE = 31

// Error codes
const val MISSING_PERMISSION_ERROR = "missing-permission"
const val ADD_GEOFENCE_ERROR = "add-geofence"
const val REMOVE_GEOFENCE_ERROR = "remove-geofence"

const val CUSTOM_SEPARATOR =
  "verificustomseperatorifyouusethisinyournetworkcredentialsyouareevil"
