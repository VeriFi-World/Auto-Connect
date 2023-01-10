import 'package:auto_connect/auto_connect.dart';
import 'package:auto_connect_example/add_network_cubit.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:network_info_plus/network_info_plus.dart';
import 'package:open_settings/open_settings.dart';

class ForgetNetworkPage extends StatefulWidget {
  const ForgetNetworkPage({super.key});

  @override
  State<StatefulWidget> createState() => _ForgetNetworkPageState();
}

class _ForgetNetworkPageState extends State<ForgetNetworkPage> {
  String? _wifiName;
  bool? _verifyResult;

  @override
  void initState() {
    super.initState();
    getWifiName();
  }

  Future<void> getWifiName() async {
    final wifiName = await NetworkInfo().getWifiName();
    setState(() {
      _wifiName = wifiName;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_verifyResult != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text((_verifyResult!) ? 'Success' : 'Failure')),
        );
        setState(() {
          _verifyResult = null;
        });
      });
    }
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 8.0),
      color: Theme.of(context).colorScheme.surface,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            height: 200,
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'In order to verify the accuracy of your contribution, we '
                    'need to connect to the network manually using the '
                    'information you provided on the previous screen.\n\n'
                    'First, please go to your WiFi settings and select '
                    '"Forget" (not just "Disconnect") for the network '
                    '"${context.read<AddNetworkCubit>().state!.ssid}".\n\n'
                    'Make sure you stay located in range of the network.',
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                ),
              ],
            ),
          ),
          SizedBox(
            height: 50,
            child: ElevatedButton(
              onPressed: () => OpenSettings.openWIFISetting(),
              child: const Text("Open WiFi settings"),
            ),
          ),
          SizedBox(
            height: 50,
            child: TextButton(
              onPressed: () => getWifiName(),
              child: const Text("Refresh"),
            ),
          ),
          Visibility(
            visible: _wifiName == null,
            child: SizedBox(
              height: 50,
              child: ElevatedButton(
                onPressed: () async {
                  final messenger = ScaffoldMessenger.of(context);
                  final result = await AutoConnect.verifyAccessPoint(
                    wifi: WiFi(
                      ssid: context.read<AddNetworkCubit>().state!.ssid,
                      password:
                          context.read<AddNetworkCubit>().state!.password ?? "",
                    ),
                  );
                  if (mounted) {
                    debugPrint(result);
                    messenger.showSnackBar(
                      SnackBar(
                        backgroundColor: Colors.blue,
                        content: Text(result),
                      ),
                    );
                  }
                },
                child: const Text("Continue"),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
