import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../api/json.dart';
import 'updater.dart';

/// The repository's releases on GitHub, where the Desktop workflow publishes each version.
///
/// Its own Dio, never the BFF's: the session token must not leave for another host.
class GitHubReleaseFeed implements ReleaseFeed {
  GitHubReleaseFeed({Dio? dio, this.repository = 'rafaeldossanto/Wallet'})
      : _dio = dio ??
            Dio(BaseOptions(
              baseUrl: 'https://api.github.com',
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 20),
              headers: {'Accept': 'application/vnd.github+json', 'User-Agent': 'Wallet-desktop'},
            ));

  final Dio _dio;
  final String repository;

  /// GitHub's "latest" leaves drafts and pre-releases out. A release without this version's
  /// installer, or without its SHA-256, is not offered: nothing unchecked gets run.
  @override
  Future<Release?> latest() async {
    final Response<Object?> response;
    try {
      response = await _dio.get('/repos/$repository/releases/latest');
    } on DioException catch (error) {
      if (error.response?.statusCode == 404) {
        return null;
      }
      rethrow;
    }
    final release = Json.of(response.data);
    final version = release.string('tag_name').replaceFirst(RegExp('^v'), '');
    final installerName = 'Wallet-Setup-$version.exe';
    for (final asset in release.list('assets')) {
      final digest = asset.stringOrNull('digest');
      if (asset.string('name') == installerName && digest != null && digest.startsWith('sha256:')) {
        return Release(
          version: version,
          installerUrl: Uri.parse(asset.string('browser_download_url')),
          sha256: digest.substring('sha256:'.length).toLowerCase(),
        );
      }
    }
    return null;
  }
}

/// `latest.json`, published by the Desktop workflow next to each installer:
/// `{"version": "0.1.2", "installer": "Wallet-Setup-0.1.2.exe", "sha256": "…"}`.
///
/// Read through the release download link, which always points at the newest release and, unlike
/// api.github.com, is not limited to 60 requests an hour per connection.
class ManifestReleaseFeed implements ReleaseFeed {
  ManifestReleaseFeed({
    Dio? dio,
    this.repository = 'rafaeldossanto/Wallet',
    this.manifest = 'latest.json',
    this.fileField = 'installer',
    String userAgent = 'Wallet-desktop',
  }) : _dio = dio ??
            Dio(BaseOptions(
              connectTimeout: const Duration(seconds: 10),
              receiveTimeout: const Duration(seconds: 20),
              headers: {'User-Agent': userAgent},
            ));

  /// `latest-android.json`, published by the Android workflow next to each APK:
  /// `{"version": "0.1.5", "apk": "Wallet-0.1.5.apk", "sha256": "…"}`. The Android app reads only
  /// this one, never the API.
  ManifestReleaseFeed.android({Dio? dio, String repository = 'rafaeldossanto/Wallet'})
      : this(dio: dio, repository: repository, manifest: 'latest-android.json', fileField: 'apk', userAgent: 'Wallet-android');

  final Dio _dio;
  final String repository;

  /// The manifest's file name in the release.
  final String manifest;

  /// The manifest's field with the installer's file name.
  final String fileField;

  /// Null when the newest release has no manifest (the ones before 0.1.2 for Windows, before 0.1.4
  /// for Android) or there is no release.
  @override
  Future<Release?> latest() async {
    final Response<String> response;
    try {
      response = await _dio.get<String>(
        'https://github.com/$repository/releases/latest/download/$manifest',
        // GitHub serves release files as octet-stream: decoded here, not by Dio.
        options: Options(responseType: ResponseType.plain),
      );
    } on DioException catch (error) {
      if (error.response?.statusCode == 404) {
        return null;
      }
      rethrow;
    }
    // A byte order mark at the start would stop jsonDecode.
    final json = Json.of(jsonDecode(response.data!.replaceFirst(String.fromCharCode(0xFEFF), '')));
    final version = json.stringOrNull('version') ?? '';
    final installer = json.stringOrNull(fileField) ?? '';
    final sha256 = (json.stringOrNull('sha256') ?? '').toLowerCase();
    if (parseVersion(version) == null || installer.isEmpty || !RegExp(r'^[0-9a-f]{64}$').hasMatch(sha256)) {
      return null;
    }
    return Release(
      version: version,
      installerUrl: Uri.parse('https://github.com/$repository/releases/download/v$version/$installer'),
      sha256: sha256,
    );
  }
}

/// The manifest first, the API when the manifest has nothing to say or cannot be read.
class FallbackReleaseFeed implements ReleaseFeed {
  const FallbackReleaseFeed(this._primary, this._fallback);

  final ReleaseFeed _primary;
  final ReleaseFeed _fallback;

  @override
  Future<Release?> latest() async {
    try {
      final release = await _primary.latest();
      if (release != null) {
        return release;
      }
    } on Exception catch (error) {
      debugPrint('[UPDATE] manifest: $error');
    }
    return _fallback.latest();
  }
}
