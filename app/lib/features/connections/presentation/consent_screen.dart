import 'package:material_ui/material_ui.dart';

import '../../../core/l10n/l10n.dart';
import '../../../core/widgets/common.dart';

/// "Antes de conectar": what the Wallet reads from the bank, why, for how long and how to take it
/// back (LGPD: the purpose is told before the data is). Shown before every new connection and every
/// renewal, whichever way it is made (Pluggy Connect or the Item ID). The box starts unchecked and
/// only the person checks it; "Continuar" stays off until then. The text has a version
/// ([ConnectionsApi.consentVersion]) that the server records with the connection: a change of the
/// text below is a new version.
class ConsentScreen extends StatefulWidget {
  const ConsentScreen({super.key});

  /// Opens the screen above the navigation. True when the person agreed and went on.
  static Future<bool> ask(BuildContext context) async =>
      await Navigator.of(context, rootNavigator: true).push<bool>(
        MaterialPageRoute(fullscreenDialog: true, builder: (_) => const ConsentScreen()),
      ) ??
      false;

  @override
  State<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends State<ConsentScreen> {
  bool _agreed = false;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.consentTitle), leading: const CloseButton()),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ContentWidth(
              maxWidth: 560,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _Section(heading: l10n.consentPurposeHeading, children: [Text(l10n.consentPurpose)]),
                  _Section(heading: l10n.consentDataHeading, children: [
                    _Item(icon: Icons.account_balance_outlined, text: l10n.consentDataAccounts),
                    _Item(icon: Icons.receipt_long_outlined, text: l10n.consentDataTransactions),
                    _Item(icon: Icons.credit_card_outlined, text: l10n.consentDataCards),
                    _Item(icon: Icons.trending_up, text: l10n.consentDataInvestments),
                    const SizedBox(height: 8),
                    Text(l10n.consentReadOnly, style: theme.textTheme.titleSmall),
                  ]),
                  _Section(heading: l10n.consentValidityHeading, children: [Text(l10n.consentValidity)]),
                  _Section(heading: l10n.consentRevokeHeading, children: [Text(l10n.consentRevoke)]),
                  CheckboxListTile(
                    value: _agreed,
                    onChanged: (value) => setState(() => _agreed = value ?? false),
                    title: Text(l10n.consentCheckbox),
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.zero,
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: _agreed ? () => Navigator.of(context).pop(true) : null,
                    child: Text(l10n.consentContinue),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.heading, required this.children});

  final String heading;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(heading, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...children,
          ],
        ),
      );
}

class _Item extends StatelessWidget {
  const _Item({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(child: Text(text)),
          ],
        ),
      );
}
