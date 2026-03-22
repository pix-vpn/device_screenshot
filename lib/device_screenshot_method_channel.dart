import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'device_screenshot_platform_interface.dart';

/// An implementation of [DeviceScreenshotPlatform] that uses method channels.
class MethodChannelDeviceScreenshot extends DeviceScreenshotPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('device_screenshot');

  @override
  Future<bool> checkMediaProjectionService() async {
    final permission = await methodChannel.invokeMethod<bool>('checkMediaProjectionService');
    return permission ?? false;
  }

  @override
  Future<Uri?> takeScreenshot({
    Duration delay = Duration.zero,
  }) async {
    await Future.delayed(delay);
    final uriPath = await methodChannel.invokeMethod<String>('takeScreenshot');
    Uri uri = Uri.file(uriPath ?? '');
    return uriPath == null ? null : uri;
  }

  @override
  Future<Uri?> take({
    Duration delay = Duration.zero,
    required int width,
    required int height
  }) async {
    await Future.delayed(delay);
    final uriPath = await methodChannel.invokeMethod<String>('take',{"width":width,"height":height});
    Uri uri = Uri.file(uriPath ?? '');
    return uriPath == null ? null : uri;
  }

  @override
  void requestMediaProjection() async {
    await methodChannel.invokeMethod('requestMediaProjection');
  }

  @override
  void stopMediaProjectionService() async {
    await methodChannel.invokeMethod('stopMediaProjectionService');
  }

  @override
  Future<Uint8List?> takeScreenshotAsBytes({
    Duration delay = Duration.zero,
  }) async {
    await Future.delayed(delay);
    final bytes = await methodChannel.invokeMethod<Uint8List>('takeScreenshotAsBytes');
    return bytes;
  }

  @override
  Future<Uint8List?> takeScreenshotInBackground() async {
    final bytes = await methodChannel.invokeMethod<Uint8List>('takeScreenshotInBackground');
    return bytes;
  }
}
