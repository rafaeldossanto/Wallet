import '../api/api_client.dart';
import '../api/json.dart';

/// What `/api/auth/login` and `/api/auth/refresh` return. On the web the refresh token stays in
/// the HttpOnly cookie, so [refreshToken] is null there.
class SessionTokens {
  const SessionTokens({required this.accessToken, required this.expiresIn, this.refreshToken});

  factory SessionTokens.fromJson(Json json) => SessionTokens(
        accessToken: json.string('accessToken'),
        expiresIn: Duration(seconds: json.integer('expiresIn')),
        refreshToken: json.stringOrNull('refreshToken'),
      );

  final String accessToken;
  final Duration expiresIn;
  final String? refreshToken;
}

class UserProfile {
  const UserProfile({
    required this.id,
    required this.email,
    required this.displayName,
    this.pendingEmail,
    this.twoFactorEnabled = false,
  });

  factory UserProfile.fromJson(Json json) => UserProfile(
        id: json.string('id'),
        email: json.string('email'),
        displayName: json.string('displayName'),
        pendingEmail: json.stringOrNull('pendingEmail'),
        twoFactorEnabled: json.raw['twoFactorEnabled'] == true,
      );

  final String id;
  final String email;
  final String displayName;

  /// The address the account is moving to, until the link sent there is opened.
  final String? pendingEmail;

  /// Whether signing in asks for the authenticator app's code.
  final bool twoFactorEnabled;

  String get firstName => displayName.trim().split(RegExp(r'\s+')).first;

  /// `RS` for Rafael Santos; one letter for a single name.
  String get initials {
    final words = displayName.trim().split(RegExp(r'\s+')).where((word) => word.isNotEmpty).toList();
    if (words.isEmpty) {
      return '?';
    }
    final first = words.first[0];
    return (words.length == 1 ? first : first + words.last[0]).toUpperCase();
  }
}

/// The start of the second factor: [otpauthUri] goes into the QR code, [secret] is for typing.
class TwoFactorSetup {
  const TwoFactorSetup({required this.secret, required this.otpauthUri});

  factory TwoFactorSetup.fromJson(Json json) =>
      TwoFactorSetup(secret: json.string('secret'), otpauthUri: json.string('otpauthUri'));

  final String secret;
  final String otpauthUri;

  /// `JBSW Y3DP EHPK 3PXP`: groups of four, easier to copy into an app by hand.
  String get groupedSecret => [
        for (var start = 0; start < secret.length; start += 4)
          secret.substring(start, start + 4 > secret.length ? secret.length : start + 4),
      ].join(' ');
}

class SessionApi {
  SessionApi(this._api);

  final ApiClient _api;

  /// [code] is the authenticator's (or a recovery code), sent only once the core asked for it.
  Future<SessionTokens> login(String email, String password, {String? code}) async =>
      SessionTokens.fromJson(Json.of(await _api.post('/api/auth/login', body: {
        'email': email,
        'password': password,
        'code': ?code,
      })));

  /// The same answer for every e-mail: the account exists only after the e-mailed link is opened.
  Future<void> register({required String displayName, required String email, required String password}) =>
      _api.post('/api/auth/register', body: {'displayName': displayName, 'email': email, 'password': password});

  Future<void> resendConfirmation(String email) => _api.post('/api/auth/email/resend', body: {'email': email});

  /// The same answer for every e-mail: the link goes only to a confirmed account.
  Future<void> forgotPassword(String email) => _api.post('/api/auth/password/forgot', body: {'email': email});

  /// The core checks [currentPassword], changes it and ends every session, this one included.
  Future<void> changePassword(String currentPassword, String newPassword) =>
      _api.put('/api/me/password', body: {'currentPassword': currentPassword, 'newPassword': newPassword});

  /// Ten new recovery codes for the old ones, with the password and a code; only this answer carries them.
  Future<List<String>> renewRecoveryCodes(String password, String code) async =>
      Json.of(await _api.post('/api/me/two-factor/recovery-codes', body: {'password': password, 'code': code}))
          .strings('recoveryCodes');

  /// Mobile sends the stored token; the web sends nothing and the browser adds the cookie.
  Future<SessionTokens> refresh(String? refreshToken) async => SessionTokens.fromJson(Json.of(
      await _api.post('/api/auth/refresh', body: refreshToken == null ? null : {'refreshToken': refreshToken})));

  Future<void> logout(String? refreshToken) =>
      _api.post('/api/auth/logout', body: refreshToken == null ? null : {'refreshToken': refreshToken});

  Future<UserProfile> me() async => UserProfile.fromJson(Json.of(await _api.get('/api/me')));

  Future<UserProfile> rename(String displayName) async =>
      UserProfile.fromJson(Json.of(await _api.put('/api/me', body: {'displayName': displayName})));

  /// The core sends a link to [newEmail]; the account moves there once it is opened.
  Future<void> changeEmail(String newEmail, String password) =>
      _api.post('/api/me/email', body: {'newEmail': newEmail, 'password': password});

  Future<TwoFactorSetup> setUpTwoFactor(String password) async =>
      TwoFactorSetup.fromJson(Json.of(await _api.post('/api/me/two-factor/setup', body: {'password': password})));

  /// The first code from the app turns it on; the answer is the recovery codes, shown only now.
  Future<List<String>> enableTwoFactor(String code) async =>
      Json.of(await _api.post('/api/me/two-factor/enable', body: {'code': code})).strings('recoveryCodes');

  Future<void> disableTwoFactor(String password, String code) =>
      _api.delete('/api/me/two-factor', body: {'password': password, 'code': code});

  /// Longer than the BFF waits for the core on this call (`wallet.core.slow-read-timeout`, 2 min):
  /// the core revokes every bank connection at Pluggy before answering, and the app must hear the
  /// BFF's answer instead of giving up first on an account that may then be deleted anyway.
  static const deleteAccountTimeout = Duration(minutes: 2, seconds: 30);

  /// The core checks [password] again before deleting anything, and revokes every bank connection.
  Future<void> deleteAccount(String password) =>
      _api.delete('/api/me', body: {'password': password}, receiveTimeout: deleteAccountTimeout);
}
