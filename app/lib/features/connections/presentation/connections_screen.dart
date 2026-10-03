import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/loadable_view.dart';
import '../data/connections_api.dart';
import 'connections_controller.dart';

class ConnectionsScreen extends StatelessWidget {
  const ConnectionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<ConnectionsController>();
    final l10n = context.l10n;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.navConnections)),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showLinkDialog(context),
        icon: const Icon(Icons.add_link),
        label: Text(l10n.connectionsLink),
      ),
      body: RefreshIndicator(
        onRefresh: controller.refresh,
        child: LoadableView(
          state: controller.state,
          onRetry: controller.load,
          builder: (context, connections) => connections.isEmpty
              ? ListView(children: [EmptyState(icon: Icons.account_balance_outlined, message: l10n.connectionsEmpty)])
              : ListView(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
                  children: [
                    ContentWidth(
                      maxWidth: 900,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (controller.refreshError != null) ...[
                            RefreshErrorBanner(error: controller.refreshError!),
                            const SizedBox(height: 16),
                          ],
                          for (final connection in connections) ...[
                            ConnectionCard(key: ValueKey(connection.id), connection: connection),
                            const SizedBox(height: 16),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }

  static Future<void> _showLinkDialog(BuildContext context) async {
    final controller = context.read<ConnectionsController>();
    final linked = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => ChangeNotifierProvider.value(value: controller, child: const _LinkDialog()),
    );
    if (linked == true && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(context.l10n.connectionsLinked)));
    }
  }
}

class ConnectionCard extends StatefulWidget {
  const ConnectionCard({super.key, required this.connection});

  final Connection connection;

  @override
  State<ConnectionCard> createState() => _ConnectionCardState();
}

class _ConnectionCardState extends State<ConnectionCard> {
  bool _confirmingUnlink = false;
  bool _busy = false;

  Future<void> _run(Future<ApiException?> Function(ConnectionsController controller) action, {String? success}) async {
    final controller = context.read<ConnectionsController>();
    final messenger = ScaffoldMessenger.of(context);
    final l10n = context.l10n;
    setState(() => _busy = true);
    final error = await action(controller);
    if (mounted) {
      setState(() {
        _busy = false;
        _confirmingUnlink = false;
      });
    }
    if (error != null) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.errorMessage(error))));
    } else if (success != null) {
      messenger.showSnackBar(SnackBar(content: Text(success)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final connection = widget.connection;
    final name = connection.institutionName ?? l10n.institutionUnknown;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                InstitutionAvatar(name: connection.institutionName, imageUrl: connection.institutionImageUrl),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(name, style: theme.textTheme.titleMedium),
                      Text(
                        connection.lastSyncedAt == null
                            ? l10n.connectionNeverSynced
                            : l10n.updatedAgo(Dates.timeAgo(connection.lastSyncedAt!, l10n)),
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                _StatusChip(status: connection.status),
              ],
            ),
            if (connection.status == ConnectionStatus.needsAttention) ...[
              const SizedBox(height: 12),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.warning_amber, color: context.walletColors.warning, size: 20),
                  const SizedBox(width: 8),
                  Expanded(child: Text(l10n.connectionNeedsAttentionHelp)),
                ],
              ),
            ],
            if (connection.consentExpiresAt != null) ...[
              const SizedBox(height: 8),
              Text(l10n.connectionConsentUntil(Dates.short(connection.consentExpiresAt!)), style: theme.textTheme.bodySmall),
            ],
            const SizedBox(height: 12),
            if (_confirmingUnlink)
              _UnlinkConfirmation(
                name: name,
                busy: _busy,
                onCancel: () => setState(() => _confirmingUnlink = false),
                onConfirm: () => _run((controller) => controller.unlink(connection.id), success: l10n.connectionUnlinked),
              )
            else
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton.tonalIcon(
                    onPressed: _busy || connection.isSyncing
                        ? null
                        : () => _run((controller) => controller.sync(connection.id), success: l10n.connectionSyncStarted),
                    icon: connection.isSyncing
                        ? const SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.sync),
                    label: Text(connection.isSyncing ? l10n.connectionSyncing : l10n.connectionSync),
                  ),
                  TextButton.icon(
                    onPressed: _busy ? null : () => setState(() => _confirmingUnlink = true),
                    icon: const Icon(Icons.link_off),
                    label: Text(l10n.connectionUnlink),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _UnlinkConfirmation extends StatelessWidget {
  const _UnlinkConfirmation({required this.name, required this.busy, required this.onCancel, required this.onConfirm});

  final String name;
  final bool busy;
  final VoidCallback onCancel;
  final VoidCallback onConfirm;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.errorContainer,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.connectionUnlinkConfirm(name), style: TextStyle(color: scheme.onErrorContainer)),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(onPressed: busy ? null : onCancel, child: Text(l10n.actionCancel)),
                const SizedBox(width: 8),
                FilledButton(
                  style: FilledButton.styleFrom(backgroundColor: scheme.error, foregroundColor: scheme.onError),
                  onPressed: busy ? null : onConfirm,
                  child: Text(l10n.connectionUnlink),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final ConnectionStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final scheme = Theme.of(context).colorScheme;
    final (label, color) = switch (status) {
      ConnectionStatus.active => (l10n.connectionStatusActive, context.walletColors.inflow),
      ConnectionStatus.syncing => (l10n.connectionStatusSyncing, scheme.primary),
      ConnectionStatus.needsAttention => (l10n.connectionStatusNeedsAttention, context.walletColors.warning),
      ConnectionStatus.unknown => (l10n.connectionStatusUnknown, scheme.onSurfaceVariant),
    };
    return Chip(
      label: Text(label),
      labelStyle: TextStyle(color: color),
      side: BorderSide(color: color.withValues(alpha: 0.5)),
      visualDensity: VisualDensity.compact,
    );
  }
}

/// Meu Pluggy: the user connects the bank in Pluggy's dashboard and pastes the item id here.
class _LinkDialog extends StatefulWidget {
  const _LinkDialog();

  @override
  State<_LinkDialog> createState() => _LinkDialogState();
}

class _LinkDialogState extends State<_LinkDialog> {
  final _itemId = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _itemId.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = context.l10n;
    final itemId = _itemId.text.trim();
    if (!ConnectionsApi.itemIdPattern.hasMatch(itemId)) {
      setState(() => _error = l10n.connectionsItemIdInvalid);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final error = await context.read<ConnectionsController>().link(itemId);
    if (!mounted) {
      return;
    }
    if (error == null) {
      Navigator.of(context).pop(true);
    } else {
      setState(() {
        _busy = false;
        _error = l10n.errorMessage(error);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return AlertDialog(
      title: Text(l10n.connectionsLink),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.connectionsLinkHelp),
            const SizedBox(height: 16),
            TextField(
              controller: _itemId,
              autofocus: true,
              enabled: !_busy,
              decoration: InputDecoration(labelText: l10n.connectionsItemId, errorText: _error),
              onSubmitted: (_) => _submit(),
            ),
            if (kDebugMode) ...[
              const SizedBox(height: 12),
              Text(l10n.connectionsDemoHint, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(false), child: Text(l10n.actionCancel)),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: _busy
              ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(l10n.connectionsLinkAction),
        ),
      ],
    );
  }
}

/// Built by the router for the connections branch.
Widget buildConnections(BuildContext context) => ChangeNotifierProvider(
      create: (context) => ConnectionsController(context.read<ConnectionsApi>(), dataChanges: context.read())..load(),
      child: const ConnectionsScreen(),
    );
