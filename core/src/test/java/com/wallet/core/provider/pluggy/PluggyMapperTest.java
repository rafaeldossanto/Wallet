package com.wallet.core.provider.pluggy;

import com.wallet.core.provider.ProviderItemStatus;
import com.wallet.core.shared.finance.AccountKind;
import com.wallet.core.shared.finance.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PluggyMapperTest {

    @Test
    void midnightUtcIsAPlainCalendarDate() {
        assertThat(PluggyMapper.toLocalDate(Instant.parse("2026-09-10T00:00:00Z"))).isEqualTo(LocalDate.parse("2026-09-10"));
    }

    @Test
    void otherMomentsAreReadInBrazilTime() {
        // 22:30 in São Paulo on the 9th is already the 10th in UTC.
        assertThat(PluggyMapper.toLocalDate(Instant.parse("2026-09-10T01:30:00Z"))).isEqualTo(LocalDate.parse("2026-09-09"));
        assertThat(PluggyMapper.toLocalDate(Instant.parse("2026-09-10T03:00:00Z"))).isEqualTo(LocalDate.parse("2026-09-10"));
    }

    @Test
    void itemStatusesCollapseIntoWhatTheWalletActsOn() {
        assertThat(PluggyMapper.toItemStatus("UPDATED")).isEqualTo(ProviderItemStatus.READY);
        assertThat(PluggyMapper.toItemStatus("MERGING")).isEqualTo(ProviderItemStatus.UPDATING);
        assertThat(PluggyMapper.toItemStatus("LOGIN_ERROR")).isEqualTo(ProviderItemStatus.NEEDS_USER_ACTION);
        assertThat(PluggyMapper.toItemStatus("OUTDATED")).isEqualTo(ProviderItemStatus.FAILED);
        assertThat(PluggyMapper.toItemStatus(null)).isEqualTo(ProviderItemStatus.FAILED);
    }

    @Test
    void bankTransactionWithoutTypeFallsBackToTheSign() {
        assertThat(PluggyMapper.toDirection(null, new BigDecimal("-10"), AccountKind.CHECKING)).isEqualTo(Direction.OUTFLOW);
        assertThat(PluggyMapper.toDirection(null, new BigDecimal("10"), AccountKind.SAVINGS)).isEqualTo(Direction.INFLOW);
    }
}
