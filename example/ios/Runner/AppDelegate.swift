import auto_connect
import Flutter
import UIKit

func registerPlugins(_ registry: (NSObjectProtocol & FlutterPluginRegistry)?) {
    GeneratedPluginRegistrant.register(with: registry!)
}

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        GeneratedPluginRegistrant.register(with: self)
        SwiftAutoConnectPlugin.setPluginRegistrantCallback(registerPlugins(_:))
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
