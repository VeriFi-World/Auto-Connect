import 'package:auto_connect_example/add_network_cubit.dart';
import 'package:auto_connect_example/add_network_page.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:auto_connect/auto_connect.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:permission_handler/permission_handler.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AutoConnect.initialize(
    locationEventCallback: geofenceCallback,
    accessPointEventCallback: accessPointCallback,
  );
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return BlocProvider<AddNetworkCubit>(
      create: (context) => AddNetworkCubit(),
      child: const MaterialApp(home: Home()),
    );
  }
}

class Home extends StatelessWidget {
  const Home({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Auto Connect Example App'),
      ),
      body: Center(
        child: Column(
          children: [
            TextButton(
              child: const Text(
                'Request Permissions',
              ),
              onPressed: () async => _requestPermissions(),
            ),
            TextButton(
              child: const Text(
                'Add Home WAP',
              ),
              onPressed: () => _addHomeWAP(),
            ),
            TextButton(
              child: const Text(
                'Remove Home WAP',
              ),
              onPressed: () => _removeHomeWAP(),
            ),
            TextButton(
              child: const Text(
                'Remove All Geofences',
              ),
              onPressed: () => _removeAllGeofences(),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => const AddNetworkPage(wifiName: "GGGGG"),
            ),
          );
        },
        label: const Text("Add Nearby Wifi"),
      ),
    );
  }

  Future<void> _requestPermissions() async {
    final inUseStatus = await Permission.locationWhenInUse.request();
    if (inUseStatus.isGranted) {
      await Permission.locationAlways.request();
    }

    final notifications = await _initializeNotifications();
    await notifications
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.requestPermission();
  }

  void _addHomeWAP() {
    AutoConnect.addAccessPointWithGeofence(
      id: 'home',
      geofence: const Geofence(
        lat: 33.505,
        lng: -82.053,
      ),
      wifi: const WiFi(
        ssid: "GGGGG",
        password: "AndroidMasterRace",
      ),
    );
  }

  void _removeHomeWAP() {
    AutoConnect.removeAccessPointWithGeofence('home');
  }

  void _removeAllGeofences() {
    AutoConnect.removeAllGeofences();
  }
}

Future<void> geofenceCallback(double lat, double lng) async {
  debugPrint("Geofence callback called");
  WidgetsFlutterBinding.ensureInitialized();
  final notifications = await _initializeNotifications();
  await notifications.show(
    0,
    'Home geofence triggered',
    '',
    const NotificationDetails(
      android: AndroidNotificationDetails(
        'auto_connect_example_1',
        'Nearby WiFi',
        channelDescription: 'A WiFi access point is nearby',
      ),
      iOS: DarwinNotificationDetails(),
    ),
  );
  return;
}

Future<void> accessPointCallback(
  String apId,
  String ssid,
  String result,
) async {
  WidgetsFlutterBinding.ensureInitialized();
  final notifications = await _initializeNotifications();
  await notifications.show(
    0,
    (result == 'Success') ? 'Connected to $ssid' : 'Failed to connect to $ssid',
    (result == 'Success') ? null : result,
    const NotificationDetails(
      android: AndroidNotificationDetails(
        'auto_connect_example_1',
        'Nearby WiFi',
        channelDescription: 'A WiFi access point is nearby',
      ),
      iOS: DarwinNotificationDetails(),
    ),
  );
  return;
}

Future<FlutterLocalNotificationsPlugin> _initializeNotifications() async {
  const initSettingsAndroid =
      AndroidInitializationSettings('@mipmap/ic_launcher');
  const initSettingsIOS = DarwinInitializationSettings();
  const initSettings = InitializationSettings(
    android: initSettingsAndroid,
    iOS: initSettingsIOS,
  );
  final notifications = FlutterLocalNotificationsPlugin();
  await notifications.initialize(initSettings);
  return notifications;
}
