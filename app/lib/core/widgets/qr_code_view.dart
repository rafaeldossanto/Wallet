import 'package:material_ui/material_ui.dart';
import 'package:qr/qr.dart';

/// A QR code drawn from [data] (the second factor's `otpauth://` URI). Black on white with the
/// four-module quiet zone scanners need, in the dark theme too: cameras read it the same anywhere.
class QrCodeView extends StatelessWidget {
  const QrCodeView({super.key, required this.data, this.size = 200});

  final String data;
  final double size;

  @override
  Widget build(BuildContext context) {
    final image = QrImage(QrCode(payload: QrPayload.fromString(data), errorCorrectLevel: QrErrorCorrectLevel.medium));
    return Semantics(
      image: true,
      label: 'QR code',
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
        child: CustomPaint(painter: _QrPainter(image)),
      ),
    );
  }
}

class _QrPainter extends CustomPainter {
  _QrPainter(this.image);

  static const _quietZone = 4;

  final QrImage image;

  @override
  void paint(Canvas canvas, Size size) {
    final modules = image.moduleCount + 2 * _quietZone;
    final module = size.width / modules;
    final dark = Paint()..color = Colors.black;
    for (var row = 0; row < image.moduleCount; row++) {
      for (var column = 0; column < image.moduleCount; column++) {
        if (image.isDark(row, column)) {
          // A hair wider than the module, so neighbours touch without seams.
          canvas.drawRect(
            Rect.fromLTWH((column + _quietZone) * module, (row + _quietZone) * module, module + 0.5, module + 0.5),
            dark,
          );
        }
      }
    }
  }

  @override
  bool shouldRepaint(_QrPainter oldDelegate) => oldDelegate.image != image;
}
