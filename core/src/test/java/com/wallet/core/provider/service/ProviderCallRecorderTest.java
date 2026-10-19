package com.wallet.core.provider.service;

import com.wallet.core.provider.ProviderCallContext;
import com.wallet.core.provider.ProviderCallReason;
import com.wallet.core.provider.entity.ProviderCall;
import com.wallet.core.provider.mapper.ProviderCallMapper;
import com.wallet.core.provider.repository.ProviderCallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class ProviderCallRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private final ProviderCallRepository repository = mock(ProviderCallRepository.class);
    private final ProviderCallRecorder recorder = new ProviderCallRecorder(repository);

    @Test
    void storesTheCallAndLogsItAsOneKeyValueLine(CapturedOutput output) {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        ProviderCallContext context = new ProviderCallContext(userId, connectionId, "item-1", ProviderCallReason.MANUAL_SYNC);
        ProviderCall call = ProviderCallMapper.toEntity("PLUGGY", "LIST_ACCOUNTS", context, NOW, 84, 200, null);

        recorder.record(call);

        verify(repository).insert(call);
        assertThat(output.getAll()).contains("[PROVIDER_CALL] provider=PLUGGY operation=LIST_ACCOUNTS outcome=SUCCESS"
                + " status=200 durationMs=84 reason=MANUAL_SYNC userId=" + userId + " connectionId=" + connectionId
                + " itemId=item-1");
    }

    @Test
    void leavesOutWhatTheCallDoesNotHave(CapturedOutput output) {
        recorder.record(ProviderCallMapper.toEntity("PLUGGY", "AUTH", null, NOW, 3, null, "provider.unreachable"));

        assertThat(output.getAll())
                .contains("[PROVIDER_CALL] provider=PLUGGY operation=AUTH outcome=FAILURE errorCode=provider.unreachable durationMs=3")
                .doesNotContain("status=", "userId=", "reason=");
    }

    @Test
    void aFailedInsertIsLoggedNotThrown(CapturedOutput output) {
        doThrow(new DataAccessResourceFailureException("database down")).when(repository).insert(any());

        assertThatCode(() -> recorder.record(ProviderCallMapper.toEntity("PLUGGY", "FIND_ITEM", null, NOW, 50, 200, null)))
                .doesNotThrowAnyException();

        assertThat(output.getAll()).contains("Could not store the record of PLUGGY FIND_ITEM: database down");
    }
}
