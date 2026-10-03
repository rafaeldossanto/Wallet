import 'package:flutter/widgets.dart';

import '../api/api_exception.dart';
import '../format/dates.dart';
import 'app_localizations.dart';

export 'app_localizations.dart';

extension L10nContext on BuildContext {
  AppLocalizations get l10n => AppLocalizations.of(this);
}

extension BillMessages on AppLocalizations {
  /// `Vence em 10/10/2026`, or `Venceu em 10/09/2026` once the day has passed.
  String billDue(DateTime dueDate, {DateTime? now}) => dueDate.isBefore(Dates.today(now))
      ? cardBillPastDue(Dates.short(dueDate))
      : cardBillDue(Dates.short(dueDate));
}

/// The BFF's and core's error codes, as sentences. Unknown codes get the generic one, so a new
/// code on the server never shows up as English to the user.
extension ErrorMessages on AppLocalizations {
  String errorMessage(ApiException error) => switch (error.code) {
        ApiException.networkUnreachable => errorNetworkUnreachable,
        ApiException.networkTimeout => errorNetworkTimeout,
        'auth.invalid_credentials' => errorInvalidCredentials,
        'auth.locked' => errorAuthLocked,
        'auth.email_taken' => errorEmailTaken,
        'auth.invalid_refresh_token' || 'auth.refresh_reused' || 'auth.unauthenticated' => errorSessionExpired,
        'validation.failed' => errorValidation,
        'rate_limit.exceeded' => errorRateLimited,
        'bff.core_unavailable' || 'provider.unavailable' => errorServiceUnavailable,
        'provider.not_configured' => errorProviderNotConfigured,
        'provider.auth_failed' => errorProviderAuthFailed,
        'provider.updating' => errorProviderUpdating,
        'connection.item_not_found' || 'provider.not_found' => errorItemNotFound,
        'connection.already_linked' => errorAlreadyLinked,
        'connection.not_found' || 'connection.gone' => errorConnectionGone,
        'connection.needs_attention' => errorConnectionNeedsAttention,
        'sync.too_soon' => errorSyncTooSoon,
        'sync.in_progress' => errorSyncInProgress,
        'account.not_found' => errorAccountNotFound,
        'period.invalid' || 'period.too_long' || 'page.invalid' || 'request.invalid_parameter' => errorInvalidFilter,
        _ => errorUnexpected,
      };
}
