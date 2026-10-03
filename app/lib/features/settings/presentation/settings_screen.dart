import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/security/app_lock.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/widgets/common.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final session = context.watch<SessionController>();
    final user = session.user;
    final lock = context.watch<AppLock?>();
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
                  child: ListTile(
                    leading: const Icon(Icons.person_outline),
                    title: Text(user?.displayName ?? '—'),
                    subtitle: Text(user?.email ?? ''),
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
                          title: Text(l10n.settingsBiometric),
                          subtitle: Text(lock.isAvailable ? l10n.settingsBiometricHelp : l10n.settingsBiometricUnavailable),
                          value: lock.isAvailable && lock.isEnabled,
                          onChanged: lock.isAvailable ? lock.setEnabled : null,
                        )
                      else
                        ListTile(
                          leading: const Icon(Icons.timer_outlined),
                          title: Text(l10n.settingsWebSession),
                          subtitle: Text(l10n.settingsWebSessionHelp),
                        ),
                      ListTile(
                        leading: const Icon(Icons.logout),
                        title: Text(l10n.settingsSignOut),
                        onTap: () => session.signOut(),
                      ),
                    ],
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
