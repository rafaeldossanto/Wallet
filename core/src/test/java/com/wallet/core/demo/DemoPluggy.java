package com.wallet.core.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A stand-in for Pluggy with synthetic data, so core, BFF and app run end to end without a Pluggy
 * account. {@link com.wallet.core.TestCoreApplication} starts it when {@code PLUGGY_CLIENT_ID} is
 * not set.
 *
 * <p>Items to link in the app ({@link #ITEMS}): two plain demos ({@value #BANK_ITEM} and
 * {@value #BROKER_ITEM}) and three that look like a real customer's banks
 * ({@value #NUBANK_ITEM}, {@value #ITAU_ITEM}, {@value #XP_ITEM}), each with its own accounts and
 * spending habits. Each day's transactions come from a seed made of the date itself, so ids and
 * amounts are the same on every sync and nothing is duplicated. The institutions are named the way
 * Pluggy names them; the people, accounts and amounts are invented.
 */
public final class DemoPluggy implements AutoCloseable {

    public static final String BANK_ITEM = "demo-banco";
    public static final String BROKER_ITEM = "demo-corretora";
    public static final String NUBANK_ITEM = "demo-nubank";
    public static final String ITAU_ITEM = "demo-itau";
    public static final String XP_ITEM = "demo-xp";
    public static final List<String> ITEMS = List.of(NUBANK_ITEM, ITAU_ITEM, XP_ITEM, BANK_ITEM, BROKER_ITEM);

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    /**
     * Where the demo accounts begin. Fixed, not "N days ago": balances add up everything since
     * then, and a window that moved with the date would forget one day of history every day.
     */
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 1, 1);

    private static final String CARD_PAYMENT = "Credit card payment";

    private static final Bank DEMO_BANK = new Bank(BANK_ITEM, 9001, "Banco Demo", 1,
            new Account("demo-checking", "Conta Corrente", "0001/23456-7", "4800.00"),
            new Account("demo-savings", "Poupança", "0001/76543-2", "18500.00"), money("96.40"),
            new Card("demo-card", "Cartão Demo Platinum", "5162", "12000.00", 3, 7,
                    new Installments("MAGAZINE DEMO NOTEBOOK", "Electronics", "389.90", 10, 18)),
            money("7350.00"), money("2150.00"),
            List.of(
                    Habit.monthly(12, "CONTA DE LUZ ENERGIA DEMO", "Electricity", 150, 260),
                    Habit.monthly(15, "INTERNET FIBRA DEMO", "Telecommunications", 119.90, 119.90),
                    Habit.weekly(DayOfWeek.SATURDAY, 0.08, "SUPERMERCADO BOM PRECO", "Groceries", 90, 380),
                    Habit.sometimes(0.18, "UBER *TRIP", "Taxi and ride-hailing", 14, 48),
                    Habit.sometimes(0.2, "PADARIA PAO QUENTE", "Eating out", 8, 35),
                    Habit.sometimes(0.05, "DROGARIA SAUDE DEMO", "Pharmacy", 25, 140),
                    Habit.income(0.04, "PIX RECEBIDO FULANO DE TAL", "Transfers", 50, 300)),
            List.of(
                    Habit.monthly(8, "STREAMING FILMES DEMO", "Video streaming", 55.90, 55.90),
                    Habit.monthly(14, "STREAMING MUSICA DEMO", "Music streaming", 21.90, 21.90),
                    Habit.sometimes(0.22, "DELIVERY COMIDA DEMO", "Food delivery", 35, 120),
                    Habit.sometimes(0.1, "RESTAURANTE SABOR DEMO", "Eating out", 60, 220),
                    Habit.sometimes(0.07, "POSTO DEMO COMBUSTIVEIS", "Gas stations", 120, 280),
                    Habit.sometimes(0.04, "LOJA DE ROUPAS DEMO", "Clothing", 90, 400)));

    /** A digital bank used for everyday card spending; income arrives by Pix. */
    private static final Bank NUBANK = new Bank(NUBANK_ITEM, 9101, "Nubank", 11,
            new Account("nubank-checking", "Conta", "0001/9876543-2", "2300.00"),
            null, null,
            new Card("nubank-card", "Cartão de crédito", "4821", "8500.00", 27, 8,
                    new Installments("SMARTPHONE LOJA ONLINE", "Electronics", "349.90", 12, 22)),
            null, null,
            List.of(
                    Habit.income(0.09, "PIX RECEBIDO CLIENTE FREELA", "Transfers", 600, 2400),
                    Habit.sometimes(0.06, "PIX ENVIADO ACADEMIA BOA FORMA", "Gyms and fitness centers", 99.90, 99.90),
                    Habit.sometimes(0.05, "PIX ENVIADO FEIRA DO BAIRRO", "Groceries", 25, 90)),
            List.of(
                    Habit.monthly(6, "NETFLIX.COM", "Video streaming", 44.90, 44.90),
                    Habit.monthly(11, "SPOTIFY", "Music streaming", 21.90, 21.90),
                    Habit.monthly(20, "AMAZON PRIME", "Online shopping", 19.90, 19.90),
                    Habit.sometimes(0.28, "IFOOD *RESTAURANTE", "Food delivery", 32, 110),
                    Habit.sometimes(0.22, "UBER *TRIP", "Taxi and ride-hailing", 12, 46),
                    Habit.sometimes(0.06, "MERCADOLIVRE*COMPRA", "Online shopping", 45, 380),
                    Habit.sometimes(0.05, "CINEMA SHOPPING", "Entertainment", 38, 90)));

    /** The salary account: rent, bills at home, savings, and a card for groceries and fuel. */
    private static final Bank ITAU = new Bank(ITAU_ITEM, 9102, "Itaú", 23,
            new Account("itau-checking", "Conta Corrente", "4321/12345-6", "6200.00"),
            new Account("itau-savings", "Poupança", "4321/54321-0", "25000.00"), money("128.40"),
            new Card("itau-card", "Cartão Itaú Visa", "7310", "15000.00", 3, 7, null),
            money("9800.00"), money("2800.00"),
            List.of(
                    Habit.monthly(9, "CONDOMINIO EDIFICIO SOL", "Housing", 690, 690),
                    Habit.monthly(12, "ENEL DISTRIBUICAO SP", "Electricity", 160, 290),
                    Habit.monthly(14, "SABESP AGUA", "Water", 70, 120),
                    Habit.monthly(15, "VIVO FIBRA", "Telecommunications", 129.90, 129.90),
                    Habit.monthly(20, "PLANO DE SAUDE", "Health insurance", 480, 480),
                    Habit.sometimes(0.04, "DROGASIL", "Pharmacy", 25, 160)),
            List.of(
                    Habit.weekly(DayOfWeek.SATURDAY, 0.06, "CARREFOUR HIPER", "Groceries", 140, 520),
                    Habit.sometimes(0.09, "POSTO SHELL", "Gas stations", 150, 300),
                    Habit.sometimes(0.08, "OUTBACK STEAKHOUSE", "Eating out", 110, 320),
                    Habit.sometimes(0.05, "RENNER", "Clothing", 90, 450),
                    Habit.sometimes(0.03, "LATAM AIRLINES", "Airport and airlines", 600, 1800)));

    private static final Broker DEMO_BROKER = new Broker(BROKER_ITEM, 9002, "Corretora Demo", List.of(
            Position.growing("demo-cdb", "FIXED_INCOME", "CDB", "CDB Banco Demo 110% CDI", "15000.00", "0.00042",
                    "14200.00", LocalDate.of(2028, 3, 15)),
            Position.growing("demo-tesouro", "FIXED_INCOME", "TREASURY", "Tesouro Selic 2029", "8200.00", "0.00038",
                    "8000.00", LocalDate.of(2029, 3, 1)),
            Position.waving("demo-fund", "MUTUAL_FUND", "MULTIMARKET_FUND", "Fundo Multimercado Demo", "6400.00", 0, "6000.00"),
            Position.waving("demo-etf", "ETF", "ETF", "ETF Índice Demo", "4800.00", 40, "5000.00"),
            Position.growing("demo-pension", "MUTUAL_FUND", "RETIREMENT", "Previdência Demo PGBL", "9100.00", "0.00030",
                    "8500.00", null)));

    private static final Broker XP = new Broker(XP_ITEM, 9103, "XP Investimentos", List.of(
            Position.growing("xp-cdb", "FIXED_INCOME", "CDB", "CDB Banco Master 120% CDI", "22000.00", "0.00045",
                    "20000.00", LocalDate.of(2028, 8, 15)),
            Position.growing("xp-tesouro", "FIXED_INCOME", "TREASURY", "Tesouro IPCA+ 2035", "15500.00", "0.00033",
                    "14000.00", LocalDate.of(2035, 5, 15)),
            Position.growing("xp-lci", "FIXED_INCOME", "LCI", "LCI Banco Inter 95% CDI", "10000.00", "0.00036",
                    "9500.00", LocalDate.of(2027, 12, 1)),
            Position.waving("xp-fia", "MUTUAL_FUND", "STOCK_FUND", "Fundo de Ações Brasil", "8200.00", 15, "8000.00"),
            Position.waving("xp-etf", "ETF", "ETF", "ETF S&P 500", "6400.00", 70, "5800.00"),
            Position.growing("xp-pension", "MUTUAL_FUND", "RETIREMENT", "Previdência XP PGBL", "18000.00", "0.00031",
                    "16500.00", null)));

    private static final List<Bank> BANKS = List.of(DEMO_BANK, NUBANK, ITAU);
    private static final List<Broker> BROKERS = List.of(DEMO_BROKER, XP);

    private final HttpServer server;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    private DemoPluggy(HttpServer server, Clock clock) {
        this.server = server;
        this.clock = clock;
    }

    public static DemoPluggy start(Clock clock) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        DemoPluggy pluggy = new DemoPluggy(server, clock);
        server.createContext("/", pluggy::handle);
        server.start();
        return pluggy;
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- HTTP ------------------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
            Object body = route(method, path, query);
            if (isNull(body)) {
                send(exchange, 404, Map.of("code", 404, "message", "Not found"));
            } else {
                send(exchange, 200, body);
            }
        }
    }

    private Object route(String method, String path, Map<String, String> query) {
        if ("POST".equals(method) && "/auth".equals(path)) {
            return Map.of("apiKey", "demo-api-key");
        }
        if (path.startsWith("/items/")) {
            String itemId = path.substring("/items/".length());
            Institution institution = institution(itemId);
            if (isNull(institution)) {
                return null;
            }
            return "DELETE".equals(method) ? Map.of("id", itemId) : item(institution);
        }
        LocalDate today = LocalDate.now(clock.withZone(BRAZIL));
        return switch (path) {
            case "/accounts" -> page(accounts(query.get("itemId"), today));
            case "/v2/transactions" -> Map.of(
                    "results", transactionsJson(query.get("accountId"), date(query.get("dateFrom")),
                            date(query.get("dateTo")), today),
                    "next", "");
            case "/bills" -> page(bills(query.get("accountId"), today));
            case "/investments" -> page(investments(query.get("itemId"), today));
            default -> null;
        };
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (isNull(raw) || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            values.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static LocalDate date(String value) {
        return isNull(value) || value.isEmpty() ? null : LocalDate.parse(value.substring(0, 10));
    }

    private static Map<String, Object> page(List<?> results) {
        return Map.of("page", 1, "total", results.size(), "totalPages", 1, "results", results);
    }

    // ---- items and accounts ------------------------------------------------------------------

    private static Institution institution(String itemId) {
        return Stream.<Institution>concat(BANKS.stream(), BROKERS.stream())
                .filter(institution -> institution.itemId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> item(Institution institution) {
        Instant now = clock.instant();
        Map<String, Object> connector = new LinkedHashMap<>();
        connector.put("id", institution.connectorId());
        connector.put("name", institution.name());
        connector.put("imageUrl", null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", institution.itemId());
        item.put("connector", connector);
        item.put("status", "UPDATED");
        item.put("executionStatus", "SUCCESS");
        item.put("lastUpdatedAt", now.minus(20, ChronoUnit.MINUTES).toString());
        item.put("consentExpiresAt", now.plus(365, ChronoUnit.DAYS).toString());
        item.put("clientUserId", null);
        return item;
    }

    private List<Map<String, Object>> accounts(String itemId, LocalDate today) {
        Bank bank = BANKS.stream().filter(candidate -> candidate.itemId().equals(itemId)).findFirst().orElse(null);
        if (isNull(bank)) {
            return List.of();
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        Account checking = bank.checking();
        accounts.add(account(bank, checking.id(), "BANK", "CHECKING_ACCOUNT", checking.number(), checking.name(),
                checking.openingBalance().add(sum(transactions(checking.id(), FIRST_DAY, today, today))), null));
        Account savings = bank.savings();
        if (nonNull(savings)) {
            accounts.add(account(bank, savings.id(), "BANK", "SAVINGS_ACCOUNT", savings.number(), savings.name(),
                    savings.openingBalance().add(sum(transactions(savings.id(), FIRST_DAY, today, today))), null));
        }
        Card card = bank.card();
        if (nonNull(card)) {
            BigDecimal used = sum(transactions(card.id(), FIRST_DAY, today, today));
            Map<String, Object> creditData = new LinkedHashMap<>();
            creditData.put("creditLimit", card.limit());
            creditData.put("availableCreditLimit", card.limit().subtract(used));
            creditData.put("balanceDueDate", instant(nextDueDate(card, today)));
            accounts.add(account(bank, card.id(), "CREDIT", "CREDIT_CARD", card.digits(), card.name(), used, creditData));
        }
        return accounts;
    }

    private static Map<String, Object> account(Bank bank, String id, String type, String subtype, String number,
                                               String name, BigDecimal balance, Map<String, Object> creditData) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", id);
        account.put("itemId", bank.itemId());
        account.put("type", type);
        account.put("subtype", subtype);
        account.put("number", number);
        account.put("name", name);
        account.put("marketingName", name);
        account.put("balance", balance);
        account.put("currencyCode", "BRL");
        account.put("creditData", creditData);
        return account;
    }

    // ---- transactions --------------------------------------------------------------------

    private record DemoTransaction(String id, String accountId, LocalDate date, String description,
                                   BigDecimal amount, String category, Integer installment, Integer installments) {
    }

    private List<Map<String, Object>> transactionsJson(String accountId, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = isNull(from) || from.isBefore(FIRST_DAY) ? FIRST_DAY : from;
        LocalDate end = isNull(to) || to.isAfter(today) ? today : to;
        boolean card = BANKS.stream().anyMatch(bank -> nonNull(bank.card()) && bank.card().id().equals(accountId));
        return transactions(accountId, start, end, today).stream().map(transaction -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", transaction.id());
            value.put("accountId", transaction.accountId());
            value.put("date", instant(transaction.date()));
            value.put("description", transaction.description());
            boolean credit = card ? transaction.amount().signum() < 0 : transaction.amount().signum() > 0;
            value.put("type", credit ? "CREDIT" : "DEBIT");
            value.put("amount", transaction.amount());
            value.put("currencyCode", "BRL");
            value.put("category", transaction.category());
            value.put("status", transaction.date().equals(today) ? "PENDING" : "POSTED");
            value.put("providerId", "of-" + transaction.id());
            value.put("creditCardMetadata", isNull(transaction.installment()) ? null : Map.of(
                    "installmentNumber", transaction.installment(),
                    "totalInstallments", transaction.installments()));
            return value;
        }).toList();
    }

    /** Bank accounts: negative is money out. Card: positive is a charge, negative a payment (Pluggy's convention). */
    private List<DemoTransaction> transactions(String accountId, LocalDate from, LocalDate to, LocalDate today) {
        List<DemoTransaction> transactions = new ArrayList<>();
        for (Bank bank : BANKS) {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                if (bank.checking().id().equals(accountId)) {
                    checkingDay(bank, day, today, transactions);
                } else if (nonNull(bank.savings()) && bank.savings().id().equals(accountId)) {
                    savingsDay(bank, day, transactions);
                } else if (nonNull(bank.card()) && bank.card().id().equals(accountId)) {
                    cardDay(bank, day, today, transactions);
                }
            }
        }
        return transactions;
    }

    private void checkingDay(Bank bank, LocalDate day, LocalDate today, List<DemoTransaction> out) {
        String account = bank.checking().id();
        Random random = new Random(day.toEpochDay() * 31 + bank.seed());
        if (nonNull(bank.salary()) && day.getDayOfMonth() == 5) {
            add(out, account, day, "SALARIO EMPRESA ACME LTDA", bank.salary(), "Salary");
        }
        if (nonNull(bank.rent()) && day.getDayOfMonth() == 7) {
            add(out, account, day, "ALUGUEL APARTAMENTO", bank.rent().negate(), "Rent");
        }
        Card card = bank.card();
        if (nonNull(card) && isDueDay(card, day)) {
            BigDecimal bill = billTotal(bank, day.minusDays(card.daysToDue()), today);
            if (bill.signum() > 0) {
                add(out, account, day, "PAGAMENTO FATURA " + bank.name().toUpperCase(), bill.negate(), CARD_PAYMENT);
            }
        }
        for (Habit habit : bank.checkingHabits()) {
            BigDecimal amount = habit.amountOn(day, random);
            if (nonNull(amount)) {
                add(out, account, day, habit.description(), habit.inflow() ? amount : amount.negate(), habit.category());
            }
        }
    }

    private static void savingsDay(Bank bank, LocalDate day, List<DemoTransaction> out) {
        if (day.getDayOfMonth() == 1) {
            add(out, bank.savings().id(), day, "RENDIMENTO POUPANCA", bank.savingsInterest(), "Interest");
        }
    }

    private void cardDay(Bank bank, LocalDate day, LocalDate today, List<DemoTransaction> out) {
        Card card = bank.card();
        if (isDueDay(card, day)) {
            BigDecimal bill = billTotal(bank, day.minusDays(card.daysToDue()), today);
            if (bill.signum() > 0) {
                add(out, card.id(), day, "PAGAMENTO RECEBIDO", bill.negate(), CARD_PAYMENT);
            }
        }
        cardCharges(bank, day, out);
    }

    private static void cardCharges(Bank bank, LocalDate day, List<DemoTransaction> out) {
        Card card = bank.card();
        Random random = new Random(day.toEpochDay() * 31 + bank.seed() + 7);
        Installments installments = card.installments();
        if (nonNull(installments) && day.getDayOfMonth() == installments.dayOfMonth()) {
            int number = (int) ChronoUnit.MONTHS.between(FIRST_DAY.withDayOfMonth(1), day.withDayOfMonth(1)) + 1;
            if (number <= installments.total()) {
                out.add(new DemoTransaction(card.id() + "-" + day + "-installment", card.id(), day,
                        "%s %02d/%02d".formatted(installments.description(), number, installments.total()),
                        installments.amount(), installments.category(), number, installments.total()));
            }
        }
        for (Habit habit : bank.cardHabits()) {
            BigDecimal amount = habit.amountOn(day, random);
            if (nonNull(amount)) {
                add(out, card.id(), day, habit.description(), amount, habit.category());
            }
        }
    }

    private static void add(List<DemoTransaction> out, String accountId, LocalDate day, String description,
                            BigDecimal amount, String category) {
        out.add(new DemoTransaction(accountId + "-" + day + "-" + out.size(), accountId, day, description, amount,
                category, null, null));
    }

    // ---- bills ---------------------------------------------------------------------------

    /**
     * Closed bills only, newest last; each one charges the card purchases of its cycle. A cycle
     * that began before the generated history would be incomplete, so it is left out.
     */
    private List<Map<String, Object>> bills(String accountId, LocalDate today) {
        Bank bank = BANKS.stream()
                .filter(candidate -> nonNull(candidate.card()) && candidate.card().id().equals(accountId))
                .findFirst().orElse(null);
        if (isNull(bank)) {
            return List.of();
        }
        Card card = bank.card();
        List<Map<String, Object>> bills = new ArrayList<>();
        for (LocalDate closing = FIRST_DAY.withDayOfMonth(card.closingDay()).plusMonths(1);
             !closing.isAfter(today); closing = closing.plusMonths(1)) {
            BigDecimal total = billTotal(bank, closing, today);
            if (total.signum() == 0) {
                continue;
            }
            Map<String, Object> bill = new LinkedHashMap<>();
            bill.put("id", card.id() + "-bill-" + closing.getYear() + "-" + closing.getMonthValue());
            bill.put("dueDate", instant(closing.plusDays(card.daysToDue())));
            bill.put("billClosingDate", instant(closing));
            bill.put("totalAmount", total);
            bill.put("totalAmountCurrencyCode", "BRL");
            bill.put("minimumPaymentAmount", total.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP));
            bills.add(bill);
        }
        return bills;
    }

    /** The charges of the cycle that closes on {@code closing}; payments do not count. */
    private static BigDecimal billTotal(Bank bank, LocalDate closing, LocalDate today) {
        LocalDate cycleStart = closing.minusMonths(1).plusDays(1);
        if (closing.isAfter(today) || cycleStart.isBefore(FIRST_DAY)) {
            return BigDecimal.ZERO;
        }
        List<DemoTransaction> charges = new ArrayList<>();
        for (LocalDate day = cycleStart; !day.isAfter(closing); day = day.plusDays(1)) {
            cardCharges(bank, day, charges);
        }
        return sum(charges);
    }

    /** A bill is paid on its due day: {@code daysToDue} after a closing day. */
    private static boolean isDueDay(Card card, LocalDate day) {
        return day.minusDays(card.daysToDue()).getDayOfMonth() == card.closingDay();
    }

    private static LocalDate nextDueDate(Card card, LocalDate today) {
        LocalDate due = today.withDayOfMonth(card.closingDay()).minusMonths(1).plusDays(card.daysToDue());
        while (due.isBefore(today)) {
            due = due.minusDays(card.daysToDue()).plusMonths(1).plusDays(card.daysToDue());
        }
        return due;
    }

    // ---- investments ---------------------------------------------------------------------

    private static List<Map<String, Object>> investments(String itemId, LocalDate today) {
        long days = ChronoUnit.DAYS.between(FIRST_DAY, today);
        return BROKERS.stream()
                .filter(broker -> broker.itemId().equals(itemId))
                .flatMap(broker -> broker.positions().stream().map(position -> position.toJson(broker.itemId(), days)))
                .toList();
    }

    // ---- helpers -------------------------------------------------------------------------

    private static String instant(LocalDate date) {
        return date.atStartOfDay(BRAZIL).toInstant().toString();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal sum(List<DemoTransaction> transactions) {
        return transactions.stream().map(DemoTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- the simulated institutions ------------------------------------------------------------

    private sealed interface Institution permits Bank, Broker {
        String itemId();

        int connectorId();

        String name();
    }

    private record Account(String id, String name, String number, BigDecimal openingBalance) {
        Account(String id, String name, String number, String openingBalance) {
            this(id, name, number, new BigDecimal(openingBalance));
        }
    }

    /** A purchase split in equal monthly installments, starting in the history's first month. */
    private record Installments(String description, String category, BigDecimal amount, int total, int dayOfMonth) {
        Installments(String description, String category, String amount, int total, int dayOfMonth) {
            this(description, category, new BigDecimal(amount), total, dayOfMonth);
        }
    }

    private record Card(String id, String name, String digits, BigDecimal limit, int closingDay, int daysToDue,
                        Installments installments) {
        Card(String id, String name, String digits, String limit, int closingDay, int daysToDue, Installments installments) {
            this(id, name, digits, new BigDecimal(limit), closingDay, daysToDue, installments);
        }
    }

    /**
     * @param seed        keeps each bank's random days apart from the others'
     * @param salary      paid into the checking account on the 5th, or none
     * @param rent        paid from the checking account on the 7th, or none
     */
    private record Bank(String itemId, int connectorId, String name, int seed, Account checking, Account savings,
                        BigDecimal savingsInterest, Card card, BigDecimal salary, BigDecimal rent,
                        List<Habit> checkingHabits, List<Habit> cardHabits) implements Institution {
    }

    private record Broker(String itemId, int connectorId, String name, List<Position> positions) implements Institution {
    }

    /**
     * A kind of spending (or income) that repeats: on a fixed day of the month, on a weekday (plus
     * now and then), or on random days.
     */
    private record Habit(String description, String category, int dayOfMonth, DayOfWeek weekday, double chance,
                         double min, double max, boolean inflow) {

        static Habit monthly(int dayOfMonth, String description, String category, double min, double max) {
            return new Habit(description, category, dayOfMonth, null, 0, min, max, false);
        }

        static Habit weekly(DayOfWeek weekday, double chance, String description, String category, double min, double max) {
            return new Habit(description, category, 0, weekday, chance, min, max, false);
        }

        static Habit sometimes(double chance, String description, String category, double min, double max) {
            return new Habit(description, category, 0, null, chance, min, max, false);
        }

        static Habit income(double chance, String description, String category, double min, double max) {
            return new Habit(description, category, 0, null, chance, min, max, true);
        }

        /** The amount on {@code day}, or null when it does not happen that day. */
        BigDecimal amountOn(LocalDate day, Random random) {
            boolean happens;
            if (dayOfMonth > 0) {
                happens = day.getDayOfMonth() == dayOfMonth;
            } else if (nonNull(weekday)) {
                happens = weekday.equals(day.getDayOfWeek()) || random.nextDouble() < chance;
            } else {
                happens = random.nextDouble() < chance;
            }
            if (!happens) {
                return null;
            }
            double value = min == max ? min : min + random.nextDouble() * (max - min);
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** An investment whose balance grows steadily, or swings like a fund or an ETF. */
    private record Position(String id, String type, String subtype, String name, BigDecimal base, BigDecimal dailyRate,
                            Integer phase, BigDecimal original, LocalDate dueDate) {

        static Position growing(String id, String type, String subtype, String name, String base, String dailyRate,
                                String original, LocalDate dueDate) {
            return new Position(id, type, subtype, name, new BigDecimal(base), new BigDecimal(dailyRate), null,
                    new BigDecimal(original), dueDate);
        }

        static Position waving(String id, String type, String subtype, String name, String base, int phase, String original) {
            return new Position(id, type, subtype, name, new BigDecimal(base), null, phase, new BigDecimal(original), null);
        }

        Map<String, Object> toJson(String itemId, long days) {
            BigDecimal balance = nonNull(dailyRate)
                    ? base.multiply(BigDecimal.ONE.add(dailyRate.multiply(BigDecimal.valueOf(days))))
                    : base.multiply(BigDecimal.valueOf(1 + 0.04 * Math.sin((days + phase) / 9.0) + 0.0003 * days));
            Map<String, Object> investment = new LinkedHashMap<>();
            investment.put("id", id);
            investment.put("itemId", itemId);
            investment.put("type", type);
            investment.put("subtype", subtype);
            investment.put("name", name);
            investment.put("currencyCode", "BRL");
            investment.put("balance", balance.setScale(2, RoundingMode.HALF_UP));
            investment.put("amountOriginal", original);
            investment.put("dueDate", nonNull(dueDate) ? instant(dueDate) : null);
            investment.put("status", "ACTIVE");
            return investment;
        }
    }
}
