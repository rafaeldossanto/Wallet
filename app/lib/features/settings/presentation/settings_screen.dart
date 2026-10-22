import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/config/app_config.dart';
import '../../../core/desktop/desktop_settings.dart';
import '../../../core/desktop/desktop_views.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/security/app_lock.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/theme/theme_controller.dart';
import '../../../core/updates/update_views.dart';
import '../../../core/updates/updater.dart';
import '../../../core/widgets/common.dart';
import 'delete_account_dialog.dart';
import 'profile_dialogs.dart';
import 'two_factor_dialogs.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final session = context.watch<SessionController>();
    final user = session.user;
    final lock = context.watch<AppLock?>();
    final isDesktop = context.watch<DesktopSettings?>() != null;
    // Android: the updater without the window's settings.
    final updates = context.watch<Updater?>() != null;
    final danger = Theme.of(context).colorScheme.error;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navSettings)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ContentWidth(
            maxWidth: 700,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SectionCard(
                  title: l10n.settingsAccount,
                  flush: true,
                  child: Column(
                    children: [
                      ListTile(
                        leading: const Icon(Icons.person_outline),
                        title: Text(user?.displayName ?? '—'),
                        subtitle: Text(l10n.settingsEditName),
                        trailing: const Icon(Icons.edit_outlined),
                        onTap: user == null ? null : () => showRenameDialog(context, user.displayName),
                      ),
                      ListTile(
                        leading: const Icon(Icons.alternate_email),
                        title: Text(user?.email ?? ''),
                        subtitle: Text(switch (user?.pendingEmail) {
                          final pending? => l10n.settingsPendingEmail(pending),
                          null => l10n.settingsChangeEmail,
                        }),
                        trailing: const Icon(Icons.edit_outlined),
                        onTap: user == null ? null : () => showChangeEmailDialog(context),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SectionCard(
                  title: l10n.settingsAppearance,
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: SegmentedButton<ThemeMode>(
                      showSelectedIcon: false,
                      segments: [
                        ButtonSegment(value: ThemeMode.light, icon: const Icon(Icons.light_mode_outlined), label: Text(l10n.themeLight)),
                        ButtonSegment(value: ThemeMode.dark, icon: const Icon(Icons.dark_mode_outlined), label: Text(l10n.themeDark)),
                        ButtonSegment(
                            value: ThemeMode.system, icon: const Icon(Icons.brightness_auto_outlined), label: Text(l10n.themeSystem)),
                      ],
                      selected: {context.watch<ThemeController>().mode},
                      onSelectionChanged: (selection) => context.read<ThemeController>().setMode(selection.first),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                SectionCard(
                  title: l10n.settingsSecurity,
                  flush: true,
                  child: Column(
                    children: [
                      if (lock != null)
                        SwitchListTile(
                          secondary: const Icon(Icons.fingerprint),
                          title: Text(AppConfig.isWindows ? l10n.settingsWindowsHello : l10n.settingsBiometric),
                          subtitle: Text(switch ((AppConfig.isWindows, lock.isAvailable)) {
                            (true, true) => l10n.settingsWindowsHelloHelp,
                            (true, false) => l10n.settingsWindowsHelloUnavailable,
                            (false, true) => l10n.settingsBiometricHelp,
                            (false, false) => l10n.settingsBiometricUnavailable,
                          }),
                          value: lock.isAvailable && lock.isEnabled,
                          onChanged: lock.isAvailable ? lock.setEnabled : null,
                        )
                      else
                        ListTile(
                          leading: const Icon(Icons.timer_outlined),
                          title: Text(l10n.settingsWebSession),
                          subtitle: Text(l10n.settingsWebSessionHelp),
                        ),
                      SwitchListTile(
                        secondary: const Icon(Icons.verified_user_outlined),
                        title: Text(l10n.settingsTwoFactor),
                        subtitle: Text(user?.twoFactorEnabled == true ? l10n.settingsTwoFactorOn : l10n.settingsTwoFactorOff),
                        value: user?.twoFactorEnabled == true,
                        onChanged: user == null
                            ? null
                            : (on) => on ? showEnableTwoFactorDialog(context) : showDisableTwoFactorDialog(context),
                      ),
                      ListTile(
                        leading: const Icon(Icons.logout),
                        title: Text(l10n.settingsSignOut),
                        onTap: () => session.signOut(),
                      ),
                    ],
                  ),
                ),
                if (isDesktop) ...[
                  const SizedBox(height: 16),
                  const DesktopSettingsCard(),
                ] else if (updates) ...[
                  const SizedBox(height: 16),
                  SectionCard(title: l10n.settingsUpdates, flush: true, child: const UpdateTile()),
                ],
                const SizedBox(height: 16),
                SectionCard(
                  title: l10n.settingsDangerZone,
                  accent: danger,
                  flush: true,
                  child: ListTile(
                    leading: Icon(Icons.delete_forever_outlined, color: danger),
                    title: Text(l10n.settingsDeleteAccount, style: TextStyle(color: danger)),
                    subtitle: Text(l10n.settingsDeleteAccountHelp),
                    onTap: () => showDeleteAccountDialog(context),
                  ),
                ),
                const SizedBox(height: 16),
                Text(l10n.settingsAbout,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
