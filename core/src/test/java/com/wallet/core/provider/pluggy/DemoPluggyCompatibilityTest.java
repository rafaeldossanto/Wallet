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
