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
}
