package com.wallet.core.planning.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/**
 * A suggestion's id, {@code 9f86d081884c7d659a2feaa0c55ad015-9}: the fingerprint of the debits'
 * description words and their usual day, which tells two series with the same description apart.
 * It says nothing about the description to whoever reads it in a URL or a log.
 */
record SuggestionId(String descriptionKey, int dayOfMonth) {

    private static final Pattern FORMAT = Pattern.compile("([0-9a-f]{32})-([1-9]|[12][0-9]|3[01])");

    /** Empty when it is not an id this core hands out. */
    static Optional<SuggestionId> parse(String id) {
        if (isNull(id)) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(id);
        return matcher.matches()
                ? Optional.of(new SuggestionId(matcher.group(1), Integer.parseInt(matcher.group(2))))
                : Optional.empty();
    }

    String value() {
        return descriptionKey + "-" + dayOfMonth;
    }
}
