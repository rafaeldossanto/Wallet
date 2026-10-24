import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/config/app_config.dart';
import '../../../core/l10n/l10n.dart';
import '../../../core/legal/accept_terms_label.dart';
import '../../../core/session/session_controller.dart';
import 'auth_layout.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  /// Same rule as the core, checked here first so the user is not sent back from the server.
  static const minPasswordLength = 8;

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _submitting = false;
  bool _acceptedTerms = false;
  String? _error;

  /// The e-mail the link went to: the account only exists once it is opened.
  String? _sentTo;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_acceptedTerms || !_form.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await context
          .read<SessionController>()
          .register(displayName: _name.text, email: _email.text, password: _password.text);
      if (mounted) {
        setState(() => _sentTo = _email.text.trim());
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = context.l10n.errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    if (_sentTo case final email?) {
      return AuthLayout(subtitle: l10n.registerCheckEmailTitle, children: [CheckEmailPanel(email: email)]);
    }
    return AuthLayout(
      subtitle: l10n.registerSubtitle,
      children: [
        Form(
          key: _form,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _name,
                  decoration: InputDecoration(labelText: l10n.fieldName),
                  autofillHints: const [AutofillHints.name],
                  textCapitalization: TextCapitalization.words,
                  textInputAction: TextInputAction.next,
                  validator: (value) => (value ?? '').trim().isEmpty ? l10n.validationRequired : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _email,
                  decoration: InputDecoration(labelText: l10n.fieldEmail),
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  textInputAction: TextInputAction.next,
                  validator: (value) => (value ?? '').contains('@') ? null : l10n.validationEmail,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _password,
                  decoration: InputDecoration(
                      labelText: l10n.fieldPassword, helperText: l10n.validationPasswordLength(RegisterScreen.minPasswordLength)),
                  obscureText: true,
                  autofillHints: const [AutofillHints.newPassword],
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  validator: (value) => (value ?? '').length < RegisterScreen.minPasswordLength
                      ? l10n.validationPasswordLength(RegisterScreen.minPasswordLength)
                      : null,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        // Unticked on purpose, and without it nothing is sent (L15).
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
          value: _acceptedTerms,
          onChanged: _submitting ? null : (value) => setState(() => _acceptedTerms = value ?? false),
          title: AcceptTermsLabel(
            termsUrl: AppConfig.termsUrl,
            privacyUrl: AppConfig.privacyUrl,
            onLinkFailed: () => setState(() => _error = l10n.legalLinkFailed),
          ),
        ),
        const SizedBox(height: 8),
        if (_error != null) FormErrorText(_error!),
        FilledButton(
          onPressed: _submitting || !_acceptedTerms ? null : _submit,
          child: _submitting
              ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(l10n.registerAction),
        ),
        const SizedBox(height: 12),
        TextButton(onPressed: () => context.go('/login'), child: Text(l10n.registerGoToLogin)),
      ],
    );
  }
}

/// After signing up: the account waits for the link. The same words whether or not the e-mail
/// already had an account, as the server's answer is the same.
class CheckEmailPanel extends StatefulWidget {
  const CheckEmailPanel({super.key, required this.email});

  final String email;

  @override
  State<CheckEmailPanel> createState() => _CheckEmailPanelState();
}

class _CheckEmailPanelState extends State<CheckEmailPanel> {
  bool _sending = false;
  String? _notice;
  String? _error;

  Future<void> _resend() async {
    setState(() {
      _sending = true;
      _notice = null;
      _error = null;
    });
    try {
      await context.read<SessionController>().resendConfirmation(widget.email);
      if (mounted) {
        setState(() => _notice = context.l10n.confirmationResent);
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Icon(Icons.mark_email_unread_outlined, size: 40, color: Theme.of(context).colorScheme.primary),
        const SizedBox(height: 16),
        Text(l10n.registerCheckEmailBody(widget.email), textAlign: TextAlign.center),
        const SizedBox(height: 24),
        if (_notice != null) FormNoticeText(_notice!),
        if (_error != null) FormErrorText(_error!),
        FilledButton(onPressed: () => context.go('/login'), child: Text(l10n.registerBackToLogin)),
        const SizedBox(height: 12),
        TextButton(onPressed: _sending ? null : _resend, child: Text(l10n.registerResend)),
      ],
    );
  }
}
