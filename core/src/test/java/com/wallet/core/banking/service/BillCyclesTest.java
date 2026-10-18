package com.wallet.core.banking.service;

import com.wallet.core.banking.entity.AccountTransaction;
import com.wallet.core.banking.entity.CreditCardBill;
import com.wallet.core.shared.finance.Direction;
import com.wallet.core.shared.money.Money;
import com.wallet.core.shared.time.DateRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillCyclesTest {

    private static final CreditCardBill SEPTEMBER = bill("bill-sep", "2026-09-10", "2026-09-03");
    private static final CreditCardBill OCTOBER = bill("bill-oct", "2026-10-10", "2026-10-03");
    private static final Set<String> KNOWN = Set.of("bill-sep", "bill-oct");

    @Test
    void aCycleRunsFromTheDayAfterThePreviousClosingToThisOne() {
        assertThat(BillCycles.cycleOf(OCTOBER, SEPTEMBER))
                .isEqualTo(new DateRange(LocalDate.parse("2026-09-04"), LocalDate.parse("2026-10-03")));
    }

    @Test
    void theFirstBillCoversTheMonthBeforeItClosed() {
        assertThat(BillCycles.cycleOf(SEPTEMBER, null))
                .isEqualTo(new DateRange(LocalDate.parse("2026-08-04"), LocalDate.parse("2026-09-03")));
    }

    @Test
    void withoutAClosingDateTheBillClosesAWeekBeforeItIsDue() {
        CreditCardBill noClosing = bill("bill-nov", "2026-11-10", null);

        assertThat(BillCycles.cycleOf(noClosing, OCTOBER))
                .isEqualTo(new DateRange(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-11-03")));
    }

    @Test
    void theOpenBillClosesAMonthAfterTheLastOne() {
        assertThat(BillCycles.openCycleAfter(OCTOBER))
                .isEqualTo(new DateRange(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-11-03")));
    }

    /** An installment booked in August but charged on October's bill, as the provider links it. */
    @Test
    void aLinkWinsOverTheDayThePurchaseWasMade() {
        AccountTransaction installment = purchase("2026-08-15", "150.00", "bill-oct");
        AccountTransaction linkedElsewhere = purchase("2026-09-20", "40.00", "bill-sep");
        AccountTransaction inCycle = purchase("2026-09-20", "200.00", null);

        List<AccountTransaction> items = BillCycles.itemsOf("bill-oct", BillCycles.cycleOf(OCTOBER, SEPTEMBER), KNOWN,
                List.of(installment, linkedElsewhere, inCycle));

        assertThat(items).containsExactly(installment, inCycle);
    }

    @Test
    void outsideTheCycleWithoutALinkIsAnotherBills() {
        AccountTransaction before = purchase("2026-09-03", "10.00", null);
        AccountTransaction first = purchase("2026-09-04", "20.00", null);
        AccountTransaction last = purchase("2026-10-03", "30.00", null);
        AccountTransaction after = purchase("2026-10-04", "40.00", null);

        assertThat(BillCycles.itemsOf("bill-oct", BillCycles.cycleOf(OCTOBER, SEPTEMBER), KNOWN,
                List.of(before, first, last, after)))
                .containsExactly(first, last);
    }

    /** A link to a bill the Wallet does not have (the open one, say) counts by its date. */
    @Test
    void aLinkToAnUnknownBillFallsBackToTheCycle() {
        AccountTransaction linkedToOpen = purchase("2026-10-05", "80.00", "bill-nov");

        assertThat(BillCycles.itemsOf(null, BillCycles.openCycleAfter(OCTOBER), KNOWN, List.of(linkedToOpen)))
                .containsExactly(linkedToOpen);
    }

    @Test
    void payingThePreviousBillIsNotOnTheBillButARefundIs() {
        AccountTransaction payment = purchase("2026-09-10", "300.00", null);
        payment.setDirection(Direction.INFLOW);
        payment.setCategory("Credit card payment");
        AccountTransaction refund = purchase("2026-09-12", "20.00", null);
        refund.setDirection(Direction.INFLOW);
        refund.setCategory("Shopping");

        assertThat(BillCycles.itemsOf("bill-oct", BillCycles.cycleOf(OCTOBER, SEPTEMBER), KNOWN, List.of(payment, refund)))
                .containsExactly(refund);
    }

    @Test
    void theSamePurchaseFoundByLinkAndByDateCountsOnce() {
        AccountTransaction both = purchase("2026-09-20", "200.00", "bill-oct");

        assertThat(BillCycles.itemsOf("bill-oct", BillCycles.cycleOf(OCTOBER, SEPTEMBER), KNOWN, List.of(both, both)))
                .containsExactly(both);
    }

    @Test
    void aDeletedPurchaseIsGone() {
        AccountTransaction deleted = purchase("2026-09-20", "200.00", null);
        deleted.setDeletedAt(Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(BillCycles.itemsOf("bill-oct", BillCycles.cycleOf(OCTOBER, SEPTEMBER), KNOWN, List.of(deleted))).isEmpty();
    }

    private static CreditCardBill bill(String id, String dueDate, String closingDate) {
        return CreditCardBill.builder()
                .id(UUID.randomUUID())
                .providerBillId(id)
                .dueDate(LocalDate.parse(dueDate))
                .closingDate(closingDate == null ? null : LocalDate.parse(closingDate))
                .totalAmount(Money.of("100"))
                .build();
    }

    private static AccountTransaction purchase(String date, String amount, String billId) {
        return AccountTransaction.builder()
                .id(UUID.randomUUID())
                .bookedOn(LocalDate.parse(date))
                .amount(Money.of(amount))
                .direction(Direction.OUTFLOW)
                .category("Shopping")
                .providerBillId(billId)
                .build();
    }
}
