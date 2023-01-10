// Channel names
const mainChannelName = "plugins.verifi.world/auto_connect";
const backgroundChannelName = "plugins.verifi.world/auto_connect_background";

// Main channel methods
const initializeMethod = 'initialize';
const startActivityMonitoringMethod = 'startActivityMonitoring';
const getGeofencesMethod = 'getGeofences';
const getPinnedGeofencesMethod = 'getPinnedGeofences';
const removeAllGeofencesMethod = 'removeAllGeofences';
const addGeofenceWithAccessPointMethod = 'addGeofenceWithAccessPoint';
const removeGeofenceWithAccessPointMethod = 'removeGeofenceWithAccessPoint';
const verifyAccessPointMethod = 'verifyAccessPoint';
const addPinAccessPointMethod = 'addPinAccessPoint';
const removePinAccessPointMethod = 'removePinAccessPoint';
const isAccessPointPinnedMethod = 'isAccessPointPinned';

// Background channel methods
const backgroundChannelInitializedMethod = 'backgroundChannelInitialized';
const locationEventMethod = 'locationEvent';
const accessPointEventMethod = 'accessPointEvent';
const locationEventCompletedMethod = 'locationEventCompleted';
const accessPointEventCompletedMethod = 'accessPointEventCompleted';

// Callback handle arguments
const callbackDispatcherHandleArg = 'callbackDispatcherHandle';
const locationEventCallbackHandleArg = 'locationEventCallbackHandle';
const accessPointEventCallbackHandleArg = 'accessPointEventCallbackHandle';

// Geofence arguments
const geofenceIdArg = 'geofenceId';
const geofenceLatArg = 'geofenceLat';
const geofenceLngArg = 'geofenceLng';

// Access point arguments
const ssidArg = "ssid";
const passwordArg = "password";
const connectionResultArg = "connectionResult";
