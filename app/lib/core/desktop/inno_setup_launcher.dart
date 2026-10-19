import 'dart:io';

import '../updates/updater.dart';

/// Runs the Inno Setup installer silently. `/RELAUNCH` is read by `windows/installer/wallet.iss`
/// to open the new version (or put it back in the tray) once the files are replaced.
class InnoSetupLauncher implements InstallerLauncher {
  const InnoSetupLauncher();

  @override
  Future<void> launch(String installer, Relaunch relaunch) async {
    await Process.start(
      installer,
      [
        '/VERYSILENT',
        '/SUPPRESSMSGBOXES',
        '/NORESTART',
        // Closes a Wallet still open, but leaves reopening it to /RELAUNCH.
        '/CLOSEAPPLICATIONS',
        '/NORESTARTAPPLICATIONS',
        '/RELAUNCH=${relaunch.name}',
      ],
      mode: ProcessStartMode.detached,
    );
  }

  /// Windows asks nothing before running an installer the app downloaded itself.
  @override
  Future<bool> allowInstalls() async => true;
}
