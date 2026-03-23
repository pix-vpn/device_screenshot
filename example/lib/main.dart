import 'dart:io';
import 'dart:typed_data';

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
  Uint8List? screenshotBytes;
  String? screenshotPath;

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
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (screenshotBytes != null) ...[
                          Container(
                            margin: const EdgeInsets.symmetric(vertical: 20),
                            constraints: const BoxConstraints(maxHeight: 400),
                            child: Image.memory(
                              screenshotBytes!,
                              fit: BoxFit.contain,
                              width: double.infinity,
                            ),
                          ),
                        ],
                        if (screenshotPath != null) ...[
                          Container(
                            margin: const EdgeInsets.symmetric(vertical: 20),
                            constraints: const BoxConstraints(maxHeight: 400),
                            child: Image.file(
                              File(screenshotPath!),
                              fit: BoxFit.contain,
                              width: double.infinity,
                            ),
                          ),
                        ],
                        SelectableText(
                          message,
                          style: Theme.of(context).textTheme.headlineSmall,
                          textAlign: TextAlign.center,
                        ),
                      ],
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
                        screenshotPath = uri.path;
                        screenshotBytes = null;
                      });
                    } else {
                      setState(() {
                        message = 'Screenshot path is: null!';
                        screenshotPath = null;
                      });
                    }
                  },
                  title: 'TAKE SCREENSHOT',
                ),
                ExampleButton(
                  onPressed: () async {
                    final bytes = await DeviceScreenshot.instance
                        .takeScreenshotAsBytes();
                    if (bytes != null) {
                      setState(() {
                        message = 'Screenshot bytes length: ${bytes.length} bytes';
                        screenshotBytes = bytes;
                        screenshotPath = null;
                      });
                    } else {
                      setState(() {
                        message = 'Screenshot bytes is: null!';
                        screenshotBytes = null;
                      });
                    }
                  },
                  title: 'TAKE SCREENSHOT AS BYTES',
                ),
                ExampleButton(
                  onPressed: () async {
                    final bytes = await DeviceScreenshot.instance
                        .takeScreenshotInBackground();
                    if (bytes != null) {
                      setState(() {
                        message = 'Background screenshot bytes length: ${bytes.length} bytes';
                        screenshotBytes = bytes;
                        screenshotPath = null;
                      });
                    } else {
                      setState(() {
                        message = 'Background screenshot failed! Please requestMediaProjection first in foreground.';
                        screenshotBytes = null;
                      });
                    }
                  },
                  title: 'TAKE SCREENSHOT IN BACKGROUND',
                ),
                ExampleButton(
                  onPressed: () async {
                    final bytes = await DeviceScreenshot.instance
                        .takeScreenshotInBackground(scale: 0.5);
                    if (bytes != null) {
                      setState(() {
                        message = 'Background screenshot (scale 0.5) bytes length: ${bytes.length} bytes';
                        screenshotBytes = bytes;
                        screenshotPath = null;
                      });
                    } else {
                      setState(() {
                        message = 'Background screenshot failed! Please requestMediaProjection first in foreground.';
                        screenshotBytes = null;
                      });
                    }
                  },
                  title: 'TAKE SCREENSHOT IN BACKGROUND (SCALE 0.5)',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
