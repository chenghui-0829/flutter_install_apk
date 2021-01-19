import 'dart:async';

import 'package:flutter/services.dart';

class FlutterInstallApk {
  static const MethodChannel _channel =
      const MethodChannel('io.flutter.plugins/flutter_install_apk');

  static Future downLoadApk(
      {String apkUrl,
      appId,
      Function(String progress) progressListener}) async {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'progressListener') {
        progressListener(call.arguments);
      }
    });
    await _channel
        .invokeMethod('download_apk', {'apkUrl': apkUrl, 'appId': appId});
  }
}
