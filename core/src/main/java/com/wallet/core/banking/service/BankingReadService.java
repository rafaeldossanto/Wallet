package com.wallet.core.banking.service;

import com.wallet.core.banking.AccountSummary;
import com.wallet.core.banking.BalancePoint;
import com.wallet.core.banking.BankingQueries;
import com.wallet.core.banking.CardBill;
import com.wallet.core.banking.CashFlow;
import com.wallet.core.banking.CategorySpending;
import com.wallet.core.banking.DailySpending;
import com.wallet.core.banking.RecurringDebit;
import com.wallet.core.banking.SalaryPayment;
import com.wallet.core.banking.dto.AccountResponse;
import com.wallet.core.banking.dto.BillResponse;
import com.wallet.core.banking.dto.CreditCardResponse;
import com.wallet.core.banking.dto.TransactionResponse;
import com.wallet.core.banking.entity.Account;
import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.CreditCardBill;
import com.wallet.core.banking.mapper.BankingReadMapper;
import com.wallet.core.banking.repository.AccountRepository;
import com.wallet.core.banking.repository.AccountTransactionRepository;
import com.wallet.core.banking.repository.BankingAggregates;
import com.wallet.core.banking.repository.CreditCardBillRepository;
import com.wallet.core.banking.repository.TransactionDescriptions;
import com.wallet.core.banking.repository.TransactionFilter;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.text.TextKeys;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.shared.web.PageQuery;
import com.wallet.core.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class BankingReadService implements BankingQueries {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("bookedOn"), Sort.Order.desc("createdAt"),
            Sort.Order.asc("id"));

    /**
     * Rows one text search may decrypt. A busy month has a few hundred transactions; this is a
     * person with several accounts over the longest search period (93 days), and costs a few
     * milliseconds of decryption.
     */
    static final int MAX_SEARCHED_ROWS = 5_000;

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final TransactionDescriptions descriptions;
    private final CreditCardBillRepository bills;
    private final BankingAggregates aggregates;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(BankingReadMapper::toResponse).toList();
    }

    /** The database filters and pages. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> listTransactions(TransactionFilter filter, PageQuery page) {
        Page<AccountTransaction> result = transactions.findAll(filter.toSpecification(), page.toPageable(NEWEST_FIRST));
        return PageResponse.of(result.map(BankingReadMapper::toResponse).getContent(), page, result.getTotalElements());
    }

    /**
     * The description is encrypted, so the database cannot match it: the period's ids and
     * descriptions are read and matched in memory, accents and case ignored, and only the page
     * found is loaded in full. The total is exact, so the app's paging stays right.
     *
     * <p>A period with more than {@link #MAX_SEARCHED_ROWS} transactions is refused as
     * {@code period.too_long} instead of searched in part: a search that silently skips rows would
     * say "nothing found" about rows it never read.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> searchTransactions(TransactionFilter filter, String q, PageQuery page) {
        List<TransactionDescriptions.Row> rows = descriptions.of(filter, MAX_SEARCHED_ROWS + 1);
        if (rows.size() > MAX_SEARCHED_ROWS) {
            throw new DomainException(ErrorType.UNPROCESSABLE, "period.too_long",
                    "A text search reads at most " + MAX_SEARCHED_ROWS + " transactions: narrow the period or the filters");
        }
        String needle = TextKeys.fold(q);
        List<UUID> matching = rows.stream()
                .filter(row -> nonNull(row.description()) && TextKeys.fold(row.description()).contains(needle))
                .map(TransactionDescriptions.Row::id)
                .toList();
        int start = Math.min((page.page() - 1) * page.pageSize(), matching.size());
        int end = Math.min(start + page.pageSize(), matching.size());
        List<UUID> pageIds = matching.subList(start, end);
        Map<UUID, AccountTransaction> found = transactions.findAllById(pageIds).stream()
                .collect(Collectors.toMap(AccountTransaction::getId, Function.identity()));
        return PageResponse.of(pageIds.stream().map(found::get).map(BankingReadMapper::toResponse).toList(),
                page, matching.size());
    }

    @Transactional(readOnly = true)
    public List<CreditCardResponse> listCreditCards(UUID userId) {
        LocalDate today = WalletTime.today(clock);
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getKind().isCreditCard())
                .map(card -> BankingReadMapper.toCardResponse(card, currentBill(card, today)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BillResponse> listBills(UUID userId, UUID accountId) {
        Account card = accounts.findByIdAndUserId(accountId, userId)
                .filter(account -> account.getKind().isCreditCard())
                .orElseThrow(() -> new DomainException(ErrorType.NOT_FOUND, "account.not_found", "No such credit card"));
        return bills.findByAccountIdOrderByDueDateDesc(card.getId()).stream().map(BankingReadMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummary> accounts(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(BankingReadMapper::toSummary).toList();
    }

    @Override
    public CashFlow cashFlow(UUID userId, DateRange period) {
        return new CashFlow(aggregates.bankTotal(userId, Direction.INFLOW, period),
                aggregates.bankTotal(userId, Direction.OUTFLOW, period));
    }

    @Override
    public List<CategorySpending> spendingByCategory(UUID userId, DateRange period) {
        return aggregates.spendingByCategory(userId, period);
    }

    @Override
    public List<DailySpending> spendingByDay(UUID userId, DateRange period) {
        return aggregates.spendingByDay(userId, period);
    }

    @Override
    public List<BalancePoint> balanceHistory(UUID userId, DateRange period) {
        List<BankingAggregates.SnapshotRow> rows = aggregates.snapshotsIn(userId, period);
        Map<UUID, BankingAggregates.SnapshotRow> latestPerAccount = new HashMap<>();
        List<BalancePoint> points = new ArrayList<>();
        int next = 0;
        for (LocalDate day : period.days()) {
            while (next < rows.size() && !rows.get(next).date().isAfter(day)) {
                latestPerAccount.put(rows.get(next).accountId(), rows.get(next));
                next++;
            }
            Money cash = Money.ZERO;
            Money debt = Money.ZERO;
            for (BankingAggregates.SnapshotRow row : latestPerAccount.values()) {
                if (AccountKind.CREDIT_CARD.equals(row.kind())) {
                    debt = debt.plus(row.balance());
                } else {
                    cash = cash.plus(row.balance());
                }
            }
            points.add(new BalancePoint(day, cash, debt));
        }
        return points;
    }

    @Override
    public List<SalaryPayment> salaryPayments(UUID userId, DateRange period) {
        return aggregates.salaryPayments(userId, period);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardBill> billsDueIn(UUID userId, YearMonth month) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getKind().isCreditCard())
                .flatMap(card -> billsDueIn(card, month).stream())
                .sorted(Comparator.comparing(CardBill::dueDate)
                        .thenComparing(CardBill::cardName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<CardBill> billsDueIn(Account card, YearMonth month) {
        List<CreditCardBill> history = bills.findByAccountIdOrderByDueDateAsc(card.getId());
        Set<String> known = history.stream().map(CreditCardBill::getProviderBillId).collect(Collectors.toSet());
        List<CardBill> due = new ArrayList<>();
        for (int index = 0; index < history.size(); index++) {
            CreditCardBill bill = history.get(index);
            if (YearMonth.from(bill.getDueDate()).equals(month)) {
                DateRange cycle = BillCycles.cycleOf(bill, index == 0 ? null : history.get(index - 1));
                List<AccountTransaction> candidates = new ArrayList<>(transactions
                        .findByAccountIdAndDeletedAtIsNullAndProviderBillId(card.getId(), bill.getProviderBillId()));
                candidates.addAll(transactionsIn(card, cycle));
                due.add(BankingReadMapper.toCardBill(card, bill.getDueDate(), bill.getClosingDate(), bill.getTotalAmount(),
                        false, BillCycles.itemsOf(bill.getProviderBillId(), cycle, known, candidates)));
            }
        }
        if (due.isEmpty() && !history.isEmpty()) {
            openBillDueIn(card, history.getLast(), known, month).ifPresent(due::add);
        }
        return due;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecurringDebit> recurringDebits(UUID userId, DateRange period, int minMonths) {
        List<AccountTransaction> outflows = accounts.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(account -> !account.getKind().isCreditCard())
                .flatMap(account -> transactionsIn(account, period).stream())
                .filter(transaction -> Direction.OUTFLOW.equals(transaction.getDirection()))
                .toList();
        return RecurringDebits.find(outflows, period, minMonths);
    }

    /**
     * The bill after the last closed one, when it falls due in {@code month}: what was bought in
     * its cycle so far. Nothing when nothing was bought yet.
     */
    private Optional<CardBill> openBillDueIn(Account card, CreditCardBill last, Set<String> known, YearMonth month) {
        LocalDate dueDate = last.getDueDate().plusMonths(1);
        if (!YearMonth.from(dueDate).equals(month)) {
            return Optional.empty();
        }
        DateRange cycle = BillCycles.openCycleAfter(last);
        List<AccountTransaction> items = BillCycles.itemsOf(null, cycle, known, transactionsIn(card, cycle));
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Money total = Money.ZERO;
        for (AccountTransaction item : items) {
            total = Direction.INFLOW.equals(item.getDirection()) ? total.minus(item.getAmount()) : total.plus(item.getAmount());
        }
        return Optional.of(BankingReadMapper.toCardBill(card, dueDate, cycle.to(), total, true, items));
    }

    private List<AccountTransaction> transactionsIn(Account account, DateRange period) {
        return transactions.findByAccountIdAndDeletedAtIsNullAndBookedOnBetween(account.getId(), period.from(), period.to());
    }

    /** The next bill to pay; when every bill is past due, the most recent one. */
    private CreditCardBill currentBill(Account card, LocalDate today) {
        List<CreditCardBill> cardBills = bills.findByAccountIdOrderByDueDateDesc(card.getId());
        return cardBills.stream()
                .filter(bill -> !bill.getDueDate().isBefore(today))
                .min(Comparator.comparing(CreditCardBill::getDueDate))
                .orElse(cardBills.isEmpty() ? null : cardBills.getFirst());
    }
}
