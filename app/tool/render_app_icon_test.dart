// Renders the app icon (stacked cards: the banks and cards gathered in one place, picked by
// Rafael on 2026-10-12) into every icon file of the web, Android and iOS builds, keeping each
// file's current pixel size, and into the Windows .ico (the executable's and the tray's).
//
// Regenerate, from app/:
//   flutter test tool/render_app_icon_test.dart
//
// It lives outside test/ so the normal `flutter test` does not run it.

import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';

const _ink = ui.Color(0xFF0B0B0C);
const _lime = ui.Color(0xFFD4F34A);
const _blue = ui.Color(0xFF3D5AFE);
const _white = ui.Color(0xFFFFFFFF);

/// How a place shows the icon: how large the cards are drawn, and whether the icon brings its own
/// rounded tile (transparent outside it) or fills the square for the system to mask.
class _Look {
  const _Look({this.scale = 1, this.rounded = false});

  /// 1 is the drawing as chosen; the cards then reach 37% of the side from the centre.
  final double scale;
  final bool rounded;
}

/// iOS, Android and the web app icons: a full square, rounded by the system.
const _square = _Look();

/// Android's and the web's maskable icons are cut to a circle of 40% radius.
const _maskable = _Look(scale: 0.9);

/// Windows from 40 px up (Start menu, Explorer): a rounded tile, as a macOS app's.
const _windows = _Look(rounded: true);

/// The title bar, the taskbar, the tray and the browser tab draw 16 to 32 px: larger cards, so
/// the three stay apart.
const _small = _Look(scale: 1.18, rounded: true);

const _favicon = 'web/favicon.png';
const _windowsIcon = 'windows/runner/resources/app_icon.ico';

/// The same icon in the Windows tray, loaded as an asset by the app.
const _trayIcon = 'assets/desktop/tray.ico';

/// What Windows picks from for the title bar, taskbar, Start menu and Explorer, at every scaling.
const _windowsIconSizes = [16, 20, 24, 32, 40, 48, 64, 256];

/// Each icon is drawn this many pixels wide (at least) and then averaged down, so the edges stay
/// smooth at every size instead of whatever the rasterizer snaps to.
const _supersampledWidth = 1024;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('renders the app icon into every platform icon file', () async {
    await File(_favicon).writeAsBytes(await _render(_pixelSize(File(_favicon)), _small, withAlpha: true));
    for (final file in _squareIconFiles()) {
      await file.writeAsBytes(await _render(_pixelSize(file), file.path.contains('maskable') ? _maskable : _square));
    }
    final ico = await _ico(_windowsIconSizes);
    await File(_windowsIcon).writeAsBytes(ico);
    await (File(_trayIcon)..parent.createSync(recursive: true)).writeAsBytes(ico);
  });
}

/// The ones the system masks; iOS also refuses icons with an alpha channel.
List<File> _squareIconFiles() => [
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

/// The chosen drawing, on a 100×100 grid: three cards fanned out (lime at the back, blue, then
/// white with a chip in front) over the near-black of the app.
void _paintIcon(ui.Canvas canvas, _Look look) {
  if (look.rounded) {
    canvas.drawRRect(
      ui.RRect.fromRectAndRadius(const ui.Rect.fromLTWH(0, 0, 100, 100), const ui.Radius.circular(22)),
      ui.Paint()..color = _ink,
    );
  } else {
    canvas.drawRect(const ui.Rect.fromLTWH(0, 0, 100, 100), ui.Paint()..color = _ink);
  }
  canvas
    ..save()
    ..translate(50, 50)
    ..scale(look.scale)
    ..translate(-50, -50);
  void card(ui.Rect rect, double radius, double degrees, ui.Offset pivot, ui.Color color) {
    canvas
      ..save()
      ..translate(pivot.dx, pivot.dy)
      ..rotate(degrees * math.pi / 180)
      ..translate(-pivot.dx, -pivot.dy)
      ..drawRRect(ui.RRect.fromRectAndRadius(rect, ui.Radius.circular(radius)), ui.Paint()..color = color)
      ..restore();
  }

  card(const ui.Rect.fromLTWH(24, 30, 54, 34), 7, -16, const ui.Offset(51, 47), _lime);
  card(const ui.Rect.fromLTWH(24, 35, 54, 34), 7, -6, const ui.Offset(51, 52), _blue);
  card(const ui.Rect.fromLTWH(23, 40, 54, 34), 7, 4, const ui.Offset(50, 57), _white);
  card(const ui.Rect.fromLTWH(31, 50, 11, 8), 2, 4, const ui.Offset(50, 57), _ink);
  canvas.restore();
}

Future<Uint8List> _render(int size, _Look look, {bool withAlpha = false}) async {
  final scale = math.max(2, (_supersampledWidth / size).ceil());
  final width = size * scale;

  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder)..scale(width / 100);
  _paintIcon(canvas, look);

  final image = await recorder.endRecording().toImage(width, width);
  final rgba = (await image.toByteData(format: ui.ImageByteFormat.rawStraightRgba))!.buffer.asUint8List();
  image.dispose();
  return _png(size, _downsample(rgba, width, scale), withAlpha: withAlpha);
}

final _toLinear = [
  for (var value = 0; value < 256; value++)
    value / 255 <= 0.04045 ? value / 255 / 12.92 : math.pow((value / 255 + 0.055) / 1.055, 2.4).toDouble(),
];

double _toSrgb(double linear) =>
    linear <= 0.0031308 ? linear * 12.92 : 1.055 * math.pow(linear, 1 / 2.4) - 0.055;

/// Averages each scale×scale block in linear light, weighted by alpha, so colour edges and the
/// rounded corners blend the way the eye expects instead of darkening.
Uint8List _downsample(Uint8List rgba, int width, int scale) {
  final size = width ~/ scale;
  final samples = scale * scale;
  final out = Uint8List(size * size * 4);
  for (var y = 0; y < size; y++) {
    for (var x = 0; x < size; x++) {
      var red = 0.0, green = 0.0, blue = 0.0, alpha = 0.0;
      for (var dy = 0; dy < scale; dy++) {
        final row = (y * scale + dy) * width;
        for (var dx = 0; dx < scale; dx++) {
          final pixel = (row + x * scale + dx) * 4;
          final weight = rgba[pixel + 3] / 255;
          red += _toLinear[rgba[pixel]] * weight;
          green += _toLinear[rgba[pixel + 1]] * weight;
          blue += _toLinear[rgba[pixel + 2]] * weight;
          alpha += weight;
        }
      }
      final target = (y * size + x) * 4;
      if (alpha > 0) {
        out[target] = (_toSrgb(red / alpha) * 255).round();
        out[target + 1] = (_toSrgb(green / alpha) * 255).round();
        out[target + 2] = (_toSrgb(blue / alpha) * 255).round();
      }
      out[target + 3] = (alpha / samples * 255).round();
    }
  }
  return out;
}

/// An 8-bit PNG. RGB with no alpha channel by default, since iOS rejects icons that carry
/// transparency; RGBA where the icon has its own rounded tile (Windows, the browser tab).
Uint8List _png(int size, Uint8List rgba, {required bool withAlpha}) {
  final scanlines = BytesBuilder();
  for (var y = 0; y < size; y++) {
    scanlines.addByte(0); // filter: none
    for (var x = 0; x < size; x++) {
      final pixel = (y * size + x) * 4;
      scanlines
        ..addByte(rgba[pixel])
        ..addByte(rgba[pixel + 1])
        ..addByte(rgba[pixel + 2]);
      if (withAlpha) {
        scanlines.addByte(rgba[pixel + 3]);
      }
    }
  }
  final header = ByteData(13)
    ..setUint32(0, size)
    ..setUint32(4, size)
    ..setUint8(8, 8) // bit depth
    ..setUint8(9, withAlpha ? 6 : 2); // color type: RGBA or RGB
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

/// A Windows .ico with one 32-bit PNG image per size, which Windows reads since Vista.
Future<Uint8List> _ico(List<int> sizes) async {
  final images = [
    for (final size in sizes) await _render(size, size <= 32 ? _small : _windows, withAlpha: true),
  ];
  final directory = ByteData(6 + 16 * sizes.length)
    ..setUint16(0, 0, Endian.little) // reserved
    ..setUint16(2, 1, Endian.little) // type: icon
    ..setUint16(4, sizes.length, Endian.little);
  var offset = directory.lengthInBytes;
  for (var index = 0; index < sizes.length; index++) {
    final entry = 6 + 16 * index;
    directory
      ..setUint8(entry, sizes[index] % 256) // width; 0 means 256
      ..setUint8(entry + 1, sizes[index] % 256) // height
      ..setUint16(entry + 4, 1, Endian.little) // colour planes
      ..setUint16(entry + 6, 32, Endian.little) // bits per pixel
      ..setUint32(entry + 8, images[index].length, Endian.little)
      ..setUint32(entry + 12, offset, Endian.little);
    offset += images[index].length;
  }
  final ico = BytesBuilder()..add(directory.buffer.asUint8List());
  for (final image in images) {
    ico.add(image);
  }
  return ico.takeBytes();
}
