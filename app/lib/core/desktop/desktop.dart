import 'desktop_settings.dart';
import 'updater.dart';

export 'desktop_start_stub.dart' if (dart.library.io) 'desktop_start_io.dart' show startDesktop;

/// What only the installed Windows app has: its window and tray settings and the updater.
/// `startDesktop(args)` opens the window and the tray and returns it; anywhere else (the phone,
/// the browser) it returns null.
class Desktop {
  const Desktop({required this.settings, required this.updater});

  final DesktopSettings settings;
  final Updater updater;
}
