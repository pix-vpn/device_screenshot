
import 'dart:ui';

import 'device_screenshot_platform_interface.dart';

class DeviceScreenshot {
  DeviceScreenshot._();

  static final DeviceScreenshot instance = DeviceScreenshot._();

  Future<bool> checkMediaProjectionService() {
    return DeviceScreenshotPlatform.instance.checkMediaProjectionService();
  }

  Future<Uri?> takeScreenshot({
    Duration delay = Duration.zero,
  }) {
    return DeviceScreenshotPlatform.instance.takeScreenshot(delay: delay);
  }

  Future<Uri?> take({
    Duration delay = Duration.zero,
    required int width,
    required int height
  }) async {

    return DeviceScreenshotPlatform.instance.take(delay: delay, width: width, height: height);
  }

  void requestMediaProjection() {
    DeviceScreenshotPlatform.instance.requestMediaProjection();
  }

  void stopMediaProjectionService() {
    DeviceScreenshotPlatform.instance.stopMediaProjectionService();
  }
}
