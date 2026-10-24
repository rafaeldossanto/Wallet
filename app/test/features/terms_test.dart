import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/gestures.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/legal/accept_terms_label.dart';
import 'package:wallet/features/connections/presentation/pluggy_connect_screen.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

/// The acceptance of the Terms of Use and the Privacy Policy (L15): the box at sign-up, and the notice
/// that stops the app while the signed-in user has not accepted the current version.
void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;
  late List<Uri> opened;
  var linksOpen = true;

  setUp(() {
    bff = FakeBff();
    opened = [];
    linksOpen = true;
  });

  PluggyConnectHost host() => PluggyConnectHost(openOutside: (url, {required preferApp}) async {
        opened.add(url);
        return linksOpen;
      });

  Map<String, Object?> me({bool required = false, String version = '2026-10-preliminar'}) => {
        'id': '6f1c2a9e-0000-4000-8000-000000000099',
        'email': 'rafael@example.com',
        'displayName': 'Rafael Santos',
        'createdAt': '2026-09-01T12:00:00Z',
        'twoFactorEnabled': false,
        'termsAcceptanceRequired': required,
        'termsVersion': version,
        'termsUrl': 'https://wallet.example.com/termos.html',
        'privacyUrl': 'https://wallet.example.com/privacidade.html',
      };

  Future<void> open(WidgetTester tester, {String? storedRefreshToken = 'refresh-1'}) async {
    tester.view.physicalSize = const Size(400, 860);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(WalletApp(
        dependencies: fakeDependencies(bff, storedRefreshToken: storedRefreshToken, pluggyConnect: host())));
    await tester.pumpAndSettle();
  }

  Finder acceptLabel() => find.text('Li e aceito os Termos de Uso e a Política de Privacidade', findRichText: true);

  /// Taps the link named [text] in the "Li e aceito os ..." line: the span's own recognizer, since a tap by
  /// position would also find the same words in the notice's title.
  Future<void> tapLink(WidgetTester tester, String text) async {
    final label = find.descendant(of: find.byType(AcceptTermsLabel), matching: find.byType(RichText)).first;
    TapGestureRecognizer? found;
    (tester.widget<RichText>(label).text as TextSpan).visitChildren((span) {
      if (span is TextSpan && span.text == text) {
        found = span.recognizer as TapGestureRecognizer?;
      }
      return true;
    });
    expect(found, isNotNull, reason: 'no link named $text');
    found!.onTap!();
    await tester.pumpAndSettle();
  }

  group('sign-up', () {
    Future<void> fillForm(WidgetTester tester) async {
      await tester.tap(find.text('Ainda não tenho conta'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextFormField, 'Nome'), 'Rafael');
      await tester.enterText(find.widgetWithText(TextFormField, 'E-mail'), 'rafael@example.com');
      await tester.enterText(find.widgetWithText(TextFormField, 'Senha'), 'certa1234');
    }

    testWidgets('the box starts unticked and without it nothing is sent', (tester) async {
      bff.json('POST', '/api/auth/register', null, status: 202);
      await open(tester, storedRefreshToken: null);
      await fillForm(tester);

      expect(acceptLabel(), findsOneWidget);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Criar conta')).onPressed, isNull);

      await tester.tap(find.widgetWithText(FilledButton, 'Criar conta'), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(bff.calls('POST', '/api/auth/register'), isEmpty);

      await tester.tap(find.byType(Checkbox));
      await tester.pump();
      expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Criar conta')).onPressed, isNotNull);
      await tester.tap(find.widgetWithText(FilledButton, 'Criar conta'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/auth/register').single.data['acceptTerms'], isTrue);
      expect(find.text('Confira seu e-mail'), findsOneWidget);
    });

    testWidgets('unticking it again takes the button back', (tester) async {
      await open(tester, storedRefreshToken: null);
      await fillForm(tester);

      await tester.tap(find.byType(Checkbox));
      await tester.pump();
      await tester.tap(find.byType(Checkbox));
      await tester.pump();

      expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Criar conta')).onPressed, isNull);
    });

    testWidgets('both names open their text in the browser', (tester) async {
      await open(tester, storedRefreshToken: null);
      await fillForm(tester);

      await tapLink(tester, 'Termos de Uso');
      await tapLink(tester, 'Política de Privacidade');

      // The site's address depends on where the app runs (the Android emulator reaches the computer at 10.0.2.2).
      expect(opened.map((url) => url.path), ['/termos.html', '/privacidade.html']);
      expect(opened.map((url) => url.port), [5181, 5181]);
    });
  });

  group('the notice when the texts changed', () {
    void signedIn({bool required = true}) {
      bff.signedIn();
      bff.json('GET', '/api/me', me(required: required));
      bff.json('GET', '/api/home', Fixtures.home());
    }

    testWidgets('does not show for someone who accepted the current version', (tester) async {
      signedIn(required: false);
      await open(tester);

      expect(find.text('Atualizamos os Termos de Uso e a Política de Privacidade'), findsNothing);
      expect(find.text('Aceitar e continuar'), findsNothing);
    });

    testWidgets('covers the app after signing in, with the box unticked and the button off', (tester) async {
      signedIn();
      await open(tester);

      expect(find.text('Atualizamos os Termos de Uso e a Política de Privacidade'), findsOneWidget);
      expect(acceptLabel(), findsOneWidget);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Aceitar e continuar')).onPressed, isNull);
      // Nothing under it is reachable: it has no way to close, and the app below is out of reach.
      expect(find.byType(ModalBarrier), findsWidgets);
      await tester.tap(find.text('Mais'), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(find.text('Atualizamos os Termos de Uso e a Política de Privacidade'), findsOneWidget);
      expect(bff.calls('POST', '/api/me/terms'), isEmpty);
    });

    testWidgets('its links open the texts the server named', (tester) async {
      signedIn();
      await open(tester);

      await tapLink(tester, 'Termos de Uso');
      await tapLink(tester, 'Política de Privacidade');

      expect(opened.map((url) => url.path), ['/termos.html', '/privacidade.html']);
      expect(opened.first.host, 'wallet.example.com');
    });

    testWidgets('accepting sends the version, asks the profile again and the notice goes away', (tester) async {
      var accepted = false;
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home());
      bff.on('GET', '/api/me', (_) async => FakeResponse(me(required: !accepted)));
      bff.on('POST', '/api/me/terms', (_) async {
        accepted = true;
        return const FakeResponse(null, status: 204);
      });
      await open(tester);

      await tester.tap(find.byType(Checkbox));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Aceitar e continuar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/me/terms').single.data, {'version': '2026-10-preliminar'});
      expect(find.text('Atualizamos os Termos de Uso e a Política de Privacidade'), findsNothing);
      expect(find.text('Mais'), findsOneWidget);
    });

    testWidgets('texts that changed meanwhile keep the notice, with the new version and an untick', (tester) async {
      var version = '2026-10-preliminar';
      bff.signedIn();
      bff.json('GET', '/api/home', Fixtures.home());
      bff.on('GET', '/api/me', (_) async => FakeResponse(me(required: true, version: version)));
      bff.on('POST', '/api/me/terms', (request) async {
        version = '2027-02';
        return const FakeResponse({'code': 'legal.terms_outdated', 'message': 'changed'}, status: 409);
      });
      await open(tester);

      await tester.tap(find.byType(Checkbox));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Aceitar e continuar'));
      await tester.pumpAndSettle();

      expect(find.text('Os textos mudaram de novo. Leia a versão atual e aceite de novo.'), findsOneWidget);
      expect(find.text('Atualizamos os Termos de Uso e a Política de Privacidade'), findsOneWidget);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);

      // The second time it carries the version the reloaded profile brought.
      bff.on('POST', '/api/me/terms', (_) async => const FakeResponse(null, status: 204));
      await tester.tap(find.byType(Checkbox));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Aceitar e continuar'));
      await tester.pumpAndSettle();
      expect(bff.calls('POST', '/api/me/terms').last.data, {'version': '2027-02'});
    });

    testWidgets('a text that cannot be opened says so', (tester) async {
      signedIn();
      linksOpen = false;
      await open(tester);

      await tapLink(tester, 'Termos de Uso');

      expect(find.text('Não foi possível abrir o texto agora. Tente de novo em instantes.'), findsOneWidget);
    });
  });
}
