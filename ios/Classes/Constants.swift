import Foundation

struct K {
    enum Channels {
        static let mainChannel = "plugins.verifi.world/auto_connect"
        static let backgroundChannel = "plugins.verifi.world/auto_connect_background"
    }

    enum Methods {
        // Foreground channel methods
        static let initialize = "initialize"
        static let startActivityMonitoring = "startActivityMonitoring"
        static let getGeofences = "getGeofences"
        static let removeAllGeofences = "removeAllGeofences"
        static let getPinnedGeofences = "getPinnedGeofences"
        static let addGeofenceWithAccessPoint = "addGeofenceWithAccessPoint"
        static let removeGeofenceWithAccessPoint = "removeGeofenceWithAccessPoint"
        static let connectToAccessPoint = "connectToAccessPoint"
        static let addPinAccessPoint = "addPinAccessPoint"
        static let removePinAccessPoint = "removePinAccessPoint"
        static let isAccessPointPinned = "isAccessPointPinned"
        // Background channel methods
        static let backgroundChannelInitialized = "backgroundChannelInitialized"
        static let locationEvent = "locationEvent"
        static let accessPointEvent = "accessPointEvent"
        static let locationEventCompleted = "locationEventCompleted"
        static let accessPointEventCompleted = "accessPointEventCompleted"
    }

    enum Arguments {
        // Callback handle arguments
        static let callbackDispatcherHandle = "callbackDispatcherHandle"
        static let locationEventCallbackHandle = "locationEventCallbackHandle"
        static let accessPointEventCallbackHandle = "accessPointEventCallbackHandle"
        // Geofence arguments
        static let geofenceId = "geofenceId"
        static let geofenceIdList = "geofenceIdList"
        static let geofenceLat = "geofenceLat"
        static let geofenceLng = "geofenceLng"
        // Access point arguments
        static let ssid = "ssid"
        static let password = "password"
        static let connectionResult = "connectionResult"
        static let accessPoints = "accessPoints"
        static let pinnedGeofences = "pinnedGeofences"
    }
}
