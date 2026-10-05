import 'package:flutter/foundation.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';

import 'core/api/api_client.dart';
import 'core/api/auth_interceptor.dart';
import 'core/desktop/desktop.dart';
import 'core/desktop/desktop_settings.dart';
import 'core/desktop/desktop_views.dart';
import 'core/desktop/window_frame.dart';
import 'core/desktop/updater.dart';
import 'core/l10n/l10n.dart';
import 'core/router/app_router.dart';
import 'core/security/app_lock.dart';
import 'core/security/idle_timeout.dart';
import 'core/security/lock_screen.dart';
import 'core/session/session_api.dart';
import 'core/session/session_controller.dart';
import 'core/session/session_store.dart';
import 'core/state/data_changes.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_controller.dart';
import 'features/calendar/data/calendar_api.dart';
import 'features/cards/data/cards_api.dart';
import 'features/connections/data/connections_api.dart';
import 'features/insights/data/insights_api.dart';
import 'features/investments/data/investments_api.dart';
import 'features/overview/data/home_api.dart';
import 'features/transactions/data/transactions_api.dart';

/// Everything the screens need, built once. Tests build it with a fake HTTP adapter.
class AppDependencies {
  AppDependencies._(this.api, this.session, this.appLock, this.theme, this.desktop);

  factory AppDependencies.create({
    ApiClient? api,
    SessionStore? store,
    ThemeController? theme,
    Desktop? desktop,
    bool withAppLock = !kIsWeb,
  }) {
    final client = api ?? ApiClient.create();
    final session = SessionController(
      api: SessionApi(client),
      store: store ?? (kIsWeb ? const CookieSessionStore() : SecureSessionStore()),
    );
    client.dio.interceptors.add(AuthInterceptor(session, client.dio));
    return AppDependencies._(client, session, withAppLock ? AppLock(session: session) : null,
        theme ?? ThemeController(MemoryThemePreference()), desktop);
  }

  final ApiClient api;
  final SessionController session;
  final AppLock? appLock;
  final ThemeController theme;

  /// The Windows app's window settings and updater; null on the phone and in the browser.
  final Desktop? desktop;
  final DataChanges dataChanges = DataChanges();

  List<SingleChildWidget> get providers => [
        ChangeNotifierProvider.value(value: session),
        ChangeNotifierProvider.value(value: dataChanges),
        ChangeNotifierProvider.value(value: theme),
        ChangeNotifierProvider<AppLock?>.value(value: appLock),
        ChangeNotifierProvider<DesktopSettings?>.value(value: desktop?.settings),
        ChangeNotifierProvider<Updater?>.value(value: desktop?.updater),
        Provider(create: (_) => HomeApi(api)),
        Provider(create: (_) => CalendarApi(api)),
        Provider(create: (_) => TransactionsApi(api)),
        Provider(create: (_) => CardsApi(api)),
        Provider(create: (_) => InvestmentsApi(api)),
        Provider(create: (_) => InsightsApi(api)),
        Provider(create: (_) => ConnectionsApi(api)),
      ];
}

class WalletApp extends StatefulWidget {
  const WalletApp({super.key, required this.dependencies});

  final AppDependencies dependencies;

  @override
  State<WalletApp> createState() => _WalletAppState();
}

class _WalletAppState extends State<WalletApp> {
  /// Light and dark, solid or for the Windows app's see-through window; built once each.
  static final _themes = <bool, (ThemeData, ThemeData)>{};

  static (ThemeData, ThemeData) _themesFor({required bool translucent}) => _themes.putIfAbsent(
        translucent,
        () => (AppTheme.light(translucent: translucent), AppTheme.dark(translucent: translucent)),
      );

  late final GoRouter _router = createRouter(widget.dependencies.session);

  @override
  void initState() {
    super.initState();
    widget.dependencies.session.restore();
    widget.dependencies.appLock?.start();
  }

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final dependencies = widget.dependencies;
    return MultiProvider(
      providers: dependencies.providers,
      child: Consumer2<ThemeController, DesktopSettings?>(
        builder: (context, theme, desktop, _) =>
            _app(dependencies, theme.mode, translucent: desktop?.translucent ?? false),
      ),
    );
  }

  Widget _app(AppDependencies dependencies, ThemeMode mode, {required bool translucent}) {
    final (light, dark) = _themesFor(translucent: translucent);
    return MaterialApp.router(
      onGenerateTitle: (context) => context.l10n.appTitle,
      debugShowCheckedModeBanner: false,
      theme: light,
      darkTheme: dark,
      themeMode: mode,
      locale: const Locale('pt', 'BR'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
      routerConfig: _router,
      builder: (context, child) {
        final app = child ?? const SizedBox.shrink();
        if (kIsWeb) {
          return IdleTimeout(session: dependencies.session, child: app);
        }
        final content = Consumer<AppLock?>(
          builder: (context, lock, _) => Stack(children: [
            app,
            if (dependencies.desktop != null) const UpdateToast(),
            if (lock?.isLocked ?? false) const LockScreen(),
          ]),
        );
        final window = dependencies.desktop?.window;
        return window == null ? content : DesktopWindowFrame(window: window, child: content);
      },
    );
  }
}
