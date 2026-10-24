import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

import '../api/api_exception.dart';
import '../l10n/l10n.dart';
import '../session/session_controller.dart';
import 'accept_terms_label.dart';

/// Puts the notice over the app while the signed-in user has not accepted the current Terms of Use and
/// Privacy Policy (L15): never accepted (an account from before), or the texts changed. Nothing under it
/// can be reached, and it has no way to close but accepting. Only the app enforces it for now: the
/// server tells whether acceptance is required and blocks no route (a conscious choice).
class TermsGate extends StatelessWidget {
  const TermsGate({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final session = context.watch<SessionController>();
    final user = session.user;
    final required = session.isSignedIn && user != null && user.termsAcceptanceRequired;
    return Stack(children: [
      // Under the notice nothing takes focus or is read out: the keyboard and the screen reader stay in it.
      ExcludeFocus(excluding: required, child: ExcludeSemantics(excluding: required, child: child)),
      if (required) const Positioned.fill(child: _TermsNotice()),
    ]);
  }
}

class _TermsNotice extends StatefulWidget {
  const _TermsNotice();

  @override
  State<_TermsNotice> createState() => _TermsNoticeState();
}

class _TermsNoticeState extends State<_TermsNotice> {
  bool _accepted = false;
  bool _busy = false;
  String? _error;

  Future<void> _accept() async {
    if (!_accepted || _busy) {
      return;
    }
    final l10n = context.l10n;
    final session = context.read<SessionController>();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await session.acceptTerms();
    } on ApiException catch (error) {
      // The texts changed while this one was open: the profile was reloaded and asks for the new version.
      if (mounted) {
        setState(() {
          _error = l10n.errorMessage(error);
          _accepted = false;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final user = context.watch<SessionController>().user;
    final termsUrl = Uri.tryParse(user?.termsUrl ?? '');
    final privacyUrl = Uri.tryParse(user?.privacyUrl ?? '');
    return Semantics(
      scopesRoute: true,
      explicitChildNodes: true,
      child: Stack(children: [
        const ModalBarrier(dismissible: false, color: Colors.black54),
        Center(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Material(
                type: MaterialType.card,
                elevation: 6,
                borderRadius: BorderRadius.circular(24),
                clipBehavior: Clip.antiAlias,
                color: Theme.of(context).colorScheme.surfaceContainerHigh,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(l10n.termsNoticeTitle, style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 12),
                      Text(l10n.termsNoticeBody),
                      const SizedBox(height: 16),
                      if (termsUrl != null && privacyUrl != null)
                        CheckboxListTile(
                          contentPadding: EdgeInsets.zero,
                          controlAffinity: ListTileControlAffinity.leading,
                          value: _accepted,
                          onChanged: _busy ? null : (value) => setState(() => _accepted = value ?? false),
                          title: AcceptTermsLabel(
                            termsUrl: termsUrl,
                            privacyUrl: privacyUrl,
                            onLinkFailed: () => setState(() => _error = l10n.legalLinkFailed),
                          ),
                        ),
                      if (_error != null) ...[
                        const SizedBox(height: 8),
                        Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                      ],
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: _accepted && !_busy ? _accept : null,
                        child: _busy
                            ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
                            : Text(l10n.termsNoticeAction),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ]),
    );
  }
}
