import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../l10n/l10n.dart';
import 'app_lock.dart';

/// Covers the whole app while [AppLock] is locked, and asks for the unlock as it appears.
class LockScreen extends StatefulWidget {
  const LockScreen({super.key});

  @override
  State<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends State<LockScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _unlock());
  }

  Future<void> _unlock() => context.read<AppLock?>()!.unlock(context.l10n.lockReason);

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.lock_outline, size: 56, color: Theme.of(context).colorScheme.primary),
              const SizedBox(height: 16),
              Text(l10n.lockTitle, style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 24),
              FilledButton.icon(onPressed: _unlock, icon: const Icon(Icons.fingerprint), label: Text(l10n.lockUnlock)),
              const SizedBox(height: 8),
              TextButton(onPressed: () => context.read<AppLock?>()!.signOut(), child: Text(l10n.settingsSignOut)),
            ],
          ),
        ),
      ),
    );
  }
}
