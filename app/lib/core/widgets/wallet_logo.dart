import 'dart:math' as math;

import 'package:flutter/widgets.dart';

const _ink = Color(0xFF0B0B0C);
const _lime = Color(0xFFD4F34A);
const _blue = Color(0xFF3D5AFE);
const _white = Color(0xFFFFFFFF);

/// The app's icon (stacked cards, chosen by Rafael on 2026-10-12) inside the app: the sign-in
/// screens and the rail. Drawn, not an image, so it is sharp at any size and matches the icon
/// files exactly: `tool/render_app_icon_test.dart` paints them with [paintWalletIcon] too.
class WalletLogo extends StatelessWidget {
  const WalletLogo({super.key, this.size = 48});

  final double size;

  @override
  Widget build(BuildContext context) => Semantics(
        image: true,
        label: 'Wallet',
        child: SizedBox.square(dimension: size, child: const CustomPaint(painter: WalletIconPainter())),
      );
}

class WalletIconPainter extends CustomPainter {
  const WalletIconPainter();

  @override
  void paint(Canvas canvas, Size size) {
    canvas
      ..save()
      ..scale(size.width / 100, size.height / 100);
    // Small on screen, like the title bar and the tray: the larger cards keep the three apart.
    paintWalletIcon(canvas, scale: size.width < 40 ? 1.18 : 1, rounded: true);
    canvas.restore();
  }

  @override
  bool shouldRepaint(WalletIconPainter oldDelegate) => false;
}

/// The chosen drawing, on a 100×100 grid: three cards fanned out (lime at the back, blue, then
/// white with a chip in front) over the near-black of the app. [rounded] gives it its own rounded
/// tile, transparent outside; otherwise it fills the square for the system to mask. [scale] grows
/// the cards from the centre.
void paintWalletIcon(Canvas canvas, {double scale = 1, bool rounded = false}) {
  if (rounded) {
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(0, 0, 100, 100), const Radius.circular(22)),
      Paint()..color = _ink,
    );
  } else {
    canvas.drawRect(const Rect.fromLTWH(0, 0, 100, 100), Paint()..color = _ink);
  }
  canvas
    ..save()
    ..translate(50, 50)
    ..scale(scale)
    ..translate(-50, -50);
  void card(Rect rect, double radius, double degrees, Offset pivot, Color color) {
    canvas
      ..save()
      ..translate(pivot.dx, pivot.dy)
      ..rotate(degrees * math.pi / 180)
      ..translate(-pivot.dx, -pivot.dy)
      ..drawRRect(RRect.fromRectAndRadius(rect, Radius.circular(radius)), Paint()..color = color)
      ..restore();
  }

  card(const Rect.fromLTWH(24, 30, 54, 34), 7, -16, const Offset(51, 47), _lime);
  card(const Rect.fromLTWH(24, 35, 54, 34), 7, -6, const Offset(51, 52), _blue);
  card(const Rect.fromLTWH(23, 40, 54, 34), 7, 4, const Offset(50, 57), _white);
  card(const Rect.fromLTWH(31, 50, 11, 8), 2, 4, const Offset(50, 57), _ink);
  canvas.restore();
}
