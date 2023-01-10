#import "AutoConnectPlugin.h"
#if __has_include(<auto_connect/auto_connect-Swift.h>)
#import <auto_connect/auto_connect-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "auto_connect-Swift.h"
#endif

@implementation AutoConnectPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    if (@available(iOS 14.0, *)) {
        [SwiftAutoConnectPlugin registerWithRegistrar:registrar];
    }
}
@end
