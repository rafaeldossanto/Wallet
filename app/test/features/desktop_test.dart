import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/in_memory_shared_preferences_async.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_async_platform_interface.dart';
import 'package:wallet/core/desktop/desktop_settings.dart';
import 'package:wallet/core/desktop/desktop_views.dart';
import 'package:wallet/core/desktop/updater.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/theme/app_theme.dart';

import '../support/fake_bff.dart';

class _Feed implements ReleaseFeed {
  Release? release;

  @override
  Future<Release?> latest() async => release;
}

class _Store implements InstallerStore {
  @override
  Future<String> fetch(Release release) async => 'Wallet-Setup-${release.version}.exe';
}

class _Launcher implements InstallerLauncher {
  final relaunches = <Relaunch>[];

  @override
  Future<void> launch(String installer, Relaunch relaunch) async => relaunches.add(relaunch);
}

void main() {
  setUpAll(setUpLocale);

  late _Feed feed;
  late _Launcher launcher;
  late MemoryStartupLaunch startup;
  late DesktopSettings settings;
  late Updater updater;

  setUp(() async {
    SharedPreferencesAsyncPlatform.instance = InMemorySharedPreferencesAsync.empty();
    feed = _Feed();
    launcher = _Launcher();
    startup = MemoryStartupLaunch();
    settings = await DesktopSettings.load(startup);
    updater = Updater(
      feed: feed,
      store: _Store(),
      launcher: launcher,
      quit: () async {},
      isAway: () async => false,
      currentVersion: '0.1.0',
    );
  });

  tearDown(() => updater.dispose());

  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(900, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider<DesktopSettings>.value(value: settings),
        ChangeNotifierProvider<Updater>.value(value: updater),
        ChangeNotifierProvider<Updater?>.value(value: updater),
      ],
      child: MaterialApp(
        theme: AppTheme.dark(),
        locale: const Locale('pt', 'BR'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
        home: Scaffold(body: Stack(children: [ListView(children: [child])])),
      ),
    ));
    await tester.pumpAndSettle();
  }

  test('closing to the tray starts on, as in Discord, and is remembered', () async {
    expect(settings.closeToTray, isTrue);

    await settings.setCloseToTray(false);

    expect((await DesktopSettings.load(startup)).closeToTray, isFalse);
    expect(await SharedPreferencesAsync().getBool('wallet.desktop.close_to_tray'), isFalse);
  });

  testWidgets('the Computador section switches the tray and the Windows sign-in and shows the version', (tester) async {
    await pump(tester, const DesktopSettingsCard());

    expect(find.text('Versão 0.1.0'), findsOneWidget);
    expect(find.text('Você está na versão mais nova.'), findsOneWidget);

    await tester.tap(find.text('Abrir com o Windows'));
    await tester.pumpAndSettle();
    expect(startup.isEnabled, isTrue);

    await tester.tap(find.text('Ao fechar, continuar na bandeja'));
    await tester.pumpAndSettle();
    expect(settings.closeToTray, isFalse);

    feed.release = Release(version: '0.2.0', installerUrl: Uri.parse('https://example.test'), sha256: 'ab' * 32);
    await tester.tap(find.text('Procurar agora'));
    await tester.pumpAndSettle();

    expect(find.text('A versão 0.2.0 já foi baixada.'), findsOneWidget);
    await tester.tap(find.text('Reiniciar e atualizar'));
    await tester.pumpAndSettle();
    expect(launcher.relaunches, [Relaunch.open]);
  });

  testWidgets('a downloaded update shows a card that restarts now or waits', (tester) async {
    await tester.pumpWidget(MultiProvider(
      providers: [ChangeNotifierProvider<Updater?>.value(value: updater)],
      child: MaterialApp(
        theme: AppTheme.dark(),
        locale: const Locale('pt', 'BR'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
        home: const Scaffold(body: Stack(children: [SizedBox.expand(), UpdateToast()])),
      ),
    ));
    expect(find.text('Atualização pronta'), findsNothing);

    feed.release = Release(version: '0.2.0', installerUrl: Uri.parse('https://example.test'), sha256: 'ab' * 32);
    await updater.check();
    await tester.pumpAndSettle();
    expect(find.text('Atualização pronta'), findsOneWidget);

    await tester.tap(find.text('Depois'));
    await tester.pumpAndSettle();
    expect(find.text('Atualização pronta'), findsNothing, reason: 'until a newer version arrives');
    expect(launcher.relaunches, isEmpty);
  });
}
