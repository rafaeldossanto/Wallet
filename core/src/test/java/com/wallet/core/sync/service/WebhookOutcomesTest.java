package com.wallet.core.sync.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookOutcomesTest {

    @Test
    void theRefusalsOfTheManualRulesBecomeWhatTheyMean() {
        assertThat(WebhookOutcomes.ofRefusal("sync.too_soon")).isEqualTo("SKIPPED_COOLDOWN");
        assertThat(WebhookOutcomes.ofRefusal("sync.in_progress")).isEqualTo("SKIPPED_BUSY");
        assertThat(WebhookOutcomes.ofRefusal("rate_limit.exceeded")).isEqualTo("SKIPPED_BUSY");
        assertThat(WebhookOutcomes.ofRefusal("service.busy")).isEqualTo("SKIPPED_BUSY");
        assertThat(WebhookOutcomes.ofRefusal("connection.consent_expired")).isEqualTo("CONSENT_EXPIRED");
    }

    @Test
    void anythingElseIsAFailure() {
        assertThat(WebhookOutcomes.ofRefusal("connection.not_found")).isEqualTo("FAILED");
    }
}
