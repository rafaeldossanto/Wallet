package com.wallet.bff.service;

import com.wallet.bff.exception.BffException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

import static java.util.Objects.isNull;

/**
 * The periods of the investments chart. "TUDO" is bounded by the longest period the core's net
 * worth history accepts (two years); the history only exists since the first sync anyway.
 */
public enum InvestmentPeriod {
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 91),
    SIX_MONTHS("6M", 182),
    ONE_YEAR("1A", 365),
    ALL("TUDO", 731);

    private final String code;
    private final int days;

    InvestmentPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    public String code() {
        return code;
    }

    public int days() {
        return days;
    }

    /** Six months when the app does not say. */
    public static InvestmentPeriod parse(String code) {
        if (isNull(code)) {
            return SIX_MONTHS;
        }
        return Arrays.stream(values())
                .filter(period -> period.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BffException(HttpStatus.BAD_REQUEST, "request.invalid_parameter",
                        "period must be one of 1M, 3M, 6M, 1A or TUDO"));
    }
}
