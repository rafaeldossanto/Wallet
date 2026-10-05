import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:tray_manager/tray_manager.dart' as tray;
import 'package:win32_registry/win32_registry.dart';
import 'package:window_manager/window_manager.dart';

import '../l10n/l10n.dart';
import 'desktop.dart';
import 'desktop_settings.dart';
import 'github_release_feed.dart';
import 'installer_io.dart';
import 'updater.dart';

/// Passed by the Windows sign-in entry (and by an update that happened in the tray): start in the
/// tray, without a window.
const hiddenArgument = '--hidden';

/// Passed by the installer when it reopens the app after an update, so the app says so.
const updatedArgument = '--updated';

/// Opens the Windows app's window (or keeps it in the tray with [hiddenArgument]), puts the icon
/// in the tray and starts the updater. Null on the phone, where none of this exists.
Future<Desktop?> startDesktop(List<String> args) async {
  if (!Platform.isWindows) {
    return null;
  }
  final settings = await DesktopSettings.load(RegistryStartupLaunch());
  final shell = await _WindowShell.open(settings, hidden: args.contains(hiddenArgument));
  final updater = Updater(
    feed: GitHubReleaseFeed(),
    store: DownloadedInstallers(),
    launcher: const InnoSetupLauncher(),
    quit: shell.quit,
    isAway: () async => !await windowManager.isVisible(),
    justUpdated: args.contains(updatedArgument),
  );
  shell.updater = updater;
  updater.start();
  return Desktop(settings: settings, updater: updater, window: shell);
}

/// The frameless window and the tray icon. The window's red button hides it in the tray while
/// [DesktopSettings.closeToTray] is on; the tray's menu opens it again or quits for good.
class _WindowShell with WindowListener implements WindowControls {
  _WindowShell._(this._settings);

  /// Narrower than a phone, the layouts stop fitting.
  static const _minimumSize = Size(400, 640);
  static const _preferredSize = Size(1280, 800);

  /// windows/runner/window_style.cpp: the blur and the rounded corners.
  static const _style = MethodChannel('wallet/window_style');

  final DesktopSettings _settings;
  final _maximized = ValueNotifier(false);
  Updater? updater;

  // Held for as long as the icon should show: a collected wrapper removes it. The menu stays
  // alive natively once attached; its items are kept for their click listeners.
  tray.TrayIcon? _trayIcon;
  final List<tray.MenuItem> _items = [];

  static Future<_WindowShell> open(DesktopSettings settings, {required bool hidden}) async {
    await windowManager.ensureInitialized();
    final shell = _WindowShell._(settings);
    final options = WindowOptions(
      title: 'Wallet',
      size: await _fittedSize(),
      minimumSize: _minimumSize,
      center: true,
      backgroundColor: const Color(0x00000000),
    );
    // The runner leaves the window hidden: it shows here, already frameless and styled (the theme
    // corrects the brightness on its first frame), or stays in the tray.
    await windowManager.waitUntilReadyToShow(options, () async {
      await windowManager.setAsFrameless();
      await shell.applyStyle(translucent: settings.translucent, dark: true);
      if (!hidden) {
        await windowManager.show();
        await windowManager.focus();
      }
    });
    await windowManager.setPreventClose(true);
    shell._maximized.value = await windowManager.isMaximized();
    windowManager.addListener(shell);
    shell._createTrayIcon();
    return shell;
  }

  /// 1280×800, or 90% of the screen on a smaller one.
  static Future<Size> _fittedSize() async {
    try {
      final screen = (await screenRetriever.getPrimaryDisplay()).visibleSize;
      if (screen == null) {
        return _preferredSize;
      }
      return Size(
        _preferredSize.width.clamp(_minimumSize.width, screen.width * 0.9),
        _preferredSize.height.clamp(_minimumSize.height, screen.height * 0.9),
      );
    } on Exception {
      return _preferredSize;
    }
  }

  void _createTrayIcon() {
    final l10n = lookupAppLocalizations(const Locale('pt'));
    final icon = tray.TrayIcon.create();
    final menu = tray.Menu.create();
    if (icon == null || menu == null) {
      return;
    }
    tray.MenuItem? item(String label, void Function() onClick) {
      final item = tray.MenuItem.createWithLabelAndType(label, tray.MenuItemType.normal);
      item?.addListener((event) {
        if (event is tray.MenuItemClickedEvent) {
          onClick();
        }
      });
      if (item != null) {
        _items.add(item);
      }
      return item;
    }

    menu
      ..addItem(item(l10n.trayOpen, () => unawaited(show())))
      ..addSeparator()
      ..addItem(item(l10n.trayQuit, () => unawaited(quit())));
    icon
      ..icon = tray.ImageAsset.fromAsset('assets/desktop/tray.ico')
      ..setTooltip('Wallet')
      ..setContextMenu(menu)
      ..setContextMenuTrigger(tray.ContextMenuTrigger.rightClicked)
      ..addListener((event) {
        if (event is tray.TrayIconClickedEvent || event is tray.TrayIconDoubleClickedEvent) {
          unawaited(show());
        }
      })
      ..setVisible(true);
    _trayIcon = icon;
  }

  Future<void> show() async {
    if (await windowManager.isMinimized()) {
      await windowManager.restore();
    }
    await windowManager.show();
    await windowManager.focus();
  }

  /// Quits for good, installing a downloaded update on the way out.
  Future<void> quit() async {
    await updater?.installOnQuit();
    _trayIcon?.dispose();
    _trayIcon = null;
    _items.clear();
    await windowManager.setPreventClose(false);
    await windowManager.destroy();
  }

  @override
  ValueListenable<bool> get isMaximized => _maximized;

  @override
  Future<void> minimize() => windowManager.minimize();

  @override
  Future<void> toggleMaximize() async =>
      await windowManager.isMaximized() ? windowManager.unmaximize() : windowManager.maximize();

  /// Goes through the window's close, so [onWindowClose] decides: the tray or out.
  @override
  Future<void> close() => windowManager.close();

  @override
  Future<void> startDragging() => windowManager.startDragging();

  @override
  Future<void> startResizing(WindowEdge edge) => windowManager.startResizing(switch (edge) {
        WindowEdge.top => ResizeEdge.top,
        WindowEdge.bottom => ResizeEdge.bottom,
        WindowEdge.left => ResizeEdge.left,
        WindowEdge.right => ResizeEdge.right,
        WindowEdge.topLeft => ResizeEdge.topLeft,
        WindowEdge.topRight => ResizeEdge.topRight,
        WindowEdge.bottomLeft => ResizeEdge.bottomLeft,
        WindowEdge.bottomRight => ResizeEdge.bottomRight,
      });

  @override
  Future<void> applyStyle({required bool translucent, required bool dark}) async {
    try {
      await _style.invokeMethod<void>('apply', {'translucent': translucent, 'dark': dark});
    } on MissingPluginException {
      // A runner without window_style.cpp: the window stays as it is.
    }
  }

  @override
  void onWindowClose() {
    unawaited(_settings.closeToTray ? windowManager.hide() : quit());
  }

  @override
  void onWindowMaximize() => _maximized.value = true;

  @override
  void onWindowUnmaximize() => _maximized.value = false;
}

/// `HKEY_CURRENT_USER\...\Run\Wallet`: Windows opens the app, in the tray, when the user signs in.
/// The uninstaller removes the value.
class RegistryStartupLaunch implements StartupLaunch {
  static const _runKey = r'Software\Microsoft\Windows\CurrentVersion\Run';
  static const _valueName = 'Wallet';

  @override
  bool get isEnabled {
    try {
      final key = CURRENT_USER.open(_runKey);
      try {
        return key.getString(_valueName) != null;
      } finally {
        key.close();
      }
    } on Exception {
      return false;
    }
  }

  @override
  void setEnabled(bool enabled) {
    final key = CURRENT_USER.create(_runKey);
    try {
      if (enabled) {
        key.setValue(_valueName, StringValue('"${Platform.resolvedExecutable}" $hiddenArgument'));
      } else if (key.getString(_valueName) != null) {
        key.removeValue(_valueName);
      }
    } finally {
      key.close();
    }
  }
}
