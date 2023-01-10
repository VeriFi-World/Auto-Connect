import 'package:auto_connect_example/network.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class AddNetworkCubit extends Cubit<Network?> {
  AddNetworkCubit() : super(null);

  void addNetwork(String ssid, String? password) {
    emit(Network(ssid: ssid, password: password));
  }

  @override
  void onChange(Change<Network?> change) {
    super.onChange(change);
    debugPrint(change.toString());
  }

  @override
  void onError(Object error, StackTrace stackTrace) {
    debugPrint(error.toString());
    super.onError(error, stackTrace);
  }
}
