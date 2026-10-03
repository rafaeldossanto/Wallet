import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/format/dates.dart';
import '../../../core/models/account.dart';
import '../../../core/models/transaction.dart';

class TransactionFilters {
  const TransactionFilters({required this.month, this.accountId, this.direction, this.query = ''});

  final DateTime month;
  final String? accountId;
  final Direction? direction;
  final String query;

  TransactionFilters copyWith({DateTime? month, String? Function()? accountId, Direction? Function()? direction, String? query}) =>
      TransactionFilters(
        month: month ?? this.month,
        accountId: accountId == null ? this.accountId : accountId(),
        direction: direction == null ? this.direction : direction(),
        query: query ?? this.query,
      );
}

class TransactionsApi {
  TransactionsApi(this._api);

  static const pageSize = 50;

  final ApiClient _api;

  Future<TransactionPage> page(TransactionFilters filters, {required int page}) async =>
      TransactionPage.fromJson(Json.of(await _api.get('/api/transactions', query: {
        'from': Dates.iso(Dates.firstOfMonth(filters.month)),
        'to': Dates.iso(Dates.lastOfMonth(filters.month)),
        'accountId': filters.accountId,
        'direction': filters.direction?.apiValue,
        'q': filters.query.trim(),
        'page': page,
        'pageSize': pageSize,
      })));

  Future<List<Account>> accounts() async =>
      [for (final account in Json.listOf(await _api.get('/api/accounts'))) Account.fromJson(account)];
}
