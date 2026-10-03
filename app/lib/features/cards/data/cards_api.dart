import '../../../core/api/api_client.dart';
import '../../../core/api/json.dart';
import '../../../core/models/credit_card.dart';

class CardsApi {
  CardsApi(this._api);

  final ApiClient _api;

  Future<List<CreditCard>> cards() async =>
      [for (final card in Json.listOf(await _api.get('/api/cards'))) CreditCard.fromJson(card)];

  /// Newest first.
  Future<List<Bill>> bills(String accountId) async {
    final bills = [
      for (final bill in Json.listOf(await _api.get('/api/cards/${Uri.encodeComponent(accountId)}/bills')))
        Bill.fromJson(bill),
    ];
    return bills..sort((first, second) => second.dueDate.compareTo(first.dueDate));
  }
}
