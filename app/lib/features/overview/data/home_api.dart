import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/format/dates.dart';
import '../../../core/models/account.dart';
import '../../../core/models/credit_card.dart';
import '../../../core/models/transaction.dart';
import '../../../core/money/money.dart';

class Overview {
  const Overview({
    required this.netWorth,
    required this.cashBalance,
    required this.creditCardDebt,
    required this.investmentsTotal,
    required this.month,
    required this.monthIncome,
    required this.monthExpenses,
    this.lastSyncedAt,
  });

  factory Overview.fromJson(Json json) => Overview(
        netWorth: json.money('netWorth'),
        cashBalance: json.money('cashBalance'),
        creditCardDebt: json.money('creditCardDebt'),
        investmentsTotal: json.money('investmentsTotal'),
        month: Dates.parseMonth(json.string('month')),
        monthIncome: json.money('monthIncome'),
        monthExpenses: json.money('monthExpenses'),
        lastSyncedAt: json.instantOrNull('lastSyncedAt'),
      );

  final Money netWorth;
  final Money cashBalance;
  final Money creditCardDebt;
  final Money investmentsTotal;
  final DateTime month;
  final Money monthIncome;
  final Money monthExpenses;
  final DateTime? lastSyncedAt;

  Money get monthBalance => monthIncome - monthExpenses;
}

/// A connection with its bank accounts (cards are listed on their own).
class InstitutionAccounts {
  const InstitutionAccounts({
    required this.connectionId,
    required this.accounts,
    this.name,
    this.imageUrl,
    this.status,
    this.lastSyncedAt,
  });

  factory InstitutionAccounts.fromJson(Json json) => InstitutionAccounts(
        connectionId: json.string('connectionId'),
        name: json.stringOrNull('institutionName'),
        imageUrl: json.stringOrNull('institutionImageUrl'),
        status: json.stringOrNull('status'),
        lastSyncedAt: json.instantOrNull('lastSyncedAt'),
        accounts: [for (final account in json.list('accounts')) Account.fromJson(account)],
      );

  final String connectionId;
  final String? name;
  final String? imageUrl;
  final String? status;
  final DateTime? lastSyncedAt;
  final List<Account> accounts;

  bool get needsAttention => status == 'NEEDS_ATTENTION';

  Money get total => accounts.map((account) => account.balance).sum();
}

/// The home screen in one answer. A part in [unavailable] failed on the server; the others are
/// still good.
class HomeData {
  const HomeData({
    required this.institutions,
    required this.creditCards,
    required this.recentTransactions,
    required this.unavailable,
    this.overview,
  });

  static const overviewPart = 'overview';
  static const accountsPart = 'accounts';
  static const connectionsPart = 'connections';
  static const creditCardsPart = 'creditCards';
  static const recentTransactionsPart = 'recentTransactions';

  factory HomeData.fromJson(Json json) => HomeData(
        overview: json.objectOrNull('overview') == null ? null : Overview.fromJson(json.object('overview')),
        institutions: [for (final item in json.list('institutions')) InstitutionAccounts.fromJson(item)],
        creditCards: [for (final card in json.list('creditCards')) CreditCard.fromJson(card)],
        recentTransactions: [for (final item in json.list('recentTransactions')) Transaction.fromJson(item)],
        unavailable: json.strings('unavailable').toSet(),
      );

  final Overview? overview;
  final List<InstitutionAccounts> institutions;
  final List<CreditCard> creditCards;
  final List<Transaction> recentTransactions;
  final Set<String> unavailable;

  bool isUnavailable(String part) => unavailable.contains(part);

  /// Nothing linked yet, and nothing failed that could be hiding it.
  bool get hasNoConnections => institutions.isEmpty && creditCards.isEmpty && unavailable.isEmpty;

  /// Account and card names, to label the recent transactions.
  Map<String, String> get accountNames => {
        for (final institution in institutions)
          for (final account in institution.accounts) account.id: account.name,
        for (final card in creditCards) card.accountId: card.name,
      };
}

class HomeApi {
  HomeApi(this._api);

  final ApiClient _api;

  Future<HomeData> home() async => HomeData.fromJson(Json.of(await _api.get('/api/home')));
}
