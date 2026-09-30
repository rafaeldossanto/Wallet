package com.wallet.core.provider.pluggy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.wallet.core.MutableClock;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderErrors;
import com.wallet.core.provider.ProviderInvestment;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.InvestmentKind;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter against a fake Pluggy. Runs without Docker or Spring, so it stays in the unit suite.
 */
class PluggyFinancialDataProviderTest {

    private static final String ITEM_ID = "5b3f1c2e-7c1a-4c55-9d7e-1a2b3c4d5e6f";
    private static final String API_KEY = "fake-api-key-1";

    @RegisterExtension
    static WireMockExtension pluggy = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-29T12:00:00Z"));
    private PluggyFinancialDataProvider provider;

    @BeforeEach
    void setUp() {
        provider = providerWith("client-id", "client-secret");
        pluggy.stubFor(post("/auth")
                .withRequestBody(equalToJson("{\"clientId\":\"client-id\",\"clientSecret\":\"client-secret\",\"nonExpiring\":false}"))
                .willReturn(okJson("{\"apiKey\":\"" + API_KEY + "\"}")));
    }

    @Test
    void findsItemWithInstitutionAndStatus() {
        stubItem();

        ProviderItem item = provider.findItem(ITEM_ID);

        assertThat(item.institutionName()).isEqualTo("Nubank");
        assertThat(item.institutionImageUrl()).endsWith("212.svg");
        assertThat(item.status()).isEqualTo(ProviderItemStatus.READY);
        assertThat(item.lastUpdatedAt()).isEqualTo(Instant.parse("2026-09-29T09:00:00Z"));
        assertThat(item.consentExpiresAt()).isEqualTo(Instant.parse("2027-09-01T12:00:00Z"));
        pluggy.verify(getRequestedFor(urlEqualTo("/items/" + ITEM_ID)).withHeader("X-API-KEY", equalTo(API_KEY)));
    }

    @Test
    void authenticatesOnceAndReusesTheApiKey() {
        stubItem();

        provider.findItem(ITEM_ID);
        provider.findItem(ITEM_ID);

        pluggy.verify(1, postRequestedFor(urlEqualTo("/auth")));
    }

    @Test
    void renewsTheApiKeyBeforeItExpires() {
        stubItem();
        provider.findItem(ITEM_ID);

        clock.advance(Duration.ofMinutes(111));
        provider.findItem(ITEM_ID);

        pluggy.verify(2, postRequestedFor(urlEqualTo("/auth")));
    }

    @Test
    void retriesOnceWithANewApiKeyWhenTheCurrentOneIsRejected() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).inScenario("revoked key")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(unauthorized())
                .willSetStateTo("key renewed"));
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).inScenario("revoked key")
                .whenScenarioStateIs("key renewed")
                .willReturn(okJson(fixture("item.json"))));

        assertThat(provider.findItem(ITEM_ID).institutionName()).isEqualTo("Nubank");
        pluggy.verify(2, postRequestedFor(urlEqualTo("/auth")));
    }

    @Test
    void unknownItemIsNotFound() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(notFound()));

        assertErrorCode(() -> provider.findItem(ITEM_ID), ProviderErrors.NOT_FOUND);
    }

    @Test
    void serverErrorsAndDroppedConnectionsAreUnavailable() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(serviceUnavailable()));
        assertErrorCode(() -> provider.findItem(ITEM_ID), ProviderErrors.UNAVAILABLE);

        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertErrorCode(() -> provider.findItem(ITEM_ID), ProviderErrors.UNAVAILABLE);
    }

    @Test
    void wrongCredentialsAreReportedAsAuthFailure() {
        pluggy.stubFor(post("/auth").willReturn(unauthorized()));

        assertErrorCode(() -> provider.findItem(ITEM_ID), ProviderErrors.AUTH_FAILED);
    }

    @Test
    void withoutCredentialsNothingIsSentToPluggy() {
        PluggyFinancialDataProvider unconfigured = providerWith("", "");

        assertErrorCode(() -> unconfigured.findItem(ITEM_ID), ProviderErrors.NOT_CONFIGURED);
        pluggy.verify(0, postRequestedFor(urlEqualTo("/auth")));
    }

    @Test
    void mapsCheckingAccountAndCreditCard() {
        pluggy.stubFor(get(urlPathEqualTo("/accounts")).withQueryParam("itemId", equalTo(ITEM_ID))
                .willReturn(okJson(fixture("accounts.json"))));

        List<ProviderAccount> accounts = provider.listAccounts(ITEM_ID);

        ProviderAccount checking = accounts.get(0);
        assertThat(checking.kind()).isEqualTo(AccountKind.CHECKING);
        assertThat(checking.name()).isEqualTo("Conta Corrente");
        assertThat(checking.numberLastDigits()).isEqualTo("3456");
        assertThat(checking.balance()).isEqualTo(Money.of("1523.47"));
        assertThat(checking.creditLimit()).isNull();

        ProviderAccount card = accounts.get(1);
        assertThat(card.kind()).isEqualTo(AccountKind.CREDIT_CARD);
        assertThat(card.balance()).isEqualTo(Money.of("845.90"));
        assertThat(card.creditLimit()).isEqualTo(Money.of("5000"));
        assertThat(card.availableCredit()).isEqualTo(Money.of("4154.10"));
    }

    @Test
    void followsTheCursorAndKeepsAmountsPositiveWithADirection() {
        pluggy.stubFor(get(urlPathEqualTo("/v2/transactions"))
                .withQueryParam("accountId", equalTo("acc-checking"))
                .withQueryParam("dateFrom", equalTo("2026-09-01"))
                .withQueryParam("dateTo", equalTo("2026-09-29"))
                .withQueryParam("after", com.github.tomakehurst.wiremock.client.WireMock.absent())
                .willReturn(okJson(fixture("transactions-checking-page1.json"))));
        pluggy.stubFor(get(urlPathEqualTo("/v2/transactions"))
                .withQueryParam("after", equalTo("cursor+2/"))
                .willReturn(okJson(fixture("transactions-checking-page2.json"))));

        List<ProviderTransaction> transactions = provider.listTransactions(
                "acc-checking", AccountKind.CHECKING, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-29"));

        assertThat(transactions).extracting(ProviderTransaction::id).containsExactly("tx-1", "tx-2", "tx-3");
        ProviderTransaction pix = transactions.get(0);
        assertThat(pix.amount()).isEqualTo(Money.of("45.90"));
        assertThat(pix.direction()).isEqualTo(Direction.OUTFLOW);
        assertThat(pix.bookedOn()).isEqualTo(LocalDate.parse("2026-09-20"));
        assertThat(pix.reconcileId()).isEqualTo("of-tx-1");
        assertThat(transactions.get(1).direction()).isEqualTo(Direction.INFLOW);
        assertThat(transactions.get(2).status()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void creditCardSignDecidesTheDirection() {
        pluggy.stubFor(get(urlPathEqualTo("/v2/transactions")).withQueryParam("accountId", equalTo("acc-card"))
                .willReturn(okJson(fixture("transactions-card.json"))));

        List<ProviderTransaction> transactions = provider.listTransactions(
                "acc-card", AccountKind.CREDIT_CARD, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-29"));

        ProviderTransaction purchase = transactions.get(0);
        assertThat(purchase.direction()).isEqualTo(Direction.OUTFLOW);
        assertThat(purchase.amount()).isEqualTo(Money.of("120"));
        assertThat(purchase.installmentNumber()).isEqualTo(3);
        assertThat(purchase.installmentTotal()).isEqualTo(10);
        assertThat(purchase.billId()).isEqualTo("bill-1");

        ProviderTransaction refund = transactions.get(1);
        assertThat(refund.direction()).isEqualTo(Direction.INFLOW);
        assertThat(refund.amount()).isEqualTo(Money.of("30"));
    }

    @Test
    void mapsBills() {
        pluggy.stubFor(get(urlPathEqualTo("/bills")).withQueryParam("accountId", equalTo("acc-card"))
                .willReturn(okJson(fixture("bills.json"))));

        ProviderBill bill = provider.listBills("acc-card").getFirst();

        assertThat(bill.accountId()).isEqualTo("acc-card");
        assertThat(bill.dueDate()).isEqualTo(LocalDate.parse("2026-10-10"));
        assertThat(bill.closingDate()).isEqualTo(LocalDate.parse("2026-10-03"));
        assertThat(bill.totalAmount()).isEqualTo(Money.of("845.90"));
        assertThat(bill.minimumPayment()).isEqualTo(Money.of("126.88"));
    }

    @Test
    void readsEveryPageOfInvestments() {
        pluggy.stubFor(get(urlPathEqualTo("/investments")).withQueryParam("page", equalTo("1"))
                .willReturn(okJson(fixture("investments-page1.json"))));
        pluggy.stubFor(get(urlPathEqualTo("/investments")).withQueryParam("page", equalTo("2"))
                .willReturn(okJson(fixture("investments-page2.json"))));

        List<ProviderInvestment> investments = provider.listInvestments(ITEM_ID);

        assertThat(investments).extracting(ProviderInvestment::kind)
                .containsExactly(InvestmentKind.FIXED_INCOME, InvestmentKind.TREASURY, InvestmentKind.FUND);
        assertThat(investments.get(0).balance()).isEqualTo(Money.of("10512.33"));
        assertThat(investments.get(0).amountInvested()).isEqualTo(Money.of("10000"));
        assertThat(investments.get(0).dueDate()).isEqualTo(LocalDate.parse("2028-01-15"));
        assertThat(investments.get(2).active()).isFalse();
    }

    @Test
    void deletesTheItem() {
        pluggy.stubFor(delete(urlEqualTo("/items/" + ITEM_ID)).willReturn(aResponse().withStatus(200)));

        provider.deleteItem(ITEM_ID);

        pluggy.verify(deleteRequestedFor(urlEqualTo("/items/" + ITEM_ID)).withHeader("X-API-KEY", equalTo(API_KEY)));
    }

    private PluggyFinancialDataProvider providerWith(String clientId, String clientSecret) {
        PluggyProperties properties = new PluggyProperties(
                pluggy.baseUrl(), clientId, clientSecret, Duration.ofSeconds(2), Duration.ofHours(2));
        RestClient restClient = PluggyConfig.restClient(RestClient.builder(), properties);
        return new PluggyFinancialDataProvider(new PluggyClient(restClient, properties, clock));
    }

    private void stubItem() {
        pluggy.stubFor(get(urlEqualTo("/items/" + ITEM_ID)).willReturn(okJson(fixture("item.json"))));
    }

    private static void assertErrorCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", code);
    }

    private static String fixture(String name) {
        try (InputStream stream = PluggyFinancialDataProviderTest.class.getResourceAsStream("/pluggy/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
