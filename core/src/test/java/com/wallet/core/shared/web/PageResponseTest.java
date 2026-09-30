package com.wallet.core.shared.web;

import com.wallet.core.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTest {

    @Test
    void countsPartialLastPage() {
        PageResponse<String> response = PageResponse.of(List.of("a"), PageQuery.of(3, 50), 101);

        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.total()).isEqualTo(101);
    }

    @Test
    void emptyListIsAValidFirstPage() {
        PageResponse<String> response = PageResponse.of(List.of(), PageQuery.of(1, 50), 0);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void pagePastTheLastIsRejected() {
        assertThatThrownBy(() -> PageResponse.of(List.of(), PageQuery.of(4, 50), 101))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "page.invalid");
    }
}
