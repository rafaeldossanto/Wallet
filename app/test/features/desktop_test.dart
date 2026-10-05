import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/in_memory_shared_preferences_async.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_async_platform_interface.dart';
import 'package:wallet/core/desktop/desktop.dart';
import 'package:wallet/core/desktop/desktop_settings.dart';
import 'package:wallet/core/desktop/desktop_views.dart';
import 'package:wallet/core/desktop/updater.dart';
import 'package:wallet/core/desktop/window_frame.dart';
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

class _Window implements WindowControls {
  final calls = <String>[];
  final styles = <(bool, bool)>[];
  final maximized = ValueNotifier(false);

  @override
  ValueListenable<bool> get isMaximized => maximized;

  @override
  Future<void> minimize() async => calls.add('minimize');

  @override
  Future<void> toggleMaximize() async => calls.add('toggleMaximize');

  @override
  Future<void> close() async => calls.add('close');

  @override
  Future<void> startDragging() async => calls.add('drag');

  @override
  Future<void> startResizing(WindowEdge edge) async => calls.add('resize ${edge.name}');

  @override
  Future<void> applyStyle({required bool translucent, required bool dark}) async => styles.add((translucent, dark));
}

void main() {
  setUpAll(setUpLocale);

  late _Feed feed;
  late _Launcher launcher;
  late MemoryStartupLaunch startup;
  late DesktopSettings settings;
  late Updater updater;

  Updater newUpdater({bool justUpdated = false}) => Updater(
        feed: feed,
        store: _Store(),
        launcher: launcher,
        quit: () async {},
        isAway: () async => false,
        currentVersion: '0.1.0',
        justUpdated: justUpdated,
      );

  setUp(() async {
    SharedPreferencesAsyncPlatform.instance = InMemorySharedPreferencesAsync.empty();
    feed = _Feed();
    launcher = _Launcher();
    startup = MemoryStartupLaunch();
    settings = await DesktopSettings.load(startup);
    updater = newUpdater();
  });

  tearDown(() => updater.dispose());

  final release = Release(version: '0.2.0', installerUrl: Uri.parse('https://example.test'), sha256: 'ab' * 32);

  /// Provided as the app does it: nullable, since only the Windows app has them.
  Future<void> pump(WidgetTester tester, Widget home, {ThemeData? theme}) async {
    tester.view.physicalSize = const Size(900, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(MultiProvider(
      providers: [
        ChangeNotifierProvider<DesktopSettings?>.value(value: settings),
        ChangeNotifierProvider<Updater?>.value(value: updater),
      ],
      child: MaterialApp(
        theme: theme ?? AppTheme.dark(),
        locale: const Locale('pt', 'BR'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
        home: home,
      ),
    ));
    await tester.pumpAndSettle();
  }

  Widget inPage(Widget child) => Scaffold(body: ListView(children: [child]));

  Widget withToast() => const Scaffold(body: Stack(children: [SizedBox.expand(), UpdateToast()]));

  test('the window starts see-through and closing goes to the tray, as in Discord; both are remembered', () async {
    expect(settings.translucent, isTrue);
    expect(settings.closeToTray, isTrue);

    await settings.setTranslucent(false);
    await settings.setCloseToTray(false);

    final reloaded = await DesktopSettings.load(startup);
    expect(reloaded.translucent, isFalse);
    expect(reloaded.closeToTray, isFalse);
    expect(await SharedPreferencesAsync().getBool('wallet.desktop.close_to_tray'), isFalse);
  });

  testWidgets('Computador picks the window background, the tray and the Windows sign-in, and updates', (tester) async {
    await pump(tester, inPage(const DesktopSettingsCard()));

    expect(find.text('Versão 0.1.0'), findsOneWidget);
    expect(find.text('Você está na versão mais nova.'), findsOneWidget);

    await tester.tap(find.text('Sólido'));
    await tester.pumpAndSettle();
    expect(settings.translucent, isFalse);

    await tester.tap(find.text('Abrir com o Windows'));
    await tester.pumpAndSettle();
    expect(startup.isEnabled, isTrue);

    await tester.tap(find.text('Ao fechar, continuar na bandeja'));
    await tester.pumpAndSettle();
    expect(settings.closeToTray, isFalse);

    feed.release = release;
    await tester.tap(find.text('Procurar agora'));
    await tester.pumpAndSettle();

    expect(find.text('A versão 0.2.0 já foi baixada.'), findsOneWidget);
    await tester.tap(find.text('Reiniciar e atualizar'));
    await tester.pumpAndSettle();
    expect(launcher.relaunches, [Relaunch.open]);
  });

  testWidgets('a new version shows in the corner and its button installs it, no download by hand', (tester) async {
    await pump(tester, withToast());
    expect(find.text('Nova versão disponível'), findsNothing);

    feed.release = release;
    await updater.check();
    await tester.pumpAndSettle();
    expect(find.text('Nova versão disponível'), findsOneWidget);
    expect(find.text('O Wallet 0.2.0 já foi baixado. Reinicie para atualizar.'), findsOneWidget);

    await tester.tap(find.text('Reiniciar'));
    await tester.pumpAndSettle();
    expect(launcher.relaunches, [Relaunch.open]);
  });

  testWidgets('"Depois" puts the notice away until a newer version arrives', (tester) async {
    await pump(tester, withToast());
    feed.release = release;
    await updater.check();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Depois'));
    await tester.pumpAndSettle();

    expect(find.text('Nova versão disponível'), findsNothing);
    expect(launcher.relaunches, isEmpty);
  });

  testWidgets('after an update the app says so once', (tester) async {
    updater.dispose();
    updater = newUpdater(justUpdated: true);
    await pump(tester, withToast());

    expect(find.text('Wallet atualizado'), findsOneWidget);
    expect(find.text('Agora você está na versão 0.1.0.'), findsOneWidget);

    await tester.tap(find.text('Ok'));
    await tester.pumpAndSettle();
    expect(find.text('Wallet atualizado'), findsNothing);
  });

  group('window frame', () {
    late _Window window;

    setUp(() => window = _Window());

    Future<void> pumpFrame(WidgetTester tester, {ThemeData? theme}) => pump(
          tester,
          DesktopWindowFrame(window: window, child: const Scaffold(body: Center(child: Text('conteúdo')))),
          theme: theme,
        );

    BorderRadiusGeometry? clip(WidgetTester tester) =>
        tester.widget<ClipRRect>(find.descendant(of: find.byType(DesktopWindowFrame), matching: find.byType(ClipRRect)).first).borderRadius;

    testWidgets('the three buttons on the left close, minimize and maximize, and the bar drags', (tester) async {
      await pumpFrame(tester);
      final bar = tester.getRect(find.text('Wallet'));

      await tester.tap(find.bySemanticsLabel('Fechar'));
      await tester.tap(find.bySemanticsLabel('Minimizar'));
      await tester.tap(find.bySemanticsLabel('Maximizar'));
      await tester.dragFrom(bar.center, const Offset(80, 40));
      await tester.pumpAndSettle();

      expect(window.calls, ['close', 'minimize', 'toggleMaximize', 'drag']);
      expect(tester.getTopLeft(find.bySemanticsLabel('Fechar')).dx, lessThan(bar.left), reason: 'on the left, as on a Mac');
    });

    testWidgets('rounded while it is a window, square and without resize edges when maximized', (tester) async {
      await pumpFrame(tester);
      expect(clip(tester), BorderRadius.circular(DesktopWindowFrame.cornerRadius));

      await tester.dragFrom(tester.getBottomRight(find.byType(DesktopWindowFrame)) - const Offset(2, 2), const Offset(30, 30));
      expect(window.calls.last, 'resize bottomRight');

      window.maximized.value = true;
      await tester.pumpAndSettle();
      expect(clip(tester), BorderRadius.zero);
    });

    testWidgets('the blur follows the setting and the theme', (tester) async {
      await pumpFrame(tester);
      expect(window.styles.last, (true, true));

      await settings.setTranslucent(false);
      await tester.pumpAndSettle();
      expect(window.styles.last, (false, true));

      await pumpFrame(tester, theme: AppTheme.light());
      expect(window.styles.last, (false, false));
    });

    test('the see-through theme leaves the page to the window and lets the cards show a little of it', () {
      final glass = AppTheme.dark(translucent: true);
      final solid = AppTheme.dark();

      expect(glass.scaffoldBackgroundColor.a, 0);
      expect(glass.cardTheme.color!.a, lessThan(1));
      expect(solid.scaffoldBackgroundColor.a, 1);
      expect(solid.cardTheme.color!.a, 1);
    });
  });
}
