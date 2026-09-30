package com.wallet.core.shared.web;

import com.wallet.core.shared.error.DomainException;
import com.wallet.core.shared.error.ErrorType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static java.util.Objects.isNull;

/**
 * Page requested by the client. Pages start at 1 (what the app shows), not at 0 (what
 * Spring Data uses): {@link #toPageable(Sort)} does the conversion.
 */
public record PageQuery(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    public static PageQuery of(Integer page, Integer pageSize) {
        int resolvedPage = isNull(page) ? 1 : page;
        int resolvedSize = isNull(pageSize) ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPage < 1 || resolvedSize < 1 || resolvedSize > MAX_PAGE_SIZE) {
            throw invalid();
        }
        return new PageQuery(resolvedPage, resolvedSize);
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, pageSize, sort);
    }

    static DomainException invalid() {
        return new DomainException(ErrorType.UNPROCESSABLE, "page.invalid",
                "page must be 1 or more and pageSize between 1 and " + MAX_PAGE_SIZE);
    }
}
