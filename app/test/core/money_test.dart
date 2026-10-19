import 'package:flutter_test/flutter_test.dart';
import 'package:wallet/core/money/money.dart';

void main() {
  const nbsp = ' ';

  group('format', () {
    test('groups thousands with dots and cents with a comma', () {
      expect(Money.parse('1234567.89').format(), 'R\$${nbsp}1.234.567,89');
      expect(Money.parse('999.90').format(), 'R\$${nbsp}999,90');
      expect(Money.parse('0').format(), 'R\$${nbsp}0,00');
    });

    test('puts the minus before the symbol', () {
      expect(Money.parse('-1500.5').format(), '-R\$${nbsp}1.500,50');
    });

    test('signs gains only when asked', () {
      expect(Money.parse('50').format(signed: true), '+R\$${nbsp}50,00');
      expect(Money.parse('-50').format(signed: true), '-R\$${nbsp}50,00');
      expect(Money.parse('0').format(signed: true), 'R\$${nbsp}0,00');
    });

    test('rounds to cents without going through double', () {
      expect(Money.parse('0.005').format(), 'R\$${nbsp}0,01');
      expect(Money.parse('123456789012345.675').format(), 'R\$${nbsp}123.456.789.012.345,68');
    });

    test('compact form for chart axes', () {
      expect(Money.parse('45200').formatCompact(), 'R\$${nbsp}45,2${nbsp}mil');
      expect(Money.parse('1250000').formatCompact(), 'R\$${nbsp}1,3${nbsp}mi');
      expect(Money.parse('800').formatCompact(), 'R\$${nbsp}800');
      expect(Money.parse('150000').formatCompact(), 'R\$${nbsp}150${nbsp}mil');
    });
  });

  group('arithmetic', () {
    test('adds cents exactly where double would drift', () {
      final total = [for (var index = 0; index < 10; index++) Money.parse('0.10')].sum();

      expect(total, Money.parse('1.00'));
    });

    test('subtracts, compares and shares', () {
      final balance = Money.parse('10512.33') - Money.parse('10000');

      expect(balance, Money.parse('512.33'));
      expect(Money.parse('-1').isNegative, isTrue);
      expect(Money.parse('-1').abs(), Money.parse('1'));
      expect(Money.parse('25').shareOf(Money.parse('100')), 0.25);
      expect(Money.parse('25').shareOf(Money.zero), 0);
      expect(Money.parse('2').compareTo(Money.parse('1')), greaterThan(0));
    });
  });

  group('typed in a form', () {
    test('reads amounts the way people here write them', () {
      expect(Money.tryParseInput('1.234,56'), Money.parse('1234.56'));
      expect(Money.tryParseInput('220,16'), Money.parse('220.16'));
      expect(Money.tryParseInput(' R\$ 50 '), Money.parse('50'));
      expect(Money.tryParseInput('R\$${nbsp}99,9'), Money.parse('99.90'));
      expect(Money.tryParseInput('220.16'), Money.parse('220.16'));
    });

    test('refuses what is not an amount in cents', () {
      expect(Money.tryParseInput(''), isNull);
      expect(Money.tryParseInput('abc'), isNull);
      expect(Money.tryParseInput('10,001'), isNull);
      expect(Money.tryParseInput('-10,00'), isNull);
      expect(Money.tryParseInput('1.234'), isNull);
    });

    test('goes to the BFF with a dot and back to the form with a comma', () {
      expect(Money.parse('220.1').toApi(), '220.10');
      expect(Money.parse('3400').toApi(), '3400.00');
      expect(Money.parse('1234.5').toInput(), '1234,50');
    });
  });
}
