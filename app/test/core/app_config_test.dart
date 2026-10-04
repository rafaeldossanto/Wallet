import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/config/app_config.dart';

void main() {
  tearDown(() => debugDefaultTargetPlatformOverride = null);

  test('the PC apps call themselves desktop and the phones mobile, so both get the token in the body', () {
    for (final platform in [TargetPlatform.windows, TargetPlatform.macOS, TargetPlatform.linux]) {
      debugDefaultTargetPlatformOverride = platform;
      expect(AppConfig.client, 'desktop', reason: '$platform');
    }
    for (final platform in [TargetPlatform.android, TargetPlatform.iOS]) {
      debugDefaultTargetPlatformOverride = platform;
      expect(AppConfig.client, 'mobile', reason: '$platform');
    }
  });

  test('only the Windows app is locked with Windows Hello, and it talks to the BFF on localhost', () {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    expect(AppConfig.isWindows, isTrue);
    expect(AppConfig.bffUrl, 'http://localhost:8080');

    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    expect(AppConfig.isWindows, isFalse);
    expect(AppConfig.bffUrl, 'http://10.0.2.2:8080');
  });
}
