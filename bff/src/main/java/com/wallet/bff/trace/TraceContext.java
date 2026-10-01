package com.wallet.bff.trace;

import lombok.experimental.UtilityClass;
import org.slf4j.MDC;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Request correlation, as in the Trilha BFF: the id is born here at the edge, goes to the logs
 * through the MDC and to the core in {@code X-Trace-Id}.
 */
@UtilityClass
public class TraceContext {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    public String current() {
        return MDC.get(MDC_KEY);
    }

    /** 8 hex characters: short enough to read out loud, enough to tell requests apart in logs. */
    public String generate() {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
