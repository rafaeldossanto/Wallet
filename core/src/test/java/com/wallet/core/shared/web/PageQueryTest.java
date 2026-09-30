package com.wallet.core.shared.web;

import com.wallet.core.shared.error.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void usesFirstPageAndDefaultSizeWhenNotInformed() {
        PageQuery query = PageQuery.of(null, null);

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.pageSize()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
    }

    @Test
    void rejectsPageBelowOneAndSizeOutOfRange() {
        assertInvalid(0, 10);
        assertInvalid(1, 0);
        assertInvalid(1, PageQuery.MAX_PAGE_SIZE + 1);
    }

    @Test
    void convertsToZeroBasedPageable() {
        Pageable pageable = PageQuery.of(3, 20).toPageable(Sort.unsorted());

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    private static void assertInvalid(Integer page, Integer pageSize) {
        assertThatThrownBy(() -> PageQuery.of(page, pageSize))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "page.invalid");
    }
}
