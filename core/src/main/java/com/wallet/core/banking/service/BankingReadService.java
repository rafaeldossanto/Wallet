package com.wallet.core.banking.service;

import com.wallet.core.banking.AccountSummary;
import com.wallet.core.banking.BalancePoint;
import com.wallet.core.banking.BankingQueries;
import com.wallet.core.banking.CashFlow;
import com.wallet.core.banking.CategorySpending;
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
import com.wallet.core.banking.repository.TransactionFilter;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import com.wallet.core.shared.time.WalletTime;
import com.wallet.core.shared.web.PageQuery;
import com.wallet.core.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasText;

@Service
@RequiredArgsConstructor
public class BankingReadService implements BankingQueries {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("bookedOn"), Sort.Order.desc("createdAt"),
            Sort.Order.asc("id"));

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final CreditCardBillRepository bills;
    private final BankingAggregates aggregates;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID userId) {
        return accounts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(BankingReadMapper::toResponse).toList();
    }

    /**
     * Without {@code q} the database pages. With {@code q} every row of the period is decrypted
     * and matched in memory, accents and case ignored; the period limit keeps that small.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> searchTransactions(TransactionFilter filter, String q, PageQuery page) {
        if (!hasText(q)) {
            Page<AccountTransaction> result = transactions.findAll(filter.toSpecification(), page.toPageable(NEWEST_FIRST));
            return PageResponse.of(result.map(BankingReadMapper::toResponse).getContent(), page, result.getTotalElements());
        }
        String needle = fold(q);
        List<AccountTransaction> matching = transactions.findAll(filter.toSpecification(), NEWEST_FIRST).stream()
                .filter(transaction -> nonNull(transaction.getDescription()) && fold(transaction.getDescription()).contains(needle))
                .toList();
        int start = Math.min((page.page() - 1) * page.pageSize(), matching.size());
        int end = Math.min(start + page.pageSize(), matching.size());
        return PageResponse.of(matching.subList(start, end).stream().map(BankingReadMapper::toResponse).toList(),
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
    public List<BalancePoint> balanceHistory(UUID userId, DateRange period) {
        List<BankingAggregates.SnapshotRow> rows = aggregates.snapshotsUpTo(userId, period.to());
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

    /** The next bill to pay; when every bill is past due, the most recent one. */
    private CreditCardBill currentBill(Account card, LocalDate today) {
        List<CreditCardBill> cardBills = bills.findByAccountIdOrderByDueDateDesc(card.getId());
        return cardBills.stream()
                .filter(bill -> !bill.getDueDate().isBefore(today))
                .min(Comparator.comparing(CreditCardBill::getDueDate))
                .orElse(cardBills.isEmpty() ? null : cardBills.getFirst());
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
