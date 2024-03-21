import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:hid_platform_interface/hid_platform_interface.dart';
import 'package:flutter/services.dart';

const MethodChannel _channel = MethodChannel('hid_android');

class HidAndroid extends HidPlatform {
  static void registerWith() {
    HidPlatform.instance = HidAndroid();
  }

  @override
  Future<List<Device>> getDeviceList({int? vendorId, int? productId}) async {
    final List<Device> list = [];
    final List<Object?> devices = await _channel.invokeMethod('getDeviceList', <String, int?>{
      'vendorId': vendorId,
      'productId': productId,
    });
    for (var deviceObject in devices) {
      final rawDevice = deviceObject as String?;
      if (rawDevice != null) {
        final json = jsonDecode(rawDevice);
        final device = UsbDevice(
            vendorId: json['vendorId'],
            productId: json['productId'],
            serialNumber: json['serialNumber'] ?? '',
            productName: json['productName'] ?? '',
            deviceName: json['deviceName'] ?? '');
        list.add(device);
      }
    }
    return list;
  }
}

class UsbDevice extends Device {
  bool isOpen = false;
  String deviceName;
  UsbDevice({required int vendorId, required int productId, required String serialNumber, required String productName, required this.deviceName})
      : super(vendorId: vendorId, productId: productId, serialNumber: serialNumber, productName: productName);

  @override
  Future<bool> open() async {
    final result = await _channel.invokeMethod('open', <String, String>{
      'deviceName': deviceName,
    });
    isOpen = result;
    return result;
  }

  @override
  Stream<List<int>> read(int length, int duration) async* {
    while (isOpen) {
      final start = DateTime.now();
      final List<Object?> array = await _channel.invokeMethod('read', <String, int>{'length': length, 'duration': duration});
      yield array.map((e) => e! as int).toList();
      var t = DateTime.now().difference(start).inMilliseconds;
      t = min(max(0, t), duration);
      await Future.delayed(Duration(milliseconds: t));
    }
  }

  @override
  Future<void> write(Uint8List bytes) async {
    await _channel.invokeMethod('write', <String, Uint8List>{'bytes': bytes});
  }

  @override
  Future<void> setFeature(Uint8List bytes) async {
    await _channel.invokeMethod('setFeature', <String, Uint8List>{'bytes': bytes});
  }

  @override
  Future<void> getFeature(Uint8List bytes) async {
    final List<Object?> array = await _channel.invokeMethod('getFeature', <String, Uint8List>{'bytes': bytes});
    final res = array.map((e) => e! as int).toList();
    final count = min(res.length, bytes.length - 1);
    var pos = 1; // keep the first byte in place
    for (var idx = 0; idx < count; idx++) {
      bytes[pos++] = res[idx];
    }
  }

  @override
  Future<void> close() async {
    isOpen = false;
    await _channel.invokeMethod('close');
  }
}
