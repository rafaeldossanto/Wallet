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

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * A stand-in for Pluggy with synthetic data, so core, BFF and app run end to end without a Pluggy
 * account. {@link com.wallet.core.TestCoreApplication} starts it when {@code PLUGGY_CLIENT_ID} is
 * not set.
 *
 * <p>Two items to link in the app: {@value #BANK_ITEM} (checking, savings and a credit card with
 * bills) and {@value #BROKER_ITEM} (investments). Each day's transactions come from a seed made of
 * the date itself, so ids and amounts are the same on every sync and nothing is duplicated.
 * Nothing here resembles a real person or account.
 */
public final class DemoPluggy implements AutoCloseable {

    public static final String BANK_ITEM = "demo-banco";
    public static final String BROKER_ITEM = "demo-corretora";

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    /**
     * Where the demo accounts begin. Fixed, not "N days ago": balances add up everything since
     * then, and a window that moved with the date would forget one day of history every day.
     */
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 1, 1);

    private static final String CHECKING = "demo-checking";
    private static final String SAVINGS = "demo-savings";
    private static final String CARD = "demo-card";
    private static final BigDecimal CARD_LIMIT = new BigDecimal("12000.00");
    private static final int CLOSING_DAY = 3;
    private static final int DUE_DAY = 10;
    private static final String CARD_PAYMENT = "Credit card payment";

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
            if (!isKnownItem(itemId)) {
                return null;
            }
            return "DELETE".equals(method) ? Map.of("id", itemId) : item(itemId);
        }
        LocalDate today = LocalDate.now(clock.withZone(BRAZIL));
        return switch (path) {
            case "/accounts" -> page(accounts(query.get("itemId"), today));
            case "/v2/transactions" -> Map.of(
                    "results", transactionsJson(query.get("accountId"), date(query.get("dateFrom")),
                            date(query.get("dateTo")), today),
                    "next", "");
            case "/bills" -> page(CARD.equals(query.get("accountId")) ? bills(today) : List.of());
            case "/investments" -> page(BROKER_ITEM.equals(query.get("itemId")) ? investments(today) : List.of());
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

    private static boolean isKnownItem(String itemId) {
        return BANK_ITEM.equals(itemId) || BROKER_ITEM.equals(itemId);
    }

    private Map<String, Object> item(String itemId) {
        Instant now = clock.instant();
        Map<String, Object> connector = new LinkedHashMap<>();
        connector.put("id", BANK_ITEM.equals(itemId) ? 9001 : 9002);
        connector.put("name", BANK_ITEM.equals(itemId) ? "Banco Demo" : "Corretora Demo");
        connector.put("imageUrl", null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", itemId);
        item.put("connector", connector);
        item.put("status", "UPDATED");
        item.put("executionStatus", "SUCCESS");
        item.put("lastUpdatedAt", now.minus(20, ChronoUnit.MINUTES).toString());
        item.put("consentExpiresAt", now.plus(365, ChronoUnit.DAYS).toString());
        item.put("clientUserId", null);
        return item;
    }

    private List<Map<String, Object>> accounts(String itemId, LocalDate today) {
        if (!BANK_ITEM.equals(itemId)) {
            return List.of();
        }
        BigDecimal cardBalance = sum(transactions(CARD, FIRST_DAY, today, today));
        Map<String, Object> creditData = new LinkedHashMap<>();
        creditData.put("creditLimit", CARD_LIMIT);
        creditData.put("availableCreditLimit", CARD_LIMIT.subtract(cardBalance));
        creditData.put("balanceDueDate", instant(nextDueDate(today)));
        return List.of(
                account(CHECKING, "BANK", "CHECKING_ACCOUNT", "0001/23456-7", "Conta Corrente",
                        new BigDecimal("4800.00").add(sum(transactions(CHECKING, FIRST_DAY, today, today))), null),
                account(SAVINGS, "BANK", "SAVINGS_ACCOUNT", "0001/76543-2", "Poupança",
                        new BigDecimal("18500.00").add(sum(transactions(SAVINGS, FIRST_DAY, today, today))), null),
                account(CARD, "CREDIT", "CREDIT_CARD", "5162", "Cartão Demo Platinum", cardBalance, creditData));
    }

    private static Map<String, Object> account(String id, String type, String subtype, String number, String name,
                                               BigDecimal balance, Map<String, Object> creditData) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", id);
        account.put("itemId", BANK_ITEM);
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
        boolean card = CARD.equals(accountId);
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
        if (isNull(accountId)) {
            return transactions;
        }
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            switch (accountId) {
                case CHECKING -> checkingDay(day, today, transactions);
                case SAVINGS -> savingsDay(day, transactions);
                case CARD -> cardDay(day, today, transactions);
                default -> {
                    return transactions;
                }
            }
        }
        return transactions;
    }

    private void checkingDay(LocalDate day, LocalDate today, List<DemoTransaction> out) {
        Random random = new Random(day.toEpochDay() * 31 + 1);
        int dayOfMonth = day.getDayOfMonth();
        if (dayOfMonth == 5) {
            add(out, CHECKING, day, "SALARIO EMPRESA DEMO LTDA", "7350.00", "Salary");
        }
        if (dayOfMonth == 7) {
            add(out, CHECKING, day, "ALUGUEL APARTAMENTO", "-2150.00", "Rent");
        }
        if (dayOfMonth == 12) {
            add(out, CHECKING, day, "CONTA DE LUZ ENERGIA DEMO", negative(between(random, 150, 260)), "Electricity");
        }
        if (dayOfMonth == 15) {
            add(out, CHECKING, day, "INTERNET FIBRA DEMO", "-119.90", "Telecommunications");
        }
        if (dayOfMonth == DUE_DAY) {
            BigDecimal bill = billTotal(day.withDayOfMonth(CLOSING_DAY), today);
            if (bill.signum() > 0) {
                add(out, CHECKING, day, "PAGAMENTO FATURA CARTAO DEMO", bill.negate().toPlainString(), CARD_PAYMENT);
            }
        }
        if (DayOfWeek.SATURDAY.equals(day.getDayOfWeek()) || random.nextDouble() < 0.08) {
            add(out, CHECKING, day, "SUPERMERCADO BOM PRECO", negative(between(random, 90, 380)), "Groceries");
        }
        if (random.nextDouble() < 0.18) {
            add(out, CHECKING, day, "UBER *TRIP", negative(between(random, 14, 48)), "Taxi and ride-hailing");
        }
        if (random.nextDouble() < 0.2) {
            add(out, CHECKING, day, "PADARIA PAO QUENTE", negative(between(random, 8, 35)), "Eating out");
        }
        if (random.nextDouble() < 0.05) {
            add(out, CHECKING, day, "DROGARIA SAUDE DEMO", negative(between(random, 25, 140)), "Pharmacy");
        }
        if (random.nextDouble() < 0.04) {
            add(out, CHECKING, day, "PIX RECEBIDO FULANO DE TAL", between(random, 50, 300).toPlainString(), "Transfers");
        }
    }

    private void savingsDay(LocalDate day, List<DemoTransaction> out) {
        if (day.getDayOfMonth() == 1) {
            add(out, SAVINGS, day, "RENDIMENTO POUPANCA", "96.40", "Interest");
        }
    }

    private void cardDay(LocalDate day, LocalDate today, List<DemoTransaction> out) {
        if (day.getDayOfMonth() == DUE_DAY) {
            BigDecimal bill = billTotal(day.withDayOfMonth(CLOSING_DAY), today);
            if (bill.signum() > 0) {
                add(out, CARD, day, "PAGAMENTO RECEBIDO", bill.negate().toPlainString(), CARD_PAYMENT);
            }
        }
        cardCharges(day, today, out);
    }

    private void cardCharges(LocalDate day, LocalDate today, List<DemoTransaction> out) {
        Random random = new Random(day.toEpochDay() * 31 + 7);
        int dayOfMonth = day.getDayOfMonth();
        if (dayOfMonth == 8) {
            add(out, CARD, day, "STREAMING FILMES DEMO", "55.90", "Video streaming");
        }
        if (dayOfMonth == 14) {
            add(out, CARD, day, "STREAMING MUSICA DEMO", "21.90", "Music streaming");
        }
        if (dayOfMonth == 18) {
            int installment = (int) ChronoUnit.MONTHS.between(FIRST_DAY.withDayOfMonth(1), day.withDayOfMonth(1)) + 1;
            if (installment <= 10) {
                out.add(new DemoTransaction(CARD + "-" + day + "-notebook", CARD, day,
                        "MAGAZINE DEMO NOTEBOOK %02d/10".formatted(installment), new BigDecimal("389.90"),
                        "Electronics", installment, 10));
            }
        }
        if (random.nextDouble() < 0.22) {
            add(out, CARD, day, "DELIVERY COMIDA DEMO", between(random, 35, 120).toPlainString(), "Food delivery");
        }
        if (random.nextDouble() < 0.1) {
            add(out, CARD, day, "RESTAURANTE SABOR DEMO", between(random, 60, 220).toPlainString(), "Eating out");
        }
        if (random.nextDouble() < 0.07) {
            add(out, CARD, day, "POSTO DEMO COMBUSTIVEIS", between(random, 120, 280).toPlainString(), "Gas stations");
        }
        if (random.nextDouble() < 0.04) {
            add(out, CARD, day, "LOJA DE ROUPAS DEMO", between(random, 90, 400).toPlainString(), "Clothing");
        }
    }

    private static void add(List<DemoTransaction> out, String accountId, LocalDate day, String description,
                            String amount, String category) {
        out.add(new DemoTransaction(accountId + "-" + day + "-" + out.size(), accountId, day, description,
                new BigDecimal(amount), category, null, null));
    }

    // ---- bills ---------------------------------------------------------------------------

    /**
     * Closed bills only, newest last; each one charges the card purchases of its cycle. A cycle
     * that began before the generated history would be incomplete, so it is left out.
     */
    private List<Map<String, Object>> bills(LocalDate today) {
        List<Map<String, Object>> bills = new ArrayList<>();
        for (LocalDate closing = FIRST_DAY.withDayOfMonth(CLOSING_DAY).plusMonths(1);
             !closing.isAfter(today); closing = closing.plusMonths(1)) {
            BigDecimal total = billTotal(closing, today);
            if (total.signum() == 0) {
                continue;
            }
            Map<String, Object> bill = new LinkedHashMap<>();
            bill.put("id", "demo-bill-" + closing.getYear() + "-" + closing.getMonthValue());
            bill.put("dueDate", instant(closing.withDayOfMonth(DUE_DAY)));
            bill.put("billClosingDate", instant(closing));
            bill.put("totalAmount", total);
            bill.put("totalAmountCurrencyCode", "BRL");
            bill.put("minimumPaymentAmount", total.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP));
            bills.add(bill);
        }
        return bills;
    }

    /** The charges of the cycle that closes on {@code closing}; payments do not count. */
    private BigDecimal billTotal(LocalDate closing, LocalDate today) {
        LocalDate cycleStart = closing.minusMonths(1).plusDays(1);
        if (closing.isAfter(today) || cycleStart.isBefore(FIRST_DAY)) {
            return BigDecimal.ZERO;
        }
        List<DemoTransaction> charges = new ArrayList<>();
        for (LocalDate day = cycleStart; !day.isAfter(closing); day = day.plusDays(1)) {
            cardCharges(day, today, charges);
        }
        return sum(charges);
    }

    private LocalDate nextDueDate(LocalDate today) {
        LocalDate due = today.withDayOfMonth(DUE_DAY);
        return due.isBefore(today) ? due.plusMonths(1) : due;
    }

    // ---- investments ---------------------------------------------------------------------

    private List<Map<String, Object>> investments(LocalDate today) {
        long days = ChronoUnit.DAYS.between(FIRST_DAY, today);
        return List.of(
                investment("demo-cdb", "FIXED_INCOME", "CDB", "CDB Banco Demo 110% CDI",
                        grow("15000.00", "0.00042", days), "14200.00", LocalDate.of(2028, 3, 15)),
                investment("demo-tesouro", "FIXED_INCOME", "TREASURY", "Tesouro Selic 2029",
                        grow("8200.00", "0.00038", days), "8000.00", LocalDate.of(2029, 3, 1)),
                investment("demo-fund", "MUTUAL_FUND", "MULTIMARKET_FUND", "Fundo Multimercado Demo",
                        wave("6400.00", days), "6000.00", null),
                investment("demo-etf", "ETF", "ETF", "ETF Índice Demo",
                        wave("4800.00", days + 40), "5000.00", null),
                investment("demo-pension", "MUTUAL_FUND", "RETIREMENT", "Previdência Demo PGBL",
                        grow("9100.00", "0.00030", days), "8500.00", null));
    }

    private static Map<String, Object> investment(String id, String type, String subtype, String name,
                                                  BigDecimal balance, String original, LocalDate dueDate) {
        Map<String, Object> investment = new LinkedHashMap<>();
        investment.put("id", id);
        investment.put("itemId", BROKER_ITEM);
        investment.put("type", type);
        investment.put("subtype", subtype);
        investment.put("name", name);
        investment.put("currencyCode", "BRL");
        investment.put("balance", balance);
        investment.put("amountOriginal", new BigDecimal(original));
        investment.put("dueDate", nonNull(dueDate) ? instant(dueDate) : null);
        investment.put("status", "ACTIVE");
        return investment;
    }

    private static BigDecimal grow(String base, String dailyRate, long days) {
        return new BigDecimal(base).multiply(BigDecimal.ONE.add(new BigDecimal(dailyRate).multiply(BigDecimal.valueOf(days))))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal wave(String base, long days) {
        double factor = 1 + 0.04 * Math.sin(days / 9.0) + 0.0003 * days;
        return new BigDecimal(base).multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    // ---- helpers -------------------------------------------------------------------------

    private static String instant(LocalDate date) {
        return date.atStartOfDay(BRAZIL).toInstant().toString();
    }

    private static BigDecimal sum(List<DemoTransaction> transactions) {
        return transactions.stream().map(DemoTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal between(Random random, int min, int max) {
        return BigDecimal.valueOf(min + random.nextDouble() * (max - min)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String negative(BigDecimal amount) {
        return amount.negate().toPlainString();
    }
}
