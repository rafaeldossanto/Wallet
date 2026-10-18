import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import '../router/adaptive_shell.dart';
import 'updater.dart';

/// The version and its update, with the button the state calls for: in Ajustes → Computador on
/// Windows, Ajustes → Atualizações on Android.
class UpdateTile extends StatelessWidget {
  const UpdateTile({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final updater = context.watch<Updater?>()!;
    final state = updater.state;
    final confirm = updater.flow == InstallFlow.confirm;
    final Widget? action = switch (state) {
      UpdateReady(problem: InstallProblem.notAllowed) =>
        FilledButton(onPressed: updater.allowInstalls, child: Text(l10n.updateAllowOpen)),
      // Only uninstalling helps; the message says so.
      UpdateReady(problem: InstallProblem.conflict) => null,
      UpdateReady(problem: InstallProblem.failed) =>
        FilledButton(onPressed: updater.restartToUpdate, child: Text(l10n.updateRetry)),
      UpdateReady(problem: null) => FilledButton(
          onPressed: updater.restartToUpdate,
          child: Text(confirm ? l10n.updateInstall : l10n.updateRestart),
        ),
      UpToDate() || UpdateFailed() => TextButton(onPressed: updater.check, child: Text(l10n.updateCheckNow)),
      UpdatesDisabled() || CheckingForUpdate() || DownloadingUpdate() => null,
    };
    return LayoutBuilder(builder: (context, constraints) {
      // On a phone, a button beside the text would leave it a narrow column: it goes under.
      final below = action != null && constraints.maxWidth < 480;
      final tile = ListTile(
        leading: const Icon(Icons.system_update_alt),
        title: Text(l10n.settingsVersion(updater.currentVersion.isEmpty ? 'dev' : updater.currentVersion)),
        subtitle: Text(switch (state) {
          UpdatesDisabled() => l10n.updateDisabled,
          UpToDate() => l10n.updateUpToDate,
          CheckingForUpdate() => l10n.updateChecking,
          DownloadingUpdate(:final version) => l10n.updateDownloading(version),
          UpdateReady(:final version, problem: InstallProblem.notAllowed) => l10n.updateNotAllowed(version),
          UpdateReady(:final version, problem: InstallProblem.conflict) => l10n.updateConflict(version),
          UpdateReady(:final version, problem: InstallProblem.failed) => l10n.updateInstallFailed(version),
          UpdateReady(:final version, problem: null) => l10n.updateReady(version),
          UpdateFailed() => l10n.updateFailed,
        }),
        trailing: below ? null : action,
      );
      if (!below) {
        return tile;
      }
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          tile,
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Align(alignment: AlignmentDirectional.centerEnd, child: action),
          ),
        ],
      );
    });
  }
}

/// The notice in the corner, as the Claude desktop app does it: a new version is downloaded and
/// one tap installs it (nothing to fetch by hand), or the app was just updated. On Windows the
/// button restarts into the new version, and "Depois" keeps the update for when the app quits or
/// is next in the tray. On Android the button opens the system's confirmation; the first time it
/// first explains the setting that lets Wallet install apps.
class UpdateToast extends StatefulWidget {
  const UpdateToast({super.key});

  @override
  State<UpdateToast> createState() => _UpdateToastState();
}

class _UpdateToastState extends State<UpdateToast> {
  /// The version and problem whose notice the user closed; a new one shows again.
  String? _dismissed;

  @override
  Widget build(BuildContext context) {
    final updater = context.watch<Updater?>();
    if (updater == null) {
      return const SizedBox.shrink();
    }
    final l10n = context.l10n;
    final state = updater.state;
    final confirm = updater.flow == InstallFlow.confirm;
    final shown = switch (state) {
      UpdateReady(:final version, :final problem) => '$version/${problem?.name}',
      _ => null,
    };
    final ready = shown != null && shown != _dismissed ? state : null;
    void dismiss() => setState(() => _dismissed = shown);
    final later = TextButton(onPressed: dismiss, child: Text(l10n.updateLater));
    final _Notice? notice = switch ((ready, updater.updatedTo)) {
      (UpdateReady(:final version, problem: InstallProblem.notAllowed), _) => _Notice(
          key: ValueKey('allow-$version'),
          icon: Icons.admin_panel_settings_outlined,
          title: l10n.updateAllowTitle,
          message: l10n.updateAllowMessage(version),
          onClose: dismiss,
          actions: [later, FilledButton(onPressed: updater.allowInstalls, child: Text(l10n.updateAllowOpen))],
        ),
      (UpdateReady(:final version, problem: InstallProblem.conflict), _) => _Notice(
          key: ValueKey('conflict-$version'),
          icon: Icons.error_outline,
          title: l10n.updateRefusedTitle,
          message: l10n.updateConflict(version),
          onClose: dismiss,
          actions: [FilledButton(onPressed: dismiss, child: Text(l10n.updatedToastOk))],
        ),
      (UpdateReady(:final version, problem: InstallProblem.failed), _) => _Notice(
          key: ValueKey('failed-$version'),
          icon: Icons.error_outline,
          title: l10n.updateRefusedTitle,
          message: l10n.updateInstallFailed(version),
          onClose: dismiss,
          actions: [later, FilledButton(onPressed: updater.restartToUpdate, child: Text(l10n.updateRetry))],
        ),
      (UpdateReady(:final version, problem: null), _) => _Notice(
          key: ValueKey('ready-$version'),
          icon: Icons.system_update_alt,
          title: l10n.updateToastTitle,
          message: confirm ? l10n.updateToastMessageConfirm(version) : l10n.updateToastMessage(version),
          onClose: dismiss,
          actions: [
            later,
            FilledButton(
              onPressed: updater.restartToUpdate,
              child: Text(confirm ? l10n.updateInstall : l10n.updateRestartShort),
            ),
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
    // Clear of the system's bars (Android draws under them) and, on a phone, of the bottom bar.
    final media = MediaQuery.of(context);
    final phone = media.size.width < AdaptiveShell.mediumWidth;
    return Positioned(
      right: 20 + media.padding.right,
      bottom: 20 + media.padding.bottom + (phone ? 80 : 0),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: media.size.width - 40 - media.padding.horizontal),
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
