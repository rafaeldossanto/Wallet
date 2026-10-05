import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import '../widgets/common.dart';
import 'desktop_settings.dart';
import 'updater.dart';

/// Settings → Computador: the window's background, closing to the tray, opening with Windows, and
/// the version with its update. Shown only in the Windows app, where both are provided.
class DesktopSettingsCard extends StatelessWidget {
  const DesktopSettingsCard({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final settings = context.watch<DesktopSettings?>()!;
    final updater = context.watch<Updater?>()!;
    final state = updater.state;
    return SectionCard(
      title: l10n.settingsDesktop,
      flush: true,
      child: Column(
        children: [
          ListTile(
            leading: const Icon(Icons.blur_on),
            title: Text(l10n.settingsWindowBackground),
            subtitle: Text(l10n.settingsWindowBackgroundHelp),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: SegmentedButton<bool>(
              showSelectedIcon: false,
              segments: [
                ButtonSegment(value: true, label: Text(l10n.settingsWindowTranslucent)),
                ButtonSegment(value: false, label: Text(l10n.settingsWindowSolid)),
              ],
              selected: {settings.translucent},
              onSelectionChanged: (selected) => settings.setTranslucent(selected.single),
            ),
          ),
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

/// The notice in the corner, as the Claude desktop app does it: a new version is downloaded and
/// one click installs it and reopens the app (nothing to fetch by hand), or the app was just
/// updated. "Depois" keeps the update for when the app quits, or for the next time it is in the
/// tray.
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
    if (updater == null) {
      return const SizedBox.shrink();
    }
    final l10n = context.l10n;
    final state = updater.state;
    final _Notice? notice = switch ((state, updater.updatedTo)) {
      (UpdateReady(:final version), _) when version != _dismissed => _Notice(
          key: ValueKey('ready-$version'),
          icon: Icons.system_update_alt,
          title: l10n.updateToastTitle,
          message: l10n.updateToastMessage(version),
          onClose: () => setState(() => _dismissed = version),
          actions: [
            TextButton(onPressed: () => setState(() => _dismissed = version), child: Text(l10n.updateLater)),
            FilledButton(onPressed: updater.restartToUpdate, child: Text(l10n.updateRestartShort)),
          ],
        ),
      (_, final String version) => _Notice(
          key: ValueKey('updated-$version'),
          icon: Icons.check_circle_outline,
          title: l10n.updatedToastTitle,
          message: l10n.updatedToastMessage(version),
          onClose: updater.dismissUpdated,
          actions: [FilledButton(onPressed: updater.dismissUpdated, child: Text(l10n.updatedToastOk))],
        ),
      _ => null,
    };
    return Positioned(
      right: 20,
      bottom: 20,
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 220),
        transitionBuilder: (child, animation) => FadeTransition(
          opacity: animation,
          child: SlideTransition(
            position: Tween(begin: const Offset(0, 0.25), end: Offset.zero).animate(animation),
            child: child,
          ),
        ),
        child: notice ?? const SizedBox.shrink(key: ValueKey('none')),
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    required this.onClose,
    required this.actions,
  });

  final IconData icon;
  final String title;
  final String message;
  final VoidCallback onClose;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return SizedBox(
      width: 320,
      child: Material(
        elevation: 10,
        shadowColor: Colors.black54,
        color: scheme.surfaceContainerHigh,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: scheme.outlineVariant),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 8, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Icon(icon, size: 20, color: scheme.primary),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: theme.textTheme.titleSmall),
                        const SizedBox(height: 4),
                        Text(message, style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: onClose,
                    icon: const Icon(Icons.close, size: 18),
                    visualDensity: VisualDensity.compact,
                    color: scheme.onSurfaceVariant,
                  ),
                ],
              ),
              const SizedBox(height: 8),
              // The buttons stack when a larger font leaves no room side by side.
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: OverflowBar(
                  alignment: MainAxisAlignment.end,
                  overflowAlignment: OverflowBarAlignment.end,
                  spacing: 8,
                  children: actions,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
