import 'dart:io';

import 'package:device_screenshot_example/example_button.dart';
import 'package:flutter/material.dart';

import 'package:device_screenshot/device_screenshot.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String message = "";

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Expanded(
                  child: Center(
                    child: SelectableText(
                      message,
                      style: Theme.of(context).textTheme.headlineSmall,
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
                ExampleButton(
                  onPressed: () async {
                    bool mediaProjectionService = await DeviceScreenshot
                        .instance
                        .checkMediaProjectionService();
                    setState(() {
                      message =
                          'Media projection service status: $mediaProjectionService';
                    });
                  },
                  title: 'Check Media Projection Service',
                ),
                ExampleButton(
                  onPressed: () async {
                    if (!await DeviceScreenshot.instance
                        .checkMediaProjectionService()) {
                      DeviceScreenshot.instance.requestMediaProjection();
                    }
                  },
                  title: 'Request Media Projection Service',
                ),
                ExampleButton(
                  onPressed: () async {
                    if (await DeviceScreenshot.instance
                        .checkMediaProjectionService()) {
                      DeviceScreenshot.instance.stopMediaProjectionService();
                    }
                  },
                  title: 'Stop Media Projection Service',
                ),
                ExampleButton(
                  onPressed: () async {
                    Uri? uri = await DeviceScreenshot.instance
                        .take(width: 1080, height: 2340);
                    if (uri != null) {
                      setState(() {
                        message = uri.path;
                      });
                    } else {
                      setState(() {
                        message = 'Screenshot path is: null!';
                      });
                    }
                  },
                  title: 'TAKE SCREENSHOT',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
