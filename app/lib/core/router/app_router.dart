import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/register_screen.dart';
import '../../features/auth/presentation/splash_screen.dart';
import '../../features/cards/presentation/cards_screen.dart';
import '../../features/connections/presentation/connections_screen.dart';
import '../../features/insights/presentation/insights_screen.dart';
import '../../features/investments/presentation/investments_screen.dart';
import '../../features/overview/presentation/overview_screen.dart';
import '../../features/settings/presentation/settings_screen.dart';
import '../../features/transactions/presentation/transactions_screen.dart';
import '../session/session_controller.dart';
import 'adaptive_shell.dart';

/// Routes, and where the session sends the user: nobody sees a screen with data before the
/// session is confirmed, and a link opened while signed out comes back after the login.
GoRouter createRouter(SessionController session) => GoRouter(
      initialLocation: '/home',
      refreshListenable: session,
      redirect: (context, state) => _redirect(session, state),
      routes: [
        GoRoute(path: '/splash', builder: (context, state) => const SplashScreen()),
        GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
        GoRoute(path: '/register', builder: (context, state) => const RegisterScreen()),
        StatefulShellRoute.indexedStack(
          builder: (context, state, navigationShell) => AdaptiveShell(navigationShell: navigationShell),
          branches: [
            _branch('/home', (context, state) => buildOverview(context)),
            _branch('/transactions',
                (context, state) => buildTransactions(context, accountId: state.uri.queryParameters['accountId'])),
            StatefulShellBranch(routes: [
              GoRoute(
                path: '/cards',
                builder: (context, state) => buildCards(context),
                routes: [
                  GoRoute(
                    path: ':accountId',
                    builder: (context, state) => buildBills(context,
                        accountId: state.pathParameters['accountId']!, cardName: state.uri.queryParameters['name']),
                  ),
                ],
              ),
            ]),
            _branch('/investments', (context, state) => buildInvestments(context)),
            _branch('/insights', (context, state) => buildInsights(context)),
            _branch('/connections', (context, state) => buildConnections(context)),
            _branch('/settings', (context, state) => const SettingsScreen()),
          ],
        ),
      ],
    );

StatefulShellBranch _branch(String path, Widget Function(BuildContext context, GoRouterState state) builder) =>
    StatefulShellBranch(routes: [GoRoute(path: path, builder: builder)]);

const _publicPaths = {'/login', '/register'};

String? _redirect(SessionController session, GoRouterState state) {
  final path = state.matchedLocation;
  final from = state.uri.queryParameters['from'];
  switch (session.status) {
    case SessionStatus.restoring:
    case SessionStatus.unreachable:
      return path == '/splash' ? null : _with('/splash', _returnTo(state));
    case SessionStatus.signedOut:
      if (_publicPaths.contains(path)) {
        return null;
      }
      // Whoever signed out on purpose starts over at the home next time.
      if (session.lastSignOutReason == SignOutReason.requested) {
        return '/login';
      }
      return _with('/login', path == '/splash' ? from : _returnTo(state));
    case SessionStatus.signedIn:
      if (_publicPaths.contains(path) || path == '/splash') {
        return from != null && from.startsWith('/') && !from.startsWith('//') ? from : '/home';
      }
      return null;
  }
}

/// Where to come back to; the entry screens are not worth returning to.
String? _returnTo(GoRouterState state) {
  final path = state.matchedLocation;
  if (path == '/splash' || _publicPaths.contains(path)) {
    return state.uri.queryParameters['from'];
  }
  return state.uri.toString();
}

String _with(String path, String? from) =>
    from == null || from == '/home' ? path : Uri(path: path, queryParameters: {'from': from}).toString();
