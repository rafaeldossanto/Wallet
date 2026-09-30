package com.wallet.core.provider.pluggy;

import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Pluggy JSON to Wallet records. Two decisions live here and nowhere else:
 * the sign convention and how a Pluggy timestamp becomes a calendar date.
 */
@UtilityClass
class PluggyMapper {

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final String DEFAULT_CURRENCY = "BRL";

    ProviderItem toItem(PluggyDtos.Item item) {
        PluggyDtos.Connector connector = item.connector();
        return new ProviderItem(
                item.id(),
                isNull(connector) ? null : connector.name(),
                isNull(connector) ? null : connector.imageUrl(),
                toItemStatus(item.status()),
                item.lastUpdatedAt(),
                item.consentExpiresAt(),
                item.clientUserId());
    }

    ProviderItemStatus toItemStatus(String status) {
        return switch (isNull(status) ? "" : status) {
            case "UPDATED" -> ProviderItemStatus.READY;
            case "UPDATING", "MERGING" -> ProviderItemStatus.UPDATING;
            case "WAITING_USER_INPUT", "WAITING_USER_ACTION", "LOGIN_ERROR" -> ProviderItemStatus.NEEDS_USER_ACTION;
            default -> ProviderItemStatus.FAILED;
        };
    }

    ProviderAccount toAccount(PluggyDtos.Account account) {
        AccountKind kind = toAccountKind(account.type(), account.subtype());
        PluggyDtos.CreditData credit = kind.isCreditCard() ? account.creditData() : null;
        return new ProviderAccount(
                account.id(),
                account.itemId(),
                kind,
                nonNull(account.name()) ? account.name() : account.marketingName(),
                lastDigits(account.number()),
                currencyOrDefault(account.currencyCode()),
                moneyOrZero(account.balance()),
                isNull(credit) ? null : moneyOrNull(credit.creditLimit()),
                isNull(credit) ? null : moneyOrNull(credit.availableCreditLimit()));
    }

    AccountKind toAccountKind(String type, String subtype) {
        if ("CREDIT".equals(type) || "CREDIT_CARD".equals(subtype)) {
            return AccountKind.CREDIT_CARD;
        }
        return switch (isNull(subtype) ? "" : subtype) {
            case "CHECKING_ACCOUNT" -> AccountKind.CHECKING;
            case "SAVINGS_ACCOUNT" -> AccountKind.SAVINGS;
            default -> AccountKind.OTHER;
        };
    }

    ProviderTransaction toTransaction(PluggyDtos.Transaction transaction, AccountKind accountKind) {
        BigDecimal amount = isNull(transaction.amount()) ? BigDecimal.ZERO : transaction.amount();
        PluggyDtos.CreditCardMetadata card = transaction.creditCardMetadata();
        return new ProviderTransaction(
                transaction.id(),
                transaction.accountId(),
                transaction.providerId(),
                toLocalDate(transaction.date()),
                transaction.description(),
                Money.of(amount.abs()),
                toDirection(transaction.type(), amount, accountKind),
                "PENDING".equals(transaction.status()) ? TransactionStatus.PENDING : TransactionStatus.POSTED,
                transaction.category(),
                isNull(card) ? null : card.installmentNumber(),
                isNull(card) ? null : card.totalInstallments(),
                isNull(card) ? null : card.billId());
    }

    /**
     * Checking and savings: {@code type} is reliable (DEBIT out, CREDIT in).
     * Credit cards: Pluggy's docs say a positive amount is a new charge and a negative one is a
     * payment or refund, so the sign decides. To be confirmed with real data in the T02 spike.
     */
    Direction toDirection(String type, BigDecimal amount, AccountKind accountKind) {
        if (accountKind.isCreditCard()) {
            return amount.signum() < 0 ? Direction.INFLOW : Direction.OUTFLOW;
        }
        if ("CREDIT".equals(type)) {
            return Direction.INFLOW;
        }
        if ("DEBIT".equals(type)) {
            return Direction.OUTFLOW;
        }
        return amount.signum() < 0 ? Direction.OUTFLOW : Direction.INFLOW;
    }

    ProviderBill toBill(PluggyDtos.Bill bill, String accountId) {
        return new ProviderBill(
                bill.id(),
                accountId,
                toLocalDate(bill.dueDate()),
                toLocalDate(bill.billClosingDate()),
                moneyOrZero(bill.totalAmount()),
                moneyOrNull(bill.minimumPaymentAmount()),
                currencyOrDefault(bill.totalAmountCurrencyCode()));
    }

    ProviderInvestment toInvestment(PluggyDtos.Investment investment) {
        return new ProviderInvestment(
                investment.id(),
                investment.itemId(),
                toInvestmentKind(investment.type(), investment.subtype()),
                investment.subtype(),
                investment.name(),
                currencyOrDefault(investment.currencyCode()),
                moneyOrZero(investment.balance()),
                moneyOrNull(investment.amountOriginal()),
                toLocalDate(investment.dueDate()),
                !"TOTAL_WITHDRAWAL".equals(investment.status()));
    }

    InvestmentKind toInvestmentKind(String type, String subtype) {
        if ("TREASURY".equals(subtype)) {
            return InvestmentKind.TREASURY;
        }
        if ("RETIREMENT".equals(subtype)) {
            return InvestmentKind.RETIREMENT;
        }
        return switch (isNull(type) ? "" : type) {
            case "FIXED_INCOME" -> InvestmentKind.FIXED_INCOME;
            case "MUTUAL_FUND" -> InvestmentKind.FUND;
            case "EQUITY", "ETF" -> InvestmentKind.EQUITY;
            default -> InvestmentKind.OTHER;
        };
    }

    /**
     * Pluggy sends dates as UTC timestamps. A timestamp at exactly 00:00 UTC is a plain calendar
     * date; anything else is a real moment and is read in Brazil's time zone, so a purchase at
     * 22:00 in São Paulo (01:00 UTC next day) stays on the day it happened. To be confirmed in T02.
     */
    LocalDate toLocalDate(Instant instant) {
        if (isNull(instant)) {
            return null;
        }
        if (instant.atOffset(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return LocalDate.ofInstant(instant, ZoneOffset.UTC);
        }
        return LocalDate.ofInstant(instant, BRAZIL);
    }

    private static String lastDigits(String number) {
        if (isNull(number)) {
            return null;
        }
        String digits = number.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static String currencyOrDefault(String currencyCode) {
        return isNull(currencyCode) ? DEFAULT_CURRENCY : currencyCode;
    }

    private static Money moneyOrZero(BigDecimal value) {
        return isNull(value) ? Money.ZERO : Money.of(value);
    }

    private static Money moneyOrNull(BigDecimal value) {
        return isNull(value) ? null : Money.of(value);
    }
}
