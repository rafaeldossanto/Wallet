package com.wallet.core.planning;

import com.jayway.jsonpath.JsonPath;
import com.wallet.core.MutableClock;
import com.wallet.core.TestClockConfiguration;
import com.wallet.core.TestUsers;
import com.wallet.core.TestcontainersConfiguration;
import com.wallet.core.connection.ConnectionStatus;
import com.wallet.core.connection.entity.Connection;
import com.wallet.core.connection.repository.ConnectionRepository;
import com.wallet.core.provider.FinancialDataProvider;
import com.wallet.core.provider.ProviderAccount;
import com.wallet.core.provider.ProviderBill;
import com.wallet.core.provider.ProviderItem;
import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.provider.ProviderTransaction;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.finance.TransactionStatus;
import com.wallet.core.shared.money.Money;
import com.wallet.core.sync.SyncRunStatus;
import com.wallet.core.sync.SyncTrigger;
import com.wallet.core.sync.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.wallet.core.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The month's plan over data written by a real sync (provider mocked). Today is 2026-10-15.
 *
 * <p>Salary 3000.00 on 2026-09-05 and 3100.00 on 2026-10-05. Card A closes on the 3rd and is due
 * on the 10th: October's bill is 512.40, of which the purchases explain 500.00 (an installment
 * booked in August and linked to it, plus two purchases of its cycle). Card B closes on the 27th
 * and is due on the 4th: October's bill is 250.00, purchases 270.00 minus a 20.00 refund.
 *
 * <p>The checking account also pays, every month: the condo fee (650.00 on the 9th, on the 10th in
 * August, July to October), the power bill (180.00, 210.00 and 195.50 around the 20th, July to
 * September) and the card bill. A gym paid in September and October only, and a magazine that
 * stopped after September, do not repeat enough.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class PlanningFlowIT {

    private static final Instant TODAY = Instant.parse("2026-10-15T12:00:00Z");

    @Autowired private WebApplicationContext context;
    @Autowired private MutableClock clock;
    @Autowired private SyncService syncService;
    @Autowired private ConnectionRepository connections;

    @MockitoBean
    private FinancialDataProvider provider;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.set(TODAY);
        userId = TestUsers.register(mockMvc);
        String itemId = UUID.randomUUID().toString();
        UUID connectionId = saveConnection(itemId);
        stubProvider(itemId);
        assertThat(syncService.syncNow(connectionId, SyncTrigger.MANUAL)).isEqualTo(SyncRunStatus.SUCCEEDED);
    }

    @Test
    void theMonthSubtractsTheBillsAndTheFixedExpensesFromTheSalary() throws Exception {
        createFixed("{\"name\":\"Faculdade\",\"amount\":\"450.00\",\"dayOfMonth\":10,\"category\":\"Education\"}");
        createFixed("{\"name\":\"Aluguel\",\"amount\":\"1200.00\",\"dayOfMonth\":31}");

        read("/internal/planning/month?month=2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.salary.amount").value("3100.00"))
                .andExpect(jsonPath("$.salary.date").value("2026-10-05"))
                .andExpect(jsonPath("$.salary.predicted").value(false))
                .andExpect(jsonPath("$.salary.source").value("RECEIVED"))
                .andExpect(jsonPath("$.bills", hasSize(2)))
                .andExpect(jsonPath("$.fixedExpenses[*].name", contains("Faculdade", "Aluguel")))
                .andExpect(jsonPath("$.fixedExpenses[1].date").value("2026-10-31"))
                .andExpect(jsonPath("$.totals.salary").value("3100.00"))
                .andExpect(jsonPath("$.totals.bills").value("762.40"))
                .andExpect(jsonPath("$.totals.fixedExpenses").value("1650.00"))
                .andExpect(jsonPath("$.totals.leftover").value("687.60"));
    }

    @Test
    void eachBillListsItsPurchasesAndWhatTheyDoNotExplain() throws Exception {
        read("/internal/planning/month?month=2026-10")
                // Card B first: due on the 4th.
                .andExpect(jsonPath("$.bills[0].cardName").value("Cartão B"))
                .andExpect(jsonPath("$.bills[0].dueDate").value("2026-10-04"))
                .andExpect(jsonPath("$.bills[0].total").value("250.00"))
                .andExpect(jsonPath("$.bills[0].otherCharges").value("0.00"))
                .andExpect(jsonPath("$.bills[0].items[*].description", contains("Tênis", "Livraria", "Estorno livraria")))
                .andExpect(jsonPath("$.bills[0].items[2].direction").value("INFLOW"))
                .andExpect(jsonPath("$.bills[1].cardName").value("Cartão A"))
                .andExpect(jsonPath("$.bills[1].numberLastDigits").value("1111"))
                .andExpect(jsonPath("$.bills[1].dueDate").value("2026-10-10"))
                .andExpect(jsonPath("$.bills[1].closingDate").value("2026-10-03"))
                .andExpect(jsonPath("$.bills[1].open").value(false))
                .andExpect(jsonPath("$.bills[1].total").value("512.40"))
                .andExpect(jsonPath("$.bills[1].otherCharges").value("12.40"))
                // The payment of September's bill and the purchases of other cycles are not on it.
                .andExpect(jsonPath("$.bills[1].items[*].description", contains("Fone de ouvido", "Bicicleta", "Mercado")))
                .andExpect(jsonPath("$.bills[1].items[1].installmentNumber").value(2))
                .andExpect(jsonPath("$.bills[1].items[1].installmentTotal").value(3))
                .andExpect(jsonPath("$.bills[1].items[1].date").value("2026-08-15"))
                .andExpect(jsonPath("$.bills[1].items[0].category").value("Electronics"));
    }

    /** November: no salary yet, and neither card has closed November's bill. */
    @Test
    void aMonthAheadPredictsTheSalaryAndShowsTheOpenBills() throws Exception {
        read("/internal/planning/month?month=2026-11")
                .andExpect(jsonPath("$.salary.amount").value("3100.00"))
                .andExpect(jsonPath("$.salary.date").value("2026-11-05"))
                .andExpect(jsonPath("$.salary.predicted").value(true))
                .andExpect(jsonPath("$.salary.source").value("PREVIOUS_MONTH"))
                .andExpect(jsonPath("$.bills", hasSize(2)))
                .andExpect(jsonPath("$.bills[0].cardName").value("Cartão B"))
                .andExpect(jsonPath("$.bills[0].dueDate").value("2026-11-04"))
                .andExpect(jsonPath("$.bills[0].closingDate").value("2026-10-27"))
                .andExpect(jsonPath("$.bills[0].open").value(true))
                .andExpect(jsonPath("$.bills[0].total").value("60.00"))
                .andExpect(jsonPath("$.bills[1].cardName").value("Cartão A"))
                .andExpect(jsonPath("$.bills[1].dueDate").value("2026-11-10"))
                .andExpect(jsonPath("$.bills[1].open").value(true))
                .andExpect(jsonPath("$.bills[1].total").value("80.00"))
                .andExpect(jsonPath("$.bills[1].otherCharges").value("0.00"))
                .andExpect(jsonPath("$.totals.leftover").value("2960.00"));

        read("/internal/planning/month?month=2026-12")
                .andExpect(jsonPath("$.bills", hasSize(0)));
    }

    @Test
    void theSalaryTheUserSetReplacesThePredictionUntilCleared() throws Exception {
        mockMvc.perform(put("/internal/planning/expected-salary").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"3400.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("3400.00"));

        read("/internal/planning/month?month=2026-11")
                .andExpect(jsonPath("$.salary.amount").value("3400.00"))
                .andExpect(jsonPath("$.salary.source").value("EXPECTED"))
                .andExpect(jsonPath("$.salary.predicted").value(true))
                .andExpect(jsonPath("$.salary.expectedAmount").value("3400.00"));
        read("/internal/planning/month?month=2026-10")
                .andExpect(jsonPath("$.salary.amount").value("3100.00"))
                .andExpect(jsonPath("$.salary.source").value("RECEIVED"))
                .andExpect(jsonPath("$.salary.expectedAmount").value("3400.00"));

        mockMvc.perform(delete("/internal/planning/expected-salary").with(as(userId))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/internal/planning/expected-salary").with(as(userId))).andExpect(status().isNoContent());

        read("/internal/planning/month?month=2026-11")
                .andExpect(jsonPath("$.salary.source").value("PREVIOUS_MONTH"))
                .andExpect(jsonPath("$.salary.expectedAmount").isEmpty());
    }

    @Test
    void fixedExpensesAreCreatedEditedAndDeleted() throws Exception {
        String id = createFixed("{\"name\":\"  Academia  \",\"amount\":\"99.90\",\"dayOfMonth\":5,\"category\":\" \"}");
        createFixed("{\"name\":\"Condomínio\",\"amount\":\"600\",\"dayOfMonth\":1}");

        read("/internal/planning/fixed-expenses")
                .andExpect(jsonPath("$[*].name", contains("Condomínio", "Academia")))
                .andExpect(jsonPath("$[0].amount").value("600.00"))
                .andExpect(jsonPath("$[1].category").isEmpty());

        mockMvc.perform(put("/internal/planning/fixed-expenses/" + id).with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Academia\",\"amount\":\"119.90\",\"dayOfMonth\":12,\"category\":\"Gyms and fitness centers\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("119.90"))
                .andExpect(jsonPath("$.dayOfMonth").value(12))
                .andExpect(jsonPath("$.category").value("Gyms and fitness centers"));

        mockMvc.perform(delete("/internal/planning/fixed-expenses/" + id).with(as(userId))).andExpect(status().isNoContent());
        read("/internal/planning/fixed-expenses").andExpect(jsonPath("$[*].name", contains("Condomínio")));
        mockMvc.perform(delete("/internal/planning/fixed-expenses/" + id).with(as(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("planning.fixed_expense_not_found"));
    }

    @Test
    void anotherUsersFixedExpenseCannotBeTouched() throws Exception {
        String id = createFixed("{\"name\":\"Faculdade\",\"amount\":\"450.00\",\"dayOfMonth\":10}");
        UUID stranger = TestUsers.register(mockMvc);

        mockMvc.perform(put("/internal/planning/fixed-expenses/" + id).with(as(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nada\",\"amount\":\"1.00\",\"dayOfMonth\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("planning.fixed_expense_not_found"));
        mockMvc.perform(delete("/internal/planning/fixed-expenses/" + id).with(as(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/planning/month?month=2026-10").with(as(stranger)))
                .andExpect(jsonPath("$.salary.source").value("NONE"))
                .andExpect(jsonPath("$.salary.amount").isEmpty())
                .andExpect(jsonPath("$.bills", hasSize(0)))
                .andExpect(jsonPath("$.fixedExpenses", hasSize(0)))
                .andExpect(jsonPath("$.totals.leftover").value("0.00"));
    }

    @Test
    void badFixedExpensesAndSalariesAreRefused() throws Exception {
        for (String body : List.of(
                "{\"name\":\"\",\"amount\":\"10.00\",\"dayOfMonth\":1}",
                "{\"name\":\"Luz\",\"amount\":\"-10.00\",\"dayOfMonth\":1}",
                "{\"name\":\"Luz\",\"amount\":\"10.001\",\"dayOfMonth\":1}",
                "{\"name\":\"Luz\",\"amount\":\"10.00\",\"dayOfMonth\":32}",
                "{\"name\":\"Luz\",\"amount\":\"10.00\"}")) {
            mockMvc.perform(post("/internal/planning/fixed-expenses").with(as(userId))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation.failed"));
        }
        mockMvc.perform(put("/internal/planning/expected-salary").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        read("/internal/planning/month?month=outubro")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid_parameter"));
    }

    @Test
    void withoutAMonthItIsTheCurrentOne() throws Exception {
        read("/internal/planning/month").andExpect(jsonPath("$.month").value("2026-10"));
    }

    @Test
    void theDebitsThatRepeatEveryMonthAreSuggestedAsFixedExpenses() throws Exception {
        read("/internal/planning/fixed-expense-suggestions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(matchesPattern("[0-9a-f]{32}-9")))
                .andExpect(jsonPath("$[0].name").value("Pag Condominio Primavera"))
                .andExpect(jsonPath("$[0].amount").value("650.00"))
                .andExpect(jsonPath("$[0].dayOfMonth").value(9))
                .andExpect(jsonPath("$[0].category").value("Housing"))
                .andExpect(jsonPath("$[0].months").value(4))
                .andExpect(jsonPath("$[0].lastDate").value("2026-10-09"))
                .andExpect(jsonPath("$[1].id").value(matchesPattern("[0-9a-f]{32}-20")))
                .andExpect(jsonPath("$[1].name").value("Conta de Luz"))
                .andExpect(jsonPath("$[1].amount").value("195.17"))
                .andExpect(jsonPath("$[1].dayOfMonth").value(20))
                .andExpect(jsonPath("$[1].months").value(3))
                .andExpect(jsonPath("$[1].lastDate").value("2026-09-21"));
    }

    /** Under its own name or another one with about the same amount, on about the same day. */
    @Test
    void aDebitAlreadyAFixedExpenseIsNotSuggested() throws Exception {
        createFixed("{\"name\":\"Condomínio\",\"amount\":\"650.00\",\"dayOfMonth\":10,\"category\":\"Housing\"}");
        read("/internal/planning/fixed-expense-suggestions").andExpect(jsonPath("$[*].name", contains("Conta de Luz")));

        createFixed("{\"name\":\"Energia\",\"amount\":\"200.00\",\"dayOfMonth\":20}");
        read("/internal/planning/fixed-expense-suggestions").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aDismissedSuggestionDoesNotComeBack() throws Exception {
        String power = JsonPath.read(read("/internal/planning/fixed-expense-suggestions")
                .andReturn().getResponse().getContentAsString(), "$[1].id");
        UUID stranger = TestUsers.register(mockMvc);
        mockMvc.perform(delete("/internal/planning/fixed-expense-suggestions/" + power).with(as(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("planning.fixed_expense_suggestion_not_found"));
        mockMvc.perform(get("/internal/planning/fixed-expense-suggestions").with(as(stranger)))
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(delete("/internal/planning/fixed-expense-suggestions/" + power).with(as(userId)))
                .andExpect(status().isNoContent());
        read("/internal/planning/fixed-expense-suggestions").andExpect(jsonPath("$[*].name", contains("Pag Condominio Primavera")));
        mockMvc.perform(delete("/internal/planning/fixed-expense-suggestions/" + power).with(as(userId)))
                .andExpect(status().isNoContent());

        for (String unknown : List.of("not-an-id", "0".repeat(32) + "-9")) {
            mockMvc.perform(delete("/internal/planning/fixed-expense-suggestions/" + unknown).with(as(userId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("planning.fixed_expense_suggestion_not_found"));
        }
    }

    private void stubProvider(String itemId) {
        when(provider.findItem(itemId)).thenReturn(new ProviderItem(itemId, "Banco Teste", null, ProviderItemStatus.READY,
                clock.instant(), null, null));
        when(provider.listAccounts(itemId)).thenReturn(List.of(
                new ProviderAccount("acc-1", itemId, AccountKind.CHECKING, "Conta", "3456", "BRL", Money.of("4000"), null, null),
                new ProviderAccount("card-a", itemId, AccountKind.CREDIT_CARD, "Cartão A", "1111", "BRL",
                        Money.of("592.40"), Money.of("5000"), Money.of("4407.60")),
                new ProviderAccount("card-b", itemId, AccountKind.CREDIT_CARD, "Cartão B", "2222", "BRL",
                        Money.of("310"), Money.of("3000"), Money.of("2690"))));
        when(provider.listTransactions(eq("acc-1"), any(), any(), any())).thenReturn(List.of(
                transaction("salary-sep", "2026-09-05", "3000.00", Direction.INFLOW, "Salário Empresa", "Salary"),
                transaction("salary-oct", "2026-10-05", "3100.00", Direction.INFLOW, "Salário Empresa", "Salary"),
                transaction("pix-in", "2026-10-07", "500.00", Direction.INFLOW, "Pix recebido", "Transfers"),
                transaction("pay-a", "2026-09-10", "300.00", Direction.OUTFLOW, "Pagamento fatura", "Credit card payment"),
                transaction("pay-jul", "2026-07-10", "280.00", Direction.OUTFLOW, "Pagamento fatura", "Credit card payment"),
                transaction("pay-aug", "2026-08-10", "310.00", Direction.OUTFLOW, "Pagamento fatura", "Credit card payment"),
                transaction("condo-jul", "2026-07-09", "650.00", Direction.OUTFLOW, "PAG CONDOMINIO PRIMAVERA 07/26", "Housing"),
                transaction("condo-aug", "2026-08-10", "650.00", Direction.OUTFLOW, "PAG CONDOMINIO PRIMAVERA 08/26", "Housing"),
                transaction("condo-sep", "2026-09-09", "650.00", Direction.OUTFLOW, "PAG CONDOMINIO PRIMAVERA 09/26", "Housing"),
                transaction("condo-oct", "2026-10-09", "650.00", Direction.OUTFLOW, "PAG CONDOMINIO PRIMAVERA 10/26", "Housing"),
                transaction("power-jul", "2026-07-20", "180.00", Direction.OUTFLOW, "CONTA DE LUZ", "Electricity"),
                transaction("power-aug", "2026-08-20", "210.00", Direction.OUTFLOW, "CONTA DE LUZ", "Electricity"),
                transaction("power-sep", "2026-09-21", "195.50", Direction.OUTFLOW, "CONTA DE LUZ", "Electricity"),
                transaction("gym-sep", "2026-09-05", "99.90", Direction.OUTFLOW, "PIX ENVIADO ACADEMIA", "Gyms and fitness centers"),
                transaction("gym-oct", "2026-10-05", "99.90", Direction.OUTFLOW, "PIX ENVIADO ACADEMIA", "Gyms and fitness centers"),
                transaction("mag-jul", "2026-07-03", "29.90", Direction.OUTFLOW, "ASSINATURA REVISTA", "Newsstands"),
                transaction("mag-aug", "2026-08-03", "29.90", Direction.OUTFLOW, "ASSINATURA REVISTA", "Newsstands"),
                transaction("mag-sep", "2026-09-03", "29.90", Direction.OUTFLOW, "ASSINATURA REVISTA", "Newsstands")));
        when(provider.listTransactions(eq("card-a"), any(), any(), any())).thenReturn(List.of(
                transaction("a-old", "2026-09-02", "45.00", Direction.OUTFLOW, "Compra do ciclo anterior", "Shopping"),
                transaction("a-payment", "2026-09-10", "300.00", Direction.INFLOW, "Pagamento recebido", "Credit card payment"),
                transaction("a-headphones", "2026-09-20", "200.00", Direction.OUTFLOW, "Fone de ouvido", "Electronics"),
                transaction("a-market", "2026-09-25", "150.00", Direction.OUTFLOW, "Mercado", "Groceries"),
                installment("a-bike", "2026-08-15", "150.00", "Bicicleta", 2, 3, "bill-a-oct"),
                transaction("a-open", "2026-10-05", "80.00", Direction.OUTFLOW, "Restaurante", "Eating out")));
        when(provider.listTransactions(eq("card-b"), any(), any(), any())).thenReturn(List.of(
                transaction("b-shoes", "2026-09-10", "190.00", Direction.OUTFLOW, "Tênis", "Clothing"),
                transaction("b-books", "2026-09-12", "80.00", Direction.OUTFLOW, "Livraria", "Bookstore"),
                transaction("b-refund", "2026-09-15", "20.00", Direction.INFLOW, "Estorno livraria", "Bookstore"),
                transaction("b-open", "2026-10-12", "60.00", Direction.OUTFLOW, "Cinema", "Entertainment")));
        when(provider.listBills("card-a")).thenReturn(List.of(
                bill("bill-a-sep", "card-a", "2026-09-10", "2026-09-03", "300.00"),
                bill("bill-a-oct", "card-a", "2026-10-10", "2026-10-03", "512.40")));
        when(provider.listBills("card-b")).thenReturn(List.of(
                bill("bill-b-oct", "card-b", "2026-10-04", "2026-09-27", "250.00")));
        when(provider.listInvestments(itemId)).thenReturn(List.of());
    }

    private static ProviderTransaction transaction(String id, String date, String amount, Direction direction,
                                                   String description, String category) {
        return new ProviderTransaction(id, "account", null, LocalDate.parse(date), description, Money.of(amount),
                direction, TransactionStatus.POSTED, category, null, null, null);
    }

    private static ProviderTransaction installment(String id, String date, String amount, String description,
                                                   int number, int total, String billId) {
        return new ProviderTransaction(id, "card-a", null, LocalDate.parse(date), description, Money.of(amount),
                Direction.OUTFLOW, TransactionStatus.POSTED, "Sports practice", number, total, billId);
    }

    private static ProviderBill bill(String id, String accountId, String dueDate, String closingDate, String total) {
        return new ProviderBill(id, accountId, LocalDate.parse(dueDate), LocalDate.parse(closingDate), Money.of(total),
                null, "BRL");
    }

    private UUID saveConnection(String itemId) {
        return connections.save(Connection.builder().id(UUID.randomUUID()).userId(userId).provider("PLUGGY")
                .providerItemId(itemId).institutionName("Banco Teste").status(ConnectionStatus.ACTIVE)
                .createdAt(TODAY).updatedAt(TODAY).build()).getId();
    }

    private String createFixed(String body) throws Exception {
        String response = mockMvc.perform(post("/internal/planning/fixed-expenses").with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions read(String path) throws Exception {
        return mockMvc.perform(get(path).with(as(userId)));
    }
}
