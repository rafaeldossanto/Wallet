import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import '../widgets/common.dart';
import 'desktop_settings.dart';
import 'updater.dart';

/// Settings → Computador: closing to the tray, opening with Windows, and the version with its
/// update.
class DesktopSettingsCard extends StatelessWidget {
  const DesktopSettingsCard({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final settings = context.watch<DesktopSettings>();
    final updater = context.watch<Updater>();
    final state = updater.state;
    return SectionCard(
      title: l10n.settingsDesktop,
      flush: true,
      child: Column(
        children: [
          SwitchListTile(
            secondary: const Icon(Icons.close_fullscreen),
            title: Text(l10n.settingsCloseToTray),
            subtitle: Text(l10n.settingsCloseToTrayHelp),
            value: settings.closeToTray,
            onChanged: settings.setCloseToTray,
          ),
          SwitchListTile(
            secondary: const Icon(Icons.power_settings_new),
            title: Text(l10n.settingsLaunchAtStartup),
            subtitle: Text(l10n.settingsLaunchAtStartupHelp),
            value: settings.launchAtStartup,
            onChanged: settings.setLaunchAtStartup,
          ),
          ListTile(
            leading: const Icon(Icons.system_update_alt),
            title: Text(l10n.settingsVersion(updater.currentVersion.isEmpty ? 'dev' : updater.currentVersion)),
            subtitle: Text(switch (state) {
              UpdatesDisabled() => l10n.updateDisabled,
              UpToDate() => l10n.updateUpToDate,
              CheckingForUpdate() => l10n.updateChecking,
              DownloadingUpdate(:final version) => l10n.updateDownloading(version),
              UpdateReady(:final version) => l10n.updateReady(version),
              UpdateFailed() => l10n.updateFailed,
            }),
            trailing: switch (state) {
              UpdateReady() => FilledButton(onPressed: updater.restartToUpdate, child: Text(l10n.updateRestart)),
              UpToDate() || UpdateFailed() => TextButton(onPressed: updater.check, child: Text(l10n.updateCheckNow)),
              UpdatesDisabled() || CheckingForUpdate() || DownloadingUpdate() => null,
            },
          ),
        ],
      ),
    );
  }
}

/// A card in the corner once an update is downloaded, as Discord's: restart now or later. Later
/// keeps it for when the app quits, or for the next time it is in the tray.
class UpdateToast extends StatefulWidget {
  const UpdateToast({super.key});

  @override
  State<UpdateToast> createState() => _UpdateToastState();
}

class _UpdateToastState extends State<UpdateToast> {
  String? _dismissed;

  @override
  Widget build(BuildContext context) {
    final updater = context.watch<Updater?>();
    final state = updater?.state;
    if (updater == null || state is! UpdateReady || state.version == _dismissed) {
      return const SizedBox.shrink();
    }
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return Positioned(
      right: 24,
      bottom: 24,
      child: SizedBox(
        width: 340,
        child: Material(
          elevation: 8,
          color: theme.colorScheme.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 12, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Icon(Icons.system_update_alt, color: theme.colorScheme.primary),
                    const SizedBox(width: 12),
                    Flexible(child: Text(l10n.updateToastTitle, style: theme.textTheme.titleMedium)),
                  ],
                ),
                const SizedBox(height: 8),
                Text(l10n.updateReady(state.version), style: theme.textTheme.bodyMedium),
                const SizedBox(height: 8),
                // The buttons stack when a larger font leaves no room side by side.
                OverflowBar(
                  alignment: MainAxisAlignment.end,
                  overflowAlignment: OverflowBarAlignment.end,
                  spacing: 8,
                  children: [
                    TextButton(
                      onPressed: () => setState(() => _dismissed = state.version),
                      child: Text(l10n.updateLater),
                    ),
                    FilledButton(onPressed: updater.restartToUpdate, child: Text(l10n.updateRestart)),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
