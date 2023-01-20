import 'dart:convert';
import 'dart:ui';

import 'package:auto_connect/constants.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

class Location {
  final double latitude;
  final double longitude;

  const Location(this.latitude, this.longitude);

  @override
  String toString() => '($latitude, $longitude)';
}

/// A circular geofence with a center at [lat], [lng].
///
/// Default radius is 100.0 meters.
class Geofence {
  final double lat;
  final double lng;

  const Geofence({
    required this.lat,
    required this.lng,
  });

  Map<String, dynamic> toDict() => {
        geofenceLatArg: lat,
        geofenceLngArg: lng,
      };
}

/// A WiFi access point.
class WiFi {
  /// The name of the network.
  final String ssid;

  /// The password of the network.
  ///
  /// If the network is open (WEP), this should be an empty string.
  final String password;

  const WiFi({
    required this.ssid,
    this.password = "",
  });

  Map<String, dynamic> toDict() => {
        ssidArg: ssid,
        passwordArg: password,
      };
}

enum AutoConnectError {
  associationFailure,
  authenticationFailure,
  ipProvisionFailure,
  unknownFailure
}

const mainChannel = MethodChannel(mainChannelName);
const backgroundChannel = MethodChannel(backgroundChannelName);

class AutoConnect {
  /// Setup callback handles for different events
  ///
  static Future<void> initialize({
    /// A function that gets called when a geofence or set of geofences
    /// gets triggered.
    required Function(double lat, double lng) locationEventCallback,

    /// A function that gets called when the system attempts to connect to an
    /// access point (typically shortly after the location event).
    required Function(
      String accessPointId,
      String ssid,
      String connectionResult,
    )
        accessPointEventCallback,
  }) async {
    debugPrint("Initializing auto connect");
    // Create background channel callback handle
    final callbackDispatcherHandle =
        PluginUtilities.getCallbackHandle(callbackDispatcher)?.toRawHandle();
    if (callbackDispatcherHandle == null) {
      debugPrint("callbackDispatcher not defined");
      return;
    }
    // Get location event callback handle
    final locationEventCallbackHandle =
        PluginUtilities.getCallbackHandle(locationEventCallback)?.toRawHandle();
    if (locationEventCallbackHandle == null) {
      debugPrint("Invalid location event callback");
      return;
    }
    // Get wifi event callback handle
    final accessPointEventCallbackHandle =
        PluginUtilities.getCallbackHandle(accessPointEventCallback)
            ?.toRawHandle();
    if (accessPointEventCallbackHandle == null) {
      debugPrint("Invalid access point event callback");
      return;
    }
    // Pass callback handles to platform
    await mainChannel.invokeMethod<bool>(
      initializeMethod,
      {
        callbackDispatcherHandleArg: callbackDispatcherHandle,
        locationEventCallbackHandleArg: locationEventCallbackHandle,
        accessPointEventCallbackHandleArg: accessPointEventCallbackHandle,
      },
    );
  }

  static Future<void> startActivityMonitoring() async {
    debugPrint("startActivityMonitoring called");
    await mainChannel.invokeMethod<void>(startActivityMonitoringMethod, {});
  }

  /// Retrieve registered geofences.
  ///
  /// Returns a [List] of geofence ids.]
  ///
  static Future<List<String>> getGeofences() async {
    debugPrint("getGeofences called");
    final geofences = await mainChannel.invokeMethod<List<Object?>>(
      getGeofencesMethod,
      {},
    );
    return (geofences != null) ? geofences.cast<String>() : [];
  }

  /// Retrieves pinned geofences.
  ///
  /// Returns a [List] of geofence ids.
  static Future<List<String>> getPinnedGeofences() async {
    debugPrint("getPinnedGeofences called");
    final geofences = await mainChannel.invokeMethod<List<Object?>>(
      getPinnedGeofencesMethod,
      {},
    );
    return (geofences != null) ? geofences.cast<String>() : [];
  }

  /// Add an access point tied to a geofence.
  ///
  /// When the geofence is triggered, the device will automatically attempt
  /// to connect to the access point.
  ///
  static Future<void> addAccessPointWithGeofence({
    required String id,
    required Geofence geofence,
    required WiFi wifi,
  }) async {
    debugPrint("addAccessPointWithGeofence called");
    final Map<String, dynamic> args = {};
    // Create map of arguments
    args.addAll({geofenceIdArg: id});
    args.addAll(geofence.toDict());
    args.addAll(wifi.toDict());
    // Call platform method
    final result = await mainChannel.invokeMethod<bool>(
      addGeofenceWithAccessPointMethod,
      args,
    );
    if (result == null) {
      debugPrint("No result for addGeofence");
      return;
    }
    debugPrint("Geofence ${(result) ? 'added successfully' : 'failed'}");
  }

  /// Remove geofence and access point from the device.
  ///
  static Future<void> removeAccessPointWithGeofence(String id) async {
    debugPrint("removeAccessPointWithGeofence called");
    // Call platform code
    await mainChannel.invokeMethod<bool>(
      removeGeofenceWithAccessPointMethod,
      {geofenceIdArg: id},
    );
  }

  /// Verifies nearby access point.
  ///
  /// This method should only be called after verifying user is within
  /// proximity of access point.
  ///
  /// [accessPointId] in [wifi] must be globally unique.
  /// It should not just be the [ssid], as multiple distinct access points
  /// can have the same SSID.
  ///
  /// [password] is optional. If omitted, it assumes the network is WEP.
  /// If provided, it assumes network is WPA2/3.
  ///
  /// Returns 'Success' if operation completed successfully. Otherwise,
  /// returns a string containing the reason the verification failed.
  ///
  static Future<String> verifyAccessPoint({
    required WiFi wifi,
  }) async {
    // Call platform code and await result
    final result = await mainChannel.invokeMethod<String>(
      verifyAccessPointMethod,
      {
        ssidArg: wifi.ssid,
        passwordArg: wifi.password,
      },
    ).timeout(
      const Duration(seconds: 10),
      onTimeout: () => 'Timeout reached',
    );
    return (result != null) ? result : "Failed to connect";
  }

  /// Pin an access point to prevent it from being deleted.
  ///
  /// Returns `true` if access point was successfully pinned,
  /// `false` otherwise.
  static Future<bool> addPinAccessPoint(String geofenceId) async {
    final result = await mainChannel.invokeMethod<bool>(
      addPinAccessPointMethod,
      {geofenceIdArg: geofenceId},
    );
    return (result != null) ? result : false;
  }

  /// Remove pin status from an access point.
  ///
  /// If the access point was not already pinned, nothing happens.
  ///
  /// Returns `true` if access point was unpinned successfuly. Returns `false`
  /// if the access point was not registered or the operation fails.
  ///
  static Future<bool> removePinAccessPoint(String geofenceId) async {
    final result = await mainChannel.invokeMethod<bool>(
      removePinAccessPointMethod,
      {geofenceIdArg: geofenceId},
    );
    return (result != null) ? result : false;
  }

  static Future<bool> isAccessPointPinned(String geofenceId) async {
    final result = await mainChannel.invokeMethod<bool>(
      isAccessPointPinnedMethod,
      {geofenceIdArg: geofenceId},
    );
    return (result != null) ? result : false;
  }

  static void removeAllGeofences() {
    mainChannel.invokeMethod<void>(removeAllGeofencesMethod, {});
  }
}

Future<void> callbackDispatcher() async {
  debugPrint("callback dispatcher called");
  WidgetsFlutterBinding.ensureInitialized();
  backgroundChannel.setMethodCallHandler(backgroundCallbackHandler);
  await backgroundChannel.invokeMethod<bool>(
    backgroundChannelInitializedMethod,
    {},
  );
}

Future<void> backgroundCallbackHandler(MethodCall call) async {
  debugPrint("background callback handler called");
  WidgetsFlutterBinding.ensureInitialized();
  final String method = call.method;
  final Map<String, dynamic>? args = jsonDecode(call.arguments as String);
  debugPrint(args.toString());
  switch (method) {
    case locationEventMethod:
      debugPrint("$locationEventMethod called");
      if (args == null) {
        debugPrint("Invalid arguments to $locationEventMethod");
        return;
      }
      // Extract location event callback
      final locationEventCallbackHandle =
          args[locationEventCallbackHandleArg] as int;

      final locationEventCallback = PluginUtilities.getCallbackFromHandle(
        CallbackHandle.fromRawHandle(locationEventCallbackHandle),
      );
      if (locationEventCallback == null) {
        debugPrint("Invalid location event callback");
        return;
      }
      final lat = args[geofenceLatArg] as double;
      final lng = args[geofenceLngArg] as double;
      debugPrint("Calling location event callback");
      await locationEventCallback(lat, lng);
      debugPrint("Location event callback complete");
      backgroundChannel.invokeMethod(locationEventCompletedMethod);
      break;
    case accessPointEventMethod:
      debugPrint("$accessPointEventMethod called");
      if (args == null) {
        debugPrint("Invalid arguments for $accessPointEventMethod");
        return;
      }
      final accessPointEventCallbackHandle =
          args[accessPointEventCallbackHandleArg] as int;

      final accessPointEventCallback = PluginUtilities.getCallbackFromHandle(
        CallbackHandle.fromRawHandle(accessPointEventCallbackHandle),
      );
      if (accessPointEventCallback == null) {
        debugPrint("Invalid access point event callback");
        return;
      }
      final accessPointId = args[geofenceIdArg] as String;
      final ssid = args[ssidArg] as String;
      final connectionResult = args[connectionResultArg] as String;
      debugPrint("Calling access point event callback");
      await accessPointEventCallback(accessPointId, ssid, connectionResult);
      backgroundChannel.invokeMethod(accessPointEventCompletedMethod);
      break;
    default:
      throw UnimplementedError("Invalid method for callback handler");
  }
}
