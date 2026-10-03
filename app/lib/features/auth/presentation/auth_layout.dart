import 'package:material_ui/material_ui.dart';

import '../../../core/l10n/l10n.dart';

/// The centered column the login and sign-up forms share.
class AuthLayout extends StatelessWidget {
  const AuthLayout({super.key, required this.subtitle, required this.children});

  final String subtitle;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Icon(Icons.account_balance_wallet_outlined, size: 48, color: theme.colorScheme.primary),
                  const SizedBox(height: 12),
                  Text(context.l10n.appTitle, textAlign: TextAlign.center, style: theme.textTheme.headlineMedium),
                  const SizedBox(height: 4),
                  Text(subtitle,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                  const SizedBox(height: 32),
                  ...children,
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class FormErrorText extends StatelessWidget {
  const FormErrorText(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Text(message,
            textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).colorScheme.error)),
      );
}

class FormNoticeText extends StatelessWidget {
  const FormNoticeText(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Text(message, textAlign: TextAlign.center),
          ),
        ),
      );
}
