import 'package:material_ui/material_ui.dart';

import '../../../core/format/categories.dart';
import '../../../core/format/dates.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/models/transaction.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/common.dart';

/// One movement: description, category and account, and the signed amount.
class TransactionTile extends StatelessWidget {
  const TransactionTile({super.key, required this.transaction, this.accountName, this.showDate = false});

  final Transaction transaction;
  final String? accountName;
  final bool showDate;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = context.l10n;
    final inflow = transaction.isInflow;
    final details = [
      if (showDate) Dates.dayMonth(transaction.bookedOn),
      Categories.label(transaction.category),
      ?accountName,
    ].join(' · ');
    final installment = transaction.installment;
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: inflow ? context.walletColors.inflow.withValues(alpha: 0.15) : theme.colorScheme.surfaceContainerHighest,
        foregroundColor: inflow ? context.walletColors.inflow : theme.colorScheme.onSurfaceVariant,
        child: Icon(inflow ? Icons.south_west : Icons.north_east, size: 20),
      ),
      title: Text(transaction.description, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(details, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          MoneyText(transaction.amount, inflow: inflow, style: theme.textTheme.bodyLarge),
          if (transaction.pending || installment != null)
            Text(
              [if (installment != null) l10n.transactionInstallment(installment), if (transaction.pending) l10n.transactionPending]
                  .join(' · '),
              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
        ],
      ),
    );
  }
}
