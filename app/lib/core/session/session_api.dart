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
  const UserProfile({required this.id, required this.email, required this.displayName});

  factory UserProfile.fromJson(Json json) => UserProfile(
        id: json.string('id'),
        email: json.string('email'),
        displayName: json.string('displayName'),
      );

  final String id;
  final String email;
  final String displayName;

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

class SessionApi {
  SessionApi(this._api);

  final ApiClient _api;

  Future<SessionTokens> login(String email, String password) async =>
      SessionTokens.fromJson(Json.of(await _api.post('/api/auth/login', body: {'email': email, 'password': password})));

  Future<void> register({required String displayName, required String email, required String password}) =>
      _api.post('/api/auth/register', body: {'displayName': displayName, 'email': email, 'password': password});

  /// Mobile sends the stored token; the web sends nothing and the browser adds the cookie.
  Future<SessionTokens> refresh(String? refreshToken) async => SessionTokens.fromJson(Json.of(
      await _api.post('/api/auth/refresh', body: refreshToken == null ? null : {'refreshToken': refreshToken})));

  Future<void> logout(String? refreshToken) =>
      _api.post('/api/auth/logout', body: refreshToken == null ? null : {'refreshToken': refreshToken});

  Future<UserProfile> me() async => UserProfile.fromJson(Json.of(await _api.get('/api/me')));
}
