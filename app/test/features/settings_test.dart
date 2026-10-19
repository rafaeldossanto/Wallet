import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:wallet/app.dart';
import 'package:wallet/core/api/api_exception.dart';
import 'package:wallet/core/format/dates.dart';
import 'package:wallet/core/l10n/l10n.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;

  setUp(() {
    bff = FakeBff();
    bff.signedIn();
    bff.json('GET', '/api/home', Fixtures.home());
  });

  Future<void> openDeleteDialog(WidgetTester tester) async {
    tester.view.physicalSize = const Size(400, 860);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(WalletApp(dependencies: fakeDependencies(bff)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mais'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ajustes'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Excluir conta'));
    await tester.pumpAndSettle();
    expect(find.text('Zona de perigo'), findsOneWidget);
    await tester.tap(find.text('Excluir conta'));
    await tester.pumpAndSettle();
    expect(find.text('Excluir sua conta?'), findsOneWidget);
  }

  Finder deleteButton() => find.widgetWithText(FilledButton, 'Excluir de vez');

  Future<void> confirmWith(WidgetTester tester, String password) async {
    await tester.tap(find.text('Entendo que não dá para desfazer'));
    await tester.pump();
    await tester.enterText(find.widgetWithText(TextField, 'Senha'), password);
    await tester.tap(deleteButton());
  }

  testWidgets('the dialog says the banks are revoked and asks for an explicit "I understand"', (tester) async {
    await openDeleteDialog(tester);

    expect(find.textContaining('as conexões são revogadas na Pluggy'), findsOneWidget);
    expect(find.textContaining('5 seguidas travam a conta por 15 minutos'), findsOneWidget);
    expect(tester.widget<FilledButton>(deleteButton()).onPressed, isNull);

    await tester.tap(find.text('Entendo que não dá para desfazer'));
    await tester.pump();

    expect(tester.widget<FilledButton>(deleteButton()).onPressed, isNotNull);
  });

  testWidgets('an empty password is caught here and never sent', (tester) async {
    await openDeleteDialog(tester);

    await confirmWith(tester, '   ');
    await tester.pumpAndSettle();

    expect(find.text('Digite sua senha para confirmar.'), findsOneWidget);
    expect(bff.calls('DELETE', '/api/me'), isEmpty);
  });

  testWidgets('a wrong password says so on the field and keeps the session', (tester) async {
    bff.error('DELETE', '/api/me', 403, 'auth.wrong_password');
    await openDeleteDialog(tester);

    await confirmWith(tester, 'errada123');
    await tester.pumpAndSettle();

    expect(find.text('Senha incorreta. Cada erro conta para o bloqueio da conta.'), findsOneWidget);
    expect(find.text('Excluir sua conta?'), findsOneWidget);
    expect(bff.calls('DELETE', '/api/me').single.data, {'password': 'errada123'});
    expect(find.widgetWithText(FilledButton, 'Entrar'), findsNothing);
  });

  testWidgets('a locked account shows until when', (tester) async {
    final until = DateTime.now().add(const Duration(minutes: 12));
    bff.json('DELETE', '/api/me',
        {'code': 'auth.locked', 'message': 'locked', 'lockedUntil': until.toUtc().toIso8601String()},
        status: 423);
    await openDeleteDialog(tester);

    await confirmWith(tester, 'certa1234');
    await tester.pumpAndSettle();

    expect(find.textContaining('Muitas tentativas erradas. Tente de novo'), findsOneWidget);
    expect(find.textContaining(Dates.time(until)), findsOneWidget);
  });

  testWidgets('with the core down it says nothing was deleted', (tester) async {
    bff.error('DELETE', '/api/me', 503, 'bff.core_unavailable');
    await openDeleteDialog(tester);

    await confirmWith(tester, 'certa1234');
    await tester.pumpAndSettle();

    expect(find.text('O serviço está fora do ar agora. Tente em alguns minutos. Nada foi apagado; você pode tentar de novo.'),
        findsOneWidget);
    expect(find.text('Excluir sua conta?'), findsOneWidget);
  });

  testWidgets('while the core revokes the banks the dialog shows it and takes no second tap', (tester) async {
    final answer = Completer<FakeResponse>();
    bff.on('DELETE', '/api/me', (_) => answer.future);
    await openDeleteDialog(tester);

    await confirmWith(tester, 'certa1234');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    expect(find.textContaining('Pode levar até 2 minutos'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNull);
    expect(tester.widget<TextButton>(find.widgetWithText(TextButton, 'Cancelar')).onPressed, isNull);

    answer.complete(const FakeResponse(null, status: 204));
    await tester.pumpAndSettle();

    expect(bff.calls('DELETE', '/api/me'), hasLength(1));
  });

  testWidgets('once deleted the session ends and the login says why', (tester) async {
    bff.json('DELETE', '/api/me', null, status: 204);
    await openDeleteDialog(tester);

    await confirmWith(tester, 'certa1234');
    await tester.pumpAndSettle();

    expect(find.widgetWithText(FilledButton, 'Entrar'), findsOneWidget);
    expect(find.text('Sua conta foi excluída, com todos os seus dados.'), findsOneWidget);
    expect(find.text('Excluir sua conta?'), findsNothing);
  });

  test('the locked message names the day too when the lock ends on another one', () {
    final l10n = lookupAppLocalizations(const Locale('pt'));
    final now = DateTime(2026, 10, 19, 23, 55);
    final today = ApiException('auth.locked', status: 423, lockedUntil: DateTime(2026, 10, 19, 23, 58));
    final tomorrow = ApiException('auth.locked', status: 423, lockedUntil: DateTime(2026, 10, 20, 0, 10));

    expect(l10n.errorMessage(today, now: now), 'Muitas tentativas erradas. Tente de novo às 23:58.');
    expect(l10n.errorMessage(tomorrow, now: now), 'Muitas tentativas erradas. Tente de novo em 20/10/2026 às 00:10.');
    expect(l10n.errorMessage(const ApiException('auth.locked', status: 423), now: now),
        'Muitas tentativas erradas. Espere 15 minutos e tente de novo.');
  });
}
