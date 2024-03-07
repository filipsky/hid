import 'package:hid_platform_interface/hid_platform_interface.dart';

export 'package:hid_platform_interface/device.dart';

HidPlatform get _platform => HidPlatform.instance;
Future<List<Device>> getDeviceList({int? vendorId, int? productId}) {
  return _platform.getDeviceList(vendorId: vendorId, productId: productId);
}
