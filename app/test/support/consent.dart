import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';

/// "Antes de conectar": ticks the box and continues, as someone who agrees to the purpose.
Future<void> agreeToConsent(WidgetTester tester) async {
  await tester.pumpAndSettle();
  expect(find.text('Antes de conectar'), findsOneWidget);
  await tester.tap(find.byType(CheckboxListTile));
  await tester.pump();
  await tester.tap(find.widgetWithText(FilledButton, 'Continuar'));
}
