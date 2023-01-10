import CoreLocation
import CoreMotion
import Flutter
import Foundation
import NetworkExtension
import SystemConfiguration
import UIKit
import UserNotifications

@available(iOS 14.0, *)
var instance: SwiftAutoConnectPlugin? = nil
var registerPlugins: FlutterPluginRegistrantCallback?

@available(iOS 14.0, *)
public class SwiftAutoConnectPlugin: NSObject, FlutterPlugin,
    CLLocationManagerDelegate
{
    private var locationManager: CLLocationManager?
    private var activityManager: CMMotionActivityManager?
    private var hotspotManager: NEHotspotConfigurationManager?
    private var mainChannel: FlutterMethodChannel?
    private var backgroundChannel: FlutterMethodChannel?
    private var headlessRunner: FlutterEngine?
    private var persistentState: UserDefaults?
    private var registrar: FlutterPluginRegistrar?
    private var eventQueue: [CLRegion] = []
    private var result: FlutterResult?
    private var previousActivity: CMMotionActivity?
    private var accessPointId: String?

    static var backgroundIsolateRun = false
    static var initialized = false

    public static func register(with registrar: FlutterPluginRegistrar) {
        let lockQueue = DispatchQueue(label: "self")
        lockQueue.sync {
            if instance == nil {
                instance = SwiftAutoConnectPlugin(registrar)
                registrar.addApplicationDelegate(instance! as FlutterPlugin)
            }
        }
    }

    public static func setPluginRegistrantCallback(
        _ callback: @escaping FlutterPluginRegistrantCallback
    ) {
        registerPlugins = callback
    }

    public func application(
        _: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [AnyHashable: Any] = [:]
    ) -> Bool {
        if launchOptions[UIApplication.LaunchOptionsKey.location] != nil {
            startHeadlessService(getCallbackDispatcherHandle())
        }
        return true
    }

    init(_ registrar: FlutterPluginRegistrar) {
        super.init()
        persistentState = UserDefaults.standard
        self.registrar = registrar

        locationManager = CLLocationManager()
        locationManager?.delegate = self
        locationManager?.distanceFilter = CLLocationDistance(20.0)
        locationManager?.startMonitoringSignificantLocationChanges()

        hotspotManager = NEHotspotConfigurationManager.shared

        mainChannel = FlutterMethodChannel(
            name: K.Channels.mainChannel,
            binaryMessenger: registrar.messenger()
        )
        self.registrar!.addMethodCallDelegate(self, channel: mainChannel!)

        headlessRunner = FlutterEngine(
            name: "AutoConnectIsolate",
            project: nil,
            allowHeadlessExecution: true
        )
        backgroundChannel = FlutterMethodChannel(
            name: K.Channels.backgroundChannel,
            binaryMessenger: headlessRunner!.binaryMessenger
        )
    }

    public func handle(
        _ call: FlutterMethodCall,
        result: @escaping FlutterResult
    ) {
        debugPrint("\(call.method) called")
        guard let arguments = call.arguments as? NSDictionary else {
            debugPrint("Failed to convert arguments to NSDictionary")
            result(false)
            return
        }
        if call.method == K.Methods.initialize {
            guard let callbackHandle =
                    arguments[K.Arguments.callbackDispatcherHandle] as? Int,
                  let locationEventHandle =
                    arguments[K.Arguments.locationEventCallbackHandle] as? Int,
                  let accessPointEventHandle =
                    arguments[K.Arguments.accessPointEventCallbackHandle] as? Int
            else {
                result(FlutterError(
                    code: "invalid-args",
                    message: "Failed to get callback handles from args",
                    details: ""
                ))
                return
            }
            setCallbackDispatcherHandle(callbackHandle)
            setLocationEventCallbackHandle(locationEventHandle)
            setAccessPointEventCallbackHandle(accessPointEventHandle)
            startHeadlessService(callbackHandle)
            result(true)
        } else if call.method == K.Methods.backgroundChannelInitialized {
            let lockQueue = DispatchQueue(label: "self")
            lockQueue.sync {
                Self.initialized = true
                while self.eventQueue.count > 0 {
                    guard let region = eventQueue[0] as? CLCircularRegion else {
                        debugPrint(
                            "Failed to extract region and event type from eventQueue"
                        )
                        result(false)
                        return
                    }
                    eventQueue.remove(at: 0)
                    sendLocationEvent(region: region)
                }
            }
            result(true)
        } else if call.method == K.Methods.startActivityMonitoring {
            activityManager = CMMotionActivityManager()
            activityManager?.startActivityUpdates(
                to: OperationQueue.main,
                withHandler: handleActivity
            )
        } else if call.method == K.Methods.addGeofenceWithAccessPoint {
            guard let id = arguments[K.Arguments.geofenceId] as? String,
                  let lat = arguments[K.Arguments.geofenceLat] as? Double,
                  let lng = arguments[K.Arguments.geofenceLng] as? Double,
                  let ssid = arguments[K.Arguments.ssid] as? String,
                  let password = arguments[K.Arguments.password] as? String
            else {
                debugPrint(
                    "Failed to extract args from \(K.Methods.addGeofenceWithAccessPoint)"
                )
                result(false)
                return
            }
            let isAlreadyAdded = checkForAlreadyMonitoredRegion(id)
            if isAlreadyAdded {
                result(false)
                return
            }
            saveAccessPoint(id: id, ssid: ssid, password: password)
            addGeofence(id, lat, lng)
            result(true)
        } else if call.method == K.Methods.removeGeofenceWithAccessPoint {
            guard let id = arguments[K.Arguments.geofenceId] as? String
            else {
                debugPrint(
                    "Failed to extract args from \(K.Methods.removeGeofenceWithAccessPoint) call"
                )
                result(false)
                return
            }
            for region in locationManager!.monitoredRegions {
                if region.identifier == id {
                    locationManager!.stopMonitoring(for: region)
                    deleteAccessPoint(for: id)
                    removeGeofencePin(for: id)
                    result(true)
                    return
                }
            }
            result(false)
        } else if call.method == K.Methods.getGeofences {
            let geofenceIds = getGeofences()
            result(geofenceIds)
            return
        } else if call.method == K.Methods.removeAllGeofences {
            removeAllGeofences()
            result(true)
            return
        } else if call.method == K.Methods.getPinnedGeofences {
            let pinnedGeofenceIds = getPinnedGeofences()
            result(pinnedGeofenceIds)
            return
        } else if call.method == K.Methods.addPinAccessPoint {
            guard let id = arguments[K.Arguments.geofenceId] as? String
            else {
                debugPrint(
                    "Failed to extract args from \(K.Methods.addPinAccessPoint) call"
                )
                result(false)
                return
            }
            addGeofencePin(for: id)
            result(true)
            return
        } else if call.method == K.Methods.removePinAccessPoint {
            guard let id = arguments[K.Arguments.geofenceId] as? String
            else {
                debugPrint(
                    "Failed to extract args from \(K.Methods.removePinAccessPoint) call"
                )
                result(false)
                return
            }
            removeGeofencePin(for: id)
            result(true)
            return

        } else if call.method == K.Methods.verifyAccessPoint {
            self.result = result
            guard let ssid = arguments[K.Arguments.ssid] as? String,
                  let password = arguments[K.Arguments.password] as? String
            else {
                debugPrint(
                    "Failed to extract args from \(K.Methods.verifyAccessPoint) call"
                )
                result(false)
                return
            }
            if password == "" {
                connectWithoutPassword(ssid)
            } else {
                connectWithPassword(ssid, password)
            }
        } else {
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: LOCATION METHODS

    public func locationManager(
        _: CLLocationManager,
        didEnterRegion region: CLRegion
    ) {
        debugPrint("Entered region \(region.identifier)")
        let lockQueue = DispatchQueue(label: "self")
        lockQueue.sync {
            if Self.initialized {
                sendLocationEvent(region: region)
                connectToAccessPoint(region.identifier)
            } else {
                eventQueue.append(region)
            }
        }
    }

    public func locationManager(
        _: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        if locations.isEmpty == false {
            debugPrint(
                "New location \(String(describing: locations.last?.coordinate))"
            )
            let lockQueue = DispatchQueue(label: "self")
            lockQueue.sync {
                if Self.initialized {
                    sendLocationEvent(region: CLCircularRegion(
                        center: locations.last!.coordinate,
                        radius: 0,
                        identifier: "managerUpdate"
                    ))
                } else {
                    debugPrint("Adding new location to event queue")
                    eventQueue.append(CLCircularRegion(
                        center: locations.last!.coordinate,
                        radius: 0,
                        identifier: "managerUpdate"
                    ))
                }
            }
        }
    }

    public func locationManager(
        _: CLLocationManager,
        didFailWithError error: Error
    ) {
        debugPrint(error)
    }

    private func sendLocationEvent(region: CLRegion) {
        guard let region = region as? CLCircularRegion else {
            debugPrint(
                "Failed to convert CLRegion to CLCircularRegion in sendLocationEvent"
            )
            return
        }
        let args: NSDictionary = [
            K.Arguments
                .locationEventCallbackHandle: getLocationEventCallbackHandle(),
            K.Arguments.geofenceLat: region.center.latitude,
            K.Arguments.geofenceLng: region.center.longitude,
        ]
        if let argsJsonData = try? JSONSerialization
            .data(withJSONObject: args)
        {
            let argsJsonString = String(data: argsJsonData, encoding: .ascii)
            backgroundChannel!.invokeMethod(
                K.Methods.locationEvent,
                arguments: argsJsonString
            )
        }
    }

    private func startHeadlessService(_ callbackHandle: Int) {
        let callbackInfo = FlutterCallbackCache
            .lookupCallbackInformation(Int64(callbackHandle))
        if callbackInfo == nil {
            print("Failed to get callback info from callback cache")
            return
        }
        let entrypoint = callbackInfo!.callbackName
        let uri = callbackInfo!.callbackLibraryPath
        headlessRunner!.run(withEntrypoint: entrypoint, libraryURI: uri)
        if Self.backgroundIsolateRun == false {
            assert(registerPlugins != nil, "Failed to set registerPlugins")
            registerPlugins!(headlessRunner!)
        }
        registrar!.addMethodCallDelegate(self, channel: backgroundChannel!)
        Self.backgroundIsolateRun = true
    }

    private func addGeofence(_ id: String, _ lat: Double, _ lng: Double) {
        if checkForAlreadyMonitoredRegion(id) {
            return
        }
        let region = CLCircularRegion(
            center: CLLocationCoordinate2D(latitude: lat, longitude: lng),
            radius: 100.0,
            identifier: id
        )
        region.notifyOnEntry = true
        locationManager?.startMonitoring(for: region)
        debugPrint("Now monitoring geofence \(id)")
    }

    private func checkForAlreadyMonitoredRegion(_ id: String) -> Bool {
        for region in locationManager!.monitoredRegions {
            if region.identifier == id {
                return true
            }
        }
        return false
    }

    private func getGeofences() -> [String] {
        let regions: Set<CLRegion> = locationManager?
            .monitoredRegions as? Set<CLRegion> ?? []
        if regions.isEmpty {
            return []
        }
        return regions.map { $0.identifier }
    }

    private func removeAllGeofences() {
        let regions = locationManager?.monitoredRegions ?? []
        for region in regions {
            locationManager?.stopMonitoring(for: region)
            deleteAccessPoint(for: region.identifier)
            removeGeofencePin(for: region.identifier)
        }
    }

    // MARK: WIFI CONNECTION METHODS

    private func connectToAccessPoint(_ accessPointId: String) {
        self.accessPointId = accessPointId
        guard let accessPoint = getAccessPointForRegion(regionId: accessPointId),
              let ssid = accessPoint[K.Arguments.ssid] as? String,
              let password = accessPoint[K.Arguments.password] as? String
        else {
            debugPrint("Failed to lookup access point for \(accessPointId)")
            return
        }
        if password == "" {
            connectWithoutPassword(ssid)
        } else {
            connectWithPassword(ssid, password)
        }
    }

    private func connectWithoutPassword(_ ssid: String) {
        let config = NEHotspotConfiguration(ssid: ssid)
        config.joinOnce = false
        hotspotManager?.apply(
            config,
            completionHandler: wifiCompletionHandler(_:)
        )
    }

    private func connectWithPassword(_ ssid: String, _ password: String) {
        let config = NEHotspotConfiguration(
            ssid: ssid,
            passphrase: password,
            isWEP: false
        )
        config.joinOnce = false
        hotspotManager?.apply(
            config,
            completionHandler: wifiCompletionHandler(_:)
        )
    }

    private func wifiCompletionHandler(_ error: Error?) {
        if error == nil {
            NEHotspotNetwork
                .fetchCurrent(completionHandler: currentWiFiCompletionHandler)
        } else {
            // Geofence trigger
            if result == nil {
                let args: NSDictionary = [
                    K.Arguments
                        .accessPointEventCallbackHandle: getAccessPointEventCallbackHandle(
                        ),
                    K.Arguments.geofenceId: self.accessPointId ?? "",
                    K.Arguments.ssid: "",
                    K.Arguments.connectionResult: error!.localizedDescription,
                ]
                if let argsJsonData = try? JSONSerialization
                    .data(withJSONObject: args)
                {
                    let argsJsonString = String(
                        data: argsJsonData,
                        encoding: .ascii
                    )
                    backgroundChannel!.invokeMethod(
                        K.Methods.accessPointEvent,
                        arguments: argsJsonString
                    )
                }
            // verifyAccessPoint
            } else {
                result!(error!.localizedDescription)
            }
        }
    }

    private func currentWiFiCompletionHandler(_ network: NEHotspotNetwork?) {
        if network == nil {
            // Geofence trigger
            if result == nil {
                let args: NSDictionary = [
                    K.Arguments
                        .accessPointEventCallbackHandle: getAccessPointEventCallbackHandle(
                        ),
                    K.Arguments.geofenceId: self.accessPointId ?? "",
                    K.Arguments.ssid: "",
                    K.Arguments.connectionResult: "Failed to connect",
                ]
                if let argsJsonData = try? JSONSerialization
                    .data(withJSONObject: args)
                {
                    let argsJsonString = String(
                        data: argsJsonData,
                        encoding: .ascii
                    )
                    backgroundChannel!.invokeMethod(
                        K.Methods.accessPointEvent,
                        arguments: argsJsonString
                    )
                }
                // verifyAccessPoint
            } else {
                result!("Failed to connect")
            }
        } else {
            let args: NSDictionary = [
                K.Arguments
                    .accessPointEventCallbackHandle: getAccessPointEventCallbackHandle(
                    ),
                K.Arguments.geofenceId: self.accessPointId ?? "",
                K.Arguments.ssid: network!.ssid,
                K.Arguments.connectionResult: "Success",
            ]
            if let argsJsonData = try? JSONSerialization
                .data(withJSONObject: args)
            {
                let argsJsonString = String(
                    data: argsJsonData,
                    encoding: .ascii
                )
                backgroundChannel!.invokeMethod(
                    K.Methods.accessPointEvent,
                    arguments: argsJsonString
                )
            }
            result!("Success")
        }
    }

    // MARK: ACTIVITY RECOGNITION METHODS

    private func handleActivity(_ activity: CMMotionActivity?) {
        guard let activity = activity else { return }
        if previousActivity != nil,
           previousActivity!.automotive || previousActivity!.cycling
        {
            locationManager?.requestLocation()
        }
        previousActivity = activity
    }

    // MARK: PERSISTENT STATE METHODS

    private func getCallbackDispatcherHandle() -> Int {
        return persistentState!
            .integer(forKey: K.Arguments.callbackDispatcherHandle)
    }

    private func setCallbackDispatcherHandle(_ handle: Int) {
        persistentState!.set(
            handle,
            forKey: K.Arguments.callbackDispatcherHandle
        )
    }

    private func getLocationEventCallbackHandle() -> Int {
        return persistentState!
            .integer(forKey: K.Arguments.locationEventCallbackHandle)
    }

    private func setLocationEventCallbackHandle(_ handle: Int) {
        persistentState!.set(
            handle,
            forKey: K.Arguments.locationEventCallbackHandle
        )
    }

    private func getAccessPointEventCallbackHandle() -> Int {
        return persistentState!
            .integer(forKey: K.Arguments.accessPointEventCallbackHandle)
    }

    private func setAccessPointEventCallbackHandle(_ handle: Int) {
        persistentState!.set(
            handle,
            forKey: K.Arguments.accessPointEventCallbackHandle
        )
    }

    private func saveAccessPoint(id: String, ssid: String, password: String) {
        debugPrint("Saving access point \(id)")
        var accessPointsDict = getAccessPointsDict()
        accessPointsDict[id] = [K.Arguments.ssid: ssid,
                                K.Arguments.password: password]
        setAccessPointsDict(accessPointsDict)
    }

    private func deleteAccessPoint(for id: String) {
        var accessPointsDict = getAccessPointsDict()
        accessPointsDict.removeValue(forKey: id)
        setAccessPointsDict(accessPointsDict)
    }

    private func getAccessPointsDict() -> [String: Any] {
        var accessPointsDict = persistentState?
            .dictionary(forKey: K.Arguments.accessPoints)
        if accessPointsDict == nil {
            accessPointsDict = [String: Any]()
            persistentState?.set(
                accessPointsDict,
                forKey: K.Arguments.accessPoints
            )
        }
        return accessPointsDict!
    }

    private func setAccessPointsDict(_ dict: [String: Any]) {
        persistentState?.set(dict, forKey: K.Arguments.accessPoints)
    }

    private func getAccessPointForRegion(regionId: String) -> [String: Any]? {
        let accessPointsDict = persistentState?
            .dictionary(forKey: K.Arguments.accessPoints)
        guard let region = accessPointsDict?[regionId] as? [String: Any] else {
            debugPrint("Failed to get access point for region \(regionId)")
            return nil
        }
        return region
    }

    private func getPinnedGeofences() -> [String] {
        var pinnedGeofences = [String]()
        guard let pinnedGeofencesDict = persistentState?
            .dictionary(forKey: K.Arguments.pinnedGeofences) as? [String: Bool]
        else {
            return []
        }
        for (key, value) in pinnedGeofencesDict {
            if value == true {
                pinnedGeofences.append(key)
            }
        }
        return pinnedGeofences
    }

    private func addGeofencePin(for id: String) {
        guard var pinnedGeofencesDict = persistentState?
            .dictionary(forKey: K.Arguments.pinnedGeofences) as? [String: Bool]
        else { return }
        pinnedGeofencesDict[id] = true
        persistentState?.set(
            pinnedGeofencesDict,
            forKey: K.Arguments.pinnedGeofences
        )
    }

    private func removeGeofencePin(for id: String) {
        guard var pinnedGeofencesDict = persistentState?
            .dictionary(forKey: K.Arguments.pinnedGeofences) as? [String: Bool]
        else { return }
        pinnedGeofencesDict.removeValue(forKey: id)
        persistentState?.set(
            pinnedGeofencesDict,
            forKey: K.Arguments.pinnedGeofences
        )
    }

    private func getGeofencePinStatus(for id: String) -> Bool {
        guard let pinnedGeofencesDict = persistentState?
            .dictionary(forKey: K.Arguments.pinnedGeofences) as? [String: Bool]
        else { return false }
        return pinnedGeofencesDict.index(forKey: id) != nil
    }
}
