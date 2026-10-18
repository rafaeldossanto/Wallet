import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';
import 'package:wallet/core/l10n/l10n.dart';
import 'package:wallet/core/state/data_changes.dart';
import 'package:wallet/core/theme/app_theme.dart';
import 'package:wallet/features/planning/data/planning_api.dart';
import 'package:wallet/features/planning/presentation/planning_controller.dart';
import 'package:wallet/features/planning/presentation/planning_screen.dart';

import '../support/fake_bff.dart';
import '../support/fixtures.dart';

void main() {
  setUpAll(setUpLocale);

  late FakeBff bff;

  setUp(() => bff = FakeBff());

  Future<void> openPlanning(WidgetTester tester, {Size size = const Size(420, 2400)}) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = PlanningApi(fakeClient(bff));
    final dataChanges = DataChanges();
    await tester.pumpWidget(MultiProvider(
      providers: [
        Provider.value(value: api),
        ChangeNotifierProvider.value(value: dataChanges),
      ],
      child: MaterialApp(
        theme: AppTheme.dark(),
        locale: const Locale('pt', 'BR'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: const [AppLocalizations.delegate, ...GlobalMaterialLocalizations.delegates],
        home: ChangeNotifierProvider(
          create: (_) => PlanningController(api, dataChanges: dataChanges, today: DateTime(2026, 10, 18))..load(),
          child: const PlanningScreen(),
        ),
      ),
    ));
    await tester.pumpAndSettle();
  }

  group('the month', () {
    testWidgets('what is left, the salary, each bill with its purchases and the fixed expenses', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      await openPlanning(tester);

      expect(find.text('Sobra do mês'), findsOneWidget);
      expect(find.text('R\$ 707,60'), findsOneWidget);
      expect(find.text('Salário − faturas − gastos fixos'), findsOneWidget);
      expect(find.text('Sobra R\$ 707,60'), findsOneWidget);
      expect(find.text('Gastos fixos R\$ 1.650,00'), findsOneWidget);
      expect(find.text('Banco Demo R\$ 512,40'), findsOneWidget);
      expect(find.text('Nubank R\$ 230,00'), findsOneWidget);

      expect(find.text('R\$ 3.100,00'), findsOneWidget);
      expect(find.text('Caiu na conta em 05/10/2026.'), findsOneWidget);
      expect(find.text('Previsto'), findsNothing);

      expect(find.text('Cartão Demo •••• 5162'), findsOneWidget);
      expect(find.text('Banco Demo · Venceu em 10/10/2026'), findsOneWidget);
      expect(find.text('Fone de ouvido'), findsOneWidget);
      expect(find.text('Eletrônicos'), findsOneWidget);
      expect(find.text('Parcela 2/3'), findsOneWidget);
      expect(find.text('Outros lançamentos'), findsOneWidget);
      expect(find.text('R\$ 12,40'), findsOneWidget);
      expect(find.text('Tarifas, juros ou compras que o banco não detalhou.'), findsOneWidget);

      expect(find.text('Estorno'), findsOneWidget);
      expect(find.text('+R\$ 20,00'), findsOneWidget);

      expect(find.text('Faculdade'), findsOneWidget);
      expect(find.text('Todo dia 10 · Educação'), findsOneWidget);
      expect(find.text('Todo dia 31 · Aluguel'), findsOneWidget);
      expect(find.text('R\$ 1.650,00'), findsOneWidget);
      expect(bff.calls('GET', '/api/planning').single.queryParameters['month'], '2026-10');
    });

    testWidgets('a long bill shows its largest purchases and "mais N compras" opens the rest', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(bills: [
        Fixtures.planBill(
          accountId: Fixtures.cardId,
          cardName: 'Cartão Demo',
          total: '600.00',
          items: [for (var index = 1; index <= 6; index++) Fixtures.planItem('Compra $index', '100.00')],
        ),
      ]));
      await openPlanning(tester);

      expect(find.text('Compra 4'), findsOneWidget);
      expect(find.text('Compra 5'), findsNothing);
      await tester.tap(find.text('mais 2 compras'));
      await tester.pumpAndSettle();

      expect(find.text('Compra 6'), findsOneWidget);
      await tester.tap(find.text('Mostrar menos'));
      await tester.pumpAndSettle();
      expect(find.text('Compra 6'), findsNothing);
    });

    testWidgets('a salary that has not landed is marked as predicted, and an open bill as open', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(
        month: '2026-11',
        salary: Fixtures.planSalary(date: '2026-11-05', predicted: true, source: 'PREVIOUS_MONTH'),
        bills: [
          Fixtures.planBill(
            accountId: Fixtures.cardId,
            cardName: 'Cartão Demo',
            total: '80.00',
            dueDate: '2026-11-10',
            closingDate: '2026-11-03',
            open: true,
            items: [Fixtures.planItem('Restaurante', '80.00', category: 'Eating out')],
          ),
        ],
      ));
      await openPlanning(tester);

      expect(find.text('Previsto'), findsOneWidget);
      expect(find.text('Previsto pelo salário do mês anterior. Deve cair por volta de 05/11/2026.'), findsOneWidget);
      expect(find.text('Aberta'), findsOneWidget);
      expect(find.text('Fecha por volta de 03/11/2026 e ainda pode crescer.'), findsOneWidget);
      expect(find.text('Outros lançamentos'), findsNothing);
    });

    testWidgets('without a salary it asks for one', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(
        salary: Fixtures.planSalary(amount: null, date: null, source: 'NONE'),
        totals: {'salary': '0.00', 'bills': '742.40', 'fixedExpenses': '1650.00', 'leftover': '-2392.40'},
      ));
      await openPlanning(tester);

      expect(find.text('-R\$ 2.392,40'), findsOneWidget);
      expect(find.text('Sem salário para este mês. Toque em Ajustar no salário e informe quanto você recebe.'), findsOneWidget);
      expect(find.text('Nenhum salário no extrato deste mês nem dos anteriores.'), findsOneWidget);
      expect(find.textContaining('Sobra R'), findsNothing);
    });

    testWidgets('a month in the red says how much is missing', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(
        totals: {'salary': '2000.00', 'bills': '742.40', 'fixedExpenses': '1650.00', 'leftover': '-392.40'},
      ));
      await openPlanning(tester);

      expect(find.text('Faltam R\$ 392,40 para fechar o mês.'), findsOneWidget);
      expect(find.textContaining('Sobra R'), findsNothing);
    });

    testWidgets('no bill due in the month says so', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(bills: []));
      await openPlanning(tester);

      expect(find.text('Nenhuma fatura de cartão vence neste mês.'), findsOneWidget);
    });

    testWidgets('the month moves back, and forward only up to next month', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      await openPlanning(tester);

      await tester.tap(find.byTooltip('Mês anterior'));
      await tester.pumpAndSettle();
      expect(find.text('Setembro de 2026'), findsOneWidget);
      expect(bff.calls('GET', '/api/planning').last.queryParameters['month'], '2026-09');

      await tester.tap(find.byTooltip('Próximo mês'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Próximo mês'));
      await tester.pumpAndSettle();
      expect(find.text('Novembro de 2026'), findsOneWidget);
      expect(tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.chevron_right)).onPressed, isNull);
    });
  });

  group('the salary', () {
    testWidgets('"Ajustar" saves the expected salary typed the Brazilian way and reloads', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      bff.json('PUT', '/api/planning/expected-salary', {'amount': '3400.00'});
      await openPlanning(tester);

      await tester.tap(find.text('Ajustar'));
      await tester.pumpAndSettle();
      expect(find.text('Usar o automático'), findsNothing);
      await tester.enterText(find.widgetWithText(TextField, 'Valor líquido'), '3.400,00');
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(bff.calls('PUT', '/api/planning/expected-salary').single.data, {'amount': '3400.00'});
      expect(find.text('Salário esperado salvo.'), findsOneWidget);
      expect(bff.calls('GET', '/api/planning'), hasLength(2));
    });

    testWidgets('an amount that is not one is refused before anything is sent', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      await openPlanning(tester);

      await tester.tap(find.text('Ajustar'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Valor líquido'), '3.400,001');
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(find.text('Digite um valor como 1.234,56.'), findsOneWidget);
      expect(bff.calls('PUT', '/api/planning/expected-salary'), isEmpty);
    });

    testWidgets('a salary set by hand can go back to the automatic one', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(
        salary: Fixtures.planSalary(date: null, predicted: true, source: 'EXPECTED', amount: '3400.00', expectedAmount: '3400.00'),
      ));
      bff.json('DELETE', '/api/planning/expected-salary', null, status: 204);
      await openPlanning(tester);

      expect(find.text('Valor que você definiu. Vale até o salário do mês cair na conta.'), findsOneWidget);
      await tester.tap(find.text('Ajustar'));
      await tester.pumpAndSettle();
      expect(find.widgetWithText(TextField, 'Valor líquido'), findsOneWidget);
      expect(find.text('3400,00'), findsOneWidget);
      await tester.tap(find.text('Usar o automático'));
      await tester.pumpAndSettle();

      expect(bff.calls('DELETE', '/api/planning/expected-salary'), hasLength(1));
      expect(find.text('O salário voltou a ser previsto pelo mês anterior.'), findsOneWidget);
    });
  });

  group('fixed expenses', () {
    testWidgets('one is added with its day and category', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      bff.json('POST', '/api/planning/fixed-expenses',
          {'id': 'fx-3', 'name': 'Academia', 'amount': '99.90', 'dayOfMonth': 5, 'category': 'Gyms and fitness centers'},
          status: 201);
      await openPlanning(tester);

      await tester.tap(find.text('Adicionar'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Nome'), ' Academia ');
      await tester.enterText(find.widgetWithText(TextField, 'Valor'), '99,90');
      await tester.enterText(find.widgetWithText(TextField, 'Dia do mês'), '5');
      await tester.tap(find.text('Sem categoria').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Academia').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(bff.calls('POST', '/api/planning/fixed-expenses').single.data,
          {'name': 'Academia', 'amount': '99.90', 'dayOfMonth': 5, 'category': 'Gyms and fitness centers'});
      expect(find.text('Gasto fixo salvo.'), findsOneWidget);
      expect(bff.calls('GET', '/api/planning'), hasLength(2));
    });

    testWidgets('the form says what is missing before sending', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      await openPlanning(tester);

      await tester.tap(find.text('Adicionar'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Valor'), 'abc');
      await tester.enterText(find.widgetWithText(TextField, 'Dia do mês'), '40');
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(find.text('Preencha este campo.'), findsOneWidget);
      expect(find.text('Digite um valor como 1.234,56.'), findsOneWidget);
      expect(find.text('Um dia de 1 a 31.'), findsOneWidget);
      expect(bff.calls('POST', '/api/planning/fixed-expenses'), isEmpty);
    });

    testWidgets('tapping one edits it', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      bff.on('PUT', '/api/planning/fixed-expenses/fx-1', (RequestOptions request) async => FakeResponse(
          {'id': 'fx-1', 'name': 'Faculdade', 'amount': '480.00', 'dayOfMonth': 10, 'category': 'Education'}));
      await openPlanning(tester);

      await tester.tap(find.text('Faculdade'));
      await tester.pumpAndSettle();
      expect(find.text('Editar gasto fixo'), findsOneWidget);
      expect(find.text('450,00'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextField, 'Valor'), '480');
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(bff.calls('PUT', '/api/planning/fixed-expenses/fx-1').single.data,
          {'name': 'Faculdade', 'amount': '480.00', 'dayOfMonth': 10, 'category': 'Education'});
    });

    testWidgets('deleting one asks first', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      bff.json('DELETE', '/api/planning/fixed-expenses/fx-2', null, status: 204);
      await openPlanning(tester);

      await tester.tap(find.text('Aluguel'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Excluir'));
      await tester.pumpAndSettle();
      expect(find.text('Excluir Aluguel? Ele sai de todos os meses.'), findsOneWidget);
      expect(bff.calls('DELETE', '/api/planning/fixed-expenses/fx-2'), isEmpty);
      await tester.tap(find.widgetWithText(FilledButton, 'Excluir'));
      await tester.pumpAndSettle();

      expect(bff.calls('DELETE', '/api/planning/fixed-expenses/fx-2'), hasLength(1));
      expect(find.text('Gasto fixo excluído.'), findsOneWidget);
    });

    testWidgets('a server refusal stays in the form, in Portuguese', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan());
      bff.error('PUT', '/api/planning/fixed-expenses/fx-1', 404, 'planning.fixed_expense_not_found');
      await openPlanning(tester);

      await tester.tap(find.text('Faculdade'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Salvar'));
      await tester.pumpAndSettle();

      expect(find.text('Esse gasto fixo não existe mais.'), findsOneWidget);
      expect(find.text('Editar gasto fixo'), findsOneWidget);
    });

    testWidgets('with none yet it explains what goes there', (tester) async {
      bff.json('GET', '/api/planning', Fixtures.plan(fixedExpenses: []));
      await openPlanning(tester);

      expect(find.text('Mensalidade, aluguel, boletos: o que não passa no cartão. Adicione uma vez e ele entra em todo mês.'),
          findsOneWidget);
    });
  });

  testWidgets('on a wide screen the salary and the fixed expenses sit beside the bills', (tester) async {
    bff.json('GET', '/api/planning', Fixtures.plan());
    await openPlanning(tester, size: const Size(1280, 1600));

    final salary = tester.getTopLeft(find.text('Salário líquido'));
    final bill = tester.getTopLeft(find.text('Cartão Demo •••• 5162'));
    expect(bill.dx, greaterThan(salary.dx + 300));
    expect((bill.dy - salary.dy).abs(), lessThan(40));
  });
}
