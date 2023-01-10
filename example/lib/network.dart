import 'package:equatable/equatable.dart';

class Network extends Equatable {
  final String ssid;
  final String? password;

  const Network({required this.ssid, this.password});

  @override
  List<Object?> get props => [ssid, password];

  @override
  String toString() => 'Network: { ssid: "$ssid", password: "$password" }';
}
