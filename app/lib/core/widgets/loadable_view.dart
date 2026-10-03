import 'package:material_ui/material_ui.dart';

import '../api/api_exception.dart';
import '../l10n/l10n.dart';
import '../state/loadable.dart';

/// Spinner, error with a retry button, or the data.
class LoadableView<T> extends StatelessWidget {
  const LoadableView({super.key, required this.state, required this.onRetry, required this.builder});

  final Loadable<T> state;
  final VoidCallback onRetry;
  final Widget Function(BuildContext context, T value) builder;

  @override
  Widget build(BuildContext context) => switch (state) {
        Loading<T>() => const Center(child: CircularProgressIndicator()),
        Failed<T>(:final error) => ErrorView(error: error, onRetry: onRetry),
        Loaded<T>(:final value) => builder(context, value),
      };
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, required this.onRetry});

  final ApiException error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.cloud_off_outlined, size: 48, color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: 16),
            Text(l10n.errorMessage(error), textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(onPressed: onRetry, child: Text(l10n.actionRetry)),
          ],
        ),
      ),
    );
  }
}

/// A failed refresh while the old data stays on screen.
class RefreshErrorBanner extends StatelessWidget {
  const RefreshErrorBanner({super.key, required this.error});

  final ApiException error;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.errorContainer,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(Icons.sync_problem, color: scheme.onErrorContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Text(context.l10n.refreshFailed(context.l10n.errorMessage(error)),
                  style: TextStyle(color: scheme.onErrorContainer)),
            ),
          ],
        ),
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.message, this.action});

  final IconData icon;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 48, color: Theme.of(context).colorScheme.onSurfaceVariant),
              const SizedBox(height: 16),
              Text(message, textAlign: TextAlign.center),
              if (action != null) ...[const SizedBox(height: 16), action!],
            ],
          ),
        ),
      );
}
