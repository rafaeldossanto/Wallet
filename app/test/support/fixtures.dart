/// BFF answers for the screen tests, shaped like the real contract (money as text).
abstract final class Fixtures {
  static const connectionId = '6f1c2a9e-0000-4000-8000-000000000001';
  static const checkingId = '6f1c2a9e-0000-4000-8000-000000000002';
  static const cardId = '6f1c2a9e-0000-4000-8000-000000000003';

  static Map<String, Object?> home({List<String> unavailable = const []}) => {
        'overview': unavailable.contains('overview')
            ? null
            : {
                'netWorth': '23456.78',
                'cashBalance': '4800.00',
                'creditCardDebt': '1243.22',
                'investmentsTotal': '19900.00',
                'month': '2026-10',
                'monthIncome': '7350.00',
                'monthExpenses': '2489.90',
                'lastSyncedAt': '2026-10-02T09:00:00Z',
              },
        'institutions': [
          {
            'connectionId': connectionId,
            'institutionName': 'Banco Demo',
            'institutionImageUrl': null,
            'status': 'ACTIVE',
            'lastSyncedAt': '2026-10-02T09:00:00Z',
            'accounts': [account()],
          },
        ],
        'creditCards': unavailable.contains('creditCards') ? <Object>[] : [card()],
        'recentTransactions': [
          transaction(id: 'tx-1', description: 'SALARIO EMPRESA DEMO', amount: '7350.00', direction: 'INFLOW', category: 'Salary'),
          transaction(id: 'tx-2', description: 'SUPERMERCADO BOM PRECO', amount: '210.45', category: 'Groceries'),
        ],
        'unavailable': unavailable,
      };

  static Map<String, Object?> account() => {
        'id': checkingId,
        'connectionId': connectionId,
        'kind': 'CHECKING',
        'name': 'Conta Corrente',
        'numberLastDigits': '4567',
        'currencyCode': 'BRL',
        'balance': '4800.00',
        'creditLimit': null,
        'availableCredit': null,
        'updatedAt': '2026-10-02T09:00:00Z',
      };

  static Map<String, Object?> card() => {
        'accountId': cardId,
        'connectionId': connectionId,
        'name': 'Cartão Demo',
        'numberLastDigits': '5162',
        'currencyCode': 'BRL',
        'creditLimit': '12000.00',
        'availableCredit': '10756.78',
        'currentBalance': '1243.22',
        'currentBill': {
          'id': 'bill-1',
          'dueDate': '2026-10-10',
          'closingDate': '2026-10-03',
          'totalAmount': '1890.40',
          'minimumPayment': '283.56',
          'currencyCode': 'BRL',
        },
      };

  static Map<String, Object?> transaction({
    required String id,
    required String description,
    required String amount,
    String direction = 'OUTFLOW',
    String bookedOn = '2026-10-01',
    String accountId = checkingId,
    String? category,
    String status = 'POSTED',
    int? installmentNumber,
    int? installmentTotal,
  }) =>
      {
        'id': id,
        'accountId': accountId,
        'bookedOn': bookedOn,
        'description': description,
        'amount': amount,
        'direction': direction,
        'status': status,
        'category': category,
        'installmentNumber': installmentNumber,
        'installmentTotal': installmentTotal,
      };

  static Map<String, Object?> page(List<Map<String, Object?>> items, {int page = 1, int totalPages = 1}) =>
      {'items': items, 'page': page, 'pageSize': 50, 'total': items.length, 'totalPages': totalPages};

  static Map<String, Object?> connection({String status = 'ACTIVE', String? lastSyncedAt = '2026-10-02T09:00:00Z'}) => {
        'id': connectionId,
        'institutionName': 'Banco Demo',
        'institutionImageUrl': null,
        'status': status,
        'lastSyncedAt': lastSyncedAt,
        'consentExpiresAt': '2027-10-02T09:00:00Z',
        'createdAt': '2026-09-01T12:00:00Z',
      };

  /// A month's plan with made-up numbers: salary 3100.00, two bills (512.40 and 230.00) and two
  /// fixed expenses (450.00 and 1200.00), so 707.60 left.
  static Map<String, Object?> plan({
    String month = '2026-10',
    Map<String, Object?>? salary,
    List<Map<String, Object?>>? bills,
    List<Map<String, Object?>>? fixedExpenses,
    Map<String, Object?>? totals,
  }) =>
      {
        'month': month,
        'salary': salary ?? planSalary(),
        'bills': bills ??
            [
              planBill(
                accountId: cardId,
                cardName: 'Cartão Demo',
                institution: 'Banco Demo',
                total: '512.40',
                otherCharges: '12.40',
                items: [
                  planItem('Fone de ouvido', '200.00', category: 'Electronics'),
                  planItem('Bicicleta', '150.00', category: 'Sports practice', installment: (2, 3), date: '2026-08-15'),
                  planItem('Mercado', '150.00', category: 'Groceries'),
                ],
              ),
              planBill(
                accountId: '6f1c2a9e-0000-4000-8000-000000000004',
                cardName: 'Cartão Roxo',
                institution: 'Nubank',
                digits: '4821',
                dueDate: '2026-10-04',
                total: '230.00',
                otherCharges: '0.00',
                items: [
                  planItem('Tênis', '190.00', category: 'Clothing'),
                  planItem('Livraria', '60.00', category: 'Bookstore'),
                  planItem('Estorno livraria', '20.00', category: 'Bookstore', refund: true),
                ],
              ),
            ],
        'fixedExpenses': fixedExpenses ??
            [
              planFixed('fx-1', 'Faculdade', '450.00', 10, category: 'Education'),
              planFixed('fx-2', 'Aluguel', '1200.00', 31, date: '2026-10-31', category: 'Rent'),
            ],
        'totals': totals ?? {'salary': '3100.00', 'bills': '742.40', 'fixedExpenses': '1650.00', 'leftover': '707.60'},
      };

  static Map<String, Object?> planSalary({
    String? amount = '3100.00',
    String? date = '2026-10-05',
    bool predicted = false,
    String source = 'RECEIVED',
    String? expectedAmount,
  }) =>
      {'amount': amount, 'date': date, 'predicted': predicted, 'source': source, 'expectedAmount': expectedAmount};

  static Map<String, Object?> planBill({
    required String accountId,
    required String cardName,
    required String total,
    required List<Map<String, Object?>> items,
    String? institution,
    String digits = '5162',
    String dueDate = '2026-10-10',
    String? closingDate = '2026-10-03',
    bool open = false,
    String otherCharges = '0.00',
  }) =>
      {
        'accountId': accountId,
        'connectionId': connectionId,
        'institutionName': institution,
        'institutionImageUrl': null,
        'cardName': cardName,
        'numberLastDigits': digits,
        'dueDate': dueDate,
        'closingDate': closingDate,
        'total': total,
        'open': open,
        'items': items,
        'otherCharges': otherCharges,
      };

  static Map<String, Object?> planItem(String description, String amount,
          {String? category, (int, int)? installment, String date = '2026-09-20', bool refund = false}) =>
      {
        'transactionId': 'tx-$description',
        'date': date,
        'description': description,
        'amount': amount,
        'direction': refund ? 'INFLOW' : 'OUTFLOW',
        'category': category,
        'installmentNumber': installment?.$1,
        'installmentTotal': installment?.$2,
      };

  static Map<String, Object?> planFixed(String id, String name, String amount, int dayOfMonth,
          {String? date, String? category}) =>
      {
        'id': id,
        'name': name,
        'amount': amount,
        'dayOfMonth': dayOfMonth,
        'date': date ?? '2026-10-${dayOfMonth.toString().padLeft(2, '0')}',
        'category': category,
      };
}
