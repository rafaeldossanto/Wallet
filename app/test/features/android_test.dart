import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/core/updates/update_views.dart';
import 'package:wallet/core/updates/updater.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

class _Feed implements ReleaseFeed {
  Release? release;

  @override
  Future<Release?> latest() async => release;
}

class _Store implements InstallerStore {
  @override
  Future<String> fetch(Release release) async => '/data/user/0/com.wallet.wallet/files/updates/Wallet-${release.version}.apk';
}

/// Android's package installer: what it was handed, and what it answers.
class _SystemInstaller implements InstallerLauncher {
  final handed = <String>[];
  final answers = <InstallProblem?>[];
  int sentToSettings = 0;

  @override
  Future<void> launch(String installer, Relaunch relaunch) async {
    handed.add(installer);
    if (answers.isNotEmpty) {
      if (answers.removeAt(0) case final problem?) {
        throw InstallRefused(problem);
      }
    }
  }

  @override
  Future<bool> allowInstalls() async {
    sentToSettings++;
    return true;
  }
}

void main() {
  setUpAll(setUpLocale);

  late _Feed feed;
  late _SystemInstaller system;
  late Updater updater;

  Updater tablet({String version = '0.1.5'}) => Updater(
        feed: feed,
        store: _Store(),
        launcher: system,
        quit: () async {},
        isAway: () async => false,
        flow: InstallFlow.confirm,
        currentVersion: version,
      );

  setUp(() {
    feed = _Feed();
    system = _SystemInstaller();
    updater = tablet();
  });

  tearDown(() => updater.dispose());

  final release = Release(version: '0.2.0', installerUrl: Uri.parse('https://example.test/Wallet-0.2.0.apk'), sha256: 'ab' * 32);
  const apk = '/data/user/0/com.wallet.wallet/files/updates/Wallet-0.2.0.apk';

  group('Ajustes', () {
    late FakeBff bff;

    setUp(() {
      bff = FakeBff()..signedIn();
      bff.json('GET', '/api/home', Fixtures.home());
    });

    Future<void> openSettings(WidgetTester tester) async {
      tester.view.physicalSize = const Size(400, 860);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff, updater: updater)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mais'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ajustes'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(find.byType(UpdateTile), 300);
      await tester.ensureVisible(find.byType(UpdateTile));
      await tester.pumpAndSettle();
    }

    Finder inTile(String text) => find.descendant(of: find.byType(UpdateTile), matching: find.text(text));

    testWidgets('Atualizações shows the version and looks for a new one, without the window\'s settings', (tester) async {
      await openSettings(tester);

      expect(find.text('Versão 0.1.5'), findsOneWidget);
      expect(find.text('Você está na versão mais nova.'), findsOneWidget);
      expect(find.text('Computador'), findsNothing);
      expect(find.text('Fundo da janela'), findsNothing);
      expect(find.text('Abrir com o Windows'), findsNothing);

      feed.release = release;
      await tester.tap(inTile('Procurar agora'));
      await tester.pumpAndSettle();

      expect(find.text('A versão 0.2.0 já foi baixada.'), findsOneWidget);
      // The notice in the corner offers the same; put away, it leaves the section uncovered.
      await tester.tap(find.text('Depois'));
      await tester.pumpAndSettle();
      await tester.tap(inTile('Atualizar'));
      await tester.pumpAndSettle();
      expect(system.handed, [apk]);
    });

    testWidgets('until Wallet may install apps, the section says where to allow it', (tester) async {
      feed.release = release;
      await updater.check();
      system.answers.add(InstallProblem.notAllowed);
      await openSettings(tester);
      await tester.tap(find.text('Depois'));
      await tester.pumpAndSettle();

      await tester.tap(inTile('Atualizar'));
      await tester.pumpAndSettle();
      expect(find.text('Permita as atualizações'), findsOneWidget, reason: 'the corner explains it too');
      await tester.tap(find.text('Depois'));
      await tester.pumpAndSettle();

      expect(
        find.text('Para instalar a versão 0.2.0, o Wallet precisa da sua permissão: em Configurações, ligue Permitir desta fonte.'),
        findsOneWidget,
      );
      await tester.ensureVisible(inTile('Abrir configurações'));
      await tester.pumpAndSettle();
      await tester.tap(inTile('Abrir configurações'));
      await tester.pumpAndSettle();
      expect(system.sentToSettings, 1);
      expect(system.handed, [apk, apk], reason: 'back from the setting, straight to the confirmation');
    });

    testWidgets('a development build never updates', (tester) async {
      updater.dispose();
      updater = tablet(version: '');
      await openSettings(tester);

      expect(find.text('Versão dev'), findsOneWidget);
      expect(find.text('Build de desenvolvimento: não se atualiza sozinho.'), findsOneWidget);
      expect(inTile('Procurar agora'), findsNothing);
    });

    testWidgets('the browser and the iPhone have no Atualizações', (tester) async {
      tester.view.physicalSize = const Size(400, 860);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mais'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ajustes'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(find.text('Sair'), 300);

      expect(find.text('Atualizações'), findsNothing);
      expect(find.byType(UpdateToast), findsNothing);
    });
  });

  group('the notice in the corner', () {
    Future<void> pump(WidgetTester tester) async {
      tester.view.physicalSize = const Size(1280, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(ChangeNotifierProvider<Updater?>.value(
        value: updater,
        child: MaterialApp(
          theme: AppTheme.dark(),
          locale: const Locale('pt', 'BR'),
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
          home: const Scaffold(body: Stack(children: [SizedBox.expand(), UpdateToast()])),
        ),
      ));
      await tester.pumpAndSettle();
    }

    testWidgets('a downloaded version offers "Atualizar", which opens Android\'s confirmation', (tester) async {
      await pump(tester);
      feed.release = release;
      await updater.check();
      await tester.pumpAndSettle();

      expect(find.text('Nova versão disponível'), findsOneWidget);
      expect(find.text('O Wallet 0.2.0 já foi baixado. Toque em Atualizar para instalar.'), findsOneWidget);
      expect(find.text('Reiniciar'), findsNothing, reason: 'Android restarts nothing; it asks');

      await tester.tap(find.text('Atualizar'));
      await tester.pumpAndSettle();
      expect(system.handed, [apk]);
    });

    testWidgets('the first time it explains the setting, opens it, and the confirmation follows', (tester) async {
      await pump(tester);
      feed.release = release;
      await updater.check();
      system.answers.add(InstallProblem.notAllowed);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Atualizar'));
      await tester.pumpAndSettle();

      expect(find.text('Permita as atualizações'), findsOneWidget);
      expect(
        find.text('Na primeira vez, o Android pede para o Wallet poder instalar a versão 0.2.0. '
            'Em Configurações, ligue Permitir desta fonte e volte para o Wallet.'),
        findsOneWidget,
      );

      await tester.tap(find.text('Abrir configurações'));
      await tester.pumpAndSettle();

      expect(system.sentToSettings, 1);
      expect(system.handed, [apk, apk]);
      expect(find.text('Permita as atualizações'), findsNothing);
    });

    testWidgets('an APK signed with another key is explained, and "Ok" puts the notice away', (tester) async {
      await pump(tester);
      feed.release = release;
      await updater.check();
      system.answers.add(InstallProblem.conflict);
      await tester.pumpAndSettle();

      await tester.tap(find.text('Atualizar'));
      await tester.pumpAndSettle();

      expect(find.text('Atualização não instalada'), findsOneWidget);
      expect(
        find.text('O Android recusou a versão 0.2.0 porque ela foi assinada com outra chave. '
            'Desinstale o Wallet e instale o APK dos Releases.'),
        findsOneWidget,
      );
      await tester.tap(find.text('Ok'));
      await tester.pumpAndSettle();
      expect(find.text('Atualização não instalada'), findsNothing);
    });

    testWidgets('"Depois" puts it away, and a failed install offers to try again', (tester) async {
      await pump(tester);
      feed.release = release;
      await updater.check();
      await tester.pumpAndSettle();

      await tester.tap(find.text('Depois'));
      await tester.pumpAndSettle();
      expect(find.text('Nova versão disponível'), findsNothing);

      system.answers.add(InstallProblem.failed);
      await updater.restartToUpdate();
      await tester.pumpAndSettle();
      expect(find.text('O Android não instalou a versão 0.2.0. Tente de novo.'), findsOneWidget);

      await tester.tap(find.text('Tentar de novo'));
      await tester.pumpAndSettle();
      expect(system.handed, [apk, apk]);
    });
  });
}
