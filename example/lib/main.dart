import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_install_apk/flutter_install_apk.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: GestureDetector(
            onTap: () {
              FlutterInstallApk.downLoadApk(
                  apkUrl:
                      "https://bobotu.oss-cn-shanghai.aliyuncs.com/apk/com.hairbobo.apk",
                  appId: "com.ch.install.flutter_install_apk_example",
                  progressListener: (pro) {
                    print('-------111------->$pro');
                  });
            },
            child: Text('DownloadApk'),
          ),
        ),
      ),
    );
  }
}
