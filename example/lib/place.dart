class Place {
  final String placeId;
  final String name;

  const Place({required this.placeId, required this.name});

  @override
  String toString() => name;
}
