import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/session/session_controller.dart';
import 'auth_layout.dart';

/// "Esqueci minha senha": the e-mail of the account, and the same sentence whatever it is. The
/// server mails the link only to a confirmed account and answers alike for every address, so
/// nothing here, or in the answer, says whether an account exists.
class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _sending = false;
  bool _sent = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    if (_sending || !_form.currentState!.validate()) {
      return;
    }
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      await context.read<SessionController>().forgotPassword(_email.text);
      if (mounted) {
        setState(() => _sent = true);
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = context.l10n.errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AuthLayout(
      subtitle: l10n.forgotSubtitle,
      children: [
        if (_sent) ...[
          Icon(Icons.mark_email_unread_outlined, size: 40, color: Theme.of(context).colorScheme.primary),
          const SizedBox(height: 16),
          Text(l10n.forgotSent, textAlign: TextAlign.center),
          const SizedBox(height: 24),
          FilledButton(onPressed: () => context.go('/login'), child: Text(l10n.registerBackToLogin)),
        ] else ...[
          Text(l10n.forgotHelp, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          Form(
            key: _form,
            child: TextFormField(
              controller: _email,
              decoration: InputDecoration(labelText: l10n.fieldEmail),
              keyboardType: TextInputType.emailAddress,
              autofillHints: const [AutofillHints.email],
              textInputAction: TextInputAction.done,
              onFieldSubmitted: (_) => _send(),
              validator: (value) => (value ?? '').contains('@') ? null : l10n.validationEmail,
            ),
          ),
          const SizedBox(height: 24),
          if (_error != null) FormErrorText(_error!),
          FilledButton(
            onPressed: _sending ? null : _send,
            child: _sending
                ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : Text(l10n.forgotAction),
          ),
          const SizedBox(height: 12),
          TextButton(onPressed: () => context.go('/login'), child: Text(l10n.registerBackToLogin)),
        ],
      ],
    );
  }
}
