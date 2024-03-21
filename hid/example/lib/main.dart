import 'dart:async';
import 'dart:typed_data';

import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:hid/hid.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  List<Device>? _hidDevices;

  @override
  void initState() {
    super.initState();
    Future.delayed(const Duration(milliseconds: 100), () {
      _listDevices();
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> _listDevices() async {
    setState(() {
      _hidDevices = null;
    });

    final hidDevices = await getDeviceList(vendorId: null, productId: null);
    hidDevices.sort((a, b) => a.usage?.compareTo(b.usage ?? 0) ?? 0);
    hidDevices.sort((a, b) => a.usagePage?.compareTo(b.usagePage ?? 0) ?? 0);
    hidDevices.sort((a, b) => a.productId.compareTo(b.productId));
    hidDevices.sort((a, b) => a.vendorId.compareTo(b.vendorId));
    hidDevices.sort((a, b) => a.productName.compareTo(b.productName));

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    _operateTheRelay();

    setState(() {
      _hidDevices = hidDevices;
    });
  }

  Future<bool> _operateTheRelay() async {
    if (!await _openTheRelay(true, 0)) {
      return false;
    }
    await _openTheRelay(true, 1);

    var states = await _getPortStates();

    await Future.delayed(const Duration(seconds: 2));
    await _openTheRelay(false, 0);
    await _openTheRelay(false, 1);

    states = await _getPortStates();

    return true;
  }

  Future<bool> _openTheRelay(bool switchOn, int portNo) async {
    final relayBoard = (await getDeviceList(vendorId: 5824, productId: 1503)).firstOrNull;

    if (relayBoard == null) {
      return false;
    }

    await relayBoard.open();
    final bytes = Uint8List.fromList([0x00, switchOn ? 0xff : 0xfd, portNo + 1, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);

    await relayBoard.setFeature(bytes);
    await relayBoard.close();
    return true;
  }

  Future<List<bool>> _getPortStates() async {
    final relayBoard = (await getDeviceList(vendorId: 5824, productId: 1503)).firstOrNull;

    final result = <bool>[];

    if (relayBoard == null) {
      return result;
    }

    try {
      final portCount = int.tryParse(relayBoard.productName.replaceAll("USBRelay", "")) ?? 0;

      await relayBoard.open();

      var bytes = Uint8List.fromList([1, 0, 0, 0, 0, 0, 0, 0, 0]);
      await relayBoard.getFeature(bytes);

      for (var portNo = 0; portNo < portCount; portNo++) {
        final state = (bytes[8] & (1 << portNo)) != 0;
        result.add(state);
      }

      return result;
    } finally {
      await relayBoard.close();
    }
  }

  _getUsagePageIcon(int? usagePage, int? usage) {
    switch (usagePage) {
      case 0x01:
        switch (usage) {
          case 0x01:
            return Icons.north_west;
          case 0x02:
            return Icons.mouse;
          case 0x04:
          case 0x05:
            return Icons.gamepad;
          case 0x06:
            return Icons.keyboard;
        }
        return Icons.computer;
      case 0x0b:
        switch (usage) {
          case 0x04:
          case 0x05:
            return Icons.headset_mic;
        }
        return Icons.phone;
      case 0x0c:
        return Icons.toggle_on;
      case 0x0d:
        return Icons.touch_app;
      case 0xf1d0:
        return Icons.security;
    }
    return Icons.usb;
  }

  @override
  Widget build(BuildContext context) {
    final dev = _hidDevices;

    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('HID Plugin example app'),
        ),
        body: dev == null
            ? const Center(child: CircularProgressIndicator())
            : ListView.builder(
                itemCount: dev.length,
                itemBuilder: (context, index) => ListTile(
                  leading: Icon(_getUsagePageIcon(dev[index].usagePage, dev[index].usage)),
                  title: Text(dev[index].productName),
                  subtitle: Text(
                      '${dev[index].vendorId.toRadixString(16).padLeft(4, '0')}:${dev[index].productId.toRadixString(16).padLeft(4, '0')}   ${dev[index].serialNumber}'),
                ),
              ),
        floatingActionButton: FloatingActionButton(
          child: const Icon(Icons.refresh),
          onPressed: dev == null ? null : _listDevices,
        ),
      ),
    );
  }
}
