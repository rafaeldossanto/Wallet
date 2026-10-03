// Renders the app icon (the login screen's wallet, white on black and tilted) into every
// icon file of the web, Android and iOS builds, keeping each file's current pixel size.
//
// Regenerate, from app/:
//   flutter test tool/render_app_icon_test.dart
//
// It lives outside test/ so the normal `flutter test` does not run it.

import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';

const _glyph = Icons.account_balance_wallet_outlined;
const _tilt = -20 * math.pi / 180;

/// Glyph size as a share of the canvas: at 0.6 the tilted wallet spans about 60% of it.
const _regularShare = 0.6;

/// Keeps the tilted wallet inside the maskable safe zone, a circle of 40% radius.
const _maskableShare = 0.5;

/// The browser tab draws the favicon at 16 px, so the wallet fills it almost edge to edge.
const _faviconShare = 0.9;

/// Each icon is drawn this many pixels wide (at least) and then averaged down, so the
/// small sizes keep the stroke weight instead of whatever the rasterizer snaps to.
const _supersampledWidth = 1024;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('renders the app icon into every platform icon file', () async {
    final font = await _materialIconsFont().readAsBytes();
    await (FontLoader(_glyph.fontFamily!)..addFont(Future.value(ByteData.sublistView(font)))).load();

    for (final file in _iconFiles()) {
      await file.writeAsBytes(await _render(_pixelSize(file), _shareFor(file)));
    }
  });
}

double _shareFor(File icon) {
  if (icon.path == _favicon) return _faviconShare;
  if (icon.path.contains('maskable')) return _maskableShare;
  return _regularShare;
}

File _materialIconsFont() {
  final flutterRoot = Platform.environment['FLUTTER_ROOT'];
  final artifacts = flutterRoot != null
      ? Directory('$flutterRoot/bin/cache/artifacts')
      // flutter_tester runs from <flutter>/bin/cache/artifacts/engine/<platform>/.
      : File(Platform.resolvedExecutable).parent.parent.parent;
  return File('${artifacts.path}/material_fonts/materialicons-regular.otf');
}

const _favicon = 'web/favicon.png';

List<File> _iconFiles() => [
      File(_favicon),
      ..._pngsUnder('web/icons'),
      ..._pngsUnder('android/app/src/main/res', named: 'ic_launcher.png'),
      ..._pngsUnder('ios/Runner/Assets.xcassets/AppIcon.appiconset'),
    ];

List<File> _pngsUnder(String directory, {String? named}) => Directory(directory)
    .listSync(recursive: true)
    .whereType<File>()
    .where((file) => named == null ? file.path.endsWith('.png') : file.uri.pathSegments.last == named)
    .toList()
  ..sort((a, b) => a.path.compareTo(b.path));

/// The width in the PNG header; every icon is square.
int _pixelSize(File png) {
  final header = ByteData.sublistView(png.readAsBytesSync(), 16, 24);
  final width = header.getUint32(0);
  if (width != header.getUint32(4)) throw StateError('${png.path} is not square');
  return width;
}

Future<Uint8List> _render(int size, double share) async {
  final scale = math.max(2, (_supersampledWidth / size).ceil());
  final width = size * scale;

  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder)..drawColor(const Color(0xFF000000), BlendMode.src);
  final glyph = TextPainter(
    text: TextSpan(
      text: String.fromCharCode(_glyph.codePoint),
      style: TextStyle(fontFamily: _glyph.fontFamily, fontSize: width * share, height: 1, color: const Color(0xFFFFFFFF)),
    ),
    textDirection: TextDirection.ltr,
  )..layout();
  canvas
    ..translate(width / 2, width / 2)
    ..rotate(_tilt);
  glyph.paint(canvas, Offset(-glyph.width / 2, -glyph.height / 2));

  final image = await recorder.endRecording().toImage(width, width);
  final rgba = (await image.toByteData(format: ui.ImageByteFormat.rawRgba))!.buffer.asUint8List();
  image.dispose();
  return _opaquePng(size, _downsample(rgba, width, scale));
}

/// Averages each scale×scale block of the red channel, read as glyph coverage, and encodes
/// the result as sRGB: blending white on black in linear light keeps thin strokes bright.
Uint8List _downsample(Uint8List rgba, int width, int scale) {
  final size = width ~/ scale;
  final gray = Uint8List(size * size);
  for (var y = 0; y < size; y++) {
    for (var x = 0; x < size; x++) {
      var coverage = 0;
      for (var dy = 0; dy < scale; dy++) {
        final row = (y * scale + dy) * width;
        for (var dx = 0; dx < scale; dx++) {
          coverage += rgba[(row + x * scale + dx) * 4];
        }
      }
      gray[y * size + x] = (_srgb(coverage / (scale * scale * 255)) * 255).round();
    }
  }
  return gray;
}

double _srgb(double linear) =>
    linear <= 0.0031308 ? linear * 12.92 : 1.055 * math.pow(linear, 1 / 2.4) - 0.055;

/// An 8-bit RGB PNG with no alpha channel: iOS rejects icons that carry transparency.
Uint8List _opaquePng(int size, Uint8List gray) {
  final scanlines = BytesBuilder();
  for (var y = 0; y < size; y++) {
    scanlines.addByte(0); // filter: none
    for (var x = 0; x < size; x++) {
      final value = gray[y * size + x];
      scanlines
        ..addByte(value)
        ..addByte(value)
        ..addByte(value);
    }
  }
  final header = ByteData(13)
    ..setUint32(0, size)
    ..setUint32(4, size)
    ..setUint8(8, 8) // bit depth
    ..setUint8(9, 2); // color type: RGB
  return (BytesBuilder()
        ..add(const [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
        ..add(_chunk('IHDR', header.buffer.asUint8List()))
        ..add(_chunk('IDAT', ZLibCodec(level: ZLibOption.maxLevel).encode(scanlines.takeBytes())))
        ..add(_chunk('IEND', const [])))
      .takeBytes();
}

Uint8List _chunk(String type, List<int> data) {
  final body = [...type.codeUnits, ...data];
  return (BytesBuilder()
        ..add(_uint32(data.length))
        ..add(body)
        ..add(_uint32(_crc32(body))))
      .takeBytes();
}

Uint8List _uint32(int value) => (ByteData(4)..setUint32(0, value)).buffer.asUint8List();

int _crc32(List<int> bytes) {
  var crc = 0xFFFFFFFF;
  for (final byte in bytes) {
    crc ^= byte;
    for (var bit = 0; bit < 8; bit++) {
      crc = crc & 1 == 1 ? (crc >> 1) ^ 0xEDB88320 : crc >> 1;
    }
  }
  return crc ^ 0xFFFFFFFF;
}
