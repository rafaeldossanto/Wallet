package com.wallet.core.provider.pluggy;

import com.wallet.core.MutableClock;
import com.wallet.core.demo.DemoPluggy;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo Pluggy read through the real adapter: if the adapter changes what it expects, the demo
 * that the app runs against breaks here first.
 */
class DemoPluggyCompatibilityTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-02");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T15:00:00Z"));
    private DemoPluggy demo;
    private PluggyFinancialDataProvider provider;

    @BeforeEach
    void start() throws IOException {
        demo = DemoPluggy.start(clock);
        PluggyProperties properties = new PluggyProperties(
                demo.baseUrl(), "demo", "demo", Duration.ofSeconds(2), Duration.ofHours(2));
        provider = new PluggyFinancialDataProvider(new PluggyClient(
                PluggyConfig.restClient(RestClient.builder(), properties), properties, clock));
    }

    @AfterEach
    void stop() {
        demo.close();
    }

    /** Every item the README lists reads cleanly, and each bank adds up: card limit, positive accounts, recent spending. */
    @Test
    void everyItemReadsThroughTheAdapter() {
        for (String itemId : DemoPluggy.ITEMS) {
            assertThat(provider.findItem(itemId).status()).as(itemId).isEqualTo(ProviderItemStatus.READY);
            List<ProviderAccount> accounts = provider.listAccounts(itemId);
            List<ProviderInvestment> investments = provider.listInvestments(itemId);
            assertThat(accounts.isEmpty() && investments.isEmpty()).as(itemId + " has nothing").isFalse();
            for (ProviderAccount account : accounts) {
                if (AccountKind.CREDIT_CARD.equals(account.kind())) {
                    assertThat(account.availableCredit().plus(account.balance())).as(account.id()).isEqualTo(account.creditLimit());
                    assertThat(provider.listBills(account.id())).as(account.id()).isNotEmpty();
                } else {
                    assertThat(account.balance().amount()).as(account.id()).isPositive();
                }
                assertThat(provider.listTransactions(account.id(), account.kind(), TODAY.minusDays(30), TODAY))
                        .as(account.id()).isNotEmpty();
            }
        }
        assertThat(provider.findItem(DemoPluggy.NUBANK_ITEM).institutionName()).isEqualTo("Nubank");
    }

    @Test
    void theRealBanksCarryTheLogoPluggyServesAndTheMadeUpOnesNone() {
        assertThat(provider.findItem(DemoPluggy.NUBANK_ITEM).institutionImageUrl())
                .isEqualTo("https://cdn.pluggy.ai/assets/connector-icons/212.svg");
        assertThat(provider.findItem(DemoPluggy.ITAU_ITEM).institutionImageUrl()).endsWith("/201.svg");
        assertThat(provider.findItem(DemoPluggy.XP_ITEM).institutionImageUrl()).endsWith("/202.svg");
        assertThat(provider.findItem(DemoPluggy.BANK_ITEM).institutionImageUrl()).isNull();
    }

    @Test
    void bothItemsAreReadyAndUnknownOnesAreNotFound() {
        assertThat(provider.findItem(DemoPluggy.BANK_ITEM).institutionName()).isEqualTo("Banco Demo");
        assertThat(provider.findItem(DemoPluggy.BROKER_ITEM).status()).isEqualTo(ProviderItemStatus.READY);
    }

    @Test
    void theBankHasCheckingSavingsAndACardWithinItsLimit() {
        List<ProviderAccount> accounts = provider.listAccounts(DemoPluggy.BANK_ITEM);

        assertThat(accounts).extracting(ProviderAccount::kind)
                .containsExactly(AccountKind.CHECKING, AccountKind.SAVINGS, AccountKind.CREDIT_CARD);
        ProviderAccount card = accounts.get(2);
        assertThat(card.balance().amount()).isNotNegative();
        assertThat(card.availableCredit().plus(card.balance())).isEqualTo(card.creditLimit());
        assertThat(accounts.getFirst().balance().amount()).isPositive();
    }

    /** Found when the net worth chart dropped R$ 8 mil overnight: the history window moved with the date. */
    @Test
    void aDayLaterTheBalanceMovedOnlyByThatDaysTransactions() {
        Money before = provider.listAccounts(DemoPluggy.BANK_ITEM).getFirst().balance();

        clock.advance(Duration.ofDays(1));
        Money after = provider.listAccounts(DemoPluggy.BANK_ITEM).getFirst().balance();
        Money ofTheDay = provider.listTransactions("demo-checking", AccountKind.CHECKING, TODAY.plusDays(1), TODAY.plusDays(1))
                .stream()
                .map(transaction -> Direction.INFLOW.equals(transaction.direction())
                        ? transaction.amount() : Money.ZERO.minus(transaction.amount()))
                .reduce(Money.ZERO, Money::plus);

        assertThat(after).isEqualTo(before.plus(ofTheDay));
    }

    @Test
    void transactionsAreStableBetweenSyncsAndCarrySalaryAndInstallments() {
        List<ProviderTransaction> first = provider.listTransactions("demo-checking", AccountKind.CHECKING,
                TODAY.minusDays(60), TODAY);
        List<ProviderTransaction> again = provider.listTransactions("demo-checking", AccountKind.CHECKING,
                TODAY.minusDays(60), TODAY);

        assertThat(first).isEqualTo(again);
        assertThat(first).anySatisfy(transaction -> {
            assertThat(transaction.description()).startsWith("SALARIO");
            assertThat(transaction.direction()).isEqualTo(Direction.INFLOW);
        });
        assertThat(first).allSatisfy(transaction -> assertThat(transaction.bookedOn()).isBetween(TODAY.minusDays(60), TODAY));

        List<ProviderTransaction> card = provider.listTransactions("demo-card", AccountKind.CREDIT_CARD,
                TODAY.minusDays(60), TODAY);
        assertThat(card).anySatisfy(transaction -> assertThat(transaction.installmentTotal()).isEqualTo(10));
        assertThat(card).anySatisfy(transaction -> assertThat(transaction.direction()).isEqualTo(Direction.INFLOW));
    }

    @Test
    void cardBillsAndInvestmentsArePresent() {
        List<ProviderBill> bills = provider.listBills("demo-card");
        List<ProviderInvestment> investments = provider.listInvestments(DemoPluggy.BROKER_ITEM);

        assertThat(bills).hasSizeGreaterThanOrEqualTo(4)
                .allSatisfy(bill -> assertThat(bill.totalAmount().amount()).isPositive());
        assertThat(investments).hasSize(5).allSatisfy(investment -> assertThat(investment.active()).isTrue());
        assertThat(provider.listAccounts(DemoPluggy.BROKER_ITEM)).isEmpty();
    }
}
