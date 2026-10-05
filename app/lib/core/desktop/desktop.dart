import 'package:flutter/foundation.dart';

import 'desktop_settings.dart';
import 'updater.dart';

export 'desktop_start_stub.dart' if (dart.library.io) 'desktop_start_io.dart' show startDesktop;

/// What only the installed Windows app has: its window, its settings and the updater.
/// `startDesktop(args)` opens the window and the tray and returns it; anywhere else (the phone,
/// the browser) it returns null.
class Desktop {
  const Desktop({required this.settings, required this.updater, required this.window});

  final DesktopSettings settings;
  final Updater updater;
  final WindowControls window;
}

/// An edge or corner the window is resized from.
enum WindowEdge { top, bottom, left, right, topLeft, topRight, bottomLeft, bottomRight }

/// The frameless window, as the frame drawn in Flutter (window_frame.dart) drives it.
abstract interface class WindowControls {
  ValueListenable<bool> get isMaximized;

  Future<void> minimize();

  Future<void> toggleMaximize();

  /// The red button: to the tray or out, as [DesktopSettings.closeToTray] says.
  Future<void> close();

  Future<void> startDragging();

  Future<void> startResizing(WindowEdge edge);

  /// The blurred see-through background (or a solid one) and the system parts in the theme's
  /// brightness.
  Future<void> applyStyle({required bool translucent, required bool dark});
}
