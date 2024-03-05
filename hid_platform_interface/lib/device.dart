import 'dart:typed_data';

abstract class Device {
  int vendorId;
  int productId;
  String serialNumber;
  String productName;
  int? usagePage;
  int? usage;
  Device(
      {required this.vendorId,
      required this.productId,
      required this.serialNumber,
      required this.productName,
      this.usagePage,
      this.usage});

  Future<bool> open() {
    throw UnimplementedError();
  }

  Future<void> close() {
    throw UnimplementedError();
  }

  Stream<List<int>> read(int length, int duration) {
    throw UnimplementedError();
  }

  Future<void> write(Uint8List bytes) {
    throw UnimplementedError();
  }

  Future<void> getFeature(Uint8List bytes) {
    throw UnimplementedError();
  }

  Future<void> setFeature(Uint8List bytes) {
    throw UnimplementedError();
  }
}
