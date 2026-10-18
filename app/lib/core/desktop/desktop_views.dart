import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import '../updates/update_views.dart';
import '../widgets/common.dart';
import 'desktop_settings.dart';

/// Settings → Computador: the window's background, closing to the tray, opening with Windows, and
/// the version with its update. Shown only in the Windows app, where both are provided.
class DesktopSettingsCard extends StatelessWidget {
  const DesktopSettingsCard({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final settings = context.watch<DesktopSettings?>()!;
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
          const UpdateTile(),
        ],
      ),
    );
  }
}
