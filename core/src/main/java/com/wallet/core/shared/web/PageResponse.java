package com.wallet.core.shared.web;

import java.util.List;

/**
 * Paged list in the format every listing uses: {@code { items, page, pageSize, total, totalPages }}.
 */
public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int totalPages) {

    /**
     * Asking for a page past the last one is a client error ({@code page.invalid}), not an empty page.
     */
    public static <T> PageResponse<T> of(List<T> items, PageQuery query, long total) {
        int totalPages = (int) ((total + query.pageSize() - 1) / query.pageSize());
        if (total > 0 && query.page() > totalPages) {
            throw PageQuery.invalid();
        }
        return new PageResponse<>(List.copyOf(items), query.page(), query.pageSize(), total, totalPages);
    }
}
